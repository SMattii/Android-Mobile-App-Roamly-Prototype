package com.example.roamly.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.roamly.data.models.Language
import com.example.roamly.data.utils.LanguageProvider
import com.example.roamly.R
import com.example.roamly.data.utils.SupabaseClientProvider
import com.example.roamly.adapter.LanguageAdapter
import com.example.roamly.data.models.LanguageLink
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.launch

/**
 * Activity per la selezione delle lingue parlate dall'utente.
 *
 * Mostra un dropdown di lingue disponibili, permette di selezionare più lingue
 * e le visualizza come chip rimovibili. Le lingue selezionate vengono caricate
 * dal profilo esistente e salvate via Supabase.
 */
class MakeProfile2Activity : AppCompatActivity() {

    /**
     * Campo di testo per la selezione delle lingue (dropdown).
     */
    private lateinit var languagesDropdown: MaterialAutoCompleteTextView

    /**
     * Contenitore di chip per le lingue selezionate.
     */
    private lateinit var selectedLanguagesChipGroup: ChipGroup

    /**
     * Pulsante per procedere al passo successivo del profilo.
     */
    private lateinit var nextButton: MaterialButton

    /**
     * Lista di tutte le lingue disponibili caricate da assets.
     */
    private var allPossibleLanguages: List<Language> = listOf()

    /**
     * Lista di lingue attualmente selezionate dall'utente.
     */
    private val selectedLanguages: MutableList<Language> = mutableListOf()

    /**
     * Adapter per popolare il dropdown con le lingue disponibili.
     */
    private lateinit var languageAdapter: LanguageAdapter

    /**
     * Inizializza l'activity, abilita il layout edge-to-edge, imposta view e listener,
     * carica le lingue da assets e le lingue utente già salvate.
     *
     * @param savedInstanceState Stato salvato precedentemente, se presente.
     */
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

        // Caricamento delle lingue disponibili da assets
        allPossibleLanguages = LanguageProvider.loadLanguagesFromAssets(this)

        languageAdapter = LanguageAdapter(this, allPossibleLanguages.toMutableList())
        languagesDropdown.setAdapter(languageAdapter)

        // Listener per la selezione di una lingua dal dropdown
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

        // Listener per il pulsante "Next"
        nextButton.setOnClickListener {
            lifecycleScope.launch {
                saveLanguagesToProfile()
            }

        }
    }

    /**
     * Aggiunge un chip per la lingua specificata nella ChipGroup delle lingue selezionate.
     * Permette la rimozione della lingua tramite l'icona di chiusura. Il layout è quello standard
     * dei chip material (icon - text - closeicon)
     *
     * @param language Lingua da aggiungere come chip.
     */
    private fun addLanguageChip(language: Language) {
        val chip = Chip(this).apply {
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
                updateDropdownList()
            }
        }
        selectedLanguagesChipGroup.addView(chip)
    }

    /**
     * Aggiorna la lista mostrata nel dropdown rimuovendo le lingue già selezionate.
     */
    private fun updateDropdownList() {
        val currentAvailableLanguages = allPossibleLanguages.filter { !selectedLanguages.contains(it) }.toMutableList()
        languageAdapter.updateLanguages(currentAvailableLanguages)
    }

    /**
     * Salva le lingue attualmente selezionate nel database Supabase e passa all'attività successiva.
     *
     * @throws Exception In caso di errore nell'aggiornamento.
     */
    private suspend fun saveLanguagesToProfile() {

        val userId = SupabaseClientProvider.auth.currentUserOrNull()?.id ?: run {
            Toast.makeText(this, "Utente non autenticato. Riprova il login.", Toast.LENGTH_LONG).show()
            return
        }

        try {

            // Inserisce le nuove selezioni, se presenti
            if (selectedLanguages.isNotEmpty()) {
                val entriesToInsert = selectedLanguages.map { language ->
                    LanguageLink(profile_id = userId, language_id = language.id)
                }
                SupabaseClientProvider.db.from("profile_languages").insert(entriesToInsert)
            }

            Toast.makeText(this, "Lingue aggiornate con successo!", Toast.LENGTH_SHORT).show()

            // Pulisce la selezione e aggiorna UI
            selectedLanguages.clear()
            selectedLanguagesChipGroup.removeAllViews()
            updateDropdownList()

            // Avanza al prossimo step del profilo
            val intent = Intent(this@MakeProfile2Activity, MakeProfile3Activity::class.java)
            startActivity(intent)
            finish()

        } catch (e: Exception) {
            Log.e("SupabaseUpdate", "Errore durante l'aggiornamento delle lingue: ${e.localizedMessage}", e)
            Toast.makeText(this, "Errore: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}