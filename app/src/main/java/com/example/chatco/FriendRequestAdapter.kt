package com.badew.chatco

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.badew.chatco.databinding.ItemFriendRequestBinding

data class FriendRequestItem(
    val id: String,
    val fromUid: String,
    val fromName: String,
    val fromPhotoUrl: String?
)

class FriendRequestAdapter(
    private val onAccept: (FriendRequestItem) -> Unit,
    private val onDecline: (FriendRequestItem) -> Unit
) : RecyclerView.Adapter<FriendRequestAdapter.VH>() {

    private val items = mutableListOf<FriendRequestItem>()

    fun submit(new: List<FriendRequestItem>) {
        items.clear()
        items.addAll(new)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemFriendRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(private val binding: ItemFriendRequestBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FriendRequestItem) {
            binding.tvFromName.text = item.fromName
            // ivAvatar has been removed from layout, so code is removed as well.
            binding.btnAccept.setOnClickListener { onAccept(item) }
            binding.btnDecline.setOnClickListener { onDecline(item) }
        }
    }
}
