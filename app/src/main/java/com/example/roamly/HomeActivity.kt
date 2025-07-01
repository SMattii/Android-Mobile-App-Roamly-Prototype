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
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.animation.flyTo
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.jan.supabase.postgrest.query.Columns

import androidx.appcompat.app.AppCompatActivity
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager

class HomeActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        mapView = findViewById(R.id.mapView)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS) { style ->
            AppCompatResources.getDrawable(this, R.drawable.marker_red)?.toBitmap()?.let {
                style.addImage("marker-red", it)
            }
            AppCompatResources.getDrawable(this, R.drawable.marker_blue)?.toBitmap()?.let {
                style.addImage("marker-blue", it)
            }

            checkLocationAndRequest()
        }
    }

    private fun checkLocationAndRequest() {
        if (isLocationEnabled()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestLocationPermissions()
            } else {
                getCurrentLocation()
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                getCurrentLocation()
            } else {
                Toast.makeText(this, "Permessi di localizzazione negati", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val userPoint = Point.fromLngLat(location.longitude, location.latitude)
                mapView.getMapboxMap().flyTo(
                    CameraOptions.Builder()
                        .center(userPoint)
                        .zoom(17.0)
                        .build()
                )

                val userId = SupabaseClientProvider.auth.currentUserOrNull()?.id
                if (userId != null) {
                    lifecycleScope.launch {
                        saveLocationToSupabase(userId, location.latitude, location.longitude)
                        showCurrentUserMarker(userId, userPoint)
                    }
                }

                Log.d("DEBUG_NEARBY_CALL", "✅ FUNZIONE fetchNearbyVisibleProfiles CHIAMATA")
                fetchNearbyVisibleProfiles(location.latitude, location.longitude)

            } else {
                Toast.makeText(this, "Posizione non trovata", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isLocationEnabled(): Boolean {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    suspend fun saveLocationToSupabase(userId: String, latitude: Double, longitude: Double) {
        try {
            val entry = LocationEntry(
                user_id = userId,
                latitude = latitude,
                longitude = longitude
            )

            withContext(Dispatchers.IO) {
                SupabaseClientProvider.db.from("locations").upsert(entry) {
                    filter { eq("user_id", userId) }
                }
            }

            println("✅ Posizione salvata con successo!")
        } catch (e: Exception) {
            println("❌ Errore durante il salvataggio posizione: ${e.message}")
        }
    }

    // Mappa per tenere traccia dei PointAnnotationManager di ogni utente
    private val userAnnotationManagers = mutableMapOf<String, PointAnnotationManager>()

    private fun showCurrentUserMarker(userId: String, userPoint: Point) {
        Log.d("DEBUG_USER_MARKER", "=== INIZIO showCurrentUserMarker ===")
        Log.d("DEBUG_USER_MARKER", "userId: $userId")
        Log.d("DEBUG_USER_MARKER", "userPoint: lat=${userPoint.latitude()}, lon=${userPoint.longitude()}")

        lifecycleScope.launch {
            try {
                Log.d("DEBUG_USER_MARKER", "Inizio query database per profilo utente")

                val profile = SupabaseClientProvider.db["profiles"]
                    .select {
                        filter { eq("id", userId) }
                    }
                    .decodeSingle<Profile>()

                Log.d("DEBUG_USER_MARKER", "Profilo recuperato: ${profile.toString()}")
                Log.d("DEBUG_USER_MARKER", "URL immagine profilo: ${profile.profile_image_url}")

                if (!profile.profile_image_url.isNullOrBlank()) {
                    Log.d("DEBUG_USER_MARKER", "Immagine profilo disponibile, inizio caricamento con Glide")

                    Glide.with(this@HomeActivity)
                        .asBitmap()
                        .load(profile.profile_image_url)
                        .circleCrop()
                        .into(object : CustomTarget<Bitmap>() {
                            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                Log.d("DEBUG_USER_MARKER", "Immagine caricata con successo")
                                Log.d("DEBUG_USER_MARKER", "Dimensioni originali bitmap: ${resource.width}x${resource.height}")

                                val largeBitmap = Bitmap.createScaledBitmap(resource, 150, 150, false)
                                Log.d("DEBUG_USER_MARKER", "Bitmap ridimensionata a: ${largeBitmap.width}x${largeBitmap.height}")

                                val style = mapView.getMapboxMap().getStyle()
                                if (style != null) {
                                    // ID immagine univoco per l'utente corrente
                                    val imageId = "current-user-marker-$userId"
                                    Log.d("DEBUG_USER_MARKER", "Aggiunta immagine '$imageId' allo stile mappa")
                                    style.addImage(imageId, largeBitmap)

                                    // Rimuovi il manager precedente se esiste
                                    userAnnotationManagers["current_user"]?.let { oldManager ->
                                        Log.d("DEBUG_USER_MARKER", "Rimozione marker precedente utente corrente")
                                        oldManager.deleteAll()
                                        mapView.annotations.removeAnnotationManager(oldManager)
                                    }

                                    val annotationManager = mapView.annotations.createPointAnnotationManager()
                                    userAnnotationManagers["current_user"] = annotationManager
                                    Log.d("DEBUG_USER_MARKER", "Creato PointAnnotationManager: $annotationManager")

                                    val options = PointAnnotationOptions()
                                        .withPoint(userPoint)
                                        .withIconImage(imageId)
                                    Log.d("DEBUG_USER_MARKER", "Opzioni marker create con punto: ${userPoint.latitude()}, ${userPoint.longitude()}")

                                    val annotation = annotationManager.create(options)
                                    Log.d("DEBUG_USER_MARKER", "Marker utente corrente creato con successo: $annotation")
                                } else {
                                    Log.e("DEBUG_USER_MARKER", "ERRORE: Stile mappa è null, impossibile aggiungere immagine")
                                }
                            }

                            override fun onLoadCleared(placeholder: Drawable?) {
                                Log.d("DEBUG_USER_MARKER", "Glide onLoadCleared chiamato")
                            }
                        })
                } else {
                    Log.w("DEBUG_USER_MARKER", "ATTENZIONE: Immagine profilo è null o vuota, marker non creato")
                }

                Log.d("DEBUG_USER_MARKER", "=== FINE showCurrentUserMarker (SUCCESS) ===")

            } catch (e: Exception) {
                Log.e("DEBUG_USER_MARKER", "ERRORE in showCurrentUserMarker: ${e.message}", e)
                Log.e("DEBUG_USER_MARKER", "Stack trace completo:", e)
                Toast.makeText(this@HomeActivity, "Errore caricamento immagine profilo: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.d("DEBUG_USER_MARKER", "=== FINE showCurrentUserMarker (ERROR) ===")
            }
        }
    }

    private fun fetchNearbyVisibleProfiles(myLat: Double, myLon: Double) {
        Log.d("DEBUG_NEARBY", "=== INIZIO fetchNearbyVisibleProfiles ===")
        Log.d("DEBUG_NEARBY", "Posizione corrente: lat=$myLat, lon=$myLon")

        lifecycleScope.launch {
            try {
                Log.d("DEBUG_NEARBY", "STEP 1: Recupero profili visibili dal database")

                // 1. Ottieni gli ID dei profili visibili
                val visibleProfiles = SupabaseClientProvider.db.from("profiles")
                    .select {
                        filter { eq("visible", true) }
                    }
                    .decodeList<Profile>()

                Log.d("DEBUG_NEARBY", "Profili visibili trovati: ${visibleProfiles.size}")
                val visibleUserIds = visibleProfiles.map { it.id }

                if (visibleUserIds.isEmpty()) {
                    Log.w("DEBUG_NEARBY", "ATTENZIONE: Nessun profilo visibile trovato, uscita anticipata")
                    return@launch
                }

                Log.d("DEBUG_NEARBY", "STEP 2: Recupero locations + immagini profilo")

                // 2. Ottieni locations con immagine profilo piatta (no struttura nidificata)
                val allLocations = SupabaseClientProvider.db.from("locations")
                    .select(Columns.raw("user_id, latitude, longitude, profiles(profile_image_url)")) {
                        filter { isIn("user_id", visibleUserIds) }
                    }
                    .decodeList<NearbyProfile>()

                Log.d("DEBUG_NEARBY", "Locations trovate: ${allLocations.size}")

                val currentUserId = SupabaseClientProvider.auth.currentUserOrNull()?.id
                var markersCreated = 0
                var markersSkippedDistance = 0
                var markersSkippedCurrentUser = 0
                var markersSkippedNoImage = 0

                for ((index, entry) in allLocations.withIndex()) {
                    Log.d("DEBUG_NEARBY", "--- Processando location $index ---")
                    Log.d("DEBUG_NEARBY", "User ID: ${entry.user_id}")
                    Log.d("DEBUG_NEARBY", "Coordinate: lat=${entry.latitude}, lon=${entry.longitude}")

                    if (entry.user_id == currentUserId) {
                        Log.d("DEBUG_NEARBY", "SKIP: È l'utente corrente")
                        markersSkippedCurrentUser++
                        continue
                    }

                    val distance = haversine(myLat, myLon, entry.latitude, entry.longitude)
                    Log.d("DEBUG_NEARBY", "Distanza calcolata: $distance km")

                    if (distance > 10) {
                        Log.d("DEBUG_NEARBY", "SKIP: Distanza troppo grande ($distance km > 10 km)")
                        markersSkippedDistance++
                        continue
                    }

                    val imageUrl = entry.profiles?.profile_image_url

                    if (imageUrl.isNullOrBlank()) {
                        Log.d("DEBUG_NEARBY", "SKIP: Immagine profilo mancante")
                        markersSkippedNoImage++
                        continue
                    }

                    val point = Point.fromLngLat(entry.longitude, entry.latitude)
                    createUserMarker(entry.user_id, imageUrl, point)
                    markersCreated++
                    Log.d("DEBUG_NEARBY", "✅ Marker creato per ${entry.user_id}")
                }

                Log.d("DEBUG_NEARBY", "=== STATISTICHE FINALI ===")
                Log.d("DEBUG_NEARBY", "Creati: $markersCreated | Skippati distanza: $markersSkippedDistance | Skippati self: $markersSkippedCurrentUser | Skippati no image: $markersSkippedNoImage")

            } catch (e: Exception) {
                Log.e("DEBUG_NEARBY", "ERRORE CRITICO: ${e.message}", e)
                Toast.makeText(this@HomeActivity, "Errore caricamento profili: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Funzione per creare marker per utenti vicini con gestione individuale
    private fun createUserMarker(userId: String, imageUrl: String, point: Point) {
        Log.d("DEBUG_CREATE_MARKER", "=== INIZIO createUserMarker ===")
        Log.d("DEBUG_CREATE_MARKER", "userId: $userId")
        Log.d("DEBUG_CREATE_MARKER", "imageUrl: $imageUrl")
        Log.d("DEBUG_CREATE_MARKER", "point: lat=${point.latitude()}, lon=${point.longitude()}")

        Glide.with(this@HomeActivity)
            .asBitmap()
            .load(imageUrl)
            .circleCrop()
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    Log.d("DEBUG_CREATE_MARKER", "Immagine caricata per $userId")
                    Log.d("DEBUG_CREATE_MARKER", "Dimensioni originali: ${resource.width}x${resource.height}")

                    val largeBitmap = Bitmap.createScaledBitmap(resource, 150, 150, false)
                    Log.d("DEBUG_CREATE_MARKER", "Bitmap ridimensionata a: ${largeBitmap.width}x${largeBitmap.height}")

                    val style = mapView.getMapboxMap().getStyle()
                    if (style != null) {
                        // ID immagine univoco per ogni utente
                        val imageId = "user-marker-$userId"
                        Log.d("DEBUG_CREATE_MARKER", "Aggiunta immagine '$imageId' allo stile mappa")

                        try {
                            style.addImage(imageId, largeBitmap)
                            Log.d("DEBUG_CREATE_MARKER", "✅ Immagine aggiunta con successo")
                        } catch (e: Exception) {
                            Log.e("DEBUG_CREATE_MARKER", "❌ Errore aggiunta immagine: ${e.message}", e)
                            return
                        }

                        // Rimuovi il manager precedente per questo utente se esiste
                        userAnnotationManagers[userId]?.let { oldManager ->
                            Log.d("DEBUG_CREATE_MARKER", "Rimozione marker precedente per $userId")
                            oldManager.deleteAll()
                            mapView.annotations.removeAnnotationManager(oldManager)
                        }

                        val annotationManager = mapView.annotations.createPointAnnotationManager()
                        userAnnotationManagers[userId] = annotationManager
                        Log.d("DEBUG_CREATE_MARKER", "Creato PointAnnotationManager per $userId: $annotationManager")

                        val options = PointAnnotationOptions()
                            .withPoint(point)
                            .withIconImage(imageId)
                        Log.d("DEBUG_CREATE_MARKER", "Opzioni marker create per $userId")

                        try {
                            val annotation = annotationManager.create(options)
                            Log.d("DEBUG_CREATE_MARKER", "✅ Marker creato con successo per $userId: $annotation")
                        } catch (e: Exception) {
                            Log.e("DEBUG_CREATE_MARKER", "❌ Errore creazione annotation per $userId: ${e.message}", e)
                        }
                    } else {
                        Log.e("DEBUG_CREATE_MARKER", "❌ ERRORE: Stile mappa è null per $userId")
                    }

                    Log.d("DEBUG_CREATE_MARKER", "=== FINE createUserMarker (SUCCESS) per $userId ===")
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    Log.d("DEBUG_CREATE_MARKER", "Glide onLoadCleared per $userId")
                }
            })
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Raggio della Terra in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c // distanza in km
    }
}
