package com.example.roamly

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.launch
import com.example.roamly.LocationEntry
import com.mapbox.maps.plugin.animation.flyTo
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class HomeActivity : ComponentActivity() {

    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) getCurrentLocation()
        else Toast.makeText(this, "Permessi di localizzazione negati", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        mapView = findViewById(R.id.mapView)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS) { style ->
            // Carica le icone personalizzate nello stile
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
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            Toast.makeText(this, "Attiva la localizzazione", Toast.LENGTH_SHORT).show()
            startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

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
                        showCurrentUserMarker(userId, userPoint) // usa immagine profilo come marker
                    }
                }

                fetchVisibleProfiles() // mostra tutti gli altri utenti

            } else {
                Toast.makeText(this, "Posizione non trovata", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUserAndOthers(userPoint: Point) {
        val annotationManager = mapView.annotations.createPointAnnotationManager()

        // Marker rosso per l'utente
        annotationManager.create(
            PointAnnotationOptions()
                .withPoint(userPoint)
                .withIconImage("marker-red") // Devi averlo caricato nello style
        )

        // Marker blu per altri utenti (esempio statico — in futuro verranno da Supabase)
        val nearbyUsers = listOf(
            Point.fromLngLat(userPoint.longitude() + 0.01, userPoint.latitude() + 0.01),
            Point.fromLngLat(userPoint.longitude() - 0.01, userPoint.latitude() - 0.01)
        )

        for (point in nearbyUsers) {
            annotationManager.create(
                PointAnnotationOptions()
                    .withPoint(point)
                    .withIconImage("marker-blue")
            )
        }
    }

    private fun isLocationEnabled(): Boolean {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun addUserMarkerFromUrl(userId: String, imageUrl: String, point: Point) {
        Glide.with(this)
            .asBitmap()
            .load(imageUrl)
            .circleCrop()
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    mapView.getMapboxMap().getStyle()?.addImage(userId, resource)

                    val annotationManager = mapView.annotations.createPointAnnotationManager()
                    val options = PointAnnotationOptions()
                        .withPoint(point)
                        .withIconImage(userId)

                    annotationManager.create(options)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    // nessuna azione
                }
            })
    }

    private fun fetchVisibleProfiles() {
        lifecycleScope.launch {
            try {
                val profiles = SupabaseClientProvider.db["profiles"]
                    .select {
                        filter { eq("visible", true) }
                    }
                    .decodeList<Profile>()

                for (profile in profiles) {
                    // 🔁 PER ORA usiamo una posizione dummy (da sostituire con lat/lon reali)
                    if (!profile.profile_image_url.isNullOrBlank()) {
                        val dummyPoint = Point.fromLngLat(10.0 + Math.random(), 45.0 + Math.random())
                        addUserMarkerFromUrl(profile.id, profile.profile_image_url, dummyPoint)
                    }
                }

            } catch (e: Exception) {
                Toast.makeText(this@HomeActivity, "Errore Supabase: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
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

    private fun showCurrentUserMarker(userId: String, userPoint: Point) {
        lifecycleScope.launch {
            try {
                val profile = SupabaseClientProvider.db["profiles"]
                    .select {
                        filter { eq("id", userId) }
                    }
                    .decodeSingle<Profile>()

                if (!profile.profile_image_url.isNullOrBlank()) {
                    Glide.with(this@HomeActivity)
                        .asBitmap()
                        .load(profile.profile_image_url)
                        .circleCrop()
                        .into(object : CustomTarget<Bitmap>() {
                            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                val largeBitmap = Bitmap.createScaledBitmap(resource, 150, 150, false)

                                mapView.getMapboxMap().getStyle()?.addImage("user-marker", largeBitmap)

                                val annotationManager = mapView.annotations.createPointAnnotationManager()
                                val options = PointAnnotationOptions()
                                    .withPoint(userPoint)
                                    .withIconImage("user-marker")

                                annotationManager.create(options)
                            }

                            override fun onLoadCleared(placeholder: Drawable?) {}
                        })
                }
            } catch (e: Exception) {
                Toast.makeText(this@HomeActivity, "Errore caricamento immagine profilo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}