package com.badew.chatco

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.badew.chatco.databinding.ActivityChatBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Locale

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: MessageAdapter
    private var messagesListener: ListenerRegistration? = null
    private var chatMetaListener: ListenerRegistration? = null

    private lateinit var chatId: String
    private lateinit var otherUid: String
    private lateinit var otherName: String
    private var myUid: String = ""
    private var myName: String = "Kullanıcı"

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    private var latestMessageDocs: List<DocumentSnapshot> = emptyList()
    private var otherLastReadMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Mesaj yazma alanının navigasyon barının üstünde kalmasını sağlar
        ViewCompat.setOnApplyWindowInsetsListener(binding.inputRow) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        myUid = auth.currentUser?.uid ?: run {
            finish()
            return
        }

        otherUid = intent.getStringExtra(EXTRA_OTHER_UID) ?: run {
            finish()
            return
        }
        otherName = intent.getStringExtra(EXTRA_OTHER_NAME) ?: "Kullanıcı"
        chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: ChatIdHelper.forUsers(myUid, otherUid)

        binding.toolbar.title = otherName
        binding.toolbar.navigationIcon = AppCompatResources.getDrawable(
            this,
            androidx.appcompat.R.drawable.abc_ic_ab_back_material
        )
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = MessageAdapter(myUid)
        binding.rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvMessages.adapter = adapter

        db.collection("users").document(myUid).get()
            .addOnSuccessListener { doc ->
                myName = doc.getString("name") ?: "Kullanıcı"
            }

        binding.btnSend.setOnClickListener { sendTextMessage() }
        
        binding.btnAttach.visibility = View.GONE
        
        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendTextMessage()
                true
            } else {
                false
            }
        }
    }

    override fun onStart() {
        super.onStart()
        messagesListener = db.collection("chats").document(chatId).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(300)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    Toast.makeText(this, "Mesajlar yüklenemedi: ${e.message}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                latestMessageDocs = snap?.documents ?: emptyList()
                rebuildMessages()
                binding.rvMessages.post {
                    if (latestMessageDocs.isNotEmpty()) {
                        binding.rvMessages.scrollToPosition(latestMessageDocs.size - 1)
                    }
                }
            }

        chatMetaListener = db.collection("chats").document(chatId)
            .addSnapshotListener { doc, e ->
                if (e != null) return@addSnapshotListener
                otherLastReadMs = parseOtherLastRead(doc)
                rebuildMessages()
            }
    }

    override fun onStop() {
        super.onStop()
        messagesListener?.remove()
        messagesListener = null
        chatMetaListener?.remove()
        chatMetaListener = null
    }

    override fun onResume() {
        super.onResume()
        markConversationRead()
    }

    private fun markConversationRead() {
        db.collection("chats").document(chatId)
            .update(FieldPath.of("lastReadAt", myUid), FieldValue.serverTimestamp())
            .addOnFailureListener { }
    }

    private fun parseOtherLastRead(doc: DocumentSnapshot?): Long {
        if (doc == null || !doc.exists()) return 0L
        @Suppress("UNCHECKED_CAST")
        val map = doc.get("lastReadAt") as? Map<String, *> ?: return 0L
        val ts = map[otherUid] as? com.google.firebase.Timestamp ?: return 0L
        return ts.toDate().time
    }

    private fun rebuildMessages() {
        val list = buildMessageUiList(latestMessageDocs, otherLastReadMs)
        adapter.submit(list)
    }

    private fun buildMessageUiList(docs: List<DocumentSnapshot>, otherReadMs: Long): List<MessageUi> {
        val parsed = docs.map { doc ->
            val sender = doc.getString("senderUid") ?: ""
            val type = doc.getString("type") ?: "text"
            val text = doc.getString("text") ?: ""
            val ts = doc.getTimestamp("createdAt")
            val label = ts?.toDate()?.let { timeFmt.format(it) } ?: ""
            val millis = ts?.toDate()?.time
            MessageUi(
                id = doc.id,
                senderUid = sender,
                type = type,
                text = text,
                imageUrl = null,
                timeLabel = label,
                createdAtMillis = millis,
                showReadReceipt = false
            )
        }
        val lastMyIndex = parsed.indexOfLast { it.senderUid == myUid && it.createdAtMillis != null }
        return parsed.mapIndexed { index, m ->
            val showRead = index == lastMyIndex &&
                m.senderUid == myUid &&
                m.createdAtMillis != null &&
                otherReadMs >= m.createdAtMillis!!
            m.copy(showReadReceipt = showRead)
        }
    }

    private fun sendTextMessage() {
        val text = binding.etMessage.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return

        binding.etMessage.text?.clear()
        binding.progressSend.visibility = View.VISIBLE
        binding.btnSend.isEnabled = false

        val chatRef = db.collection("chats").document(chatId)
        val msgRef = chatRef.collection("messages").document()
        val msg = hashMapOf(
            "senderUid" to myUid,
            "type" to "text",
            "text" to text,
            "createdAt" to FieldValue.serverTimestamp()
        )
        val chatMeta = chatMetaUpdate(text)
        val batch = db.batch()
        batch.set(chatRef, chatMeta, SetOptions.merge())
        batch.set(msgRef, msg)
        batch.commit()
            .addOnSuccessListener {
                binding.progressSend.visibility = View.GONE
                binding.btnSend.isEnabled = true
            }
            .addOnFailureListener { err ->
                binding.progressSend.visibility = View.GONE
                binding.btnSend.isEnabled = true
                Toast.makeText(this, "Gönderilemedi: ${err.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun chatMetaUpdate(lastMessagePreview: String): HashMap<String, Any> {
        return hashMapOf(
            "memberIds" to listOf(myUid, otherUid),
            "lastMessage" to lastMessagePreview,
            "lastAt" to FieldValue.serverTimestamp(),
            "lastSenderUid" to myUid,
            "displayNames" to mapOf(myUid to myName, otherUid to otherName)
        )
    }

    companion object {
        const val EXTRA_CHAT_ID = "extra_chat_id"
        const val EXTRA_OTHER_UID = "extra_other_uid"
        const val EXTRA_OTHER_NAME = "extra_other_name"
    }
}
