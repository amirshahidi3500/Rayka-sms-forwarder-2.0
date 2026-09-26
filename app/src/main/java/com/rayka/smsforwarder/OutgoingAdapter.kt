package com.rayka.smsforwarder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OutgoingAdapter : RecyclerView.Adapter<OutgoingAdapter.VH>() {

    private val items = mutableListOf<OutgoingRecord>()
    private val fmt = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())

    fun submit(newItems: List<OutgoingRecord>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val phone: MaterialTextView = view.findViewById(R.id.txtOutPhone)
        val body: MaterialTextView = view.findViewById(R.id.txtOutBody)
        val mode: MaterialTextView = view.findViewById(R.id.txtOutMode)
        val sentAt: MaterialTextView = view.findViewById(R.id.txtOutSentAt)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_outgoing, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.phone.text = r.phone
        holder.body.text = r.body
        holder.sentAt.text = "ارسال: ${fmt.format(Date(r.sentAt))} — وضعیت: ${if (r.status == "SENT") "موفق" else "ناموفق"}"

        if (r.mode == MessageRecord.MODE_ONLINE) {
            holder.mode.text = "آنلاین"
            holder.mode.setBackgroundResource(R.drawable.bg_pill_online)
            holder.mode.setTextColor(holder.itemView.resources.getColor(R.color.online_green, holder.itemView.context.theme))
        } else {
            holder.mode.text = "آفلاین"
            holder.mode.setBackgroundResource(R.drawable.bg_pill_offline)
            holder.mode.setTextColor(holder.itemView.resources.getColor(R.color.offline_orange, holder.itemView.context.theme))
        }
    }
}
