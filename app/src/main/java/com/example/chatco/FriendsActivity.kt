package com.badew.chatco

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.badew.chatco.databinding.ActivityFriendsBinding
import com.badew.chatco.databinding.ItemFriendRowBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class FriendRowUi(val uid: String, val name: String, val photoUrl: String?)

class FriendsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFriendsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var registration: ListenerRegistration? = null
    private lateinit var adapter: FriendsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFriendsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.toolbar.title = getString(R.string.friends_title)
        binding.toolbar.navigationIcon = AppCompatResources.getDrawable(
            this,
            androidx.appcompat.R.drawable.abc_ic_ab_back_material
        )
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = FriendsAdapter { row ->
            val chatId = ChatIdHelper.forUsers(auth.currentUser!!.uid, row.uid)
            startActivity(
                Intent(this, ChatActivity::class.java).apply {
                    putExtra(ChatActivity.EXTRA_CHAT_ID, chatId)
                    putExtra(ChatActivity.EXTRA_OTHER_UID, row.uid)
                    putExtra(ChatActivity.EXTRA_OTHER_NAME, row.name)
                }
            )
        }
        binding.rvFriends.layoutManager = LinearLayoutManager(this)
        binding.rvFriends.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        val uid = auth.currentUser?.uid ?: return
        registration = db.collection("users").document(uid).collection("friends")
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    Toast.makeText(this, "Arkadaşlar yüklenemedi: ${e.message}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                val rows = snap.documents.map { doc ->
                    val name = doc.getString("name") ?: "Kullanıcı"
                    // photoUrl artık kullanılmıyor
                    FriendRowUi(doc.id, name, null)
                }.sortedBy { it.name.lowercase() }
                adapter.submit(rows)
                binding.tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    override fun onStop() {
        super.onStop()
        registration?.remove()
        registration = null
    }
}

class FriendsAdapter(
    private val onPick: (FriendRowUi) -> Unit
) : RecyclerView.Adapter<FriendsAdapter.VH>() {

    private val items = mutableListOf<FriendRowUi>()

    fun submit(new: List<FriendRowUi>) {
        items.clear()
        items.addAll(new)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val binding = ItemFriendRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(private val binding: ItemFriendRowBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: FriendRowUi) {
            binding.tvName.text = row.name


            binding.root.setOnClickListener { onPick(row) }
        }
    }
}
