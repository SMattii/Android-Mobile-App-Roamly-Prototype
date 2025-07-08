package com.example.roamly.data.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.lifecycleScope
import com.example.roamly.R
import com.example.roamly.activity.HomeActivity
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

    private val eventAnnotationManagers = mutableMapOf<String, PointAnnotationManager>()
    private val eventMarkers = mutableMapOf<String, PointAnnotation>()

    fun createEventMarker(
        context: Context,
        mapView: MapView,
        mapboxMap: MapboxMap,
        event: Event,
        point: Point,
        getCurrentShownEventId: () -> String?,
        onToggleEventCallout: (newEventId: String?) -> Unit
    ): PointAnnotationManager {

        val annotationManager = eventAnnotationManagers.getOrPut("global_event_manager") {
            mapView.annotations.createPointAnnotationManager()
        }

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


// 🔧 FIX: Usa la mappa interna invece di cercare nelle annotations
        val existingAnnotation = eventMarkers[event.id!!]

        if (existingAnnotation != null) {
            // Aggiorna marker esistente
            existingAnnotation.point = point
            annotationManager.update(existingAnnotation)
            Log.d("EVENT_MARKER", "Marker evento ${event.id} aggiornato")
        } else {
            // Crea nuovo marker
            val options = PointAnnotationOptions()
                .withPoint(point)
                .withIconImage(imageId)
                .withData(JsonParser.parseString("{\"eventId\": \"${event.id}\"}").asJsonObject)

            try {
                val annotation = annotationManager.create(options)
                eventMarkers[event.id] = annotation
                Log.d("EVENT_MARKER", "Nuovo marker evento ${event.id} creato")
            } catch (e: Exception) {
                Log.e("EVENT_MARKER", "Errore creazione marker evento: ${e.message}")
                return annotationManager
            }
        }

        annotationManager.addClickListener { annotation ->
            Log.d("MARKER_CLICK", "✅ Marker evento cliccato")

            val clickedEventId = annotation.getData()?.asJsonObject?.get("eventId")?.asString
            val currentId = getCurrentShownEventId()
            val wasOpen = clickedEventId == currentId
            Log.d("MARKER_CLICK", "🔍 currentShownEventId = $currentId, clickedEventId = $clickedEventId, wasOpen = $wasOpen")
            val newIdToDisplay = if (wasOpen) null else clickedEventId

            Log.d("MARKER_CLICK", "🆔 clickedEventId = $clickedEventId, wasOpen = $wasOpen → newIdToDisplay = $newIdToDisplay")

            val activity = mapView.context as? HomeActivity
            activity?.hideUserCallout()

            // 🔑 MUOVI QUI il toggle prima del flyTo
            onToggleEventCallout(newIdToDisplay)

            if (wasOpen) {
                Log.d("MARKER_CLICK", "❎ Tooltip evento già aperto, lo chiudo")
                return@addClickListener true
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
                        val eventRepo = EventRepository
                        val profileRepo = ProfileRepository

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
                        val allInterests = InterestRepository.fetchAllInterests()

                        //Log.d("MARKER_CLICK", "📌 onToggleEventCallout con $newIdToDisplay")
                        //onToggleEventCallout(newIdToDisplay)

                        val tooltipContainer = mapView.rootView.findViewById<FrameLayout>(R.id.tooltipContainer)
                        if (tooltipContainer == null) {
                            Log.e("MARKER_CLICK", "❌ tooltipContainer non trovato")
                            return@launch
                        }

                        Log.d("MARKER_CLICK", "✨ Mostro tooltip evento")
                        val currentUserId = SupabaseClientProvider.auth.currentUserOrNull()?.id
                        if (currentUserId == null) {
                            Log.e("MARKER_CLICK", "❌ currentUserId è null, non posso mostrare tooltip")
                            return@launch
                        }

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
                            currentUserId = currentUserId,
                            onJoinClick = { joinedEventId ->
                                scope.launch {
                                    val ok = eventRepo.addParticipant(joinedEventId, currentUserId)
                                    if (ok) {
                                        Toast.makeText(context, "Ti sei unito all'evento!", Toast.LENGTH_SHORT).show()
                                        onToggleEventCallout(null)
                                    } else {
                                        Toast.makeText(context, "Errore partecipazione", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onLeaveClick = { eventId ->
                                scope.launch {
                                    val ok = eventRepo.removeParticipant(eventId, currentUserId)
                                    if (ok) {
                                        Toast.makeText(context, "Hai lasciato l’evento", Toast.LENGTH_SHORT).show()
                                        onToggleEventCallout(null)
                                    } else {
                                        Toast.makeText(context, "Errore uscita evento", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onEditClick = { event ->
                                Toast.makeText(context, "TODO: implementa modifica evento", Toast.LENGTH_SHORT).show()
                            },
                            onDeleteClick = { eventId ->
                                scope.launch {
                                    val ok = eventRepo.deleteEvent(eventId)
                                    if (ok) {
                                        EventAnnotationManager.removeEventMarker(eventId)
                                        val activity = context as? HomeActivity
                                        activity?.hideEventCallout()
                                        activity?.currentShownEventId = null
                                        Toast.makeText(context, "Evento eliminato", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Errore eliminazione", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                } else {
                    Log.d("MARKER_CLICK", "⚠️ newIdToDisplay è null, non apro tooltip")
                }
            }, 100L)

            true
        }

        return annotationManager
    }

    fun removeEventMarker(eventId: String) {
        val annotation = eventMarkers[eventId]
        if (annotation != null) {
            eventAnnotationManagers["global_event_manager"]?.delete(annotation)
            eventMarkers.remove(eventId)
            Log.d("EVENT_MARKER", "Marker evento $eventId rimosso.")
        } else {
            Log.w("EVENT_MARKER", "Nessun marker trovato per evento $eventId")
        }
    }

    suspend fun synchronizeEventMarkers(
        context: Context,
        mapView: MapView,
        mapboxMap: MapboxMap,
        myLat: Double,
        myLon: Double,
        getCurrentShownEventId: () -> String?,
        onToggleEventCallout: (String?) -> Unit
    ) {
        val style = mapView.mapboxMap.getStyle()
        if (style == null) {
            Log.w("EVENT_SYNC", "⚠️ Stile non ancora pronto → skip sincronizzazione")
            return
        }

        try {
            val eventRepo = EventRepository
            Log.d("EVENT_SYNC", "🚀 Inizio sincronizzazione eventi")
            val allEvents = eventRepo.getEvents()
            Log.d("EVENT_SYNC", "📥 Ricevuti ${allEvents.size} eventi dal repository")

            val now = System.currentTimeMillis()

            val validEventIds = mutableListOf<String>()

            Log.d("EVENT_SYNC", "🔍 Eventi trovati dal DB: ${allEvents.size}")
            Log.d("EVENT_SYNC", "📊 Marker attualmente attivi: ${eventMarkers.size}")

            for (event in allEvents) {
                val dist = MapUtils.haversine(myLat, myLon, event.latitude, event.longitude)

                val visibleUntilMillis = try {
                    event.visible_until?.let {
                        java.time.Instant.parse(it).toEpochMilli()
                    } ?: Long.MAX_VALUE
                } catch (e: Exception) {
                    Log.w("EVENT_SYNC", "Data visibilità malformata per evento ${event.id}: ${event.visible_until}")
                    Long.MIN_VALUE
                }

                Log.d("EVENT_SYNC", "📍 Evento ${event.id}: dist=$dist km, tipo=${event.event_type}, scaduto=${visibleUntilMillis < now}")

                if (dist <= 10 && visibleUntilMillis >= now) {
                    validEventIds.add(event.id!!)
                    Log.d("EVENT_SYNC", "✅ Evento ${event.id} valido - creando marker")

                    createEventMarker(
                        context = context,
                        mapView = mapView,
                        mapboxMap = mapboxMap,
                        event = event,
                        point = Point.fromLngLat(event.longitude, event.latitude),
                        getCurrentShownEventId = getCurrentShownEventId,
                        onToggleEventCallout = onToggleEventCallout
                    )
                } else {
                    Log.d("EVENT_SYNC", "❌ Evento ${event.id} non valido")
                }
            }

            Log.d("EVENT_SYNC", "📊 Sincronizzazione completata - Marker attivi: ${eventMarkers.size}")

            // Rimuovi marker non più validi
            val currentMarkers = eventMarkers.keys.toList()
            for (eventId in currentMarkers) {
                if (!validEventIds.contains(eventId)) {
                    removeEventMarker(eventId)
                    Log.d("EVENT_SYNC", "Marker evento $eventId rimosso (non più valido)")
                }
            }

        } catch (e: Exception) {
            Log.e("EVENT_SYNC", "Errore sincronizzazione eventi: ${e.message}", e)
        }
    }

    fun clearAll() {
        eventAnnotationManagers.values.forEach { it.deleteAll() }
        eventAnnotationManagers.clear()
        eventMarkers.clear()
        Log.d("EVENT_MARKER", "Tutti i marker e manager evento rimossi.")
    }

}