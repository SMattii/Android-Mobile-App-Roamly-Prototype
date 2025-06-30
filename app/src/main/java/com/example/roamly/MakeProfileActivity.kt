package com.example.roamly

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import io.ktor.http.ContentType.Image
import kotlin.jvm.java

class MakeProfileActivity : AppCompatActivity() {

    private lateinit var profileImageView: ImageView
    private var uploadedImageUrl: String? = null
    private lateinit var nameField: TextInputEditText
    private lateinit var ageSlider: Slider
    private lateinit var ageValueText: TextView
    private lateinit var countryDropdown: MaterialAutoCompleteTextView
    private lateinit var countryDropdownLayout: com.google.android.material.textfield.TextInputLayout
    private lateinit var categoryDropdown: MaterialAutoCompleteTextView
    private lateinit var categoryDropdownLayout: com.google.android.material.textfield.TextInputLayout
    private lateinit var nextButton: com.google.android.material.button.MaterialButton

    val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            profileImageView.setImageURI(uri)

            lifecycleScope.launch {
                val imageUrl = uploadProfileImage(this@MakeProfileActivity, uri)

                if (imageUrl != null) {
                    Log.d("SupabaseUpload", "URL immagine profilo: $imageUrl")
                    uploadedImageUrl = imageUrl
                } else {
                    Toast.makeText(this@MakeProfileActivity, "Errore durante l'upload", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_make_profile)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val session = SupabaseClientProvider.auth.currentSessionOrNull()
        val user = SupabaseClientProvider.auth.currentUserOrNull()
        Log.d("AuthDebug", "Session: ${session != null} | User ID: ${user?.id}")
        Log.d("AuthDebug", "Access token: ${session?.accessToken}")

        profileImageView = findViewById(R.id.profileImageView)
        nameField = findViewById(R.id.nameField)
        ageSlider = findViewById(R.id.ageSlider)
        ageValueText = findViewById(R.id.ageValueText)
        countryDropdown = findViewById(R.id.countryDropdown)
        countryDropdownLayout = findViewById(R.id.countryDropdownLayout)
        categoryDropdown = findViewById(R.id.categoryDropdown)
        categoryDropdownLayout = findViewById(R.id.categoryDropdownLayout)
        nextButton = findViewById(R.id.nextButton)

        val countries = CountryProvider.loadCountriesFromAssets(this)
        val adapter = CountryAdapter(this, countries)

        countryDropdown.setAdapter(adapter)

        ageSlider.addOnChangeListener { _, value, _ ->
            ageValueText.text = "Age: ${value.toInt()}"
        }

        val categoryItems = listOf(
            CategoryItem("Student", R.drawable.ic_category_student),
            CategoryItem("Traveler", R.drawable.ic_category_traveler),
            CategoryItem("Nomad", R.drawable.ic_category_nomad),
            CategoryItem("Other", R.drawable.ic_category_other)
        )

        val categoryAdapter = CategoryAdapter(this, categoryItems)

        categoryDropdown.setAdapter(categoryAdapter)

        categoryDropdown.setOnItemClickListener { parent, view, position, id ->
            val selectedCategoryItem = parent.getItemAtPosition(position) as CategoryItem
            Log.d("CategoryDropdown", "Selected category: ${selectedCategoryItem.name}")
        }

        categoryDropdown.setText(categoryItems[0].name, false)

        nextButton.setOnClickListener {
            lifecycleScope.launch {
                updateUserProfile()
            }
        }
    }

    fun selectProfileImage(view: View) {
        Log.d("MakeProfileActivity", "selectProfileImage() called. Launching image picker.")
        pickImageLauncher.launch("image/*")
    }

    suspend fun uploadProfileImage(context: Context, imageUri: Uri): String? {
        return try {
            val sessionBeforeUpload = SupabaseClientProvider.auth.currentSessionOrNull()
            Log.d("SupabaseUploadDebug", "Session prima dell'upload: ${sessionBeforeUpload != null}")
            Log.d("SupabaseUploadDebug", "User ID prima dell'upload: ${sessionBeforeUpload?.user?.id}")
            Log.d("SupabaseUploadDebug", "Access token (parziale) prima dell'upload: ${sessionBeforeUpload?.accessToken?.take(20)}...")
            val accessToken = sessionBeforeUpload?.accessToken
            if (accessToken != null) {
                try {
                    val parts = accessToken.split(".")
                    if (parts.size == 3) {
                        val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE))
                        val json = org.json.JSONObject(payload)
                        val exp = json.optLong("exp")
                        if (exp > 0) {
                            val expirationDate = java.util.Date(exp * 1000)
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

            val inputStream = context.contentResolver.openInputStream(imageUri) ?: return null
            val imageBytes = inputStream.readBytes()

            val userId = SupabaseClientProvider.auth.currentUserOrNull()?.id ?: return null // Ottieni l'ID utente
            val fileName = "${userId}/profile_${System.currentTimeMillis()}.jpg" // Percorso: user_id/nome_file.jpg

            val bucket = SupabaseClientProvider.storage["avatars"]

            bucket.upload(
                path = fileName,
                data = imageBytes
            ) {
                upsert = true
                contentType = Image.JPEG
            }


            bucket.publicUrl(fileName)

        } catch (e: Exception) {
            Log.e("SupabaseUpload", "Errore durante l'upload immagine: ${e.localizedMessage}")
            null
        }
    }

    private suspend fun updateUserProfile() {
        val userId = SupabaseClientProvider.auth.currentUserOrNull()?.id ?: run {
            Toast.makeText(this, "Utente non autenticato. Riprova il login.", Toast.LENGTH_LONG).show()
            return
        }

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

            val result = SupabaseClientProvider.db.from("profiles").update(updatedProfileData) {
                filter {
                    eq("id", userId)
                }
            }

            Log.d("SupabaseUpdate", "Update result: $result")

            Toast.makeText(this, "Profilo aggiornato!", Toast.LENGTH_SHORT).show()

            val intent = Intent(this@MakeProfileActivity, MakeProfile2Activity::class.java)
            startActivity(intent)
            finish()

        } catch (e: Exception) {
            Log.e("SupabaseUpdate", "Errore durante l'aggiornamento del profilo: ${e.localizedMessage}", e)
            Toast.makeText(this, "Errore aggiornamento profilo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}