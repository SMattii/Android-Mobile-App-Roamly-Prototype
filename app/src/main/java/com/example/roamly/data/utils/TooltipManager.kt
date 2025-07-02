package com.example.roamly.data.utils

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.roamly.Country
import com.example.roamly.InterestProvider
import com.example.roamly.Language
import com.example.roamly.R
import com.example.roamly.data.models.NearbyUserProfile
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap

object TooltipManager {

    /**
     * Mostra un tooltip sopra il marker selezionato.
     */
    fun show(
        context: Context,
        mapView: MapView,
        mapboxMap: MapboxMap,
        tooltipContainer: FrameLayout,
        point: Point,
        profile: NearbyUserProfile,
        allCountries: List<Country>,
        allLanguages: List<Language>
    ) {
        tooltipContainer.removeAllViews()

        val view = LayoutInflater.from(context).inflate(R.layout.marker_callout, tooltipContainer, false)

        view.findViewById<TextView>(R.id.txtNameAge).text = "${profile.fullName}, ${profile.age ?: "?"}"

        Log.d("TOOLTIP_SHOW", "Mostro tooltip per ${profile.fullName} (${profile.id}) in posizione ${point.latitude()}, ${point.longitude()}")

        val countryFlag = view.findViewById<ImageView>(R.id.countryFlag)
        val countryName = view.findViewById<TextView>(R.id.countryName)

        profile.country?.let { countryNameFromProfile ->
            val country = allCountries.find { it.name.equals(countryNameFromProfile, ignoreCase = true) }
            val countryCodeLower = country?.code?.lowercase()
            val resId = countryCodeLower?.let {
                context.resources.getIdentifier(it, "drawable", context.packageName)
            } ?: 0

            if (resId != 0) {
                countryFlag.setImageResource(resId)
                countryFlag.visibility = View.VISIBLE
            } else {
                countryFlag.visibility = View.GONE
            }

            countryName.text = country?.name ?: countryNameFromProfile
        } ?: run {
            countryFlag.visibility = View.GONE
            countryName.text = ""
        }

        val categoryIcon = view.findViewById<ImageView>(R.id.categoryIcon)
        val categoryText = view.findViewById<TextView>(R.id.txtCategory)

        val category = profile.category?.lowercase()
        categoryText.text = profile.category ?: ""
        val catResId = category?.let {
            context.resources.getIdentifier("ic_category_$it", "drawable", context.packageName)
        } ?: 0

        if (catResId != 0) {
            categoryIcon.setImageResource(catResId)
            categoryIcon.visibility = View.VISIBLE
        } else {
            categoryIcon.visibility = View.GONE
        }

        view.findViewById<TextView>(R.id.txtVibe).text = profile.vibe ?: ""

        val langContainer = view.findViewById<LinearLayout>(R.id.languagesContainer)
        langContainer.removeAllViews()

        profile.languages.forEach { code ->
            val lang = allLanguages.find { it.code == code }
            lang?.let {
                val image = ImageView(context).apply {
                    setImageResource(it.flagResId)
                    layoutParams = LinearLayout.LayoutParams(64, 64).apply {
                        setMargins(8, 0, 8, 0)
                    }
                }
                langContainer.addView(image)
            }
        }

        val intContainer = view.findViewById<LinearLayout>(R.id.interestsContainer)
        intContainer.removeAllViews()

        profile.interests.forEach { name ->
            val resId = InterestProvider.getIconResIdFor(name)
            if (resId != null) {
                val iconView = ImageView(context).apply {
                    setImageResource(resId)
                    layoutParams = LinearLayout.LayoutParams(64, 64).apply {
                        setMargins(8, 0, 8, 0)
                    }
                }
                intContainer.addView(iconView)
            }
        }

        tooltipContainer.addView(view)

        view.post {
            val screenCoords = mapboxMap.pixelForCoordinate(point)
            view.x = screenCoords.x.toFloat() - view.measuredWidth / 2
            view.y = screenCoords.y.toFloat() - view.measuredHeight - 40f
        }
        Log.d("TOOLTIP_SHOW", "Tooltip mostrato correttamente per ${profile.id}")
    }
}