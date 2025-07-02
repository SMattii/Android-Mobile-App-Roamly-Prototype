package com.example.roamly

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.roamly.adapters.LanguageAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.launch

class MakeProfile2Activity : AppCompatActivity() {

    private lateinit var languagesDropdown: MaterialAutoCompleteTextView
    private lateinit var selectedLanguagesChipGroup: ChipGroup
    private lateinit var nextButton: MaterialButton

    private var allPossibleLanguages: List<Language> = listOf()
    private val selectedLanguages: MutableList<Language> = mutableListOf()
    private lateinit var languageAdapter: LanguageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_make_profile2)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_2)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        languagesDropdown = findViewById(R.id.languagesDropdown)
        selectedLanguagesChipGroup = findViewById(R.id.selectedLanguagesChipGroup)
        nextButton = findViewById(R.id.nextButton2)

        allPossibleLanguages = LanguageProvider.loadLanguagesFromAssets(this)

        languageAdapter = LanguageAdapter(this, allPossibleLanguages.toMutableList())
        languagesDropdown.setAdapter(languageAdapter)

        languagesDropdown.setOnItemClickListener { parent, view, position, id ->
            val selectedLanguage = parent.getItemAtPosition(position) as Language

            if (!selectedLanguages.contains(selectedLanguage)) {
                selectedLanguages.add(selectedLanguage)
                addLanguageChip(selectedLanguage)
                updateDropdownList()
            }
            languagesDropdown.setText("", false)
            languagesDropdown.clearFocus()
        }

        nextButton.setOnClickListener {

            lifecycleScope.launch {
                saveLanguagesToProfile()
            }

        }

        lifecycleScope.launch {
            loadUserLanguages()
        }
    }

    private fun addLanguageChip(language: Language) {
        val chip = Chip(this).apply {
            text = language.name
            chipIcon = resources.getDrawable(language.flagResId, theme)
            isCloseIconVisible = true
            isClickable = true
            isCheckable = false
            setOnCloseIconClickListener {
                selectedLanguages.remove(language)
                selectedLanguagesChipGroup.removeView(this)
                updateDropdownList()
            }
        }
        selectedLanguagesChipGroup.addView(chip)
    }

    private fun updateDropdownList() {
        val currentAvailableLanguages = allPossibleLanguages.filter { !selectedLanguages.contains(it) }.toMutableList()
        languageAdapter.updateLanguages(currentAvailableLanguages)
    }

    private suspend fun loadUserLanguages() {

        val userId = SupabaseClientProvider.auth.currentUserOrNull()?.id ?: return

        try {
            val userLanguageEntries = SupabaseClientProvider.db.from("profile_languages")
                .select() {
                    filter { eq("profile_id", userId) }
                }
                .decodeList<ProfileLanguage>()

            val existingLanguages = userLanguageEntries.mapNotNull { entry ->
                allPossibleLanguages.find { it.code == entry.language_id }
            }

            existingLanguages.forEach { language ->
                if (!selectedLanguages.contains(language)) {
                    selectedLanguages.add(language)
                    addLanguageChip(language)
                }
            }
            updateDropdownList()

        } catch (e: Exception) {
            Log.e("SupabaseLoad", "Errore durante il caricamento delle lingue utente: ${e.localizedMessage}", e)
        }
    }

    private suspend fun saveLanguagesToProfile() {

        val userId = SupabaseClientProvider.auth.currentUserOrNull()?.id ?: run {
            Toast.makeText(this, "Utente non autenticato. Riprova il login.", Toast.LENGTH_LONG).show()
            return
        }

        try {

            SupabaseClientProvider.db.from("profile_languages")
                .delete {
                    filter { eq("profile_id", userId) }
                }

            if (selectedLanguages.isNotEmpty()) {
                val entriesToInsert = selectedLanguages.map { language ->
                    ProfileLanguage(profile_id = userId, language_id = language.code)
                }
                SupabaseClientProvider.db.from("profile_languages").insert(entriesToInsert)
            }

            Toast.makeText(this, "Lingue aggiornate con successo!", Toast.LENGTH_SHORT).show()

            selectedLanguages.clear()
            selectedLanguagesChipGroup.removeAllViews()
            updateDropdownList()

            val intent = Intent(this@MakeProfile2Activity, MakeProfile3Activity::class.java)
            startActivity(intent)
            finish()

        } catch (e: Exception) {
            Log.e("SupabaseUpdate", "Errore durante l'aggiornamento delle lingue: ${e.localizedMessage}", e)
            Toast.makeText(this, "Errore: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}