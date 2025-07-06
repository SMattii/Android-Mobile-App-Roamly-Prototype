package com.example.roamly.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        fun bind(event: Event) {
            descText.text = event.desc
            dateText.text = "${event.date} ${event.time}"
            itemView.setOnClickListener { onClick(event) }
        }
    }
}