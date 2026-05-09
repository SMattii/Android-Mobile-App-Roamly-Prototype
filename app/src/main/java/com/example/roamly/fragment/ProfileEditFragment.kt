package com.example.roamly.fragment

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.roamly.R
import com.example.roamly.activity.HomeActivity
import com.example.roamly.activity.OnboardingActivity
import com.example.roamly.adapter.InterestAdapter
import com.example.roamly.adapter.LanguageAdapter
import com.example.roamly.data.models.*
import com.example.roamly.data.repository.InterestRepository
import com.example.roamly.data.repository.ProfileRepository
import com.example.roamly.data.utils.AuthSessionCache
import com.example.roamly.data.utils.AuthValidation
import com.example.roamly.data.utils.LanguageProvider
import com.example.roamly.data.utils.SupabaseClientProvider
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

/**
 * Fragment per modificare il profilo utente.
 *
 * Consente di aggiornare nome, cognome, età (non modificabile), paese, categoria,
 * vibe (chill/party), lingue parlate, interessi, immagine profilo e visibilità pubblica.
 * Include anche funzionalità per logout e cambio password.
 *
 * Utilizza Supabase per autenticazione, storage e persistenza dei dati utente.
 *
 * @see Profile
 * @see ProfileRepository
 * @see SupabaseClientProvider
 */
class ProfileEditFragment : Fragment() {

    private val profileRepository = ProfileRepository
    private val interestRepository = InterestRepository

    private lateinit var profileImageView: ShapeableImageView
    private lateinit var profilePhotoHint: TextView
    private lateinit var profileDisplayName: TextView
    private lateinit var profileDetailsText: TextView
    private lateinit var firstNameField: TextInputEditText
    private lateinit var lastNameField: TextInputEditText
    private lateinit var ageSlider: Slider
    private lateinit var ageValueText: TextView
    private lateinit var countryDropdown: MaterialAutoCompleteTextView
    private lateinit var categoryDropdown: MaterialAutoCompleteTextView
    private lateinit var vibeToggleGroup: MaterialButtonToggleGroup
    private lateinit var languagesDropdown: MaterialAutoCompleteTextView
    private lateinit var interestsDropdown: MaterialAutoCompleteTextView
    private lateinit var selectedLanguagesChipGroup: ChipGroup
    private lateinit var selectedInterestsChipGroup: ChipGroup
    private lateinit var selectedLanguagesEmptyText: TextView
    private lateinit var selectedInterestsEmptyText: TextView
    private lateinit var visibleSwitch: MaterialSwitch
    private lateinit var saveButton: Button
    private lateinit var btnClose: Button
    private lateinit var btnChangePassword: Button
    private lateinit var btnLogout: Button

    private lateinit var allLanguages: List<Language>
    private lateinit var allInterests: List<Interest>
    private lateinit var languageAdapter: LanguageAdapter

    private val selectedLanguages = mutableListOf<Language>()
    private val selectedInterests = mutableSetOf<Interest>()

    private var currentUserId: String? = null
    private var currentProfile: Profile? = null

    private var selectedImageUri: Uri? = null
    private var isSaving = false

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            profileImageView.setImageURI(it) // preview immediata
            uploadAndSaveImage(it)
        }
    }

    /**
     * Crea la view del fragment dal layout `fragment_edit_profile`.
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_edit_profile, container, false)

    /**
     * Inizializza la UI, carica il profilo corrente e imposta i listener.
     * Gestisce il salvataggio del profilo, il logout e il cambio password.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        configureSelectionFields()
        setupHeaderLiveUpdates()
        updateSelectionEmptyStates()

        // Permette selezione immagine e avvia upload
        profileImageView.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        // Chiude il fragment
        btnClose.setOnClickListener {
            parentFragmentManager.popBackStack()
            requireActivity().findViewById<View>(R.id.profileFragmentContainer).visibility = View.GONE
        }

        // Carica dati profilo utente
        lifecycleScope.launch {
            currentUserId = SupabaseClientProvider.auth.currentUserOrNull()?.id
            if (currentUserId == null) return@launch

            allLanguages = LanguageProvider.loadLanguagesFromAssets(requireContext())
            allInterests = interestRepository.fetchAllInterests()

            setupLanguageDropdown()
            setupInterestDropdown()

            loadUserProfileAndPopulate()
        }

        saveButton.setOnClickListener {
            saveProfileChanges()
        }

        // Esegue logout e torna alla schermata di onboarding
        btnLogout.setOnClickListener {
            lifecycleScope.launch {
                try {
                    // Prima di fare logout, resetta lo stato della mappa se possibile
                    (requireActivity() as? HomeActivity)?.let { homeActivity ->
                        homeActivity.resetMapState()
                    }

                    // Fai logout
                    SupabaseClientProvider.auth.signOut()
                    AuthSessionCache.clear(requireContext())

                    // Redirect a OnboardingActivity
                    val intent = Intent(requireContext(), OnboardingActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    requireActivity().finish()

                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Errore durante il logout", Toast.LENGTH_SHORT).show()
                    Log.e("Logout", "Logout fallito: ${e.localizedMessage}")
                }
            }
        }

        // Mostra dialog per il cambio password
        btnChangePassword.setOnClickListener {
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_change_password, null)
            val newPasswordInput = dialogView.findViewById<EditText>(R.id.newPasswordInput)
            val confirmPasswordInput = dialogView.findViewById<EditText>(R.id.confirmPasswordInput)

            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Change Password")
                .setView(dialogView)
                .setPositiveButton("Change", null)
                .setNegativeButton("Cancel", null)
                .create()

            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val newPassword = newPasswordInput.text.toString()
                    val confirmPassword = confirmPasswordInput.text.toString()

                    val validationError = AuthValidation.passwordValidationMessage(newPassword)
                        ?: AuthValidation.passwordsMatchMessage(newPassword, confirmPassword)
                    if (validationError != null) {
                        Toast.makeText(requireContext(), validationError, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    lifecycleScope.launch {
                        try {
                            SupabaseClientProvider.auth.updateUser {
                                password = newPassword
                            }
                            Toast.makeText(requireContext(), "Password changed successfully", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "Password update failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            dialog.show()
        }
    }

    /**
     * Associa le view XML alle variabili del fragment.
     */
    private fun bindViews(view: View) {
        profileImageView = view.findViewById(R.id.profileImageView)
        profilePhotoHint = view.findViewById(R.id.profilePhotoHint)
        profileDisplayName = view.findViewById(R.id.profileDisplayName)
        profileDetailsText = view.findViewById(R.id.profileDetailsText)
        firstNameField = view.findViewById(R.id.firstNameField)
        lastNameField = view.findViewById(R.id.lastNameField)
        ageSlider = view.findViewById(R.id.ageSlider)
        ageValueText = view.findViewById(R.id.ageValueText)
        countryDropdown = view.findViewById(R.id.countryDropdown)
        categoryDropdown = view.findViewById(R.id.categoryDropdown)
        vibeToggleGroup = view.findViewById(R.id.vibeToggleGroup)
        languagesDropdown = view.findViewById(R.id.languagesDropdown)
        interestsDropdown = view.findViewById(R.id.interestsDropdown)
        selectedLanguagesChipGroup = view.findViewById(R.id.selectedLanguagesChipGroup)
        selectedInterestsChipGroup = view.findViewById(R.id.selectedInterestsChipGroup)
        selectedLanguagesEmptyText = view.findViewById(R.id.selectedLanguagesEmptyText)
        selectedInterestsEmptyText = view.findViewById(R.id.selectedInterestsEmptyText)
        visibleSwitch = view.findViewById(R.id.visibleSwitch)
        saveButton = view.findViewById(R.id.saveButton)
        btnClose = view.findViewById(R.id.btnCloseProfile)
        btnChangePassword = view.findViewById(R.id.btnChangePassword)
        btnLogout = view.findViewById(R.id.btnLogout)
    }

    private fun configureSelectionFields() {
        configureDropdown(languagesDropdown)
        configureDropdown(interestsDropdown)

        ageSlider.addOnChangeListener { _, value, _ ->
            ageValueText.text = getString(R.string.profile_age_value, value.toInt())
            updateProfileSummary()
        }
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
        dropdown.setOnClickListener {
            hideKeyboard(it)
            dropdown.showDropDown()
        }
    }

    private fun setupHeaderLiveUpdates() {
        firstNameField.doAfterTextChanged { updateProfileSummary() }
        lastNameField.doAfterTextChanged { updateProfileSummary() }
        vibeToggleGroup.addOnButtonCheckedListener { _, _, _ -> updateProfileSummary() }
    }

    private fun hideKeyboard(view: View) {
        val inputManager =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    /**
     * Configura il dropdown delle lingue con autocomplete e chip removibili.
     */
    private fun setupLanguageDropdown() {
        languageAdapter = LanguageAdapter(requireContext(), allLanguages.toMutableList())
        languagesDropdown.setAdapter(languageAdapter)

        languagesDropdown.setOnItemClickListener { parent, _, position, _ ->
            val selectedLanguage = parent.getItemAtPosition(position) as Language

            if (!selectedLanguages.contains(selectedLanguage)) {
                selectedLanguages.add(selectedLanguage)
                addLanguageChip(selectedLanguage)
                updateLanguageDropdown()
            }
            languagesDropdown.setText("", false)
            languagesDropdown.clearFocus()
            updateSelectionEmptyStates()
        }
    }

    /**
     * Aggiorna il dropdown lingue escludendo quelle già selezionate.
     */
    private fun updateLanguageDropdown() {
        val remainingLanguages = allLanguages.filterNot { selectedLanguages.contains(it) }
        languageAdapter.updateLanguages(remainingLanguages)
    }

    /**
     * Aggiunge un chip visivo per una lingua selezionata, con icona bandiera.
     */
    private fun addLanguageChip(language: Language) {
        val chip = Chip(requireContext()).apply {
            text = language.name
            val resId = language.getFlagResId(context)
            chipIcon = if (resId != 0) {
                ContextCompat.getDrawable(context, resId)
            } else {
                ContextCompat.getDrawable(context, R.drawable.ic_flag_default)
            }
            isCloseIconVisible = true
            isClickable = true
            isCheckable = false
            applySelectionChipStyle(tintIcon = false)
            setOnCloseIconClickListener {
                selectedLanguages.remove(language)
                selectedLanguagesChipGroup.removeView(this)
                updateLanguageDropdown()
                updateSelectionEmptyStates()
            }
        }
        selectedLanguagesChipGroup.addView(chip)
        updateSelectionEmptyStates()
    }

    /**
     * Configura il dropdown interessi con autocomplete e chip.
     */
    private fun setupInterestDropdown() {
        val interestPairs = allInterests.map { it to R.drawable.ic_interest_default }
        val interestAdapter = InterestAdapter(requireContext(), interestPairs.toMutableList())
        interestsDropdown.setAdapter(interestAdapter)

        interestsDropdown.setOnItemClickListener { parent, _, position, _ ->
            @Suppress("UNCHECKED_CAST")
            val selectedPair = parent.getItemAtPosition(position) as Pair<Interest, Int>
            val interest = selectedPair.first
            if (selectedInterests.add(interest)) {
                addInterestChip(interest)
            }
            interestsDropdown.setText("", false)
            interestsDropdown.clearFocus()
            updateSelectionEmptyStates()
        }
    }

    /**
     * Aggiunge un chip visivo per un interesse selezionato.
     */
    private fun addInterestChip(interest: Interest) {
        val chip = Chip(requireContext()).apply {
            text = interest.name
            val iconResId = getInterestIconResId(interest.name)
            chipIcon = ContextCompat.getDrawable(context, iconResId)
            chipIconSize = 48f // opzionale: controlla grandezza icona
            isCloseIconVisible = true
            isClickable = true
            isCheckable = false
            applySelectionChipStyle(tintIcon = true)
            setOnCloseIconClickListener {
                selectedInterests.remove(interest)
                selectedInterestsChipGroup.removeView(this)
                updateSelectionEmptyStates()
            }
        }
        selectedInterestsChipGroup.addView(chip)
        updateSelectionEmptyStates()
    }

    private fun Chip.applySelectionChipStyle(tintIcon: Boolean) {
        chipBackgroundColor = ColorStateList.valueOf(
            ContextCompat.getColor(context, R.color.roamly_blush)
        )
        setTextColor(ContextCompat.getColor(context, R.color.roamly_black))
        closeIconTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.roamly_black))
        chipIconTint = if (tintIcon) {
            ColorStateList.valueOf(ContextCompat.getColor(context, R.color.roamly_black))
        } else {
            null
        }
    }

    private fun updateSelectionEmptyStates() {
        selectedLanguagesEmptyText.visibility =
            if (selectedLanguages.isEmpty()) View.VISIBLE else View.GONE
        selectedInterestsEmptyText.visibility =
            if (selectedInterests.isEmpty()) View.VISIBLE else View.GONE
    }

    /**
     * Carica i dati del profilo da Supabase e popola i campi del fragment.
     */
    private suspend fun loadUserProfileAndPopulate() {
        val userId = currentUserId ?: return
        val profileData = profileRepository.getCompleteProfile(userId)
        val profile = profileData?.profile ?: return
        currentProfile = profile

        firstNameField.setText(profile.first_name ?: "")
        lastNameField.setText(profile.last_name ?: "")

        val ageInt = profile.age?.trim()?.toIntOrNull()
        val clampedAge = ageInt?.coerceIn(18, 99) ?: 18

        ageSlider.value = clampedAge.toFloat()
        ageValueText.text = getString(R.string.profile_age_value, clampedAge)

        countryDropdown.setText(profile.country ?: "")
        categoryDropdown.setText(profile.category ?: "")
        visibleSwitch.isChecked = profile.visible

        when (profile.vibe?.lowercase()) {
            "chill" -> vibeToggleGroup.check(R.id.vibeChill)
            "party" -> vibeToggleGroup.check(R.id.vibeParty)
        }

        if (!profile.profile_image_url.isNullOrBlank()) {
            Glide.with(this).load(profile.profile_image_url).circleCrop().into(profileImageView)
        }

        profileData.selectedInterests.forEach {
            if (selectedInterests.add(it)) {
                addInterestChip(it)
            }
        }

        val languageEntries = SupabaseClientProvider.db.from("profile_languages")
            .select()
            .decodeList<LanguageLink>()
            .filter { it.profile_id == userId }

        val existingLanguages = languageEntries.mapNotNull { entry ->
            allLanguages.find { it.id == entry.language_id }
        }

        existingLanguages.forEach {
            if (!selectedLanguages.contains(it)) {
                selectedLanguages.add(it)
                addLanguageChip(it)
            }
        }

        updateLanguageDropdown()
        updateProfileSummary()
        updateSelectionEmptyStates()
    }

    /**
     * Salva le modifiche effettuate al profilo su Supabase.
     */
    private fun saveProfileChanges() {
        if (isSaving) return

        val firstName = firstNameField.text?.toString()?.trim().orEmpty()
        val lastName = lastNameField.text?.toString()?.trim().orEmpty()
        val fullName = listOf(firstName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        val profile = currentProfile?.copy(
            full_name = fullName.ifBlank { null },
            first_name = firstName.ifBlank { null },
            last_name = lastName.ifBlank { null },
            age = ageSlider.value.toInt().toString(),
            vibe = when (vibeToggleGroup.checkedButtonId) {
                R.id.vibeChill -> "chill"
                R.id.vibeParty -> "party"
                else -> null
            },
            visible = visibleSwitch.isChecked
        ) ?: return

        val languageCodes = selectedLanguages.map { it.id }
        val interestIds = selectedInterests.map { it.id }

        lifecycleScope.launch {
            setSaveLoading(true)
            try {
                val success = profileRepository.saveCompleteProfile(
                    profile = profile,
                    languageIds = languageCodes,
                    interestIds = interestIds
                )

                if (success) {
                    currentProfile = profile
                    AuthSessionCache.rememberProfile(requireContext(), profile)
                    updateProfileSummary()
                }

                Toast.makeText(
                    requireContext(),
                    if (success) "Profilo aggiornato con successo" else "Errore nel salvataggio",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                setSaveLoading(false)
            }
        }
    }

    private fun setSaveLoading(isLoading: Boolean) {
        isSaving = isLoading
        saveButton.isEnabled = !isLoading
        saveButton.text = getString(
            if (isLoading) R.string.profile_save_loading else R.string.profile_save
        )
    }

    private fun updateProfileSummary() {
        val firstName = firstNameField.text?.toString()?.trim().orEmpty()
        val lastName = lastNameField.text?.toString()?.trim().orEmpty()
        val typedName = listOf(firstName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val displayName = typedName.ifBlank {
            currentProfile?.full_name?.takeIf { it.isNotBlank() }
        } ?: getString(R.string.profile_display_name_empty)

        profileDisplayName.text = displayName

        val details = buildList {
            currentProfile?.category?.takeIf { it.isNotBlank() }?.let(::add)
            currentProfile?.country?.takeIf { it.isNotBlank() }?.let(::add)
            add(getString(R.string.profile_age_value, ageSlider.value.toInt()))
            currentVibeLabel()?.let(::add)
        }

        profileDetailsText.text = if (details.isEmpty()) {
            getString(R.string.profile_details_empty)
        } else {
            details.joinToString(" | ")
        }
    }

    private fun currentVibeLabel(): String? {
        return when (vibeToggleGroup.checkedButtonId) {
            R.id.vibeChill -> getString(R.string.profile_vibe_chill)
            R.id.vibeParty -> getString(R.string.profile_vibe_party)
            else -> null
        }
    }

    /**
     * Esegue l’upload dell’immagine profilo selezionata su Supabase Storage
     * e aggiorna l’URL del profilo con l’immagine pubblica.
     *
     * @param uri Uri dell’immagine selezionata.
     */
    private fun uploadAndSaveImage(uri: Uri) {
        lifecycleScope.launch {
            val imageBytes = requireContext().contentResolver.openInputStream(uri)?.readBytes() ?: return@launch
            val userId = SupabaseClientProvider.auth.currentUserOrNull()?.id ?: return@launch
            val fileName = "${userId}/profile_${System.currentTimeMillis()}.jpg"

            profileImageView.isEnabled = false
            profilePhotoHint.text = getString(R.string.profile_photo_uploading)

            try {
                val bucket = SupabaseClientProvider.storage["avatars"]

                bucket.upload(path = fileName, data = imageBytes) {
                    upsert = true
                    contentType = io.ktor.http.ContentType.Image.JPEG
                }

                val publicUrl = bucket.publicUrl(fileName)

                if (currentProfile == null) {
                    currentUserId?.let { userId ->
                        val profile = profileRepository.getCompleteProfile(userId)?.profile
                        if (profile != null) {
                            currentProfile = profile.copy(profile_image_url = publicUrl)
                            profileRepository.updateProfile(currentProfile!!)
                        } else {
                            Log.e("ProfileImageUpload", "Impossibile recuperare il profilo per aggiornare l'immagine.")
                        }
                    }
                } else {
                    currentProfile = currentProfile!!.copy(profile_image_url = publicUrl)
                    profileRepository.updateProfile(currentProfile!!)
                }

                Log.d("ProfileImageUpload", "Aggiornato profilo con nuova immagine: $publicUrl")

                // Ricarica immagine con Glide (senza cache)
                Glide.with(requireContext())
                    .load(publicUrl)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .circleCrop()
                    .into(profileImageView)

                Toast.makeText(requireContext(), "Immagine aggiornata!", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Errore durante l'upload", Toast.LENGTH_SHORT).show()
                Log.e("ProfileImageUpload", "Errore upload: ${e.localizedMessage}", e)
            } finally {
                profileImageView.isEnabled = true
                profilePhotoHint.text = getString(R.string.profile_photo_edit)
            }
        }
    }

    /**
     * Restituisce l’ID della risorsa drawable associata a un interesse.
     *
     * @param name Nome dell’interesse.
     * @return ID della risorsa drawable.
     */
    private fun getInterestIconResId(name: String): Int {
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
}
