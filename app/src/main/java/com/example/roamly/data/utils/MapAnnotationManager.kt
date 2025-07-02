package com.example.roamly.data.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
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

object MapAnnotationManager {

    fun createOrUpdateUserMarker(
        context: Context,
        mapView: MapView,
        mapboxMap: MapboxMap,
        userId: String,
        imageUrl: String,
        point: Point,
        getCurrentShownProfileId: () -> String?, // ✅ Funzione per ottenere il valore corrente
        onToggleCallout: (newUserId: String?) -> Unit
    ): PointAnnotationManager {
        val annotationManager = mapView.annotations.createPointAnnotationManager()

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
                        // immagine già aggiunta
                    }

                    val existing = annotationManager.annotations.firstOrNull {
                        it.getData()?.asJsonObject?.get("userId")?.asString == userId
                    } as? PointAnnotation

                    if (existing != null) {
                        existing.point = point
                        annotationManager.update(existing)
                    } else {
                        val options = PointAnnotationOptions()
                            .withPoint(point)
                            .withIconImage(imageId)
                            .withData(JsonParser.parseString("{'userId': '$userId'}").asJsonObject)

                        try {
                            annotationManager.create(options)
                        } catch (e: Exception) {
                            Log.e("MARKER_ERROR", "Errore creazione marker: ${e.message}")
                        }
                    }

                    // ✅ LOGICA CORRETTA DEL CLICK LISTENER
                    annotationManager.addClickListener { annotation ->
                        val clickedUserId = annotation.getData()?.asJsonObject?.get("userId")?.asString

                        if (clickedUserId != null) {
                            val currentShownProfileId = getCurrentShownProfileId()

                            Log.d("MARKER_CLICK", "Marker cliccato per userId=$clickedUserId")
                            Log.d("MARKER_CLICK", "currentShownProfileId attuale = $currentShownProfileId")

                            val newUserIdToDisplay = if (clickedUserId == currentShownProfileId) {
                                Log.d("MARKER_CLICK", "Tooltip già aperto per $clickedUserId, lo chiudo (toggle).")
                                null
                            } else {
                                Log.d("MARKER_CLICK", "Marker diverso cliccato o nessun tooltip aperto, mostro per $clickedUserId.")
                                clickedUserId
                            }

                            Log.d("MARKER_CLICK", "Decisione finale: newUserIdToDisplay = $newUserIdToDisplay")

                            // 👉 Se stai cambiando utente, chiudi SUBITO il tooltip precedente
                            if (clickedUserId != currentShownProfileId) {
                                Log.d("TOOLTIP_ACTION", "Chiudo subito il tooltip precedente (se presente).")
                                onToggleCallout(null)
                            }

                            mapboxMap.flyTo(
                                CameraOptions.Builder()
                                    .center(annotation.point)
                                    .zoom(mapboxMap.cameraState.zoom)
                                    .build(),
                                MapAnimationOptions.mapAnimationOptions {
                                    duration(500L)
                                }
                            )

                            mapView.postDelayed({
                                Log.d("FLY_TO_COMPLETE", "FlyTo completato. Chiamo onToggleCallout con $newUserIdToDisplay")
                                onToggleCallout(newUserIdToDisplay)
                            }, 700L)
                        }

                        true
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
            })

        return annotationManager
    }
}