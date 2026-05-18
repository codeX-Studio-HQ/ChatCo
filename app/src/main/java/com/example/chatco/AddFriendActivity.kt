package com.badew.chatco

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.badew.chatco.databinding.ActivityAddFriendBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class AddFriendActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddFriendBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddFriendBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.toolbar.navigationIcon = AppCompatResources.getDrawable(
            this,
            androidx.appcompat.R.drawable.abc_ic_ab_back_material
        )
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnSendRequest.setOnClickListener { sendFriendRequest() }
    }

    private fun sendFriendRequest() {
        val myUid = auth.currentUser?.uid ?: return
        val raw = binding.etUsername.text.toString().trim()
        if (raw.length < 2) {
            Toast.makeText(this, "Kullanıcı adını girin", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSendRequest.isEnabled = false
        binding.progressSend.visibility = View.VISIBLE

        UserDiscovery.findUserForFriendRequest(
            db,
            raw,
            onFound = { targetDoc -> proceedWithTargetUser(myUid, targetDoc) },
            onNotFound = {
                Toast.makeText(
                    this,
                    getString(R.string.friend_user_not_found_hint),
                    Toast.LENGTH_LONG
                ).show()
                resetSendUi()
            },
            onAmbiguousDisplayName = {
                Toast.makeText(this, getString(R.string.friend_user_ambiguous_name), Toast.LENGTH_LONG).show()
                resetSendUi()
            },
            onError = { e ->
                val msg = e.message ?: ""
                val hint = if (msg.contains("PERMISSION_DENIED", ignoreCase = true)) {
                    getString(R.string.firestore_permission_hint)
                } else {
                    getString(R.string.friend_search_error, msg)
                }
                Toast.makeText(this, hint, Toast.LENGTH_LONG).show()
                resetSendUi()
            }
        )
    }

    private fun resetSendUi() {
        binding.btnSendRequest.isEnabled = true
        binding.progressSend.visibility = View.GONE
    }

    private fun proceedWithTargetUser(myUid: String, targetDoc: DocumentSnapshot) {
        val toUid = targetDoc.id
        if (toUid == myUid) {
            Toast.makeText(this, "Kendinize istek gönderemezsiniz", Toast.LENGTH_SHORT).show()
            resetSendUi()
            return
        }

        val outgoingId = "${myUid}_$toUid"
        val incomingId = "${toUid}_$myUid"

        db.collection("friend_requests").document(incomingId).get()
            .addOnSuccessListener { incoming ->
                if (incoming.exists() && incoming.getString("status") == "pending") {
                    Toast.makeText(
                        this,
                        "Bu kullanıcı size zaten istek göndermiş; Gelen isteklerden kabul edebilirsiniz.",
                        Toast.LENGTH_LONG
                    ).show()
                    resetSendUi()
                    return@addOnSuccessListener
                }

                db.collection("friend_requests").document(outgoingId).get()
                    .addOnSuccessListener { outgoing ->
                        if (outgoing.exists() && outgoing.getString("status") == "pending") {
                            Toast.makeText(this, "İstek zaten gönderilmiş", Toast.LENGTH_SHORT).show()
                            resetSendUi()
                            return@addOnSuccessListener
                        }

                        db.collection("users").document(myUid).collection("friends").document(toUid).get()
                            .addOnSuccessListener { friendSnap ->
                                if (friendSnap.exists()) {
                                    Toast.makeText(this, "Zaten arkadaşsınız", Toast.LENGTH_SHORT).show()
                                    resetSendUi()
                                    return@addOnSuccessListener
                                }

                                db.collection("users").document(myUid).get()
                                    .addOnSuccessListener { mine ->
                                        val fromName = mine.getString("name") ?: "Kullanıcı"
                                        // fromPhotoUrl özelliği kaldırıldı
                                        val data = hashMapOf(
                                            "fromUid" to myUid,
                                            "toUid" to toUid,
                                            "fromName" to fromName,
                                            "status" to "pending",
                                            "createdAt" to FieldValue.serverTimestamp()
                                        )
                                        db.collection("friend_requests").document(outgoingId).set(data)
                                            .addOnSuccessListener {
                                                Toast.makeText(
                                                    this,
                                                    "Arkadaşlık isteği gönderildi",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                finish()
                                            }
                                            .addOnFailureListener { e ->
                                                Toast.makeText(
                                                    this,
                                                    "Gönderilemedi: ${e.message}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                resetSendUi()
                                            }
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                                        resetSendUi()
                                    }
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                                resetSendUi()
                            }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                        resetSendUi()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                resetSendUi()
            }
    }
}
