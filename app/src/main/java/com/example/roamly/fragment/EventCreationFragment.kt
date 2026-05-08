package com.example.roamly.fragment

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.example.roamly.R
import com.example.roamly.activity.HomeActivity
import com.example.roamly.adapter.EventTypeAdapter
import com.example.roamly.adapter.InterestAdapter
import com.example.roamly.adapter.LanguageAdapter
import com.example.roamly.data.models.Event
import com.example.roamly.data.models.Interest
import com.example.roamly.data.models.Language
import com.example.roamly.data.repository.EventRepository
import com.example.roamly.data.repository.InterestRepository
import com.example.roamly.data.utils.EventAnnotationManager
import com.example.roamly.data.utils.EventTypeProvider
import com.example.roamly.data.utils.EventTypeProvider.EventTypeOption
import com.example.roamly.data.utils.InterestProvider
import com.example.roamly.data.utils.LanguageProvider
import com.example.roamly.data.utils.SupabaseClientProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.slider.RangeSlider
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class EventCreationFragment : Fragment() {

    private val tag = "EventCreationFragment"

    private lateinit var eventNameLayout: TextInputLayout
    private lateinit var eventTypeLayout: TextInputLayout
    private lateinit var dateInputLayout: TextInputLayout
    private lateinit var timeInputLayout: TextInputLayout

    private lateinit var eventNameInput: TextInputEditText
    private lateinit var eventTypeDropdown: MaterialAutoCompleteTextView
    private lateinit var interestsDropdown: MaterialAutoCompleteTextView
    private lateinit var interestsChipGroup: ChipGroup
    private lateinit var languagesDropdown: MaterialAutoCompleteTextView
    private lateinit var languagesChipGroup: ChipGroup
    private lateinit var ageRangeSlider: RangeSlider
    private lateinit var ageRangeText: TextView
    private lateinit var dateInput: TextInputEditText
    private lateinit var timeInput: TextInputEditText
    private lateinit var participantsSlider: Slider
    private lateinit var participantsValueText: TextView
    private lateinit var createEventButton: MaterialButton

    private lateinit var languageAdapter: LanguageAdapter
    private lateinit var interestAdapter: InterestAdapter

    private val selectedLanguages = mutableListOf<Language>()
    private val selectedInterests = mutableListOf<Interest>()
    private var selectedEventType: EventTypeOption? = null
    private var selectedEventDate: LocalDate? = LocalDate.now()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_create_event, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bindViews(view)
        configureSelectionFields()
        setupDropdowns()
        setupChipSelections()
        setupSliders()
        setupDatePicker()
        setupTimePicker()
        setupCreateButton()
    }

    override fun onDestroy() {
        super.onDestroy()
        (activity as? HomeActivity)
            ?.findViewById<FrameLayout>(R.id.eventFragmentContainer)
            ?.visibility = View.GONE
    }

    private fun bindViews(view: View) {
        eventNameLayout = view.findViewById(R.id.eventNameLayout)
        eventTypeLayout = view.findViewById(R.id.eventTypeLayout)
        dateInputLayout = view.findViewById(R.id.dateInputLayout)
        timeInputLayout = view.findViewById(R.id.timeInputLayout)
        eventNameInput = view.findViewById(R.id.descriptionInput)
        eventTypeDropdown = view.findViewById(R.id.eventTypeDropdown)
        interestsDropdown = view.findViewById(R.id.interestsDropdown)
        interestsChipGroup = view.findViewById(R.id.interestsChipGroup)
        languagesDropdown = view.findViewById(R.id.languagesDropdown)
        languagesChipGroup = view.findViewById(R.id.languagesChipGroup)
        ageRangeSlider = view.findViewById(R.id.ageRangeSlider)
        ageRangeText = view.findViewById(R.id.ageRangeText)
        dateInput = view.findViewById(R.id.dateInput)
        timeInput = view.findViewById(R.id.timeInput)
        participantsSlider = view.findViewById(R.id.participantsSlider)
        participantsValueText = view.findViewById(R.id.participantsValueText)
        createEventButton = view.findViewById(R.id.createEventButton)
    }

    private fun configureSelectionFields() {
        configureDropdown(eventTypeDropdown)
        configureDropdown(interestsDropdown)
        configureDropdown(languagesDropdown)
    }

    private fun configureDropdown(dropdown: MaterialAutoCompleteTextView) {
        dropdown.inputType = InputType.TYPE_NULL
        dropdown.keyListener = null
        dropdown.showSoftInputOnFocus = false
        dropdown.isCursorVisible = false
        dropdown.isClickable = true
        dropdown.threshold = 0
        dropdown.dropDownHeight = dp(312)
        dropdown.setDropDownBackgroundDrawable(
            ContextCompat.getDrawable(requireContext(), R.drawable.bg_dropdown_popup)
        )
        dropdown.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) hideKeyboard(view)
        }
    }

    private fun hideKeyboard(view: View) {
        val inputManager =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun setupDropdowns() {
        val eventTypes = EventTypeProvider.commonTypes()
        eventTypeDropdown.setAdapter(EventTypeAdapter(requireContext(), eventTypes))
        eventTypeDropdown.setOnClickListener {
            hideKeyboard(it)
            eventTypeDropdown.showDropDown()
        }

        eventTypeDropdown.setOnItemClickListener { parent, _, position, _ ->
            val selected = parent.getItemAtPosition(position) as EventTypeOption
            selectedEventType = selected
            eventTypeDropdown.setText(getString(selected.labelResId), false)
            eventTypeDropdown.clearFocus()
            eventTypeLayout.error = null
        }

        lifecycleScope.launch {
            try {
                val allInterests = InterestRepository.fetchAllInterests()
                val interestPairs = allInterests.map { interest ->
                    interest to (InterestProvider.getIconResIdFor(interest.name)
                        ?: R.drawable.ic_interest_default)
                }

                interestAdapter = InterestAdapter(requireContext(), interestPairs.toMutableList())
                interestsDropdown.setAdapter(interestAdapter)
                interestsDropdown.setOnClickListener {
                    hideKeyboard(it)
                    interestsDropdown.showDropDown()
                }

                val allLanguages = LanguageProvider.loadLanguagesFromAssets(requireContext())
                languageAdapter = LanguageAdapter(requireContext(), allLanguages.toMutableList())
                languagesDropdown.setAdapter(languageAdapter)
                languagesDropdown.setOnClickListener {
                    hideKeyboard(it)
                    languagesDropdown.showDropDown()
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to load interests or languages", e)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.event_data_load_error),
                    Toast.LENGTH_SHORT
                ).show()
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
            languagesDropdown.clearFocus()
        }

        interestsDropdown.setOnItemClickListener { parent, _, position, _ ->
            val (selectedInterest, iconResId) =
                parent.getItemAtPosition(position) as Pair<Interest, Int>
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
            interestsDropdown.clearFocus()
        }
    }

    private fun addChipWithIcon(
        chipGroup: ChipGroup,
        label: String,
        iconResId: Int,
        onRemove: () -> Unit
    ) {
        val strokeColor = ContextCompat.getColor(requireContext(), R.color.light_gray)
        val chip = Chip(requireContext()).apply {
            text = label
            chipIcon = ContextCompat.getDrawable(requireContext(), iconResId)
            chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
            chipStrokeColor = ColorStateList.valueOf(strokeColor)
            chipStrokeWidth = 1f
            closeIconTint = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.roamly_black))
            isCloseIconVisible = true
            isCheckable = false
            setTextColor(ContextCompat.getColor(requireContext(), R.color.roamly_black))
            setOnCloseIconClickListener {
                chipGroup.removeView(this)
                onRemove()
            }
        }
        chipGroup.addView(chip)
    }

    private fun setupSliders() {
        updateAgeRangeText(ageRangeSlider.values)
        ageRangeSlider.addOnChangeListener { slider, _, _ ->
            updateAgeRangeText(slider.values)
        }

        updateParticipantsText(participantsSlider.value.toInt())
        participantsSlider.addOnChangeListener { slider, _, _ ->
            updateParticipantsText(slider.value.toInt())
        }
    }

    private fun updateAgeRangeText(values: List<Float>) {
        ageRangeText.text = getString(
            R.string.event_age_value,
            values[0].toInt(),
            values[1].toInt()
        )
    }

    private fun updateParticipantsText(value: Int) {
        participantsValueText.text = getString(R.string.event_participants_value, value)
    }

    private fun setupDatePicker() {
        selectedEventDate?.let { dateInput.setText(formatDateForDisplay(it)) }

        dateInput.setOnClickListener {
            val constraints = CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.now())
                .build()

            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(getString(R.string.event_select_date_title))
                .setSelection(selectedEventDate?.toUtcMillis() ?: MaterialDatePicker.todayInUtcMilliseconds())
                .setCalendarConstraints(constraints)
                .build()

            picker.addOnPositiveButtonClickListener { selection ->
                selectedEventDate = Instant.ofEpochMilli(selection)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                dateInput.setText(formatDateForDisplay(selectedEventDate!!))
                dateInputLayout.error = null
            }

            picker.show(parentFragmentManager, "eventDatePicker")
        }
    }

    private fun formatDateForDisplay(date: LocalDate): String {
        val formatter = DateTimeFormatter
            .ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
        return date.format(formatter)
    }

    private fun LocalDate.toUtcMillis(): Long {
        return atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    private fun setupTimePicker() {
        timeInput.setOnClickListener {
            val fallback = LocalTime.now().plusHours(1).withMinute(0)
            val currentTime = parseSelectedTime() ?: fallback

            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(currentTime.hour)
                .setMinute(currentTime.minute)
                .setTitleText(getString(R.string.event_select_time_title))
                .build()

            picker.addOnPositiveButtonClickListener {
                val selectedTime = String.format(Locale.ROOT, "%02d:%02d", picker.hour, picker.minute)
                timeInput.setText(selectedTime)
                timeInputLayout.error = null
            }

            picker.show(parentFragmentManager, "eventTimePicker")
        }
    }

    private fun parseSelectedTime(): LocalTime? {
        return runCatching {
            val parts = timeInput.text?.toString().orEmpty().split(":")
            LocalTime.of(parts[0].toInt(), parts[1].toInt())
        }.getOrNull()
    }

    private fun setupCreateButton() {
        createEventButton.setOnClickListener {
            if (!validateForm()) return@setOnClickListener

            val profileId = SupabaseClientProvider.auth.currentUserOrNull()?.id
            if (profileId == null) {
                Toast.makeText(requireContext(), getString(R.string.event_auth_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val latitude = arguments?.getDouble("latitude") ?: return@setOnClickListener
            val longitude = arguments?.getDouble("longitude") ?: return@setOnClickListener

            val eventName = eventNameInput.text?.toString()?.trim().orEmpty()
            val eventType = selectedEventType?.key ?: return@setOnClickListener
            val date = selectedEventDate?.toString() ?: return@setOnClickListener
            val time = timeInput.text?.toString().orEmpty()
            val minAge = ageRangeSlider.values.getOrNull(0)?.toInt()
            val maxAge = ageRangeSlider.values.getOrNull(1)?.toInt()
            val maxParticipants = participantsSlider.value.toInt()
            val interests = selectedInterests.map { it.id }
            val languages = selectedLanguages.map { it.id }
            val generatedId = java.util.UUID.randomUUID().toString()

            val event = Event(
                id = generatedId,
                desc = eventName,
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
                vibe = eventType
            )

            createEventButton.isEnabled = false
            lifecycleScope.launch {
                try {
                    EventRepository.createEvent(event)
                    Toast.makeText(requireContext(), getString(R.string.event_create_success), Toast.LENGTH_SHORT).show()

                    val eventPoint = Point.fromLngLat(longitude, latitude)
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

                    parentFragmentManager.popBackStack(
                        "EventCreationFragment",
                        FragmentManager.POP_BACK_STACK_INCLUSIVE
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Failed to create event", e)
                    createEventButton.isEnabled = true
                    Toast.makeText(requireContext(), getString(R.string.event_create_error), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun validateForm(): Boolean {
        eventNameLayout.error = null
        eventTypeLayout.error = null
        dateInputLayout.error = null
        timeInputLayout.error = null

        var valid = true
        if (eventNameInput.text?.toString()?.trim().isNullOrBlank()) {
            eventNameLayout.error = getString(R.string.event_name_required)
            valid = false
        }
        if (selectedEventType == null) {
            eventTypeLayout.error = getString(R.string.event_type_required)
            valid = false
        }
        if (selectedEventDate == null) {
            dateInputLayout.error = getString(R.string.event_date_required)
            valid = false
        }
        if (timeInput.text?.toString()?.trim().isNullOrBlank()) {
            timeInputLayout.error = getString(R.string.event_time_required)
            valid = false
        }
        return valid
    }

    companion object {
        fun newInstance(latitude: Double, longitude: Double): EventCreationFragment {
            return EventCreationFragment().apply {
                arguments = Bundle().apply {
                    putDouble("latitude", latitude)
                    putDouble("longitude", longitude)
                }
            }
        }
    }
}
