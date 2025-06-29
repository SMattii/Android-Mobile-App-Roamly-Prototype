package com.example.roamly

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.MaterialAutoCompleteTextView

class MakeProfile1_Activity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_IMAGE_PICK = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_make_profile1)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

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

    fun selectProfileImage(view: View) {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_CODE_IMAGE_PICK)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_IMAGE_PICK && resultCode == RESULT_OK && data != null) {
            val imageUri = data.data
            val imageView = findViewById<ImageView>(R.id.profileImageView)
            imageView.setImageURI(imageUri)
        }
    }
}