package com.example.roamly.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.roamly.R
import com.example.roamly.data.models.EventMessage
import com.example.roamly.data.models.EventMessageWithSender
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Adapter per visualizzare una lista di messaggi in una chat di gruppo evento.
 * Distingue tra messaggi inviati dall’utente loggato (vista "sent") e ricevuti dagli altri (vista "received").
 * I messaggi ricevuti possono mostrare intestazioni con nome e avatar se non consecutivi dallo stesso sender.
 *
 * @param messages Lista dei messaggi da visualizzare, con incluso il mittente.
 * @param currentUserId ID dell’utente loggato (per distinguere i messaggi "sent").
 * @param userColorMap Mappa da userId a colore della bolla, usata per differenziare visivamente i mittenti.
 */
class EventMessageAdapter(
    private val messages: List<EventMessageWithSender>,
    private val currentUserId: String,
    private val userColorMap: Map<String, Int>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    /**
     * Determina il tipo di vista per un messaggio in base al mittente.
     */
    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return if (message.sender.id == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    /**
     * Crea il ViewHolder per il tipo di vista specificato.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SENT) {
            val view = inflater.inflate(R.layout.item_event_message_sent, parent, false)
            SentViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_event_message_received, parent, false)
            ReceivedViewHolder(view)
        }
    }

    /**
     * Associa i dati del messaggio alla vista, gestendo anche l'intestazione nei ricevuti.
     */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val current = messages[position]
        val showHeader = position == 0 || messages[position - 1].message.sender_id != current.message.sender_id

        if (holder is SentViewHolder) {
            holder.bind(current.message)
        } else if (holder is ReceivedViewHolder) {
            holder.bind(current, showHeader, userColorMap[current.message.sender_id] ?: 0xFFE0E0E0.toInt())
        }
    }

    override fun getItemCount(): Int = messages.size


    /**
     * ViewHolder per i messaggi inviati dall’utente loggato.
     */
    class SentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.textMessage)
        private val timeText: TextView = view.findViewById(R.id.timeText)


        /**
         * Popola la vista con il testo del messaggio e l’orario.
         */
        fun bind(message: EventMessage) {
            messageText.text = message.message
            messageText.setBackgroundColor(Color.parseColor("#BBDEFB")) // blu chiaro
            timeText.text = formatTime(message.created_at)
        }
    }

    /**
     * ViewHolder per i messaggi ricevuti da altri utenti.
     * Mostra il nome e l’avatar del mittente solo se non è un messaggio consecutivo.
     */
    class ReceivedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.textMessage)
        private val senderName: TextView = view.findViewById(R.id.textSenderName)
        private val senderAvatar: ImageView = view.findViewById(R.id.imageSenderAvatar)
        private val timeText: TextView = view.findViewById(R.id.timeText)

        /**
         * Popola la vista con messaggio, orario, nome e avatar del mittente.
         * L’intestazione viene mostrata solo se `showHeader` è true.
         *
         * @param wrapper Messaggio e mittente associato.
         * @param showHeader Se true, mostra nome e avatar del mittente.
         * @param bubbleColor Colore della bolla assegnato a questo mittente.
         */
        fun bind(wrapper: EventMessageWithSender, showHeader: Boolean, bubbleColor: Int) {
            val (message, sender) = wrapper

            messageText.text = message.message
            messageText.setBackgroundColor(bubbleColor)
            timeText.text = formatTime(message.created_at)

            if (showHeader) {
                senderName.visibility = View.VISIBLE
                senderName.text = sender.full_name ?: "Anonimo"

                senderAvatar.visibility = View.VISIBLE
                Glide.with(senderAvatar.context)
                    .load(sender.profile_image_url)
                    .placeholder(android.R.drawable.sym_def_app_icon)
                    .circleCrop()
                    .into(senderAvatar)
            } else {
                senderName.visibility = View.GONE
                senderAvatar.visibility = View.INVISIBLE
            }
        }
    }
}

/**
 * Converte una stringa ISO-8601 in formato orario `HH:mm`, oppure restituisce `"??:??"` in caso di errore.
 *
 * @param isoTimestamp Timestamp in formato ISO (es. `2024-07-09T16:45:00Z`).
 * @return L’orario formattato o un fallback.
 */
private fun formatTime(isoTimestamp: String): String {
    return try {
        val instant = OffsetDateTime.parse(isoTimestamp)
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        instant.toLocalTime().format(formatter)
    } catch (e: Exception) {
        "??:??"
    }
}