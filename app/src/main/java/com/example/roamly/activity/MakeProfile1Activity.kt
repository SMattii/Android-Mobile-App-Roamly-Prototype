package com.example.roamly.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.roamly.adapter.CategoryAdapter
import com.example.roamly.data.models.Category
import com.example.roamly.adapter.CountryAdapter
import com.example.roamly.data.utils.CountryProvider
import com.example.roamly.data.models.Profile
import com.example.roamly.R
import com.example.roamly.data.utils.SupabaseClientProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import io.ktor.http.ContentType.Image
import org.json.JSONObject
import java.util.Date
import kotlin.jvm.java

/**
 * MakeProfile1Activity gestisce la creazione e l'aggiornamento del profilo utente:
 * consente la selezione e l'upload di un'immagine del profilo,
 * raccoglie nome, età, paese e categoria,
 * e invia i dati a Supabase per memorizzare il profilo.
 */
class MakeProfile1Activity : AppCompatActivity() {

    /**
     * ImageView per mostrare l'anteprima dell'immagine del profilo selezionata.
     */
    private lateinit var profileImageView: ImageView

    /**
     * URL pubblico dell'immagine caricata, ricevuto da Supabase.
     */
    private var uploadedImageUrl: String? = null

    // Campi di input per nome, età, paese e categoria
    private lateinit var nameField: TextInputEditText
    private lateinit var ageSlider: Slider
    private lateinit var ageValueText: TextView
    private lateinit var countryDropdown: MaterialAutoCompleteTextView
    private lateinit var countryDropdownLayout: TextInputLayout
    private lateinit var categoryDropdown: MaterialAutoCompleteTextView
    private lateinit var categoryDropdownLayout: TextInputLayout
    private lateinit var nextButton: MaterialButton

    /**
     * Launcher per la selezione di un'immagine dalla galleria.
     * Una volta selezionata, mostra l'anteprima e avvia l'upload.
     */
    val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            // Mostra l'immagine selezionata
            profileImageView.setImageURI(uri)

            // Carica l'immagine su Supabase in background
            lifecycleScope.launch {
                val imageUrl = uploadProfileImage(this@MakeProfile1Activity, uri)

                if (imageUrl != null) {
                    Log.d("SupabaseUpload", "URL immagine profilo: $imageUrl")
                    uploadedImageUrl = imageUrl
                } else {
                    Toast.makeText(this@MakeProfile1Activity, "Errore durante l'upload", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Invocato alla creazione dell'Activity: configura edge-to-edge, inizializza i campi,
     * imposta adapter per dropdown, listener per slider e click listener per il pulsante "Avanti".
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_make_profile)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Debug autenticazione: controlla sessione e token utente
        val session = SupabaseClientProvider.auth.currentSessionOrNull()
        val user = SupabaseClientProvider.auth.currentUserOrNull()
        Log.d("AuthDebug", "Session: ${session != null} | User ID: ${user?.id}")
        Log.d("AuthDebug", "Access token: ${session?.accessToken}")

        // Inizializzazione view references
        profileImageView = findViewById(R.id.profileImageView)
        nameField = findViewById(R.id.nameField)
        ageSlider = findViewById(R.id.ageSlider)
        ageValueText = findViewById(R.id.ageValueText)
        countryDropdown = findViewById(R.id.countryDropdown)
        countryDropdownLayout = findViewById(R.id.countryDropdownLayout)
        categoryDropdown = findViewById(R.id.categoryDropdown)
        categoryDropdownLayout = findViewById(R.id.categoryDropdownLayout)
        nextButton = findViewById(R.id.nextButton)

        // Caricamento lista paesi da asset e impostazione adapter
        val countries = CountryProvider.loadCountriesFromAssets(this)
        val adapter = CountryAdapter(this, countries)

        countryDropdown.setAdapter(adapter)

        // Listener slider per aggiornare il testo dell'età in tempo reale
        ageSlider.addOnChangeListener { _, value, _ ->
            ageValueText.text = "${value.toInt()}"
        }

        // Definizione categorie e adapter per dropdown
        val categories = listOf(
            Category("Student", R.drawable.ic_category_student),
            Category("Traveler", R.drawable.ic_category_traveler),
            Category("Nomad", R.drawable.ic_category_nomad),
            Category("Other", R.drawable.ic_category_other)
        )

        val categoryAdapter = CategoryAdapter(this, categories)

        categoryDropdown.setAdapter(categoryAdapter)

        // Log della categoria selezionata dall'utente
        categoryDropdown.setOnItemClickListener { parent, view, position, id ->
            val selectedCategory = parent.getItemAtPosition(position) as Category
            Log.d("CategoryDropdown", "Selected category: ${selectedCategory.name}")
        }

        categoryDropdown.setText(categories[0].name, false)

        // Click listener per avanzare e aggiornare il profilo
        nextButton.setOnClickListener {
            lifecycleScope.launch {
                updateUserProfile()
            }
        }
    }

    /**
     * Metodo invocato al click sull'ImageView: apre il selettore di immagini.
     */
    fun selectProfileImage(view: View) {
        Log.d("MakeProfileActivity", "selectProfileImage() called. Launching image picker.")
        pickImageLauncher.launch("image/*")
    }

    /**
     * Carica l'immagine su Supabase Storage e restituisce l'URL pubblico.
     * Effettua anche il controllo di validità del token JWT prima dell'upload.
     *
     * @param context Context corrente per aprire lo stream
     * @param imageUri Uri dell'immagine selezionata
     * @return String? URL immagine o null in caso di errore
     */
    suspend fun uploadProfileImage(context: Context, imageUri: Uri): String? {
        return try {
            // 1.1 Recupero della sessione e del token JWT
            val sessionBeforeUpload = SupabaseClientProvider.auth.currentSessionOrNull()
            Log.d("SupabaseUploadDebug", "Session prima dell'upload: ${sessionBeforeUpload != null}")
            Log.d("SupabaseUploadDebug", "User ID prima dell'upload: ${sessionBeforeUpload?.user?.id}")
            Log.d("SupabaseUploadDebug", "Access token (parziale) prima dell'upload: ${sessionBeforeUpload?.accessToken?.take(20)}...")
            val accessToken = sessionBeforeUpload?.accessToken
            if (accessToken != null) {
                // 1.2 Se esiste un token, ne decodifico il payload per leggere la data di scadenza ("exp")
                try {
                    val parts = accessToken.split(".")
                    if (parts.size == 3) {
                        val payload = String(Base64.decode(parts[1], Base64.URL_SAFE))
                        val json = JSONObject(payload)
                        val exp = json.optLong("exp")
                        if (exp > 0) {
                            val expirationDate = Date(exp * 1000)
                            Log.d("SupabaseUploadDebug", "JWT Scadenza: $expirationDate")
                            val currentTime = System.currentTimeMillis()
                            if (expirationDate.time < currentTime) {
                                Log.d("SupabaseUploadDebug", "ATTENZIONE: Il token è scaduto prima dell'upload!")
                            } else {
                                Log.d("SupabaseUploadDebug", "Token valido. Scade tra: ${(expirationDate.time - currentTime) / 1000 / 60} minuti.")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SupabaseUploadDebug", "Errore decodifica JWT: ${e.localizedMessage}")
                }
            } else {
                Log.d("SupabaseUploadDebug", "Access token è null prima dell'upload.")
            }

            // 1.3 Apertura dello stream e lettura dei byte dell’immagine
            val inputStream = context.contentResolver.openInputStream(imageUri) ?: return null
            val imageBytes = inputStream.readBytes()

            // 1.4 Creazione del percorso di upload basato sull’ID utente e timestamp
            val userId = SupabaseClientProvider.auth.currentUserOrNull()?.id ?: return null // Ottieni l'ID utente
            val fileName = "${userId}/profile_${System.currentTimeMillis()}.jpg" // Percorso: user_id/nome_file.jpg

            // 1.5 Ottengo il bucket "avatars" e lancio l’upload
            val bucket = SupabaseClientProvider.storage["avatars"]

            bucket.upload(
                path = fileName,
                data = imageBytes
            ) {
                upsert = true
                contentType = Image.JPEG
            }

            // 1.6 Restituisco l’URL pubblico appena creato
            bucket.publicUrl(fileName)

        } catch (e: Exception) {
            // 1.7 In caso di qualsiasi errore, loggo e restituisco null
            Log.e("SupabaseUpload", "Errore durante l'upload immagine: ${e.localizedMessage}")
            null
        }
    }

    /**
     * Raccoglie i dati inseriti dall'utente, li valida e aggiorna il profilo nel database Supabase.
     * In caso di successo, naviga alla MakeProfile2Activity.
     *
     * @throws Exception in caso di fallimento update database
     */
    private suspend fun updateUserProfile() {
        // 1. Verifica autenticazione
        val userId = SupabaseClientProvider.auth.currentUserOrNull()?.id ?: run {
            Toast.makeText(this, "Utente non autenticato. Riprova il login.", Toast.LENGTH_LONG).show()
            return
        }

        // 2. Raccolta e validazione campi
        val fullName = nameField.text.toString().trim()
        val age = ageValueText.text.toString()
        val country = countryDropdown.text.toString().trim()
        val category = categoryDropdown.text.toString().trim()

        if (fullName.isBlank()) {
            Toast.makeText(this, "Inserisci il tuo nome completo", Toast.LENGTH_SHORT).show()
            return
        }
        if (country.isBlank()) {
            Toast.makeText(this, "Seleziona il paese di origine", Toast.LENGTH_SHORT).show()
            return
        }
        if (category.isBlank()) {
            Toast.makeText(this, "Seleziona una categoria", Toast.LENGTH_SHORT).show()
            return
        }


        val nameParts = fullName.split(" ", limit = 2)
        val firstName = nameParts.firstOrNull() ?: ""
        val lastName = if (nameParts.size > 1) nameParts[1] else ""

        // 3. Costruzione oggetto Profile
        val updatedProfileData = Profile(
            id = userId,
            full_name = fullName,
            first_name = firstName,
            last_name = lastName,
            profile_image_url = uploadedImageUrl,
            has_logged_before = false,
            age = age,
            country = country,
            category = category,
            vibe = null,
            visible = true
        )

        try {

            // 4. Update su Supabase
            val result = SupabaseClientProvider.db.from("profiles").update(updatedProfileData) {
                filter {
                    eq("id", userId)
                }
            }

            Log.d("SupabaseUpdate", "Update result: $result")

            Toast.makeText(this, "Profilo aggiornato!", Toast.LENGTH_SHORT).show()

            val intent = Intent(this@MakeProfile1Activity, MakeProfile2Activity::class.java)
            startActivity(intent)
            finish()

        } catch (e: Exception) {
            Log.e("SupabaseUpdate", "Errore durante l'aggiornamento del profilo: ${e.localizedMessage}", e)
            Toast.makeText(this, "Errore aggiornamento profilo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}