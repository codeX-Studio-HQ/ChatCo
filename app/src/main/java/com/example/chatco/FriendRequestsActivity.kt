package com.badew.chatco

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.LinearLayoutManager
import com.badew.chatco.databinding.ActivityFriendRequestsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class FriendRequestsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFriendRequestsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: FriendRequestAdapter
    private var registration: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFriendRequestsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.toolbar.navigationIcon = AppCompatResources.getDrawable(
            this,
            androidx.appcompat.R.drawable.abc_ic_ab_back_material
        )
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = FriendRequestAdapter(
            onAccept = { item -> accept(item) },
            onDecline = { item -> decline(item) }
        )
        binding.rvRequests.layoutManager = LinearLayoutManager(this)
        binding.rvRequests.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        val myUid = auth.currentUser?.uid ?: return
        registration = db.collection("friend_requests")
            .whereEqualTo("toUid", myUid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    Toast.makeText(this, "Liste yüklenemedi: ${e.message}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                val list = snap.documents.mapNotNull { doc ->
                    val fromUid = doc.getString("fromUid") ?: return@mapNotNull null
                    val fromName = doc.getString("fromName") ?: "Kullanıcı"
                    // fromPhotoUrl kaldırıldı
                    FriendRequestItem(doc.id, fromUid, fromName, null)
                }
                adapter.submit(list)
                binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    override fun onStop() {
        super.onStop()
        registration?.remove()
        registration = null
    }

    private fun decline(item: FriendRequestItem) {
        db.collection("friend_requests").document(item.id).delete()
            .addOnFailureListener { e ->
                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            }
    }

    private fun accept(item: FriendRequestItem) {
        val myUid = auth.currentUser?.uid ?: return
        db.collection("users").document(myUid).get()
            .addOnSuccessListener { mine ->
                val myName = mine.getString("name") ?: "Kullanıcı"
                // photoUrl bilgisi artık kullanılmıyor
                db.collection("users").document(item.fromUid).get()
                    .addOnSuccessListener { fromUser ->
                        val batch = db.batch()
                        val meRef = db.collection("users").document(myUid).collection("friends").document(item.fromUid)
                        val themRef =
                            db.collection("users").document(item.fromUid).collection("friends").document(myUid)
                        batch.set(
                            meRef,
                            hashMapOf(
                                "uid" to item.fromUid,
                                "name" to item.fromName,
                                "addedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        batch.set(
                            themRef,
                            hashMapOf(
                                "uid" to myUid,
                                "name" to myName,
                                "addedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        batch.delete(db.collection("friend_requests").document(item.id))
                        batch.commit()
                            .addOnSuccessListener {
                                Toast.makeText(this, "Arkadaş eklendi", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Kabul edilemedi: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            }
    }
}
