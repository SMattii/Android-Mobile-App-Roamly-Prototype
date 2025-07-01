package com.example.roamly

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.*
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

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
        val languages: List<String> = emptyList(),  // Codici lingua, es: "en", "it"
        val interests: List<String> = emptyList()   // UUID o nomi delle icone
    )

    @Serializable
    data class InterestLinkWithName(
        val profile_id: String,
        @SerialName("interests") val interest: Interest
    )

    @Serializable
    data class LanguageLink(  // Mapping tra profilo e lingua
        val profile_id: String,
        val language_id: String
    )

    private val userProfilesCache = mutableMapOf<String, NearbyUserProfile>()
    private var currentShownProfileId: String? = null

    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private val userAnnotationManagers = mutableMapOf<String, PointAnnotationManager>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        mapView = findViewById(R.id.mapView)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS) {
            checkLocationAndRequest()
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
            startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
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
                mapView.getMapboxMap().flyTo(
                    CameraOptions.Builder()
                        .center(userPoint)
                        .zoom(17.0)
                        .build()
                )
            }
        }

        fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)
    }

    private fun isLocationEnabled(): Boolean {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
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
                                    style.addImage(imageId, largeBitmap)

                                    userAnnotationManagers["current_user"]?.let {
                                        it.deleteAll()
                                        mapView.annotations.removeAnnotationManager(it)
                                    }

                                    val annotationManager = mapView.annotations.createPointAnnotationManager()
                                    userAnnotationManagers["current_user"] = annotationManager

                                    val options = PointAnnotationOptions()
                                        .withPoint(userPoint)
                                        .withIconImage(imageId)

                                    annotationManager.create(options)
                                }
                            }

                            override fun onLoadCleared(placeholder: Drawable?) {}
                        })
                }
            } catch (e: Exception) {
                Log.e("MARKER_CURRENT_USER", "Errore creazione marker: ${e.message}", e)
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
                if (visibleUserIds.isEmpty()) return@launch

                val allLocations = SupabaseClientProvider.db.from("locations")
                    .select(Columns.raw("user_id, latitude, longitude, profiles(profile_image_url)")) {
                        filter { isIn("user_id", visibleUserIds) }
                    }
                    .decodeList<NearbyUser>()

                val currentUserId = SupabaseClientProvider.auth.currentUserOrNull()?.id
                val nearbyUserIds = mutableListOf<String>()

                for (entry in allLocations) {
                    if (entry.user_id == currentUserId) continue
                    if (haversine(myLat, myLon, entry.latitude, entry.longitude) > 10) continue
                    val imageUrl = entry.profiles?.profile_image_url ?: continue
                    val point = Point.fromLngLat(entry.longitude, entry.latitude)

                    nearbyUserIds.add(entry.user_id)
                    createUserMarker(entry.user_id, imageUrl, point)
                }

                if (nearbyUserIds.isNotEmpty()) {
                    val detailedProfiles = fetchNearbyProfilesWithDetails(nearbyUserIds)
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
                        } catch (_: Exception) {}

                        // Rimuovi il manager precedente se esiste
                        userAnnotationManagers[userId]?.let {
                            it.deleteAll()
                            mapView.annotations.removeAnnotationManager(it)
                        }

                        // Crea nuovo annotation manager
                        val annotationManager = mapView.annotations.createPointAnnotationManager()

                        annotationManager.addClickListener { annotation ->
                            if (userId == currentShownProfileId) {
                                removeCallout()
                                currentShownProfileId = null
                            } else {
                                removeCallout()
                                showUserProfileCallout(userId, annotation.point)
                                currentShownProfileId = userId
                            }
                            true
                        }

                        // Salva il manager nella mappa
                        userAnnotationManagers[userId] = annotationManager

                        // Crea l'annotation
                        val options = PointAnnotationOptions()
                            .withPoint(point)
                            .withIconImage(imageId)

                        try {
                            val annotation = annotationManager.create(options)
                            Log.d("MARKER_CREATED", "Marker creato per userId: $userId, annotationId: ${annotation.id}")
                        } catch (e: Exception) {
                            Log.e("MARKER_ERROR", "Errore creazione marker: ${e.message}")
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
        val tooltipContainer = findViewById<FrameLayout>(R.id.tooltipContainer)

        // Se è già visibile lo stesso profilo, lo nasconde (toggle)
        if (currentShownProfileId == userId) {
            hideCallout()
            return
        }

        tooltipContainer.removeAllViews()
        val view = layoutInflater.inflate(R.layout.marker_callout, tooltipContainer, false)

        // Riempie i dati
        view.findViewById<TextView>(R.id.txtNameAge).text = "${profile.fullName}, Age: ${profile.age ?: "?"}"
        view.findViewById<TextView>(R.id.txtCountry).text = profile.country ?: ""
        view.findViewById<TextView>(R.id.txtCategory).text = profile.category ?: ""
        view.findViewById<TextView>(R.id.txtVibe).text = profile.vibe ?: ""

        val langContainer = view.findViewById<LinearLayout>(R.id.languagesContainer)
        langContainer.removeAllViews()
        profile.languages.forEach { code ->
            val text = TextView(this).apply {
                text = code.uppercase()
                setPadding(8, 4, 8, 4)
            }
            langContainer.addView(text)
        }

        val intContainer = view.findViewById<LinearLayout>(R.id.interestsContainer)
        intContainer.removeAllViews()
        profile.interests.forEach { interest ->
            val text = TextView(this).apply {
                text = interest
                setPadding(8, 4, 8, 4)
            }
            intContainer.addView(text)
        }

        // Aggiunge il tooltip e aggiorna l'ID mostrato
        tooltipContainer.addView(view)
        currentShownProfileId = userId

        view.post {
            val screenCoords = mapView.getMapboxMap().pixelForCoordinate(point)
            view.x = screenCoords.x.toFloat() - view.measuredWidth / 2
            view.y = screenCoords.y.toFloat() - view.measuredHeight - 20f
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