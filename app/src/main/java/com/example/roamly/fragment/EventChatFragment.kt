package com.example.roamly.fragment

import android.os.Bundle
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
import com.example.roamly.data.models.EventMessage
import com.example.roamly.data.repository.EventRepository
import com.example.roamly.data.utils.SupabaseClientProvider
import kotlinx.coroutines.launch

class EventChatFragment : Fragment() {

    companion object {
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
    private val messageList = mutableListOf<EventMessage>()

    private lateinit var eventId: String
    private lateinit var currentUserId: String
    private lateinit var eventDesc: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_event_chat, container, false)
    }

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

        adapter = EventMessageAdapter(messageList, currentUserId)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        loadMessages()

        sendButton.setOnClickListener {
            val text = inputBox.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                inputBox.setText("")
            }
        }

        // ✅ Imposta padding dinamico in base alla BottomNavigationView della Home
        view.post {
            val bottomNav = requireActivity().findViewById<View>(R.id.bottomNavigationView)
            val bottomHeight = bottomNav?.height ?: 0

            val inputContainer = inputBox.parent as? View
            inputContainer?.updatePadding(bottom = bottomHeight)

            recyclerView.updatePadding(bottom = bottomHeight)
        }
    }

    private fun loadMessages() {
        lifecycleScope.launch {
            val messages = EventRepository.getMessagesForEvent(eventId)
            messageList.clear()
            messageList.addAll(messages)
            adapter.notifyDataSetChanged()
            recyclerView.scrollToPosition(messageList.size - 1)
        }
    }

    private fun sendMessage(text: String) {
        lifecycleScope.launch {
            EventRepository.sendMessageToEvent(eventId, currentUserId, text)
            loadMessages()
        }
    }
}