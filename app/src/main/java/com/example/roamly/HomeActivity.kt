package com.example.roamly

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.FrameLayout
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
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.*
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import com.google.gson.JsonParser
import com.mapbox.maps.extension.style.style
import com.example.roamly.data.models.NearbyUserProfile
import com.example.roamly.data.repository.ProfileRepository
import com.example.roamly.data.utils.MapUtils.haversine
import com.example.roamly.data.utils.TooltipManager
import com.example.roamly.data.utils.MapAnnotationManager
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures


class HomeActivity : AppCompatActivity() {

    private val profileRepository = ProfileRepository()


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

        mapView.gestures.addOnMoveListener(object : OnMoveListener {
            override fun onMoveBegin(detector: MoveGestureDetector) {
                if (currentShownProfileId != null) {
                    hideCallout()
                    currentShownProfileId = null
                }
            }
            override fun onMove(detector: MoveGestureDetector): Boolean = false
            override fun onMoveEnd(detector: MoveGestureDetector) {}
        })

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
                        profileRepository.saveLocationToSupabase(userId, location.latitude, location.longitude)
                        showCurrentUserMarker(userId, userPoint)
                    }
                }

                Log.d("DEBUG_NEARBY_CALL", "fetchNearbyVisibleProfiles chiamata")
                fetchNearbyVisibleProfiles(location.latitude, location.longitude)

                Log.d("MapDebug", "onLocationResult: prima del check cameraCenteredOnce: $cameraCenteredOnce")
                if (!cameraCenteredOnce) {
                    Log.d("MapDebug", "FlyTo: Centering map for the first time or due to reset.")
                    mapView.mapboxMap.flyTo(
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
                                val style = mapView.mapboxMap.style
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

                    val manager = MapAnnotationManager.createOrUpdateUserMarker(
                        context = this@HomeActivity,
                        mapView = mapView,
                        mapboxMap = mapView.getMapboxMap(),
                        userId = entry.user_id,
                        imageUrl = imageUrl,
                        point = point,
                        getCurrentShownProfileId = { currentShownProfileId }, // ✅ Funzione che restituisce il valore corrente
                        onToggleCallout = { newIdToDisplay -> // ✅ newIdToDisplay può essere null (per chiudere) o l'ID (per aprire)

                            Log.d("TOOLTIP_FLOW", "Callback ricevuto: newIdToDisplay=$newIdToDisplay, currentShownProfileId=$currentShownProfileId")

                            // ✅ GESTIONE UNIFICATA DEL CALLBACK onToggleCallout
                            if (newIdToDisplay == null) {
                                // Se MapAnnotationManager ha detto di chiudere (newIdToDisplay è null)
                                Log.d("TOOLTIP_ACTION", "hideCallout chiamato. Rimuovo tooltip e azzero currentShownProfileId.")
                                hideCallout() // Chiude il tooltip e azzera currentShownProfileId
                                currentShownProfileId = null // ✅ Assicurati che sia azzerato
                                Log.d("TOOLTIP_FLOW", "Richiesta di chiusura tooltip dal click. currentShownProfileId azzerato.")
                            } else {
                                // Se MapAnnotationManager ha detto di aprire (newIdToDisplay è un ID)
                                Log.d("TOOLTIP_ACTION", "removeCallout chiamato. Rimuovo TUTTI i tooltip e azzero currentShownProfileId.")
                                removeCallout() // Assicurati che non ci siano altri tooltip aperti, e azzera currentShownProfileId
                                currentShownProfileId = newIdToDisplay // Imposta il nuovo ID del profilo da mostrare
                                Log.d("TOOLTIP_FLOW", "currentShownProfileId impostato a: $currentShownProfileId")

                                val pt = point
                                val profile = userProfilesCache[newIdToDisplay]
                                if (profile != null) {
                                    Log.d("TOOLTIP_FLOW", "Chiamo TooltipManager.show per userId=$newIdToDisplay")
                                    TooltipManager.show(
                                        context = this@HomeActivity,
                                        mapView = mapView,
                                        mapboxMap = mapView.mapboxMap,
                                        tooltipContainer = findViewById(R.id.tooltipContainer),
                                        point = pt,
                                        profile = profile,
                                        allCountries = allCountries,
                                        allLanguages = allLanguages
                                    )
                                } else {
                                    Log.w("TOOLTIP_FLOW", "Profilo non trovato nella cache per userId=$newIdToDisplay")
                                }
                            }
                        }
                    )

                    userAnnotationManagers[entry.user_id] = manager
                }

                // Pulizia dei marker che non sono più nelle vicinanze
                cleanupNonNearbyMarkers(nearbyUserIdsCurrentlyFetched)

                if (nearbyUserIdsCurrentlyFetched.isNotEmpty()) {
                    val detailedProfiles = profileRepository.fetchNearbyProfilesWithDetails(nearbyUserIdsCurrentlyFetched)
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

    // Nasconde il tooltip corrente, se presente
    private fun hideCallout() {
        val tooltipContainer = findViewById<FrameLayout>(R.id.tooltipContainer)
        tooltipContainer.removeAllViews()
        currentShownProfileId = null
        Log.d("TOOLTIP_ACTION", "hideCallout chiamato. Rimuovo tooltip e azzero currentShownProfileId.")
    }

    // Rimuove forzatamente tutti i tooltip
    private fun removeCallout() {
        val tooltipContainer = findViewById<FrameLayout>(R.id.tooltipContainer)
        tooltipContainer.removeAllViews()
        currentShownProfileId = null
        Log.d("TOOLTIP_ACTION", "removeCallout chiamato. Rimuovo TUTTI i tooltip e azzero currentShownProfileId.")
    }

}