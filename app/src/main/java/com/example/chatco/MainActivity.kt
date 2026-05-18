package com.badew.chatco

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.badew.chatco.R
import com.badew.chatco.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var chatAdapter: ChatThreadAdapter
    private var chatsListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val userId = auth.currentUser?.uid
        if (userId != null) {
            FcmTokenRegistrar.register(db, userId)
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        binding.tvUserName.text = document.getString("name") ?: "Kullanıcı"
                        syncUsernameIndex(userId, document)
                    }
                }
        }

        chatAdapter = ChatThreadAdapter { item ->
            startActivity(
                Intent(this, ChatActivity::class.java).apply {
                    putExtra(ChatActivity.EXTRA_CHAT_ID, item.chatId)
                    putExtra(ChatActivity.EXTRA_OTHER_UID, item.otherUid)
                    putExtra(ChatActivity.EXTRA_OTHER_NAME, item.otherName)
                }
            )
        }
        binding.rvChats.layoutManager = LinearLayoutManager(this)
        binding.rvChats.adapter = chatAdapter

        binding.toolbar.inflateMenu(R.menu.menu_main)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_friend_requests -> {
                    startActivity(Intent(this, FriendRequestsActivity::class.java))
                    true
                }
                R.id.menu_friends -> {
                    startActivity(Intent(this, FriendsActivity::class.java))
                    true
                }
                else -> false
            }
        }

        binding.btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.fabNewChat.setOnClickListener {
            startActivity(Intent(this, AddFriendActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        val uid = auth.currentUser?.uid ?: return
        
        // Hata Index eksikligiydi. .orderBy'i kaldirdik, siralamayi kodla yapiyoruz.
        chatsListener = db.collection("chats")
            .whereArrayContains("memberIds", uid)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    binding.tvEmptyState.text = "Hata: ${e.message}"
                    binding.tvEmptyState.visibility = View.VISIBLE
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                
                val threads = snap.documents.mapNotNull { doc -> parseChatThread(doc, uid) }
                    .sortedByDescending { it.lastAtMillis ?: 0L }
                
                chatAdapter.submit(threads)

                val empty = threads.isEmpty()
                binding.tvEmptyState.visibility = if (empty) View.VISIBLE else View.GONE
                binding.rvChats.visibility = if (empty) View.GONE else View.VISIBLE
                if (empty) {
                    binding.tvEmptyState.setText(R.string.empty_chat_msg)
                }
            }
    }

    override fun onStop() {
        super.onStop()
        chatsListener?.remove()
        chatsListener = null
    }

    private fun parseChatThread(doc: DocumentSnapshot, myUid: String): ChatThreadUi? {
        val memberIds = (doc.get("memberIds") as? List<*>)?.map { it.toString() } ?: return null
        if (memberIds.size < 2) return null
        val otherUid = memberIds.firstOrNull { it != myUid } ?: return null
        @Suppress("UNCHECKED_CAST")
        val names = doc.get("displayNames") as? Map<String, Any>
        val otherName = (names?.get(otherUid) as? String) ?: "Kullanıcı"
        
        val lastMessage = doc.getString("lastMessage") ?: ""
        val lastAt = doc.getTimestamp("lastAt")?.toDate()?.time
        return ChatThreadUi(doc.id, otherUid, otherName, null, lastMessage, lastAt)
    }

    private fun syncUsernameIndex(uid: String, document: DocumentSnapshot) {
        val lower = document.getString("usernameLower")
        if (!lower.isNullOrBlank()) UsernameRegistry.writeMapping(db, uid, lower)
    }
}
