package com.example.roamly.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.roamly.R
import com.example.roamly.adapter.EventMessageAdapter
import com.example.roamly.data.models.EventMessageWithSender
import com.example.roamly.data.models.Profile
import com.example.roamly.data.repository.EventRepository
import com.example.roamly.data.repository.ProfileRepository
import com.example.roamly.data.utils.SupabaseClientProvider
import kotlinx.coroutines.launch

/**
 * Fragment che gestisce la chat di gruppo per un evento.
 *
 * Mostra una lista di messaggi associati a un evento specifico, con supporto per:
 * - Invio di nuovi messaggi (solo se partecipante, altrimenti aggiunto automaticamente)
 * - Caricamento dei profili partecipanti (per mostrare nome, avatar, colori distintivi)
 * - Scroll automatico all'ultimo messaggio
 * - UI adattata dinamicamente alla `BottomNavigationView`
 *
 * I messaggi sono visualizzati tramite [EventMessageAdapter], con differenziazione visiva
 * tra mittente loggato e altri partecipanti.
 */
class EventChatFragment : Fragment() {

    companion object {
        /**
         * Crea una nuova istanza del fragment per una chat evento, factory method.
         *
         * @param eventId ID dell'evento da associare alla chat.
         * @param eventDesc Titolo descrittivo mostrato nella UI.
         */
        fun newInstance(eventId: String, eventDesc: String): EventChatFragment {
            val fragment = EventChatFragment()
            val args = Bundle()
            args.putString("event_id", eventId)
            args.putString("event_desc", eventDesc)
            fragment.arguments = args
            return fragment
        }
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var inputBox: EditText
    private lateinit var sendButton: ImageView
    private lateinit var adapter: EventMessageAdapter

    private lateinit var eventId: String
    private lateinit var currentUserId: String
    private lateinit var eventDesc: String

    private val messagesWithSender = mutableListOf<EventMessageWithSender>()
    private lateinit var participantsMap: Map<String, Profile>
    private var userColorMap: Map<String, Int> = emptyMap()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_event_chat, container, false)
    }

    /**
     * Inizializza la UI e imposta listener e logica di caricamento.
     *
     * - Imposta titolo evento nella toolbar
     * - Inizializza RecyclerView e layout manager
     * - Carica messaggi e partecipanti
     * - Registra il listener per il bottone di invio
     * - Applica padding dinamico per la compatibilità con la BottomNavigationView
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        eventId = requireArguments().getString("event_id")!!

        currentUserId = SupabaseClientProvider.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Utente non loggato")

        eventDesc = requireArguments().getString("event_desc") ?: "Chat Evento"
        val titleText = view.findViewById<TextView>(R.id.textChatTitle)
        titleText.text = eventDesc

        recyclerView = view.findViewById(R.id.recyclerViewEventChat)
        inputBox = view.findViewById(R.id.editTextMessage)
        sendButton = view.findViewById(R.id.buttonSend)

        // Solo layout manager, il resto lo fa loadParticipantsAndMessages()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Carica tutto
        loadParticipantsAndMessages()

        sendButton.setOnClickListener {
            val text = inputBox.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                inputBox.setText("")
            }
        }

        // Imposta padding dinamico in base alla BottomNavigationView della Home
        view.post {
            val bottomNav = requireActivity().findViewById<View>(R.id.bottomNavigationView)
            val bottomHeight = bottomNav?.height ?: 0

            val inputContainer = inputBox.parent as? View
            inputContainer?.updatePadding(bottom = bottomHeight)

            recyclerView.updatePadding(bottom = bottomHeight)
        }
    }

    /**
     * Invia un nuovo messaggio alla chat evento.
     *
     * - Verifica se l'utente è partecipante all'evento (e lo aggiunge se non lo è)
     * - Invia il messaggio tramite Supabase
     * - Ricarica messaggi e partecipanti per aggiornare la lista
     *
     * @param text Il contenuto testuale del messaggio da inviare.
     */
    private fun sendMessage(text: String) {
        lifecycleScope.launch {
            Log.d("EVENT_CHAT", "sendMessage iniziato per utente: $currentUserId")

            // 1. Controlla i partecipanti correnti
            val currentParticipants = EventRepository.getEventParticipants(eventId)
            Log.d("EVENT_CHAT", "Partecipanti attuali: $currentParticipants")

            // 2. Se l'utente non è tra i partecipanti, aggiungilo
            if (!currentParticipants.contains(currentUserId)) {
                Log.d("EVENT_CHAT", "Utente non trovato tra i partecipanti: lo aggiungo")
                val added = EventRepository.addParticipant(eventId, currentUserId)
                Log.d("EVENT_CHAT", "Aggiunta partecipante: $added")
            } else {
                Log.d("EVENT_CHAT", "Utente già partecipante")
            }

            // 3. Invio del messaggio
            EventRepository.sendMessageToEvent(eventId, currentUserId, text)
            Log.d("EVENT_CHAT", "Messaggio inviato: \"$text\"")

            // 4. Ricarica messaggi e partecipanti
            loadParticipantsAndMessages()
            Log.d("EVENT_CHAT", "Ricaricati messaggi e partecipanti")
        }
    }

    /**
     * Carica i partecipanti e i messaggi della chat evento.
     *
     * - Ottiene la lista di profili partecipanti
     * - Assegna un colore identificativo ad ogni partecipante
     * - Ottiene i messaggi e li associa ai profili mittenti
     * - Inizializza o aggiorna l’adapter della RecyclerView
     */
    private fun loadParticipantsAndMessages() {
        lifecycleScope.launch {
            val participantIds = EventRepository.getEventParticipants(eventId)

            // Carica profili dei partecipanti
            val profiles = ProfileRepository.getProfilesByIds(participantIds)

            // Mappa da sender_id a Profile
            participantsMap = profiles.associateBy { it.id }

            // Assegna colori ai partecipanti (passaggio 3 che vedremo tra poco)
            userColorMap = assignColorsToUsers(profiles)

            // Carica messaggi
            val messages = EventRepository.getMessagesForEvent(eventId)

            // Wrappa messaggi
            messagesWithSender.clear()
            messagesWithSender.addAll(
                messages.mapNotNull { msg ->
                    val senderProfile = participantsMap[msg.sender_id]
                    senderProfile?.let { EventMessageWithSender(msg, it) }
                }
            )

            // Aggiorna adapter
            adapter = EventMessageAdapter(messagesWithSender, currentUserId, userColorMap)
            recyclerView.adapter = adapter
            adapter.notifyDataSetChanged()
            recyclerView.scrollToPosition(messagesWithSender.size - 1)
        }
    }

    /**
     * Assegna un colore identificativo a ciascun partecipante (escluso l'utente loggato).
     * I colori sono ciclici tra una lista predefinita.
     *
     * @param profiles Lista dei profili partecipanti.
     * @return Mappa da userId a colore (Int).
     */
    private fun assignColorsToUsers(profiles: List<Profile>): Map<String, Int> {
        val availableColors = listOf(
            0xFFE57373.toInt(), // rosso
            0xFF64B5F6.toInt(), // azzurro
            0xFF81C784.toInt(), // verde
            0xFFFFB74D.toInt(), // arancio
            0xFFBA68C8.toInt(), // viola
            0xFFA1887F.toInt(), // marrone
            0xFFFF8A65.toInt(), // salmone
            0xFF4DD0E1.toInt(), // teal chiaro
            0xFFDCE775.toInt(), // lime
            0xFFFFD54F.toInt()  // giallo
        )

        val colorMap = mutableMapOf<String, Int>()
        var colorIndex = 0

        for (profile in profiles) {
            if (profile.id == currentUserId) continue // evito di colorare l'utente loggato
            colorMap[profile.id] = availableColors[colorIndex % availableColors.size]
            colorIndex++
        }

        return colorMap
    }
}