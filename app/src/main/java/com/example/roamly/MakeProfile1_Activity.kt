package com.example.roamly

import android.os.Bundle
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.example.roamly.CountryAdapter

class MakeProfile1_Activity : AppCompatActivity() {

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

    }
}