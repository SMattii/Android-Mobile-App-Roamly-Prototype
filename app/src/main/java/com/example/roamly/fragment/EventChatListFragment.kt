package com.example.roamly.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.roamly.R
import com.example.roamly.adapter.EventChatListAdapter
import com.example.roamly.data.models.Event
import com.example.roamly.data.repository.EventRepository
import com.example.roamly.data.utils.SupabaseClientProvider
import kotlinx.coroutines.launch

/**
 * Fragment che mostra la lista di eventi a cui l'utente partecipa e per i quali è attiva una chat.
 *
 * Ogni elemento della lista rappresenta un evento e, se cliccato, apre la chat corrispondente
 * tramite un `EventChatFragment`.
 *
 * La lista viene caricata da Supabase in base all'utente autenticato.
 */
class EventChatListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EventChatListAdapter
    private val eventList = mutableListOf<Event>()

    /**
     * Infla il layout del fragment.
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_event_chat_list, container, false)
    }

    /**
     * Inizializza la RecyclerView e carica gli eventi dell'utente corrente.
     *
     * - Imposta il layout manager
     * - Inizializza l'adapter con listener click
     * - Avvia il caricamento asincrono degli eventi partecipati
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = view.findViewById(R.id.recyclerViewChatList)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = EventChatListAdapter(eventList) { event ->
            openChat(event.id!!, event.desc)
        }
        recyclerView.adapter = adapter

        loadUserEvents()
    }

    /**
     * Recupera tutti gli eventi a cui l'utente attualmente autenticato partecipa.
     * Popola la lista `eventList` e aggiorna l'adapter.
     */
    private fun loadUserEvents() {
        val currentUserId = SupabaseClientProvider.auth.currentUserOrNull()?.id ?: return
        lifecycleScope.launch {
            val allEvents = EventRepository.getEventsWithUserParticipation(currentUserId)
            eventList.clear()
            eventList.addAll(allEvents)
            adapter.notifyDataSetChanged()
        }
    }

    /**
     * Apre il fragment della chat per l’evento selezionato.
     *
     * @param eventId ID dell’evento da aprire in chat.
     * @param eventDesc Descrizione testuale dell’evento (usata come titolo chat).
     */
    private fun openChat(eventId: String, eventDesc: String) {
        val fragment = EventChatFragment.newInstance(eventId, eventDesc)
        parentFragmentManager.beginTransaction()
            .replace(R.id.chatFragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }
}