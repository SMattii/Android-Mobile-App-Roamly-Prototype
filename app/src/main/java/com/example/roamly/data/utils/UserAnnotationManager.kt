package com.example.roamly.data.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.roamly.activity.HomeActivity
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.google.gson.JsonParser
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager

object UserAnnotationManager {

    private val userAnnotationManagers = mutableMapOf<String, PointAnnotationManager>()
    private val userMarkers = mutableMapOf<String, PointAnnotation>()

    fun createOrUpdateUserMarker(
        context: Context,
        mapView: MapView,
        mapboxMap: MapboxMap,
        userId: String,
        imageUrl: String,
        point: Point,
        getCurrentShownProfileId: () -> String?,
        onToggleCallout: (newUserId: String?) -> Unit
    ): PointAnnotationManager {
        val annotationManager = userAnnotationManagers.getOrPut(userId) {
            mapView.annotations.createPointAnnotationManager()
        }

        Glide.with(context)
            .asBitmap()
            .load(imageUrl)
            .circleCrop()
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    val bitmap = Bitmap.createScaledBitmap(resource, 150, 150, false)
                    val style = mapView.mapboxMap.style ?: return

                    val imageId = "user-marker-$userId"
                    try {
                        style.addImage(imageId, bitmap)
                    } catch (_: Exception) {
                        // Ignora eccezione se immagine già aggiunta
                    }

                    val existing = userMarkers[userId]
                    if (existing != null) {
                        existing.point = point
                        annotationManager.update(existing)
                        Log.d("MARKER_UPDATE", "Aggiornato marker per userId=$userId")
                    } else {
                        val options = PointAnnotationOptions()
                            .withPoint(point)
                            .withIconImage(imageId)
                            .withData(JsonParser.parseString("{\"userId\": \"$userId\"}").asJsonObject)

                        try {
                            val marker = annotationManager.create(options)
                            userMarkers[userId] = marker
                            Log.d("MARKER_CREATE", "Creato nuovo marker per userId=$userId")

                            annotationManager.addClickListener { annotation ->
                                val clickedUserId = annotation.getData()?.asJsonObject?.get("userId")?.asString
                                val currentShownProfileId = getCurrentShownProfileId()

                                val newUserIdToDisplay = if (clickedUserId == currentShownProfileId) null else clickedUserId
                                if (clickedUserId != currentShownProfileId) onToggleCallout(null)
                                val activity = mapView.context as? HomeActivity
                                activity?.hideEventCallout()


                                mapboxMap.flyTo(
                                    CameraOptions.Builder()
                                        .center(annotation.point)
                                        .zoom(mapboxMap.cameraState.zoom)
                                        .build(),
                                    MapAnimationOptions.mapAnimationOptions { duration(500L) }
                                )

                                mapView.postDelayed({
                                    onToggleCallout(newUserIdToDisplay)
                                }, 700L)

                                true
                            }
                        } catch (e: Exception) {
                            Log.e("MARKER_ERROR", "Errore creazione marker: ${e.message}")
                        }
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
            })

        return annotationManager
    }

    fun removeUserMarker(userId: String) {
        userMarkers[userId]?.let { marker ->
            userAnnotationManagers[userId]?.delete(marker)
            userMarkers.remove(userId)
        }
        userAnnotationManagers[userId]?.let { manager ->
            manager.deleteAll()
            userAnnotationManagers.remove(userId)
        }
        Log.d("MARKER_REMOVE", "Rimosso marker e manager per userId=$userId")
    }

    fun clearAll() {
        userAnnotationManagers.forEach { (_, manager) ->
            manager.deleteAll()
        }
        userAnnotationManagers.clear()
        userMarkers.clear()
    }

    fun getManagedUserIds(): List<String> = userAnnotationManagers.keys.toList()
}