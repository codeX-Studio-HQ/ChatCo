package com.badew.chatco

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.badew.chatco.databinding.ItemMessageReceivedBinding
import com.badew.chatco.databinding.ItemMessageSentBinding

data class MessageUi(
    val id: String,
    val senderUid: String,
    val type: String,
    val text: String,
    val imageUrl: String?,
    val timeLabel: String,
    val createdAtMillis: Long?,
    val showReadReceipt: Boolean
)

class MessageAdapter(private val myUid: String) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SENT = 1
        private const val TYPE_RECEIVED = 2
    }

    private val items = mutableListOf<MessageUi>()

    fun submit(new: List<MessageUi>) {
        items.clear()
        items.addAll(new)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        // KESİN ÇÖZÜM: Gönderen UID'si ile sizin UID'niz eşleşiyorsa TYPE_SENT (Sağa yaslı)
        // Boşlukları temizleyerek karşılaştırma yapıyoruz.
        val sender = items[position].senderUid.trim()
        val current = myUid.trim()
        return if (sender == current) TYPE_SENT else TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SENT) {
            val b = ItemMessageSentBinding.inflate(inflater, parent, false)
            SentVH(b)
        } else {
            val b = ItemMessageReceivedBinding.inflate(inflater, parent, false)
            RecVH(b)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val m = items[position]
        when (holder) {
            is SentVH -> holder.bind(m)
            is RecVH -> holder.bind(m)
        }
    }

    class SentVH(private val binding: ItemMessageSentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(m: MessageUi) {
            binding.tvMessage.text = m.text
            binding.tvTime.text = m.timeLabel
            binding.tvRead.visibility = if (m.showReadReceipt) View.VISIBLE else View.GONE
        }
    }

    class RecVH(private val binding: ItemMessageReceivedBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(m: MessageUi) {
            binding.tvMessage.text = m.text
            binding.tvTime.text = m.timeLabel
        }
    }
}
