package com.example.roamly

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.MaterialAutoCompleteTextView

class MakeProfileActivity : AppCompatActivity() {

    private lateinit var profileImageView: ImageView // Declare profileImageView at class level

    // Declare the ActivityResultLauncher
    private val pickImageLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            // This callback is executed when the user selects an image or cancels the picker.
            if (uri != null) {
                profileImageView.setImageURI(uri)
            } else {
                // User cancelled or no image selected.
                // You can add a Toast message or log here if needed, e.g.:
                Log.d("ImagePicker", "No image selected or picker cancelled.")
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

        profileImageView = findViewById(R.id.profileImageView)

        val countryDropdown = findViewById<MaterialAutoCompleteTextView>(R.id.countryDropdown)
        val countries = CountryProvider.loadCountriesFromAssets(this)
        val adapter = CountryAdapter(this, countries)
        countryDropdown.setAdapter(adapter)

        val ageSlider = findViewById<Slider>(R.id.ageSlider)
        val ageValueText = findViewById<TextView>(R.id.ageValueText)

        ageSlider.addOnChangeListener { _, value, _ ->
            ageValueText.text = "Age: ${value.toInt()}"
        }

        val categoryItems = listOf(
            CategoryItem("Student", R.drawable.ic_category_nomad),
            CategoryItem("Traveler", R.drawable.ic_category_traveler),
            CategoryItem("Nomad", R.drawable.ic_category_student),
            CategoryItem("Other", R.drawable.ic_category_other)
        )

        val categoryDropdown = findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.categoryDropdown)

        val categoryAdapter = CategoryAdapter(this, categoryItems)

        categoryDropdown.setAdapter(categoryAdapter)

        categoryDropdown.setOnItemClickListener { parent, view, position, id ->
            val selectedCategoryItem = parent.getItemAtPosition(position) as CategoryItem
            Log.d("CategoryDropdown", "Selected category: ${selectedCategoryItem.name}")
        }

        // Opzionale: Pre-seleziona un valore iniziale
        categoryDropdown.setText(categoryItems[0].name, false)

    }

    fun selectProfileImage(view: View) { // RE-ADD THE 'view: View' PARAMETER HERE!
        // "image/*" requests all image types
        +        Log.d("MakeProfileActivity", "selectProfileImage() called. Launching image picker.") // Add this for debugging
        pickImageLauncher.launch("image/*")
    }
}