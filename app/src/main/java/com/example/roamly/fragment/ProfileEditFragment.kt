package com.example.roamly.fragment

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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

class ProfileEditFragment : Fragment() {

    private val profileRepository = ProfileRepository
    private val interestRepository = InterestRepository

    private lateinit var profileImageView: ShapeableImageView
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

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            profileImageView.setImageURI(it) // preview immediata
            uploadAndSaveImage(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_edit_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)

        profileImageView.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        btnClose.setOnClickListener {
            parentFragmentManager.popBackStack()
            requireActivity().findViewById<View>(R.id.profileFragmentContainer).visibility = View.GONE
        }

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

        // Modifica il logout in ProfileEditFragment

        btnLogout.setOnClickListener {
            lifecycleScope.launch {
                try {
                    // Prima di fare logout, resetta lo stato della mappa se possibile
                    (requireActivity() as? HomeActivity)?.let { homeActivity ->
                        homeActivity.resetMapState()
                    }

                    // Fai logout
                    SupabaseClientProvider.auth.signOut()

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

        btnChangePassword.setOnClickListener {
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_change_password, null)
            val newPasswordInput = dialogView.findViewById<EditText>(R.id.newPasswordInput)
            val confirmPasswordInput = dialogView.findViewById<EditText>(R.id.confirmPasswordInput)

            AlertDialog.Builder(requireContext())
                .setTitle("Change Password")
                .setView(dialogView)
                .setPositiveButton("Change") { _, _ ->
                    val newPassword = newPasswordInput.text.toString()
                    val confirmPassword = confirmPasswordInput.text.toString()

                    if (newPassword != confirmPassword) {
                        Toast.makeText(requireContext(), "Passwords do not match", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    if (newPassword.length < 6) {
                        Toast.makeText(requireContext(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    lifecycleScope.launch {
                        try {
                            SupabaseClientProvider.auth.updateUser {
                                password = newPassword
                            }
                            Toast.makeText(requireContext(), "Password changed successfully", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun bindViews(view: View) {
        profileImageView = view.findViewById(R.id.profileImageView)
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
        visibleSwitch = view.findViewById(R.id.visibleSwitch)
        saveButton = view.findViewById(R.id.saveButton)
        btnClose = view.findViewById(R.id.btnCloseProfile)
        btnChangePassword = view.findViewById(R.id.btnChangePassword)
        btnLogout = view.findViewById(R.id.btnLogout)
    }

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
        }
    }

    private fun updateLanguageDropdown() {
        val remainingLanguages = allLanguages.filterNot { selectedLanguages.contains(it) }
        languageAdapter.updateLanguages(remainingLanguages)
    }

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
            setOnCloseIconClickListener {
                selectedLanguages.remove(language)
                selectedLanguagesChipGroup.removeView(this)
                updateLanguageDropdown()
            }
        }
        selectedLanguagesChipGroup.addView(chip)
    }

    private fun setupInterestDropdown() {
        val interestPairs = allInterests.map { it to R.drawable.ic_interest_default }
        val interestAdapter = InterestAdapter(requireContext(), interestPairs.toMutableList())
        interestsDropdown.setAdapter(interestAdapter)

        interestsDropdown.setOnItemClickListener { _, _, position, _ ->
            val (interest, _) = interestPairs[position]
            if (selectedInterests.add(interest)) {
                addInterestChip(interest)
            }
            interestsDropdown.setText("", false)
        }
    }

    private fun addInterestChip(interest: Interest) {
        val chip = Chip(requireContext()).apply {
            text = interest.name
            val iconResId = getInterestIconResId(interest.name)
            chipIcon = ContextCompat.getDrawable(context, iconResId)
            chipIconSize = 48f // opzionale: controlla grandezza icona
            isCloseIconVisible = true
            isClickable = true
            isCheckable = false
            setOnCloseIconClickListener {
                selectedInterests.remove(interest)
                selectedInterestsChipGroup.removeView(this)
            }
        }
        selectedInterestsChipGroup.addView(chip)
    }

    private suspend fun loadUserProfileAndPopulate() {
        val profileData = profileRepository.getCompleteProfile(currentUserId!!)
        val profile = profileData?.profile ?: return
        currentProfile = profile

        firstNameField.setText(profile.first_name ?: "")
        lastNameField.setText(profile.last_name ?: "")

        val ageInt = profile.age?.trim()?.toIntOrNull()
        val clampedAge = ageInt?.coerceIn(18, 99) ?: 18

        ageSlider.value = clampedAge.toFloat()
        ageSlider.isEnabled = false
        ageValueText.text = "Age: $clampedAge"

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
            .filter { it.profile_id == currentUserId }

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
    }

    private fun saveProfileChanges() {
        val profile = currentProfile?.copy(
            first_name = firstNameField.text?.toString(),
            last_name = lastNameField.text?.toString(),
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
            val success = profileRepository.saveCompleteProfile(
                profile = profile,
                languageIds = languageCodes,
                interestIds = interestIds
            )

            Toast.makeText(
                requireContext(),
                if (success) "Profilo aggiornato con successo" else "Errore nel salvataggio",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun uploadAndSaveImage(uri: Uri) {
        lifecycleScope.launch {
            val imageBytes = requireContext().contentResolver.openInputStream(uri)?.readBytes() ?: return@launch
            val userId = SupabaseClientProvider.auth.currentUserOrNull()?.id ?: return@launch
            val fileName = "${userId}/profile_${System.currentTimeMillis()}.jpg"

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
                            Log.e("ProfileImageUpload", "❌ Impossibile recuperare il profilo per aggiornare l'immagine.")
                        }
                    }
                } else {
                    currentProfile = currentProfile!!.copy(profile_image_url = publicUrl)
                    profileRepository.updateProfile(currentProfile!!)
                }

                Log.d("ProfileImageUpload", "✅ Aggiornato profilo con nuova immagine: $publicUrl")

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
            }
        }
    }

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