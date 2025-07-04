package com.example.roamly.fragment

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.roamly.R
import com.example.roamly.adapter.InterestAdapter
import com.example.roamly.adapter.LanguageAdapter
import com.example.roamly.data.models.Event
import com.example.roamly.data.models.Interest
import com.example.roamly.data.models.Language
import com.example.roamly.data.repository.EventRepository
import com.example.roamly.data.repository.InterestRepository
import com.example.roamly.data.utils.LanguageProvider
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.RangeSlider
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.launch

class EventEditFragment : Fragment() {

    private lateinit var interestsDropdown: MaterialAutoCompleteTextView
    private lateinit var interestsChipGroup: ChipGroup
    private lateinit var languagesDropdown: MaterialAutoCompleteTextView
    private lateinit var languagesChipGroup: ChipGroup
    private lateinit var ageRangeSlider: RangeSlider
    private lateinit var ageRangeText: TextView
    private lateinit var timeInput: EditText
    private lateinit var participantsSlider: Slider
    private lateinit var participantsValueText: TextView
    private lateinit var saveEventButton: Button

    private val selectedLanguages = mutableListOf<Language>()
    private val selectedInterests = mutableListOf<Interest>()
    private lateinit var languageAdapter: LanguageAdapter
    private lateinit var interestAdapter: InterestAdapter

    private var eventTime: java.time.LocalTime? = null

    private var currentEvent: Event? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_event_edit, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bindViews(view)

        ageRangeSlider.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            ageRangeText.text = "Age range: ${values[0].toInt()} - ${values[1].toInt()}"
        }

        participantsSlider.addOnChangeListener { slider, value, fromUser ->
            participantsValueText.text = "Participants: ${value.toInt()}"
        }

        timeInput.setOnClickListener {
            showTimePicker()
        }

        val event = eventToEdit
        if (event == null) {
            Toast.makeText(requireContext(), "Evento non valido", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        lifecycleScope.launch {
            val allLanguages = LanguageProvider.loadLanguagesFromAssets(requireContext())
            val allInterests = InterestRepository().fetchAllInterests()

            currentEvent = event
            setupDropdowns(allLanguages, allInterests)
            populateFields(event, allLanguages, allInterests)

            val participantIds = EventRepository().getEventParticipants(event.id!!)
            val currentParticipantsCount = participantIds.size

            participantsSlider.valueFrom = currentParticipantsCount.toFloat()

            // Se il valore corrente è inferiore, aggiorna anche il valore iniziale
            if (participantsSlider.value < currentParticipantsCount) {
                participantsSlider.value = currentParticipantsCount.toFloat()
            }
            participantsValueText.text = "Participants: ${participantsSlider.value.toInt()}"

        }

        saveEventButton.setOnClickListener {
            saveChanges()
        }
    }

    private fun bindViews(view: View) {
        interestsDropdown = view.findViewById(R.id.interestsDropdown)
        interestsChipGroup = view.findViewById(R.id.interestsChipGroup)
        languagesDropdown = view.findViewById(R.id.languagesDropdown)
        languagesChipGroup = view.findViewById(R.id.languagesChipGroup)
        ageRangeSlider = view.findViewById(R.id.ageRangeSlider)
        ageRangeText = view.findViewById(R.id.ageRangeText)
        timeInput = view.findViewById(R.id.timeInput)
        participantsSlider = view.findViewById(R.id.participantsSlider)
        participantsValueText = view.findViewById(R.id.participantsValueText)
        saveEventButton = view.findViewById(R.id.saveChangesButton)
    }

    private fun setupDropdowns(allLanguages: List<Language>, allInterests: List<Interest>) {
        languageAdapter = LanguageAdapter(requireContext(), allLanguages.toMutableList())
        languagesDropdown.setAdapter(languageAdapter)

        languagesDropdown.setOnItemClickListener { parent, _, position, _ ->
            val selected = parent.getItemAtPosition(position) as Language
            if (!selectedLanguages.contains(selected)) {
                selectedLanguages.add(selected)
                addChip(languagesChipGroup, selected.name, selected.getFlagResId(requireContext())) {
                    selectedLanguages.remove(selected)
                }
            }
            languagesDropdown.setText("", false)
        }

        val interestIconMap = mapOf(
            "nature" to R.drawable.ic_nature,
            "networking" to R.drawable.ic_networking,
            "nightlife" to R.drawable.ic_nightlife,
            "culture" to R.drawable.ic_culture,
            "sport" to R.drawable.ic_sport,
            "food and drinks" to R.drawable.ic_food
        )

        val interestPairs = allInterests.mapNotNull { interest ->
            interestIconMap[interest.name.lowercase()]?.let { icon ->
                interest to icon
            }
        }

        interestAdapter = InterestAdapter(requireContext(), interestPairs.toMutableList())
        interestsDropdown.setAdapter(interestAdapter)

        interestsDropdown.setOnItemClickListener { parent, _, position, _ ->
            val (selected, icon) = parent.getItemAtPosition(position) as Pair<Interest, Int>
            if (!selectedInterests.contains(selected)) {
                selectedInterests.add(selected)
                addChip(interestsChipGroup, selected.name, icon) {
                    selectedInterests.remove(selected)
                }
            }
            interestsDropdown.setText("", false)
        }
    }

    private fun populateFields(event: Event, allLanguages: List<Language>, allInterests: List<Interest>) {
        timeInput.setText(event.time)
        ageRangeSlider.setValues(event.min_age?.toFloat() ?: 18f, event.max_age?.toFloat() ?: 99f)
        ageRangeText.text = "Age range: ${event.min_age} - ${event.max_age}"
        participantsSlider.value = event.max_participants?.toFloat() ?: 2f
        participantsValueText.text = "Participants: ${event.max_participants}"

        eventTime = try {
            java.time.LocalTime.parse(event.time.take(5)) // Es: "20:30"
        } catch (e: Exception) {
            null
        }

        val selectedLangs = allLanguages.filter { event.languages.contains(it.id) }
        selectedLanguages.addAll(selectedLangs)
        selectedLangs.forEach {
            addChip(languagesChipGroup, it.name, it.getFlagResId(requireContext())) {
                selectedLanguages.remove(it)
            }
        }

        val selectedInts = allInterests.filter { event.interests.contains(it.id) }
        selectedInterests.addAll(selectedInts)
        selectedInts.forEach {
            val icon = getInterestIcon(it.name)
            addChip(interestsChipGroup, it.name, icon) {
                selectedInterests.remove(it)
            }
        }
    }

    private fun addChip(group: ChipGroup, label: String, icon: Int, onRemove: () -> Unit) {
        val chip = Chip(requireContext()).apply {
            text = label
            chipIcon = ContextCompat.getDrawable(requireContext(), icon)
            isCloseIconVisible = true
            isCheckable = false
            setOnCloseIconClickListener {
                group.removeView(this)
                onRemove()
            }
        }
        group.addView(chip)
    }

    private fun getInterestIcon(name: String): Int {
        return when (name.lowercase()) {
            "food and drinks" -> R.drawable.ic_food
            "nightlife" -> R.drawable.ic_nightlife
            "culture" -> R.drawable.ic_culture
            "nature" -> R.drawable.ic_nature
            "sport" -> R.drawable.ic_sport
            "networking" -> R.drawable.ic_networking
            else -> R.drawable.ic_interest_default
        }
    }

    private fun saveChanges() {
        val updated = currentEvent?.copy(
            time = timeInput.text.toString(),
            min_age = ageRangeSlider.values[0].toInt(),
            max_age = ageRangeSlider.values[1].toInt(),
            max_participants = participantsSlider.value.toInt(),
            interests = selectedInterests.map { it.id },
            languages = selectedLanguages.map { it.id }
        ) ?: return

        lifecycleScope.launch {
            val success = EventRepository().updateEvent(updated)
            if (success) {
                val tooltipContainer = requireActivity().findViewById<FrameLayout>(R.id.tooltipContainer)
                tooltipContainer?.removeAllViews()
                Toast.makeText(requireContext(), "Evento aggiornato!", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            } else {
                Toast.makeText(requireContext(), "Errore aggiornamento", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showTimePicker() {
        val currentHour =  eventTime?.hour ?: 20
        val currentMinute = eventTime?.minute ?: 0

        TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
            // Salva l'orario scelto in variabile e aggiorna UI
            val formattedTime = String.format("%02d:%02d", hourOfDay, minute)
            timeInput.setText(formattedTime)
        }, currentHour, currentMinute, true).show()
    }

    companion object {
        var eventToEdit: Event? = null

        fun newInstance(event: Event): EventEditFragment {
            eventToEdit = event
            return EventEditFragment()
        }
    }
}