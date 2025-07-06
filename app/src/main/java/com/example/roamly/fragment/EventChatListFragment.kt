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

class EventChatListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EventChatListAdapter
    private val eventList = mutableListOf<Event>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_event_chat_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = view.findViewById(R.id.recyclerViewChatList)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = EventChatListAdapter(eventList) { event ->
            openChat(event.id!!, event.desc)
        }
        recyclerView.adapter = adapter

        loadUserEvents()
    }

    private fun loadUserEvents() {
        val currentUserId = SupabaseClientProvider.auth.currentUserOrNull()?.id ?: return
        lifecycleScope.launch {
            val allEvents = EventRepository.getEventsWithUserParticipation(currentUserId)
            eventList.clear()
            eventList.addAll(allEvents)
            adapter.notifyDataSetChanged()
        }
    }

    private fun openChat(eventId: String, eventDesc: String) {
        val fragment = EventChatFragment.newInstance(eventId, eventDesc)
        parentFragmentManager.beginTransaction()
            .replace(R.id.chatFragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }
}