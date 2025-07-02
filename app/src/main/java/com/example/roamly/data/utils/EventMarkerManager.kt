package com.example.roamly.data.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.lifecycleScope
import com.example.roamly.R
import com.example.roamly.data.models.Event
import com.example.roamly.data.repository.EventRepository
import com.example.roamly.data.repository.InterestRepository
import com.example.roamly.data.repository.ProfileRepository
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
import kotlinx.coroutines.launch

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
            Log.d("MARKER_CLICK", "✅ Marker evento cliccato")

            val clickedEventId = annotation.getData()?.asJsonObject?.get("eventId")?.asString
            Log.d("MARKER_CLICK", "🆔 clickedEventId = $clickedEventId")

            val currentShownEventId = getCurrentShownEventId()
            Log.d("MARKER_CLICK", "🎯 currentShownEventId = $currentShownEventId")

            val newIdToDisplay = if (clickedEventId == currentShownEventId) null else clickedEventId
            Log.d("MARKER_CLICK", "📌 newIdToDisplay = $newIdToDisplay")

            if (clickedEventId != currentShownEventId) {
                Log.d("MARKER_CLICK", "🔁 Chiudo tooltip precedente")
                onToggleEventCallout(null)
            }

            Log.d("MARKER_CLICK", "🗺️ Eseguo flyTo")
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
                Log.d("MARKER_CLICK", "⏱️ Entrato in postDelayed (dopo flyTo)")

                if (newIdToDisplay != null) {
                    val context = mapView.context
                    val lifecycleOwner = context as? androidx.lifecycle.LifecycleOwner
                    if (lifecycleOwner == null) {
                        Log.e("MARKER_CLICK", "❌ lifecycleOwner NULL")
                        return@postDelayed
                    }

                    val scope = lifecycleOwner.lifecycleScope
                    if (scope == null) {
                        Log.e("MARKER_CLICK", "❌ lifecycleScope NULL")
                        return@postDelayed
                    }

                    Log.d("MARKER_CLICK", "🚀 Lancio coroutine")
                    scope.launch {
                        val eventRepo = EventRepository()
                        val profileRepo = ProfileRepository()

                        Log.d("MARKER_CLICK", "📡 Fetch eventi da Supabase")
                        val events = eventRepo.getEvents()

                        val thisEvent = events.find { it.id == newIdToDisplay }
                        if (thisEvent == null) {
                            Log.e("MARKER_CLICK", "❌ Evento non trovato per ID $newIdToDisplay")
                            return@launch
                        }
                        Log.d("MARKER_CLICK", "✅ Evento trovato: ${thisEvent.event_type}")

                        Log.d("MARKER_CLICK", "👥 Fetch partecipanti dell'evento")
                        val participantIds = eventRepo.getEventParticipants(thisEvent.id!!)
                        Log.d("MARKER_CLICK", "👤 ID partecipanti = $participantIds")

                        Log.d("MARKER_CLICK", "📄 Fetch profili partecipanti")
                        val participantProfiles = profileRepo.getProfilesByIds(participantIds)
                        Log.d("MARKER_CLICK", "📎 Profili trovati: ${participantProfiles.size}")

                        Log.d("MARKER_CLICK", "📚 Carico tutte le lingue e interessi")
                        val allLanguages = LanguageProvider.loadLanguagesFromAssets(context)
                        val allInterests = InterestRepository().fetchAllInterests()

                        Log.d("MARKER_CLICK", "📌 onToggleEventCallout con $newIdToDisplay")
                        onToggleEventCallout(newIdToDisplay)

                        val tooltipContainer = mapView.rootView.findViewById<FrameLayout>(R.id.tooltipContainer)
                        if (tooltipContainer == null) {
                            Log.e("MARKER_CLICK", "❌ tooltipContainer non trovato")
                            return@launch
                        }

                        Log.d("MARKER_CLICK", "✨ Mostro tooltip evento")
                        EventTooltipManager.show(
                            context = context,
                            mapView = mapView,
                            mapboxMap = mapboxMap,
                            tooltipContainer = tooltipContainer,
                            point = annotation.point,
                            event = thisEvent,
                            participants = participantProfiles,
                            allLanguages = allLanguages,
                            allInterests = allInterests,
                            onJoinClick = { joinedEventId ->
                                Log.d("MARKER_CLICK", "👆 JOIN cliccato su evento $joinedEventId")
                                SupabaseClientProvider.auth.currentUserOrNull()?.id?.let { uid ->
                                    scope.launch {
                                        val ok = eventRepo.addParticipant(joinedEventId, uid)
                                        if (ok) {
                                            Toast.makeText(context, "Ti sei unito all'evento!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Errore partecipazione", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )
                    }
                } else {
                    Log.d("MARKER_CLICK", "⚠️ newIdToDisplay è null, non apro tooltip")
                }
            }, 700L)
            true
        }

        return annotationManager
    }
}