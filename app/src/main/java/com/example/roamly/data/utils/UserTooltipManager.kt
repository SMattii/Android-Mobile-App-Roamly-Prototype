package com.example.roamly.data.utils

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.roamly.data.models.Country
import com.example.roamly.data.models.Language
import com.example.roamly.R
import com.example.roamly.data.models.NearbyUserProfile
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap

/**
 * Manager responsabile della visualizzazione del tooltip profilo utente
 * sopra un marker nella mappa.
 */
object UserTooltipManager {

    /**
     * Mostra un tooltip sopra il marker selezionato contenente i dati del profilo utente.
     *
     * @param context Contesto Android per accedere a risorse e inflater.
     * @param mapView Vista della mappa su cui si sovrappone il tooltip.
     * @param mapboxMap Istanza della mappa per ottenere coordinate schermo.
     * @param tooltipContainer FrameLayout sovrapposto alla mappa per contenere il tooltip.
     * @param point Coordinate geografiche del marker utente cliccato.
     * @param profile Dati profilo dell’utente selezionato.
     * @param allCountries Lista completa dei Paesi (per nome e codice bandiera).
     * @param allLanguages Lista completa delle lingue supportate (per visualizzare bandiere).
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

        // Rimuove eventuali tooltip già visibili
        tooltipContainer.removeAllViews()

        // Crea la view del tooltip a partire dal layout XML
        val view = LayoutInflater.from(context).inflate(R.layout.user_tooltip, tooltipContainer, false)

        // Imposta nome e età (usa "?" se età non disponibile)
        view.findViewById<TextView>(R.id.txtNameAge).text = "${profile.fullName}, ${profile.age ?: "?"}"

        Log.d("TOOLTIP_SHOW", "Mostro tooltip per ${profile.fullName} (${profile.id}) in posizione ${point.latitude()}, ${point.longitude()}")

        // Gestione bandiera e nome del Paese
        val countryFlag = view.findViewById<ImageView>(R.id.countryFlag)
        val countryName = view.findViewById<TextView>(R.id.countryName)

        profile.country?.let { countryNameFromProfile ->
            // Cerca il paese nella lista e ne recupera il codice ISO
            val country = allCountries.find { it.name.equals(countryNameFromProfile, ignoreCase = true) }
            val countryCodeLower = country?.code?.lowercase()
            val resId = countryCodeLower?.let {
                context.resources.getIdentifier(it, "drawable", context.packageName)
            } ?: 0

            // Se bandiera disponibile, la mostra, altrimenti nasconde l’immagine
            if (resId != 0) {
                countryFlag.setImageResource(resId)
                countryFlag.visibility = View.VISIBLE
            } else {
                countryFlag.visibility = View.GONE
            }

            // Mostra il nome del paese, se trovato
            countryName.text = country?.name ?: countryNameFromProfile
        } ?: run {
            // Nessun paese: nasconde elementi
            countryFlag.visibility = View.GONE
            countryName.text = ""
        }

        // Gestione categoria: testo e icona dinamica
        val categoryIcon = view.findViewById<ImageView>(R.id.categoryIcon)
        val categoryText = view.findViewById<TextView>(R.id.txtCategory)

        // Mostra icona della categoria, se disponibile
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

        // Mostra la vibe dell’utente (es. "Chill", "Party")
        view.findViewById<TextView>(R.id.txtVibe).text = profile.vibe ?: ""

        // Container per bandiere delle lingue
        val langContainer = view.findViewById<LinearLayout>(R.id.languagesContainer)
        langContainer.removeAllViews()

        // Per ogni lingua, aggiunge la rispettiva bandiera al layout
        profile.languages.forEach { code ->
            val lang = allLanguages.find { it.id == code }
            lang?.let {
                val image = ImageView(context).apply {
                    setImageResource(it.getFlagResId(context))
                    layoutParams = LinearLayout.LayoutParams(64, 64).apply {
                        setMargins(8, 0, 8, 0)
                    }
                }
                langContainer.addView(image)
            }
        }

        // Container per icone degli interessi
        val intContainer = view.findViewById<LinearLayout>(R.id.interestsContainer)
        intContainer.removeAllViews()

        // Per ogni interesse, mostra la relativa icona
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

        // Aggiunge il tooltip alla vista contenitore
        tooltipContainer.addView(view)

        // Posiziona il tooltip in base alle coordinate del marker cliccato
        view.post {
            val screenCoords = mapboxMap.pixelForCoordinate(point)
            view.x = screenCoords.x.toFloat() - view.measuredWidth / 2
            view.y = screenCoords.y.toFloat() - view.measuredHeight - 40f
        }
        Log.d("TOOLTIP_SHOW", "Tooltip mostrato correttamente per ${profile.id}")
    }
}