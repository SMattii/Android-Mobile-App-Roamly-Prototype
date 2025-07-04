package com.example.roamly.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.example.roamly.R
import com.example.roamly.activity.HomeActivity
import com.example.roamly.adapter.InterestAdapter
import com.example.roamly.adapter.LanguageAdapter
import com.example.roamly.data.models.Event
import com.example.roamly.data.models.Interest
import com.example.roamly.data.models.Language
import com.example.roamly.data.repository.EventRepository
import com.example.roamly.data.utils.LanguageProvider
import com.example.roamly.data.utils.SupabaseClientProvider
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.RangeSlider
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.launch
import com.example.roamly.data.repository.InterestRepository
import com.example.roamly.data.utils.EventAnnotationManager
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class EventCreationFragment : Fragment() {

    private val TAG = "EventCreationFragment"

    private lateinit var eventTypeDropdown: MaterialAutoCompleteTextView
    private lateinit var interestsDropdown: MaterialAutoCompleteTextView
    private lateinit var interestsChipGroup: ChipGroup
    private lateinit var languagesDropdown: MaterialAutoCompleteTextView
    private lateinit var languagesChipGroup: ChipGroup
    private lateinit var ageRangeSlider: RangeSlider
    private lateinit var ageRangeText: TextView
    private lateinit var dateDropdown: MaterialAutoCompleteTextView
    private lateinit var timeInput: EditText
    private lateinit var participantsSlider: Slider
    private lateinit var participantsValueText: TextView
    private lateinit var createEventButton: Button

    private lateinit var languageAdapter: LanguageAdapter
    private lateinit var interestAdapter: InterestAdapter

    private val selectedLanguages = mutableListOf<Language>()
    private val selectedInterests = mutableListOf<Interest>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView: inflating layout")
        return inflater.inflate(R.layout.fragment_create_event, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "onViewCreated: binding views")
        bindViews(view)

        Log.d(TAG, "onViewCreated: setting up dropdowns")
        setupDropdowns()

        Log.d(TAG, "onViewCreated: setting up chip selections")
        setupChipSelections()

        Log.d(TAG, "onViewCreated: setting up sliders")
        setupSliders()

        Log.d(TAG, "onViewCreated: setting up time picker")
        setupTimePicker()

        Log.d(TAG, "onViewCreated: setting up create button")
        setupCreateButton()
    }

    private fun bindViews(view: View) {
        try {
            eventTypeDropdown = view.findViewById(R.id.eventTypeDropdown)
            interestsDropdown = view.findViewById(R.id.interestsDropdown)
            interestsChipGroup = view.findViewById(R.id.interestsChipGroup)
            languagesDropdown = view.findViewById(R.id.languagesDropdown)
            languagesChipGroup = view.findViewById(R.id.languagesChipGroup)
            ageRangeSlider = view.findViewById(R.id.ageRangeSlider)
            ageRangeText = view.findViewById(R.id.ageRangeText)
            dateDropdown = view.findViewById(R.id.dateDropdown)
            timeInput = view.findViewById(R.id.timeInput)
            participantsSlider = view.findViewById(R.id.participantsSlider)
            participantsValueText = view.findViewById(R.id.participantsValueText)
            createEventButton = view.findViewById(R.id.createEventButton)
            Log.d(TAG, "bindViews: all views successfully bound")
        } catch (e: Exception) {
            Log.e(TAG, "bindViews: error binding views", e)
        }
    }

    private fun setupDropdowns() {

        val eventTypes = listOf("Chill", "Party")

        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy z")
        val today = ZonedDateTime.now()
        val tomorrow = today.plusDays(1)

        val dateOptions = listOf(
            today.format(formatter),
            tomorrow.format(formatter)
        )

        eventTypeDropdown.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, eventTypes)
        )

        dateDropdown.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, dateOptions)
        )

        lifecycleScope.launch {
            try {
                // --- Interessi ---
                val interestRepo = InterestRepository()
                val allInterests = interestRepo.fetchAllInterests()

                val interestIconMap = mapOf(
                    "nature" to R.drawable.ic_nature,
                    "networking" to R.drawable.ic_networking,
                    "nightlife" to R.drawable.ic_nightlife,
                    "culture" to R.drawable.ic_culture,
                    "sport" to R.drawable.ic_sport,
                    "food and drinks" to R.drawable.ic_food
                )

                val interestPairs = allInterests.mapNotNull { interest ->
                    interestIconMap[interest.name.lowercase()]?.let { iconResId ->
                        Pair(interest, iconResId)
                    }
                }

                interestAdapter = InterestAdapter(requireContext(), interestPairs.toMutableList())
                interestsDropdown.setAdapter(interestAdapter)

                // --- Lingue ---
                val allLanguages = LanguageProvider.loadLanguagesFromAssets(requireContext())
                languageAdapter = LanguageAdapter(requireContext(), allLanguages.toMutableList())
                languagesDropdown.setAdapter(languageAdapter)


            } catch (e: Exception) {
                Log.e(TAG, "Errore durante il caricamento di interessi o lingue", e)
                Toast.makeText(requireContext(), "Errore durante il caricamento dati", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupChipSelections() {
        languagesDropdown.setOnItemClickListener { parent, _, position, _ ->
            val selectedLanguage = parent.getItemAtPosition(position) as Language
            if (!selectedLanguages.contains(selectedLanguage)) {
                selectedLanguages.add(selectedLanguage)
                addChipWithIcon(
                    chipGroup = languagesChipGroup,
                    label = selectedLanguage.name,
                    iconResId = selectedLanguage.getFlagResId(requireContext())
                ) {
                    selectedLanguages.remove(selectedLanguage)
                }
            }
            languagesDropdown.setText("", false)
        }

        interestsDropdown.setOnItemClickListener { parent, _, position, _ ->
            val (selectedInterest, iconResId) = parent.getItemAtPosition(position) as Pair<Interest, Int>
            if (!selectedInterests.contains(selectedInterest)) {
                selectedInterests.add(selectedInterest)
                addChipWithIcon(
                    chipGroup = interestsChipGroup,
                    label = selectedInterest.name,
                    iconResId = iconResId
                ) {
                    selectedInterests.remove(selectedInterest)
                }
            }
            interestsDropdown.setText("", false)
        }
    }

    private fun addChipWithIcon(
        chipGroup: ChipGroup,
        label: String,
        iconResId: Int,
        onRemove: () -> Unit
    ) {
        val chip = Chip(requireContext()).apply {
            text = label
            chipIcon = ContextCompat.getDrawable(requireContext(), iconResId)
            isCloseIconVisible = true
            isCheckable = false
            setOnCloseIconClickListener {
                chipGroup.removeView(this)
                onRemove()
            }
        }
        chipGroup.addView(chip)
    }

    private fun setupSliders() {
        ageRangeSlider.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            ageRangeText.text = "Age range: ${values[0].toInt()} - ${values[1].toInt()}"
        }

        participantsValueText.text = "Participants: ${participantsSlider.value.toInt()}"
        participantsSlider.addOnChangeListener { slider, _, _ ->
            participantsValueText.text = "Participants: ${slider.value.toInt()}"
        }
    }

    private fun setupTimePicker() {
        timeInput.setOnClickListener {
            Log.d(TAG, "Time picker clicked")
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(18)
                .setMinute(0)
                .setTitleText("Select time")
                .build()

            picker.show(parentFragmentManager, "timePicker")
            picker.addOnPositiveButtonClickListener {
                val selectedTime = String.format("%02d:%02d", picker.hour, picker.minute)
                timeInput.setText(selectedTime)
                Log.d(TAG, "Time selected: $selectedTime")
            }
        }
    }

    private fun setupCreateButton() {
        Log.d(TAG, "setupCreateButton: attaching listener")

        createEventButton.setOnClickListener {
            Log.d(TAG, "createEventButton clicked")

            // Controllo autenticazione
            val profileId = SupabaseClientProvider.auth.currentUserOrNull()?.id
            if (profileId == null) {
                Log.w(TAG, "Utente non autenticato")
                Toast.makeText(requireContext(), "Utente non autenticato", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Log.d(TAG, "Utente autenticato con ID: $profileId")

            // Recupero coordinate dal bundle
            val latitude = arguments?.getDouble("latitude") ?: run {
                Log.e(TAG, "Latitude non trovata nei parametri")
                return@setOnClickListener
            }
            val longitude = arguments?.getDouble("longitude") ?: run {
                Log.e(TAG, "Longitude non trovata nei parametri")
                return@setOnClickListener
            }
            Log.d(TAG, "Coordinate recuperate: lat=$latitude, long=$longitude")

            // Recupero valori dai campi UI
            val eventType = eventTypeDropdown.text.toString()

            val rawDate = dateDropdown.text.toString().substringBefore(" ") // "03/07/2025"
            val inputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            val date = LocalDate.parse(rawDate, inputFormatter).toString() // "2025-07-03"

            val time = timeInput.text.toString()
            val minAge = ageRangeSlider.values.getOrNull(0)?.toInt()
            val maxAge = ageRangeSlider.values.getOrNull(1)?.toInt()
            val maxParticipants = participantsSlider.value.toInt()
            val interests = selectedInterests.map { it.id }
            val languages = selectedLanguages.map { it.id }
            val vibe = eventType // stesso valore di eventType per ora

            // Log dei dati inseriti
            Log.d(TAG, "Tipo evento: $eventType")
            Log.d(TAG, "Data: $date, Ora: $time")
            Log.d(TAG, "Range età: $minAge - $maxAge")
            Log.d(TAG, "Partecipanti max: $maxParticipants")
            Log.d(TAG, "Interessi: $interests")
            Log.d(TAG, "Lingue: $languages")

            val generatedId = java.util.UUID.randomUUID().toString()

            val event = Event(
                id = generatedId, // ✅ ID generato manualmente
                profile_id = profileId,
                latitude = latitude,
                longitude = longitude,
                event_type = eventType,
                interests = interests,
                languages = languages,
                date = date,
                time = time,
                min_age = minAge,
                max_age = maxAge,
                max_participants = maxParticipants,
                vibe = vibe
            )

            Log.d(TAG, "Evento costruito: $event")

            // Invio al repository per creazione su Supabase
            lifecycleScope.launch {
                try {
                    Log.d(TAG, "Invio evento a Supabase...")
                    EventRepository().createEvent(event)
                    Log.i(TAG, "Evento creato con successo")
                    Toast.makeText(requireContext(), "Evento creato!", Toast.LENGTH_SHORT).show()

                    val eventPoint = Point.fromLngLat(longitude, latitude)

                    // QUI CE IL PROBLEMA BISOGNA DICHIARARE CURRENTSHOWNEVENTID

                    EventAnnotationManager.createEventMarker(
                        context = requireContext(),
                        mapView = requireActivity().findViewById(R.id.mapView),
                        mapboxMap = requireActivity().findViewById<MapView>(R.id.mapView).mapboxMap,
                        event = event,
                        point = eventPoint,
                        getCurrentShownEventId = { (activity as? HomeActivity)?.currentShownEventId },
                        onToggleEventCallout = { newId ->
                            (activity as? HomeActivity)?.let { home ->
                                if (newId == null) {
                                    home.hideEventCallout()
                                } else {
                                    home.removeEventCallout()
                                    home.currentShownEventId = newId
                                }
                            }
                        }
                    )

                    // Torna indietro nella pila dei fragment
                    parentFragmentManager.popBackStack("EventCreationFragment", FragmentManager.POP_BACK_STACK_INCLUSIVE)
                } catch (e: Exception) {
                    Log.e(TAG, "Errore durante la creazione evento", e)
                    Toast.makeText(requireContext(), "Errore creazione evento", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        fun newInstance(latitude: Double, longitude: Double): EventCreationFragment {
            val fragment = EventCreationFragment()
            val args = Bundle()
            args.putDouble("latitude", latitude)
            args.putDouble("longitude", longitude)
            fragment.arguments = args
            return fragment
        }
    }
}