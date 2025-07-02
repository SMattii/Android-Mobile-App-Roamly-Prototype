package com.example.roamly

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.gms.location.*
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.*
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import com.google.gson.JsonParser
import com.mapbox.maps.extension.style.style
import com.mapbox.common.Cancelable



class HomeActivity : AppCompatActivity() {

    @Serializable
    data class NearbyUserProfile(
        val id: String,
        @SerialName("full_name") val fullName: String,
        val age: String? = null,
        val country: String? = null,
        val category: String? = null,
        val vibe: String? = null,
        @SerialName("profile_image_url") val profileImageUrl: String? = null,
        val languages: List<String> = emptyList(),
        val interests: List<String> = emptyList()
    )

    @Serializable
    data class InterestLinkWithName(
        val profile_id: String,
        @SerialName("interests") val interest: Interest
    )

    @Serializable
    data class LanguageLink(
        val profile_id: String,
        val language_id: String
    )

    private val userProfilesCache = mutableMapOf<String, NearbyUserProfile>()
    private var currentShownProfileId: String? = null

    private lateinit var allInterests: List<Interest>
    private lateinit var allLanguages: List<Language>
    private lateinit var allCountries: List<Country>

    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    private var cameraCenteredOnce = false
    private var isFlyToInProgress = false
    private var pendingTooltip: Pair<String, Point>? = null
    private var cameraSubscription: Cancelable? = null

    private val userAnnotationManagers = mutableMapOf<String, PointAnnotationManager>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MapDebug", "onCreate chiamato. cameraCenteredOnce = $cameraCenteredOnce")
        setContentView(R.layout.activity_home)

        allInterests = InterestProvider.getAllAvailableInterests()
        allLanguages = LanguageProvider.loadLanguagesFromAssets(this)
        allCountries = CountryProvider.loadCountriesFromAssets(this)

        mapView = findViewById(R.id.mapView)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mapView.mapboxMap.loadStyle(
            style(
                Style.MAPBOX_STREETS,
                block = {} // blocco vuoto, richiesto dalla firma della funzione
            )
        ) {
            checkLocationAndRequest()
        }

        mapView.mapboxMap.subscribeCameraChanged { cameraChangedEventData ->
            if (currentShownProfileId != null) {
                hideCallout()
                currentShownProfileId = null
            }
        }

        savedInstanceState?.let {
            cameraCenteredOnce = it.getBoolean("cameraCenteredOnceState", false)
            Log.d("MapDebug", "Stato ripristinato. cameraCenteredOnce = $cameraCenteredOnce")
        }
    }

    private fun checkLocationAndRequest() {
        if (isLocationEnabled()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestLocationPermissions()
            } else {
                startLocationUpdates()
            }
        } else {
            Toast.makeText(this, "Attiva la localizzazione", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            startLocationUpdates()
        } else {
            Toast.makeText(this, "Permessi di localizzazione negati", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 20_000L)
            .setMinUpdateIntervalMillis(20_000L)
            .setWaitForAccurateLocation(true)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val userPoint = Point.fromLngLat(location.longitude, location.latitude)

                val userId = SupabaseClientProvider.auth.currentUserOrNull()?.id
                if (userId != null) {
                    lifecycleScope.launch {
                        saveLocationToSupabase(userId, location.latitude, location.longitude)
                        showCurrentUserMarker(userId, userPoint)
                    }
                }

                Log.d("DEBUG_NEARBY_CALL", "fetchNearbyVisibleProfiles chiamata")
                fetchNearbyVisibleProfiles(location.latitude, location.longitude)

                // opzionale: centra la mappa solo una volta
                Log.d("MapDebug", "onLocationResult: prima del check cameraCenteredOnce: $cameraCenteredOnce")
                if (!cameraCenteredOnce) {
                    Log.d("MapDebug", "FlyTo: Centering map for the first time or due to reset.")
                    mapView.getMapboxMap().flyTo(
                        CameraOptions.Builder()
                            .center(userPoint)
                            .zoom(17.0)
                            .build()
                    )
                    cameraCenteredOnce = true
                    Log.d("MapDebug", "cameraCenteredOnce settato a true.")
                } else {
                    Log.d("MapDebug", "FlyTo: Mappa già centrata, skippo il ricentraggio automatico.")
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)
    }

    private fun isLocationEnabled(): Boolean {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        userAnnotationManagers.values.forEach { it.deleteAll() }
        mapView.annotations.removeAnnotationManager(mapView.annotations.createPointAnnotationManager()) // Questo potrebbe essere necessario se non tutti i manager sono tracciati singolarmente, o se un manager globale esiste
        cameraSubscription?.cancel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("cameraCenteredOnceState", cameraCenteredOnce)
        Log.d("MapDebug", "onSaveInstanceState: salvato cameraCenteredOnce = $cameraCenteredOnce")
    }

    // onRestoreInstanceState non è strettamente necessario qui, poiché lo stato viene ripristinato in onCreate.
    // Tuttavia, se volessi, potresti usarlo per ulteriori controlli o log.
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val restoredState = savedInstanceState.getBoolean("cameraCenteredOnceState", false)
        Log.d("MapDebug", "onRestoreInstanceState: ripristinato cameraCenteredOnce = $restoredState")
    }

    suspend fun saveLocationToSupabase(userId: String, latitude: Double, longitude: Double) {
        try {
            val entry = LocationEntry(user_id = userId, latitude = latitude, longitude = longitude)
            withContext(Dispatchers.IO) {
                SupabaseClientProvider.db.from("locations").upsert(entry) {
                    filter { eq("user_id", userId) }
                }
            }
        } catch (e: Exception) {
            Log.e("SUPABASE_SAVE", "Errore salvataggio posizione: ${e.message}", e)
        }
    }

    // --- MODIFICHE QUI ---
    private fun showCurrentUserMarker(userId: String, userPoint: Point) {
        lifecycleScope.launch {
            try {
                val profile = SupabaseClientProvider.db["profiles"]
                    .select { filter { eq("id", userId) } }
                    .decodeSingle<Profile>()

                if (!profile.profile_image_url.isNullOrBlank()) {
                    Glide.with(this@HomeActivity)
                        .asBitmap()
                        .load(profile.profile_image_url)
                        .circleCrop()
                        .into(object : CustomTarget<Bitmap>() {
                            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                val largeBitmap = Bitmap.createScaledBitmap(resource, 150, 150, false)
                                val style = mapView.getMapboxMap().getStyle()
                                if (style != null) {
                                    val imageId = "current-user-marker-$userId"
                                    try {
                                        style.addImage(imageId, largeBitmap)
                                    } catch (e: Exception) {
                                        // Ignora se l'immagine è già stata aggiunta (errore comune di Mapbox in questi casi)
                                        Log.w("MARKER_CURRENT_USER", "Immagine già aggiunta o altro errore: ${e.message}")
                                    }

                                    // Ottieni o crea il PointAnnotationManager per l'utente corrente
                                    val annotationManager = userAnnotationManagers.getOrPut("current_user_manager") {
                                        mapView.annotations.createPointAnnotationManager()
                                    }

                                    // Controlla se esiste già un marker per l'utente corrente
                                    val existingAnnotation = annotationManager.annotations.firstOrNull {
                                        // Usiamo un ID fisso per l'istanza del marker dell'utente corrente
                                        it.getData()?.asJsonObject?.get("markerId")?.asString == "current_user_marker_instance"
                                    } as? PointAnnotation

                                    if (existingAnnotation != null) {
                                        // Aggiorna la posizione del marker esistente
                                        existingAnnotation.point = userPoint
                                        annotationManager.update(existingAnnotation)
                                        Log.d("MARKER_CURRENT_USER", "Aggiornato marker esistente per utente corrente.")
                                    } else {
                                        // Crea un nuovo marker se non esiste
                                        val options = PointAnnotationOptions()
                                            .withPoint(userPoint)
                                            .withIconImage(imageId)
                                            // Aggiungi un dato univoco per identificare questo marker in futuro
                                            .withData(JsonParser.parseString("{'markerId': 'current_user_marker_instance', 'userId': '$userId'}").asJsonObject)

                                        val newAnnotation = annotationManager.create(options)
                                        Log.d("MARKER_CURRENT_USER", "Creato nuovo marker per utente corrente.")
                                    }
                                }
                            }
                            override fun onLoadCleared(placeholder: Drawable?) {}
                        })
                }
            } catch (e: Exception) {
                Log.e("MARKER_CURRENT_USER", "Errore gestione marker utente: ${e.message}", e)
                Toast.makeText(this@HomeActivity, "Errore immagine profilo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchNearbyVisibleProfiles(myLat: Double, myLon: Double) {
        lifecycleScope.launch {
            try {
                val visibleProfiles = SupabaseClientProvider.db.from("profiles")
                    .select { filter { eq("visible", true) } }
                    .decodeList<Profile>()

                val visibleUserIds = visibleProfiles.map { it.id }
                if (visibleUserIds.isEmpty()) {
                    // Se non ci sono profili visibili, rimuovi tutti i marker tranne quello dell'utente corrente
                    cleanupNonNearbyMarkers(emptyList())
                    return@launch
                }

                val allLocations = SupabaseClientProvider.db.from("locations")
                    .select(Columns.raw("user_id, latitude, longitude, profiles(profile_image_url)")) {
                        filter { isIn("user_id", visibleUserIds) }
                    }
                    .decodeList<NearbyUser>()

                val currentUserId = SupabaseClientProvider.auth.currentUserOrNull()?.id
                val nearbyUserIdsCurrentlyFetched = mutableListOf<String>()

                for (entry in allLocations) {
                    if (entry.user_id == currentUserId) continue
                    if (haversine(myLat, myLon, entry.latitude, entry.longitude) > 10) continue // SOGLIA DI 10 KM
                    val imageUrl = entry.profiles?.profile_image_url ?: continue
                    val point = Point.fromLngLat(entry.longitude, entry.latitude)

                    nearbyUserIdsCurrentlyFetched.add(entry.user_id)
                    createUserMarker(entry.user_id, imageUrl, point) // Questo aggiornerà/creerà il marker
                }

                // Pulizia dei marker che non sono più nelle vicinanze
                cleanupNonNearbyMarkers(nearbyUserIdsCurrentlyFetched)

                if (nearbyUserIdsCurrentlyFetched.isNotEmpty()) {
                    val detailedProfiles = fetchNearbyProfilesWithDetails(nearbyUserIdsCurrentlyFetched)
                    userProfilesCache.clear()
                    userProfilesCache.putAll(detailedProfiles)
                    Log.d("PROFILES_CACHE", "Cache popolata con ${userProfilesCache.size} profili")
                }

            } catch (e: Exception) {
                Log.e("NEARBY_PROFILES", "Errore caricamento profili: ${e.message}", e)
                Toast.makeText(this@HomeActivity, "Errore profili: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- NUOVO METODO PER LA PULIZIA DEI MARKER ---
    private fun cleanupNonNearbyMarkers(currentNearbyIds: List<String>) {
        val currentUserId = SupabaseClientProvider.auth.currentUserOrNull()?.id

        // Raccoglie gli ID dei manager che dovrebbero rimanere
        val managersToKeep = mutableSetOf("current_user_manager") // Assicurati di mantenere il manager dell'utente corrente
        if (currentUserId != null) {
            managersToKeep.add("current_user_manager") // Aggiungi il manager dell'utente corrente alla lista
        }
        managersToKeep.addAll(currentNearbyIds) // Aggiungi gli ID degli utenti nelle vicinanze

        val managersToRemove = userAnnotationManagers.keys.filter { it !in managersToKeep }

        managersToRemove.forEach { userId ->
            userAnnotationManagers[userId]?.let { manager ->
                manager.deleteAll() // Rimuovi tutte le annotazioni dal manager
                mapView.annotations.removeAnnotationManager(manager) // Rimuovi il manager dallo stile della mappa
                userAnnotationManagers.remove(userId) // Rimuovi dalla tua mappa di manager
                Log.d("MARKER_CLEANUP", "Rimosso manager e marker per userId: $userId (non più nelle vicinanze).")
            }
        }
    }

    private fun createUserMarker(userId: String, imageUrl: String, point: Point) {
        Glide.with(this@HomeActivity)
            .asBitmap()
            .load(imageUrl)
            .circleCrop()
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    val largeBitmap = Bitmap.createScaledBitmap(resource, 150, 150, false)
                    val style = mapView.getMapboxMap().getStyle()
                    if (style != null) {
                        val imageId = "user-marker-$userId"
                        try {
                            style.addImage(imageId, largeBitmap)
                        } catch (_: Exception) { /* immagine già esistente */ }

                        val annotationManager = userAnnotationManagers.getOrPut(userId) {
                            mapView.annotations.createPointAnnotationManager().apply {
                                addClickListener { annotation ->
                                    val clickedUserId = annotation.getData()?.asJsonObject?.get("userId")?.asString
                                    clickedUserId?.let {
                                        if (it == currentShownProfileId) {
                                            removeCallout()
                                            currentShownProfileId = null
                                        } else {
                                            removeCallout()
                                            currentShownProfileId = it

                                            // 🔄 Imposta tooltip in sospeso e stato animazione
                                            pendingTooltip = Pair(it, annotation.point)
                                            isFlyToInProgress = true

                                            // 🔁 Anima verso il marker
                                            mapView.mapboxMap.flyTo(
                                                CameraOptions.Builder()
                                                    .center(annotation.point)
                                                    .zoom(mapView.mapboxMap.cameraState.zoom)
                                                    .build(),
                                                MapAnimationOptions.mapAnimationOptions {
                                                    duration(700L)
                                                }
                                            )

                                            // ⏳ Mostra tooltip dopo l’animazione
                                            mapView.postDelayed({
                                                if (isFlyToInProgress && pendingTooltip != null) {
                                                    val (uid, pt) = pendingTooltip!!
                                                    showUserProfileCallout(uid, pt)
                                                    isFlyToInProgress = false
                                                    pendingTooltip = null
                                                }
                                            }, 750L)
                                        }
                                    }
                                    true
                                }
                            }
                        }

                        val existingAnnotation = annotationManager.annotations.firstOrNull {
                            it.getData()?.asJsonObject?.get("userId")?.asString == userId
                        } as? PointAnnotation

                        if (existingAnnotation != null) {
                            existingAnnotation.point = point
                            annotationManager.update(existingAnnotation)
                            Log.d("MARKER_UPDATE", "Aggiornato marker per userId: $userId")
                        } else {
                            val options = PointAnnotationOptions()
                                .withPoint(point)
                                .withIconImage(imageId)
                                .withData(JsonParser.parseString("{'userId': '$userId'}").asJsonObject)

                            try {
                                val annotation = annotationManager.create(options)
                                Log.d("MARKER_CREATED", "Marker creato per userId: $userId, annotationId: ${annotation.id}")
                            } catch (e: Exception) {
                                Log.e("MARKER_ERROR", "Errore creazione marker: ${e.message}")
                            }
                        }
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    suspend fun fetchNearbyProfilesWithDetails(visibleUserIds: List<String>): Map<String, NearbyUserProfile> {
        val db = SupabaseClientProvider.db

        // 1. Profili base
        val profiles = db.from("profiles")
            .select(Columns.raw("id, full_name, age, country, category, vibe, profile_image_url")) {
                filter { isIn("id", visibleUserIds) }
            }
            .decodeList<NearbyUserProfile>()

        // 2. Interessi
        val interests = db.from("profile_interests")
            .select(Columns.raw("profile_id, interests(id, name)")) {
                filter { isIn("profile_id", visibleUserIds) }
            }
            .decodeList<InterestLinkWithName>()

        // 3. Lingue
        val languages = db.from("profile_languages")
            .select(Columns.raw("profile_id, language_id")) {
                filter { isIn("profile_id", visibleUserIds) }
            }
            .decodeList<LanguageLink>()

        // 4. Mappa profilo → interessi
        val interestsMap = interests.groupBy { it.profile_id }
            .mapValues { it.value.map { link -> link.interest.name } }

        // 5. Mappa profilo → lingue
        val languagesMap = languages.groupBy { it.profile_id }
            .mapValues { it.value.map { link -> link.language_id } }

        // 6. Composizione finale
        return profiles.associateBy { it.id }.mapValues { (_, profile) ->
            profile.copy(
                interests = interestsMap[profile.id] ?: emptyList(),
                languages = languagesMap[profile.id] ?: emptyList()
            )
        }
    }


    private fun showUserProfileCallout(userId: String, point: Point) {
        val profile = userProfilesCache[userId] ?: return
        val currentUserId = SupabaseClientProvider.auth.currentUserOrNull()?.id // Ottieni l'ID utente corrente

        // Centra la mappa solo se il marker cliccato NON è quello dell'utente corrente
        if (userId != currentUserId) {
            mapView.getMapboxMap().flyTo(
                CameraOptions.Builder()
                    .center(point)
                    .zoom(mapView.getMapboxMap().cameraState.zoom) // Mantiene lo zoom corrente
                    .build(),
                MapAnimationOptions.mapAnimationOptions {
                    duration(700L)
                }
            )
        }


        val tooltipContainer = findViewById<FrameLayout>(R.id.tooltipContainer)

        // Se è già visibile lo stesso profilo, lo nasconde (toggle)
        if (currentShownProfileId == userId) {
            hideCallout()
            return
        }

        tooltipContainer.removeAllViews()
        val view = layoutInflater.inflate(R.layout.marker_callout, tooltipContainer, false)

        // Riempie i dati
        view.findViewById<TextView>(R.id.txtNameAge).text = "${profile.fullName}, ${profile.age ?: "?"}"

        val countryFlag = view.findViewById<ImageView>(R.id.countryFlag)
        val countryName = view.findViewById<TextView>(R.id.countryName)

        profile.country?.let { countryNameFromProfile ->
            Log.d("CountryFlagDebug", "Nome paese ricevuto: $countryNameFromProfile")

            // Trova il codice ISO da allCountries in base al nome (es. "Australia" → "AU")
            val country = allCountries.find { it.name.equals(countryNameFromProfile, ignoreCase = true) }

            val countryCodeLower = country?.code?.lowercase()
            Log.d("CountryFlagDebug", "Codice ISO derivato: $countryCodeLower")

            // Carica la bandiera se esiste
            val resId = countryCodeLower?.let {
                resources.getIdentifier(it, "drawable", packageName)
            } ?: 0

            if (resId != 0) {
                countryFlag.setImageResource(resId)
                countryFlag.visibility = View.VISIBLE
            } else {
                Log.w("CountryFlagDebug", "Bandiera mancante per codice: $countryCodeLower")
                countryFlag.visibility = View.GONE
            }

            // Mostra il nome ufficiale se trovato, altrimenti mostra quello del profilo
            countryName.text = country?.name ?: countryNameFromProfile
        } ?: run {
            countryFlag.visibility = View.GONE
            countryName.text = ""
        }

        val categoryIcon = view.findViewById<ImageView>(R.id.categoryIcon)
        val categoryText = view.findViewById<TextView>(R.id.txtCategory)

        val category = profile.category?.lowercase()
        categoryText.text = profile.category ?: ""

        // Prova a caricare la risorsa drawable corrispondente (es. "ic_category_nomad")
        val catResId = category?.let {
            resources.getIdentifier("ic_category_$it", "drawable", packageName)
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
                val image = ImageView(this).apply {
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
                val iconView = ImageView(this).apply {
                    setImageResource(resId)
                    layoutParams = LinearLayout.LayoutParams(64, 64).apply {
                        setMargins(8, 0, 8, 0)
                    }
                }
                intContainer.addView(iconView)
            }
        }

        // Aggiunge il tooltip e aggiorna l'ID mostrato
        tooltipContainer.addView(view)
        currentShownProfileId = userId

        view.post {
            val screenCoords = mapView.getMapboxMap().pixelForCoordinate(point)
            view.x = screenCoords.x.toFloat() - view.measuredWidth / 2
            view.y = screenCoords.y.toFloat() - view.measuredHeight - 40f
        }
    }

    // Nasconde il tooltip corrente, se presente
    private fun hideCallout() {
        val tooltipContainer = findViewById<FrameLayout>(R.id.tooltipContainer)
        tooltipContainer.removeAllViews()
        currentShownProfileId = null
    }

    // Rimuove forzatamente tutti i tooltip
    private fun removeCallout() {
        val tooltipContainer = findViewById<FrameLayout>(R.id.tooltipContainer)
        tooltipContainer.removeAllViews()
        currentShownProfileId = null
    }

}