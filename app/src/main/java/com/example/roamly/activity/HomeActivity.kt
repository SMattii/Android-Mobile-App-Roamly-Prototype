package com.example.roamly.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.roamly.data.models.Country
import com.example.roamly.data.utils.CountryProvider
import com.example.roamly.data.models.Interest
import com.example.roamly.data.utils.InterestProvider
import com.example.roamly.data.models.Language
import com.example.roamly.data.utils.LanguageProvider
import com.example.roamly.data.models.NearbyUser
import com.example.roamly.data.models.Profile
import com.example.roamly.R
import com.example.roamly.data.utils.SupabaseClientProvider
import com.example.roamly.data.models.NearbyUserProfile
import com.example.roamly.data.repository.EventRepository
import com.example.roamly.data.repository.ProfileRepository
import com.example.roamly.data.utils.EventAnnotationManager
import com.example.roamly.data.utils.UserAnnotationManager
import com.example.roamly.data.utils.MapUtils
import com.example.roamly.data.utils.UserTooltipManager
import com.example.roamly.fragment.EventCreationFragment
import com.example.roamly.fragment.ProfileFragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.JsonParser
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.style
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch


class HomeActivity : AppCompatActivity() {

    private val profileRepository = ProfileRepository()

    private val userProfilesCache = mutableMapOf<String, NearbyUserProfile>()
    private var currentShownProfileId: String? = null

    var currentShownEventId: String? = null

    private lateinit var allInterests: List<Interest>
    private lateinit var allLanguages: List<Language>
    private lateinit var allCountries: List<Country>

    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    private var cameraCenteredOnce = false

    private val userAnnotationManagers = mutableMapOf<String, PointAnnotationManager>()

    private lateinit var fabHome: FloatingActionButton
    private lateinit var bottomNav: BottomNavigationView

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
                Style.Companion.MAPBOX_STREETS,
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
                if (currentShownEventId != null) {
                    hideEventCallout()
                    currentShownEventId = null
                }
            }
            override fun onMove(detector: MoveGestureDetector): Boolean = false
            override fun onMoveEnd(detector: MoveGestureDetector) {}
        })

        savedInstanceState?.let {
            cameraCenteredOnce = it.getBoolean("cameraCenteredOnceState", false)
            Log.d("MapDebug", "Stato ripristinato. cameraCenteredOnce = $cameraCenteredOnce")
        }

        fabHome = findViewById<FloatingActionButton>(R.id.fabHome)
        bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        val draggableEventIcon = findViewById<ImageView>(R.id.draggableEventIcon)
        val profileFragmentContainer = findViewById<FrameLayout>(R.id.profileFragmentContainer)
        val eventFragmentContainer = findViewById<FrameLayout>(R.id.eventFragmentContainer)

        fabHome.setOnClickListener {

            val profileFragment = supportFragmentManager.findFragmentById(R.id.profileFragmentContainer)
            if (profileFragment != null && profileFragment.isVisible) {
                supportFragmentManager.popBackStack("ProfileFragment", 0)
                findViewById<FrameLayout>(R.id.profileFragmentContainer).visibility = View.GONE
                Log.d("FAB_HOME", "ProfileFragment chiuso")
            }

            removeCallout()
            centerCameraOnUser()
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_profile -> {
                    profileFragmentContainer.visibility = View.VISIBLE
                    supportFragmentManager.beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        .add(R.id.profileFragmentContainer, ProfileFragment())
                        .addToBackStack("ProfileFragment")
                        .commit()
                    true
                }
                else -> false
            }
        }


        bottomNav.post {
            val menuView = bottomNav.getChildAt(0) as? ViewGroup ?: return@post
            for (i in 0 until menuView.childCount) {
                val item = menuView.getChildAt(i)
                val menuItemId = bottomNav.menu.getItem(i).itemId

                if (menuItemId == R.id.nav_create_event) {

                    // Assegna direttamente un TouchListener al bottone "Crea"
                    item.setOnTouchListener { _, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                // Mostra subito l'icona e posizionala sotto il dito
                                draggableEventIcon.visibility = View.VISIBLE
                                draggableEventIcon.x = event.rawX - draggableEventIcon.width / 2
                                draggableEventIcon.y = event.rawY - draggableEventIcon.height / 2
                                true
                            }

                            MotionEvent.ACTION_MOVE -> {
                                // Segui il dito durante il drag
                                draggableEventIcon.x = event.rawX - draggableEventIcon.width / 2
                                draggableEventIcon.y = event.rawY - draggableEventIcon.height / 2
                                true
                            }

                            MotionEvent.ACTION_UP -> {
                                // Al rilascio, nascondi l'icona e mostra le coordinate
                                draggableEventIcon.visibility = View.GONE

                                val screenPoint = com.mapbox.maps.ScreenCoordinate(
                                    event.rawX.toDouble(),
                                    event.rawY.toDouble()
                                )
                                val geoCoords = mapView.mapboxMap.coordinateForPixel(screenPoint)

                                eventFragmentContainer.visibility = View.VISIBLE

                                supportFragmentManager.beginTransaction()
                                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                                    .add(
                                        R.id.eventFragmentContainer,
                                        EventCreationFragment.newInstance(
                                            geoCoords.latitude(),
                                            geoCoords.longitude()
                                        )
                                    )
                                    .addToBackStack("EventCreationFragment")
                                    .commit()

                                true
                            }

                            else -> false
                        }
                    }
                }
            }
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
                fetchNearbyEvents(location.latitude, location.longitude)

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
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER)
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        UserAnnotationManager.clearAll()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("cameraCenteredOnceState", cameraCenteredOnce)
        Log.d("MapDebug", "onSaveInstanceState: salvato cameraCenteredOnce = $cameraCenteredOnce")
    }

   override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val restoredState = savedInstanceState.getBoolean("cameraCenteredOnceState", false)
        Log.d("MapDebug", "onRestoreInstanceState: ripristinato cameraCenteredOnce = $restoredState")
    }

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
                    cleanupNonNearbyMarkers(emptyList())
                    return@launch
                }

                val allLocations = SupabaseClientProvider.db.from("locations")
                    .select(Columns.Companion.raw("user_id, latitude, longitude, profiles(profile_image_url)")) {
                        filter { isIn("user_id", visibleUserIds) }
                    }
                    .decodeList<NearbyUser>()

                val currentUserId = SupabaseClientProvider.auth.currentUserOrNull()?.id
                val nearbyUserIdsCurrentlyFetched = mutableListOf<String>()

                for (entry in allLocations) {
                    if (entry.user_id == currentUserId) continue
                    if (MapUtils.haversine(myLat, myLon, entry.latitude, entry.longitude) > 10) continue // SOGLIA DI 10 KM
                    val imageUrl = entry.profiles?.profile_image_url ?: continue
                    val point = Point.fromLngLat(entry.longitude, entry.latitude)

                    nearbyUserIdsCurrentlyFetched.add(entry.user_id)

                    UserAnnotationManager.createOrUpdateUserMarker(
                        context = this@HomeActivity,
                        mapView = mapView,
                        mapboxMap = mapView.mapboxMap,
                        userId = entry.user_id,
                        imageUrl = imageUrl,
                        point = point,
                        getCurrentShownProfileId = { currentShownProfileId },
                        onToggleCallout = { newIdToDisplay -> // newIdToDisplay può essere null (per chiudere) o l'ID (per aprire)

                            Log.d("TOOLTIP_FLOW", "Callback ricevuto: newIdToDisplay=$newIdToDisplay, currentShownProfileId=$currentShownProfileId")

                            if (newIdToDisplay == null) {
                                // Se MapAnnotationManager ha detto di chiudere (newIdToDisplay è null)
                                Log.d("TOOLTIP_ACTION", "hideCallout chiamato. Rimuovo tooltip e azzero currentShownProfileId.")
                                hideCallout() // Chiude il tooltip e azzera currentShownProfileId
                                currentShownProfileId = null
                                Log.d("TOOLTIP_FLOW", "Richiesta di chiusura tooltip dal click. currentShownProfileId azzerato.")
                            } else {
                                // Se MapAnnotationManager ha detto di aprire (newIdToDisplay è un ID)
                                Log.d("TOOLTIP_ACTION", "removeCallout chiamato. Rimuovo TUTTI i tooltip e azzero currentShownProfileId.")
                                removeCallout() // Assicura che non ci siano altri tooltip aperti, e azzera currentShownProfileId
                                currentShownProfileId = newIdToDisplay // Imposta il nuovo ID del profilo da mostrare
                                Log.d("TOOLTIP_FLOW", "currentShownProfileId impostato a: $currentShownProfileId")

                                val pt = point
                                val profile = userProfilesCache[newIdToDisplay]
                                if (profile != null) {
                                    Log.d("TOOLTIP_FLOW", "Chiamo TooltipManager.show per userId=$newIdToDisplay")
                                    UserTooltipManager.show(
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

    private fun fetchNearbyEvents(myLat: Double, myLon: Double) {
        lifecycleScope.launch {
            try {
                val eventRepo = EventRepository()
                val profileRepo = ProfileRepository()

                val events = eventRepo.getEvents()

                for (event in events) {
                    val dist = MapUtils.haversine(myLat, myLon, event.latitude, event.longitude)
                    if (dist > 10) continue  // distanza > 10km? Skippa

                    val participants = eventRepo.getEventParticipants(event.id!!)
                    Log.d("EVENT_PARTICIPANTS", "Evento ${event.id} ha ${participants.size} partecipanti.")

                    val profiles = profileRepo.getProfilesByIds(participants)
                    Log.d("EVENT_PARTICIPANTS", "Profili caricati per evento ${event.id}: ${profiles.map { it.id }}")

                    EventAnnotationManager.createEventMarker(
                        context = this@HomeActivity,
                        mapView = mapView,
                        mapboxMap = mapView.mapboxMap,
                        event = event,
                        point = Point.fromLngLat(event.longitude, event.latitude),
                        getCurrentShownEventId = { currentShownEventId },
                        onToggleEventCallout = { newId ->
                            if (newId == null) {
                                hideEventCallout()
                            } else {
                                removeEventCallout()
                                currentShownEventId = newId
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("EVENT_FETCH", "Errore nel caricamento eventi: ${e.message}", e)
                Toast.makeText(this@HomeActivity, "Errore caricamento eventi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cleanupNonNearbyMarkers(currentNearbyIds: List<String>) {
        val currentUserId = SupabaseClientProvider.auth.currentUserOrNull()?.id
        val idsToKeep = mutableSetOf<String>()
        if (currentUserId != null) idsToKeep.add("current_user_manager")
        idsToKeep.addAll(currentNearbyIds)

        val idsToRemove = UserAnnotationManager.getManagedUserIds().filter { it !in idsToKeep }

        for (userId in idsToRemove) {
            UserAnnotationManager.removeUserMarker(userId)
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

    fun hideEventCallout() {
        val tooltipContainer = findViewById<FrameLayout>(R.id.tooltipContainer)
        tooltipContainer.removeAllViews()
        currentShownEventId = null
        Log.d("EVENT_TOOLTIP", "Tooltip evento nascosto.")
    }

    fun removeEventCallout() {
        val tooltipContainer = findViewById<FrameLayout>(R.id.tooltipContainer)
        tooltipContainer.removeAllViews()
        currentShownEventId = null
        Log.d("EVENT_TOOLTIP", "Tutti i tooltip evento rimossi.")
    }

    private fun centerCameraOnUser() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permessi di localizzazione non concessi", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val userPoint = Point.fromLngLat(location.longitude, location.latitude)
                mapView.mapboxMap.flyTo(
                    CameraOptions.Builder()
                        .center(userPoint)
                        .zoom(17.0)
                        .build()
                )
                Toast.makeText(this, "Mappa centrata sulla tua posizione", Toast.LENGTH_SHORT).show()
                Log.d("FAB_CENTER", "Mappa centrata manualmente su lat=${location.latitude}, lon=${location.longitude}")
            } else {
                Toast.makeText(this, "Posizione non disponibile", Toast.LENGTH_SHORT).show()
                Log.w("FAB_CENTER", "Posizione null da fusedLocationClient")
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Errore nel recupero della posizione", Toast.LENGTH_SHORT).show()
            Log.e("FAB_CENTER", "Errore recupero posizione: ${it.message}", it)
        }
    }

}