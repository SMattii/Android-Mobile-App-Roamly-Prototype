package com.example.roamly.data.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.appcompat.content.res.AppCompatResources
import com.example.roamly.R
import com.example.roamly.data.models.Event
import com.google.gson.JsonParser
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager

object EventAnnotationManager {

    fun createEventMarker(
        context: Context,
        mapView: MapView,
        mapboxMap: MapboxMap,
        event: Event,
        point: Point,
        getCurrentShownEventId: () -> String?,
        onToggleEventCallout: (newEventId: String?) -> Unit
    ): PointAnnotationManager {
        val annotationManager = mapView.annotations.createPointAnnotationManager()

        val iconRes = when (event.event_type.lowercase()) {
            "party" -> R.drawable.ic_event_party
            "chill" -> R.drawable.ic_event_chill
            else -> R.drawable.ic_event_generic
        }

        val iconDrawable = AppCompatResources.getDrawable(context, iconRes)
        if (iconDrawable == null) {
            Log.e("EVENT_MARKER", "Drawable null per icona: $iconRes")
            return annotationManager
        }

        // Imposta dimensione totale del marker
        val size = 150
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // 1. Disegna il bordo nero (più grande)
        val borderPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, borderPaint)

        // 2. Disegna il cerchio bianco sopra (più piccolo)
        val whitePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, whitePaint)


        // 2. Disegna l'icona sopra, centrata con padding
        iconDrawable.setBounds(30, 30, size - 30, size - 30) // padding = 30px
        iconDrawable.draw(canvas)

        val scaledBitmap = bitmap // non serve ridimensionare, è già 150x150


        val imageId = "event-icon-${event.event_type.lowercase()}"
        try {
            mapView.mapboxMap.style?.addImage(imageId, scaledBitmap)
            Log.d("EVENT_MARKER", "Icona aggiunta per tipo evento: ${event.event_type}")
        } catch (e: Exception) {
            Log.w("EVENT_MARKER", "Icona già presente o errore: ${e.message}")
        }


        val existing = annotationManager.annotations.firstOrNull {
            it.getData()?.asJsonObject?.get("eventId")?.asString == event.id
        } as? PointAnnotation

        if (existing != null) {
            existing.point = point
            annotationManager.update(existing)
        } else {
            val options = PointAnnotationOptions()
                .withPoint(point)
                .withIconImage(imageId)
                .withData(JsonParser.parseString("{\"eventId\": \"${event.id}\"}").asJsonObject)

            try {
                annotationManager.create(options)
            } catch (e: Exception) {
                Log.e("EVENT_MARKER", "Errore creazione marker evento: ${e.message}")
            }
        }

        annotationManager.addClickListener { annotation ->
            val clickedEventId = annotation.getData()?.asJsonObject?.get("eventId")?.asString
            val currentShownEventId = getCurrentShownEventId()

            val newIdToDisplay = if (clickedEventId == currentShownEventId) null else clickedEventId

            if (clickedEventId != currentShownEventId) {
                onToggleEventCallout(null)
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
                onToggleEventCallout(newIdToDisplay)
            }, 700L)

            true
        }

        return annotationManager
    }
}