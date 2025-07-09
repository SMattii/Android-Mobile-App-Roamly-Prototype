package  com.example.roamly.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.roamly.R
import com.example.roamly.data.models.Event

/**
 * Adapter per una lista di eventi mostrati in una schermata di riepilogo.
 * Ogni item mostra:
 * - descrizione dell’evento
 * - data e ora
 * - un'icona basata sul tipo di evento ("party", "chill"...)
 *
 * L’adapter gestisce anche il click sugli elementi per navigare o interagire.
 *
 * @param events Lista di eventi da visualizzare.
 * @param onClick Callback eseguita quando un evento viene cliccato.
 */
class EventChatListAdapter(
    private val events: List<Event>,
    private val onClick: (Event) -> Unit
) : RecyclerView.Adapter<EventChatListAdapter.EventViewHolder>() {

    /**
     * Crea e restituisce un nuovo ViewHolder per un item evento.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event_chat, parent, false)
        return EventViewHolder(view)
    }

    /**
     * Restituisce il numero totale di eventi nella lista.
     */
    override fun getItemCount(): Int = events.size

    /**
     * Associa i dati dell’evento alla vista in una determinata posizione.
     */
    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(events[position])
    }

    /**
     * ViewHolder che rappresenta un singolo elemento della lista eventi.
     *
     * @param itemView La vista radice dell’item layout.
     */
    inner class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val descText = itemView.findViewById<TextView>(R.id.txt_event_desc)
        private val dateText = itemView.findViewById<TextView>(R.id.txt_event_datetime)
        private val iconType = itemView.findViewById<ImageView>(R.id.img_event_type)

        /**
         * Associa i dati di un [Event] alla UI dell’item.
         *
         * @param event Evento da visualizzare.
         */
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

        /**
         * Formatta la data e l’ora in una stringa leggibile.
         *
         * @param date Data dell’evento in formato stringa.
         * @param time Ora dell’evento in formato stringa.
         * @return Una stringa formattata o un fallback.
         */
        private fun formatDateTime(date: String?, time: String?): String {
            return if (!date.isNullOrBlank() && !time.isNullOrBlank()) {
                "$date, $time"
            } else {
                "Data non disponibile"
            }
        }
    }
}