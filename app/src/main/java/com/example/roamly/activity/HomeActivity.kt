package com.example.roamly.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.fragment.app.FragmentManager
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
import com.example.roamly.data.repository.ProfileRepository
import com.example.roamly.data.utils.EventAnnotationManager
import com.example.roamly.data.utils.UserAnnotationManager
import com.example.roamly.data.utils.MapUtils
import com.example.roamly.data.utils.UserTooltipManager
import com.example.roamly.fragment.EventChatListFragment
import com.example.roamly.fragment.EventCreationFragment
import com.example.roamly.fragment.ProfileEditFragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.bottomnavigation.BottomNavigationView
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.roamly.data.utils.DataCache

/**
 *
 * Gestisce la visualizzazione della mappa Mapbox con profili utente e eventi nelle vicinanze,
 * la navigazione tramite bottom navigation (profilo, home, chat), il polling degli eventi,
 * e il tracciamento della posizione utente. Permette inoltre la creazione eventi tramite
 * drag-and-drop sulla mappa, la gestione dei marker con tooltip dinamici e il salvataggio
 * della posizione in Supabase.
 *
 * Funzionalità principali:
 * - Visualizzazione e sincronizzazione eventi con `EventAnnotationManager`
 * - Mostra profili utente vicini e i relativi marker con tooltip
 * - Navigazione tra sezioni (profilo, mappa, chat)
 * - Drag-and-drop per la creazione eventi
 * - Autenticazione Supabase e gestione permessi di localizzazione
 *
 * @see EventAnnotationManager
 * @see UserAnnotationManager
 * @see ProfileRepository
 * @see SupabaseClientProvider
 */
class HomeActivity : AppCompatActivity() {

    private val profileRepository = ProfileRepository

    private val eventRefreshInterval: Long = 30_000L
    private var eventRefreshJob: Job? = null

    private var currentShownProfileId: String? = null

    var currentShownEventId: String? = null

    private lateinit var allInterests: List<Interest>
    private lateinit var allLanguages: List<Language>
    private lateinit var allCountries: List<Country>

    internal lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    private var cameraCenteredOnce = false

    private lateinit var bottomNav: BottomNavigationView

    /**
     * Inizializza l'activity, configura la mappa, gestisce la navigazione e avvia il tracciamento posizione.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("MapDebug", "onCreate chiamato")

        // Controlla se l'utente è autenticato prima di procedere
        val currentUser = SupabaseClientProvider.auth.currentUserOrNull()

        if (currentUser == null) {
            Log.d("MapDebug", "onCreate: No authenticated user, redirecting to login")
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        EventAnnotationManager.clearAll()
        UserAnnotationManager.clearAll()

        setContentView(R.layout.activity_home)

        // Reset stato se è una nuova sessione
        if (savedInstanceState == null) {
            cameraCenteredOnce = false
            Log.d("MapDebug", "onCreate: Fresh start, cameraCenteredOnce reset to false")
        }

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
                    hideUserCallout()
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

        bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        val draggableEventIcon = findViewById<ImageView>(R.id.draggableEventIcon)
        val profileFragmentContainer = findViewById<FrameLayout>(R.id.profileFragmentContainer)
        val eventFragmentContainer = findViewById<FrameLayout>(R.id.eventFragmentContainer)
        val chatFragmentContainer = findViewById<FrameLayout>(R.id.chatFragmentContainer)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_profile -> {
                    profileFragmentContainer.visibility = View.VISIBLE
                    eventFragmentContainer.visibility = View.GONE
                    chatFragmentContainer.visibility = View.GONE
                    supportFragmentManager.beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        .add(R.id.profileFragmentContainer, ProfileEditFragment())
                        .addToBackStack("ProfileFragment")
                        .commit()
                    true
                }
                R.id.nav_home -> {

                    supportFragmentManager.popBackStackImmediate(null,  FragmentManager.POP_BACK_STACK_INCLUSIVE)

                    profileFragmentContainer.visibility = View.GONE
                    eventFragmentContainer.visibility = View.GONE
                    chatFragmentContainer.visibility = View.GONE

                    removeUserCallout()
                    removeEventCallout()

                    centerCameraOnUser()

                    true
                }

                R.id.nav_chat -> {
                    // Mostra il container chat e nascondi gli altri se serve
                    chatFragmentContainer.visibility = View.VISIBLE
                    profileFragmentContainer.visibility = View.GONE
                    eventFragmentContainer.visibility = View.GONE

                    supportFragmentManager.beginTransaction()
                        .replace(R.id.chatFragmentContainer, EventChatListFragment())
                        .addToBackStack("EventChatList")
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
                                chatFragmentContainer.visibility = View.GONE
                                profileFragmentContainer.visibility = View.GONE
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

    /**
     * Verifica se il GPS è attivo e i permessi sono stati concessi.
     * In caso positivo, avvia l’aggiornamento della posizione.
     */
    private fun checkLocationAndRequest() {
        Log.d("MapDebug", "checkLocationAndRequest: start")

        if (isLocationEnabled()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestLocationPermissions()
            } else {
                startLocationUpdates()
            }
        } else {
            Log.w("MapDebug", "checkLocationAndRequest: location disabilitata")
            Toast.makeText(this, "Attiva la localizzazione", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }

    /**
     * Richiede i permessi di localizzazione all’utente se non già concessi.
     */
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

    /**
     * Gestisce il risultato della richiesta permessi di localizzazione.
     */
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

    /**
     * Avvia l’ascolto della posizione dell’utente tramite FusedLocationProviderClient.
     * Ogni aggiornamento salva la posizione su Supabase, mostra il marker e sincronizza eventi e profili vicini.
     */
    private fun startLocationUpdates() {
        Log.d("MapDebug", "startLocationUpdates: invoked")

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        Log.w("MapDebug", "startLocationUpdates: permessi non concessi")

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 20_000L)
            .setMinUpdateIntervalMillis(20_000L)
            .setWaitForAccurateLocation(true)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                Log.d("MapDebug", "onLocationResult: ricevuta posizione")

                val location = result.lastLocation ?: return
                val userPoint = Point.fromLngLat(location.longitude, location.latitude)

                val userId = SupabaseClientProvider.auth.currentUserOrNull()?.id
                if (userId != null) {
                    lifecycleScope.launch {
                        try {
                            profileRepository.saveLocationToSupabase(userId, location.latitude, location.longitude)
                            showCurrentUserMarker(userId, userPoint)
                        } catch (e: Exception) {
                            Log.e("HomeActivity", "Error saving location/showing marker: ${e.message}", e)
                        }
                    }
                }

                Log.d("DEBUG_NEARBY_CALL", "fetchNearbyVisibleProfiles chiamata")
                fetchNearbyVisibleProfiles(location.latitude, location.longitude)

                lifecycleScope.launch {
                    EventAnnotationManager.synchronizeEventMarkers(
                        context = this@HomeActivity,
                        mapView = mapView,
                        mapboxMap = mapView.mapboxMap,
                        myLat = location.latitude,
                        myLon = location.longitude,
                        getCurrentShownEventId = { currentShownEventId },
                        onToggleEventCallout = { newId ->
                            if (newId == null) {
                                hideEventCallout()
                                currentShownEventId = null
                                Log.d("TOOLTIP_FLOW", "Chiudo il tooltip e setto a null, ID: $newId")
                            } else {
                                removeEventCallout()
                                currentShownEventId = newId
                                Log.d("TOOLTIP_FLOW", "Nuovo evento selezionato, ID: $newId")
                            }
                        }
                    )
                }

                startEventRefreshLoop(location.latitude, location.longitude)

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

        Log.d("MapDebug", "startLocationUpdates: richiedo aggiornamenti")

        // Inizia ad ascoltare gli update continui.
        fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)

        // Prova subito a centrare la mappa sulla lastLocation disponibile per un feedback più rapido.
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null && !cameraCenteredOnce) {
                val userPoint = Point.fromLngLat(location.longitude, location.latitude)
                mapView.mapboxMap.flyTo(
                    CameraOptions.Builder()
                        .center(userPoint)
                        .zoom(17.0)
                        .build()
                )
                cameraCenteredOnce = true
                Log.d("MapDebug", "Camera centrata immediatamente su lastLocation")
            }
        }
    }

    /**
     * Verifica se la localizzazione è attiva a livello di sistema.
     *
     * @return true se GPS o rete sono abilitati, false altrimenti.
     */
    private fun isLocationEnabled(): Boolean {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Alla chiusura dell’activity, interrompe aggiornamenti di posizione e cancella i marker.
     */
    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        eventRefreshJob?.cancel()
        UserAnnotationManager.clearAll()
    }

    /**
     * Salva lo stato della mappa, in particolare se la camera è stata centrata almeno una volta.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("cameraCenteredOnceState", cameraCenteredOnce)
        Log.d("MapDebug", "onSaveInstanceState: salvato cameraCenteredOnce = $cameraCenteredOnce")
    }

    /**
     * Ripristina lo stato salvato, incluso lo zoom iniziale centrato.
     */
   override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val restoredState = savedInstanceState.getBoolean("cameraCenteredOnceState", false)
        Log.d("MapDebug", "onRestoreInstanceState: ripristinato cameraCenteredOnce = $restoredState")
    }

    /**
     * Mostra il marker per l’utente corrente sulla mappa, con immagine profilo personalizzata.
     *
     * @param userId ID dell’utente.
     * @param userPoint Posizione geografica dell’utente.
     */
    private fun showCurrentUserMarker(userId: String, userPoint: Point) {
        lifecycleScope.launch {
            try {
                val profile = SupabaseClientProvider.db["profiles"]
                    .select { filter { eq("id", userId) } }
                    .decodeSingle<Profile>()

                val imageUrl = profile.profile_image_url ?: return@launch

                UserAnnotationManager.createOrUpdateUserMarker(
                    context = this@HomeActivity,
                    mapView = mapView,
                    mapboxMap = mapView.mapboxMap,
                    userId = userId,
                    imageUrl = imageUrl,
                    point = userPoint,
                    getCurrentShownProfileId = { currentShownProfileId },
                    onToggleCallout = { newId ->
                        if (newId == null) {
                            hideUserCallout()
                            currentShownProfileId = null
                        } else {
                            removeUserCallout()
                            currentShownProfileId = newId
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("MARKER_CURRENT_USER", "Errore gestione marker utente: ${e.message}", e)
                Toast.makeText(this@HomeActivity, "Errore immagine profilo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Recupera i profili visibili da Supabase, filtra quelli entro 10 km e mostra i relativi marker.
     * Popola la cache con i profili dettagliati.
     *
     * @param myLat Latitudine dell’utente corrente.
     * @param myLon Longitudine dell’utente corrente.
     */
    private fun fetchNearbyVisibleProfiles(myLat: Double, myLon: Double) {
        Log.d("NEARBY_PROFILES", "fetchNearbyVisibleProfiles: chiamato con lat=$myLat, lon=$myLon")

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
                                hideUserCallout() // Chiude il tooltip e azzera currentShownProfileId
                                currentShownProfileId = null
                                Log.d("TOOLTIP_FLOW", "Richiesta di chiusura tooltip dal click. currentShownProfileId azzerato.")
                            } else {
                                // Aprire un nuovo tooltip: chiudiamo l'esistente ma NON lo mostriamo qui;
                                // sarà il listener in UserAnnotationManager a farlo dopo il flyTo.
                                removeUserCallout()
                                currentShownProfileId = newIdToDisplay
                                Log.d("TOOLTIP_FLOW", "currentShownProfileId impostato a: $currentShownProfileId (tooltip verrà mostrato dal manager)")
                            }
                        }
                    )
                }

                // Pulizia dei marker che non sono più nelle vicinanze
                cleanupNonNearbyMarkers(nearbyUserIdsCurrentlyFetched)

                if (nearbyUserIdsCurrentlyFetched.isNotEmpty()) {
                    val detailedProfiles = profileRepository.fetchNearbyProfilesWithDetails(nearbyUserIdsCurrentlyFetched)
                    DataCache.putUsers(detailedProfiles.values)
                    Log.d("PROFILES_CACHE", "Cache popolata con ${detailedProfiles.size} profili")
                }

            } catch (e: Exception) {
                Log.e("NEARBY_PROFILES", "Errore caricamento profili: ${e.message}", e)
                Toast.makeText(this@HomeActivity, "Errore profili: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Rimuove dalla mappa i marker di utenti che non sono più vicini.
     *
     * @param currentNearbyIds Lista degli ID utente ancora rilevati come vicini.
     */
    private fun cleanupNonNearbyMarkers(currentNearbyIds: List<String>) {
        val currentUserId = SupabaseClientProvider.auth.currentUserOrNull()?.id
        val idsToKeep = mutableSetOf<String>()
        if (currentUserId != null) idsToKeep.add(currentUserId)
        idsToKeep.addAll(currentNearbyIds)

        val idsToRemove = UserAnnotationManager.getManagedUserIds().filter { it !in idsToKeep }

        for (userId in idsToRemove) {
            UserAnnotationManager.removeUserMarker(userId)
        }
    }

    /**
     * Nasconde il tooltip dell’utente mostrato attualmente.
     */
    fun hideUserCallout() {
        val tooltipContainer = findViewById<FrameLayout>(R.id.tooltipContainer)
        tooltipContainer.removeAllViews()
        currentShownProfileId = null
        Log.d("TOOLTIP_ACTION", "hideCallout chiamato. Rimuovo tooltip e azzero currentShownProfileId.")
    }

    /**
     * Rimuove tutti i tooltip utente forzatamente dalla mappa.
     */
    fun removeUserCallout() {
        val tooltipContainer = findViewById<FrameLayout>(R.id.tooltipContainer)
        tooltipContainer.removeAllViews()
        currentShownProfileId = null
        Log.d("TOOLTIP_ACTION", "removeCallout chiamato. Rimuovo TUTTI i tooltip e azzero currentShownProfileId.")
    }

    /**
     * Nasconde il tooltip evento attualmente mostrato.
     */
    fun hideEventCallout() {
        findViewById<FrameLayout>(R.id.tooltipContainer).removeAllViews()
        currentShownEventId = null
        Log.d("EVENT_TOOLTIP", "Tooltip evento nascosto.")
    }

    /**
     * Rimuove tutti i tooltip evento visibili dalla mappa.
     */
    fun removeEventCallout() {
        val tooltipContainer = findViewById<FrameLayout>(R.id.tooltipContainer)
        tooltipContainer.removeAllViews()
        currentShownEventId = null
        Log.d("EVENT_TOOLTIP", "Tutti i tooltip evento rimossi.")
    }

    /**
     * Ricentra manualmente la mappa sulla posizione corrente dell’utente.
     */
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

    /**
     * Avvia un job che aggiorna periodicamente i marker evento sulla mappa.
     * Include il controllo visibilità e la gestione dei tooltip evento.
     *
     * @param lat Latitudine corrente dell’utente.
     * @param lon Longitudine corrente dell’utente.
     */
    private fun startEventRefreshLoop(lat: Double, lon: Double) {
        eventRefreshJob?.cancel() // Cancella il job precedente se esiste
        eventRefreshJob = lifecycleScope.launch {
            while (isActive) {
                Log.d("EventRefresh", "Refreshing events...")
                try {
                    EventAnnotationManager.synchronizeEventMarkers(
                        context = this@HomeActivity,
                        mapView = mapView,
                        mapboxMap = mapView.mapboxMap,
                        myLat = lat,
                        myLon = lon,
                        getCurrentShownEventId = { currentShownEventId },
                        onToggleEventCallout = { newId ->
                            if (newId == null) {
                                hideEventCallout()
                                currentShownEventId = null
                            } else {
                                removeEventCallout()
                                currentShownEventId = newId
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e("EventRefresh", "Error refreshing events: ${e.message}", e)
                }
                delay(eventRefreshInterval)
            }
        }
    }

    /**
     * Reset completo dello stato della mappa:
     * - cancella tooltip
     * - resetta variabili
     * - svuota la cache e annulla i job in corso.
     */
    internal fun resetMapState() {
        Log.d("MapDebug", "resetMapState: Resetting all map state variables")

        // Reset stato camera
        cameraCenteredOnce = false

        // Pulisci cache globale
        DataCache.clear()

        // Reset ID mostrati
        currentShownProfileId = null
        currentShownEventId = null

        // Rimuovi tutti i tooltip
        removeUserCallout()
        removeEventCallout()

        // Pulisci annotation managers
        UserAnnotationManager.clearAll()

        // Ferma refresh eventi
        eventRefreshJob?.cancel()
        EventAnnotationManager.clearAll()

        Log.d("MapDebug", "resetMapState: completato, user markers size = ${UserAnnotationManager.getManagedUserIds().size}")
    }

    /**
     * All'avvio dell’activity (o ritorno in foreground), se l’utente è autenticato riavvia la sincronizzazione.
     * Altrimenti resetta completamente la mappa.
     */
    override fun onResume() {
        super.onResume()

        val currentUser = SupabaseClientProvider.auth.currentUserOrNull()

        if (currentUser == null) {
            Log.d("MapDebug", "onResume: No authenticated user, resetting map state")
            resetMapState()
        } else {
            Log.d("MapDebug", "onResume: User authenticated, checking location")

            EventAnnotationManager.clearAll()
            UserAnnotationManager.clearAll()

            if (::fusedLocationClient.isInitialized) {
                checkLocationAndRequest()
            }
        }
    }

    /**
     * In pausa, interrompe l’aggiornamento ciclico degli eventi.
     */
    override fun onPause() {
        super.onPause()
        eventRefreshJob?.cancel()
        Log.d("MapDebug", "onPause: Event refresh job cancelled.")
    }

}