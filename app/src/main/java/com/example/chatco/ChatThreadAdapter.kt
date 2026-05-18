package com.badew.chatco

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.badew.chatco.databinding.ItemChatThreadBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatThreadUi(
    val chatId: String,
    val otherUid: String,
    val otherName: String,
    val otherPhotoUrl: String?,
    val lastMessage: String,
    val lastAtMillis: Long?
)

class ChatThreadAdapter(
    private val onOpen: (ChatThreadUi) -> Unit
) : RecyclerView.Adapter<ChatThreadAdapter.VH>() {

    private val items = mutableListOf<ChatThreadUi>()
    private val timeFmt = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

    fun submit(new: List<ChatThreadUi>) {
        items.clear()
        items.addAll(new)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemChatThreadBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(private val binding: ItemChatThreadBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ChatThreadUi) {
            binding.tvTitle.text = item.otherName
            binding.tvLast.text = item.lastMessage.ifEmpty { " " }
            binding.tvTime.text = item.lastAtMillis?.let { timeFmt.format(Date(it)) } ?: ""

            binding.root.setOnClickListener { onOpen(item) }
        }
    }
}
