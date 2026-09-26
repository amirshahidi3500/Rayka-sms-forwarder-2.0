package com.rayka.smsforwarder

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogAdapter : RecyclerView.Adapter<LogAdapter.VH>() {

    private val items = mutableListOf<MessageRecord>()
    private val fmt = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())

    fun submit(newItems: List<MessageRecord>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val sender: MaterialTextView = view.findViewById(R.id.txtSender)
        val body: MaterialTextView = view.findViewById(R.id.txtBody)
        val mode: MaterialTextView = view.findViewById(R.id.txtMode)
        val receivedAt: MaterialTextView = view.findViewById(R.id.txtReceivedAt)
        val sentAt: MaterialTextView = view.findViewById(R.id.txtSentAt)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.sender.text = r.sender
        holder.body.text = r.body
        holder.receivedAt.text = "دریافت: ${fmt.format(Date(r.receivedAt))}"
        holder.sentAt.text = if (r.sentAt != null) "ارسال: ${fmt.format(Date(r.sentAt!!))}" else "ارسال: در انتظار"

        when (r.mode) {
            MessageRecord.MODE_ONLINE -> {
                holder.mode.text = "آنلاین"
                holder.mode.setBackgroundResource(R.drawable.bg_pill_online)
                holder.mode.setTextColor(holder.itemView.resources.getColor(R.color.online_green, holder.itemView.context.theme))
            }
            MessageRecord.MODE_OFFLINE -> {
                holder.mode.text = "آفلاین (سرور محلی)"
                holder.mode.setBackgroundResource(R.drawable.bg_pill_offline)
                holder.mode.setTextColor(holder.itemView.resources.getColor(R.color.offline_orange, holder.itemView.context.theme))
            }
            "QUEUED_THEN_SENT" -> {
                holder.mode.text = "ذخیره‌شده و ارسال بعدی"
                holder.mode.setBackgroundResource(R.drawable.bg_pill_online)
                holder.mode.setTextColor(holder.itemView.resources.getColor(R.color.online_green, holder.itemView.context.theme))
            }
            else -> {
                holder.mode.text = "در صف انتظار"
                holder.mode.setBackgroundResource(R.drawable.bg_pill_offline)
                holder.mode.setTextColor(holder.itemView.resources.getColor(R.color.queued_red, holder.itemView.context.theme))
            }
        }

        holder.itemView.startAnimation(AnimationUtils.loadAnimation(holder.itemView.context, R.anim.item_fade_in))
    }
}
