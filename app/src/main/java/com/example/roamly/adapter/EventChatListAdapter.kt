package  com.example.roamly.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.roamly.R
import com.example.roamly.data.models.Event

class EventChatListAdapter(
    private val events: List<Event>,
    private val onClick: (Event) -> Unit
) : RecyclerView.Adapter<EventChatListAdapter.EventViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event_chat, parent, false)
        return EventViewHolder(view)
    }

    override fun getItemCount(): Int = events.size

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(events[position])
    }

    inner class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val descText = itemView.findViewById<TextView>(R.id.txt_event_desc)
        private val dateText = itemView.findViewById<TextView>(R.id.txt_event_datetime)
        private val iconType = itemView.findViewById<ImageView>(R.id.img_event_type)

        fun bind(event: Event) {
            descText.text = event.desc
            dateText.text = formatDateTime(event.date, event.time)

            val iconRes = when (event.event_type.lowercase()) {
                "party" -> R.drawable.ic_event_party
                "chill" -> R.drawable.ic_event_chill
                else -> R.drawable.ic_event_generic
            }
            iconType.setImageResource(iconRes)

            itemView.setOnClickListener { onClick(event) }
        }

        private fun formatDateTime(date: String?, time: String?): String {
            return if (!date.isNullOrBlank() && !time.isNullOrBlank()) {
                "$date, $time"
            } else {
                "Data non disponibile"
            }
        }
    }
}