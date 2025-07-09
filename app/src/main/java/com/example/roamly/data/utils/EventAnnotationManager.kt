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
import com.example.roamly.data.utils.DataCache
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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Singleton responsabile della gestione dei marker evento sulla mappa Mapbox.
 *
 * Si occupa di:
 * - Creare marker personalizzati con icona e bordo
 * - Sincronizzare i marker validi entro 10km e non scaduti
 * - Gestire i click sui marker per mostrare un tooltip dettagliato
 * - Rimuovere marker non più validi (evento scaduto o fuori raggio)
 * - Evitare la duplicazione dei listener
 *
 * Utilizza:
 * - [PointAnnotationManager] per la creazione dei marker
 * - Cache condivisa via [DataCache]
 * - Chiamate asincrone a Supabase via [EventRepository] e [ProfileRepository]
 *
 * Deve essere inizializzato all'interno di una `MapView` e richiede accesso a `HomeActivity`
 * per gestire correttamente il contesto e la UI associata.
 */
object EventAnnotationManager {

    private var eventAnnotationManager: PointAnnotationManager? = null
    private var listenerRegistered = false
    private val eventMarkers = mutableMapOf<String, PointAnnotation>()
    private val isSyncing = AtomicBoolean(false)

    /**
     * Crea o aggiorna un marker per un evento sulla mappa.
     *
     * Se l'evento esiste già, aggiorna la sua posizione. Altrimenti crea un nuovo marker
     * con icona dinamica basata sul tipo evento ("party", "chill", ecc.) e lo registra nel sistema Mapbox.
     *
     * Registra anche un listener click (una sola volta) che apre/chiude il tooltip dell'evento
     * ed effettua uno `flyTo` animato sul punto selezionato.
     *
     * @param context Contesto dell'app (deve essere associato a HomeActivity).
     * @param mapView La MapView attiva.
     * @param mapboxMap Istanza MapboxMap associata.
     * @param event L'evento da rappresentare.
     * @param point La posizione geografica (lat/lon) del marker.
     * @param getCurrentShownEventId Lambda per ottenere l’ID del tooltip attualmente visibile.
     * @param onToggleEventCallout Callback per mostrare o chiudere il tooltip associato.
     * @return Il manager dei marker evento.
     */
    fun createEventMarker(
        context: Context,
        mapView: MapView,
        mapboxMap: MapboxMap,
        event: Event,
        point: Point,
        getCurrentShownEventId: () -> String?,
        onToggleEventCallout: (newEventId: String?) -> Unit
    ): PointAnnotationManager {

        if (eventAnnotationManager == null) {
            eventAnnotationManager = mapView.annotations.createPointAnnotationManager()
        }

        val annotationManager = eventAnnotationManager!!

        // Aggiorna cache globale con l'evento corrente
        event.id?.let { DataCache.putEvents(listOf(event)) }

        // Registra il listener di click una sola volta per evitare duplicati
        if (!listenerRegistered) {
            annotationManager.addClickListener { annotation ->
                Log.d("MARKER_CLICK", "Marker evento cliccato")

                val clickedEventId = annotation.getData()?.asJsonObject?.get("eventId")?.asString
                val currentId = getCurrentShownEventId()
                val wasOpen = clickedEventId == currentId
                val newIdToDisplay = if (wasOpen) null else clickedEventId

                val activity = mapView.context as? HomeActivity
                activity?.hideUserCallout()

                // Toggle tooltip prima dell'animazione
                onToggleEventCallout(newIdToDisplay)

                if (wasOpen) {
                    return@addClickListener true
                }

                mapboxMap.flyTo(
                    CameraOptions.Builder()
                        .center(annotation.point)
                        .zoom(mapboxMap.cameraState.zoom)
                        .build(),
                    MapAnimationOptions.mapAnimationOptions { duration(500L) }
                )

                mapView.postDelayed({
                    if (newIdToDisplay != null) {
                        val context = mapView.context
                        val lifecycleOwner = context as? androidx.lifecycle.LifecycleOwner ?: return@postDelayed
                        val scope = lifecycleOwner.lifecycleScope

                        scope.launch {
                            val eventRepo = EventRepository
                            val profileRepo = ProfileRepository

                            // Usa cache globale se possibile
                            val cached = DataCache.getEvent(newIdToDisplay)
                            val thisEvent = cached ?: run {
                                val fetched = eventRepo.getEvents().find { it.id == newIdToDisplay }
                                if (fetched != null) DataCache.putEvents(listOf(fetched))
                                fetched
                            } ?: return@launch

                            val participantIds = eventRepo.getEventParticipants(thisEvent.id!!)
                            val participantProfiles = profileRepo.getProfilesByIds(participantIds)

                            val allLanguages = LanguageProvider.loadLanguagesFromAssets(context)
                            val allInterests = InterestRepository.fetchAllInterests()

                            val tooltipContainer = mapView.rootView.findViewById<FrameLayout>(R.id.tooltipContainer) ?: return@launch

                            val currentUserId = SupabaseClientProvider.auth.currentUserOrNull()?.id ?: return@launch

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
                                onEditClick = { eventToEdit ->
                                    Toast.makeText(context, "TODO: implementa modifica evento", Toast.LENGTH_SHORT).show()
                                },
                                onDeleteClick = { eventIdToDelete ->
                                    scope.launch {
                                        val ok = eventRepo.deleteEvent(eventIdToDelete)
                                        if (ok) {
                                            removeEventMarker(eventIdToDelete)
                                            val act = context as? HomeActivity
                                            act?.hideEventCallout()
                                            act?.currentShownEventId = null
                                            Toast.makeText(context, "Evento eliminato", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Errore eliminazione", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }, 500L)

                true
            }
            listenerRegistered = true
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


// FIX: Usa la mappa interna invece di cercare nelle annotations
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

        return annotationManager
    }

    /**
     * Rimuove il marker evento associato all'ID specificato, se esistente.
     *
     * @param eventId ID dell’evento da rimuovere.
     */
    fun removeEventMarker(eventId: String) {
        val annotation = eventMarkers[eventId]
        if (annotation != null) {
            eventAnnotationManager?.delete(annotation)
            eventMarkers.remove(eventId)
            Log.d("EVENT_MARKER", "Marker evento $eventId rimosso.")
        } else {
            Log.w("EVENT_MARKER", "Nessun marker trovato per evento $eventId")
        }
    }

    /**
     * Sincronizza tutti i marker evento visibili sulla mappa.
     *
     * Recupera gli eventi dal repository, filtra quelli entro 10km e non scaduti
     * (tramite `visible_until`), e aggiunge o aggiorna i marker corrispondenti.
     *
     * Rimuove automaticamente i marker associati a eventi che non sono più validi.
     * Il metodo è protetto da flag `isSyncing` per evitare chiamate parallele.
     *
     * @param context Contesto dell’app.
     * @param mapView MapView attiva.
     * @param mapboxMap Istanza mappa.
     * @param myLat Latitudine utente.
     * @param myLon Longitudine utente.
     * @param getCurrentShownEventId Lambda per sapere quale evento è attualmente aperto.
     * @param onToggleEventCallout Callback per aprire o chiudere il tooltip.
     */
    suspend fun synchronizeEventMarkers(
        context: Context,
        mapView: MapView,
        mapboxMap: MapboxMap,
        myLat: Double,
        myLon: Double,
        getCurrentShownEventId: () -> String?,
        onToggleEventCallout: (String?) -> Unit
    ) {
        if (!isSyncing.compareAndSet(false, true)) {
            Log.d("EVENT_SYNC", "Sync already in progress, skipping.")
            return
        }

        try {
            val style = mapView.mapboxMap.getStyle()
            if (style == null) {
                Log.w("EVENT_SYNC", "Stile non ancora pronto → skip sincronizzazione")
                return
            }

            val eventRepo = EventRepository
            Log.d("EVENT_SYNC", "Inizio sincronizzazione eventi")
            val allEvents = eventRepo.getEvents()
            Log.d("EVENT_SYNC", "Ricevuti ${allEvents.size} eventi dal repository")

            val now = System.currentTimeMillis()

            val validEventIds = mutableListOf<String>()

            Log.d("EVENT_SYNC", "Eventi trovati dal DB: ${allEvents.size}")
            Log.d("EVENT_SYNC", "Marker attualmente attivi: ${eventMarkers.size}")

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

                Log.d("EVENT_SYNC", "Evento ${event.id}: dist=$dist km, tipo=${event.event_type}, scaduto=${visibleUntilMillis < now}")

                if (dist <= 10 && visibleUntilMillis >= now) {
                    validEventIds.add(event.id!!)
                    Log.d("EVENT_SYNC", "Evento ${event.id} valido - creando marker")

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
                    Log.d("EVENT_SYNC", "Evento ${event.id} non valido")
                }
            }

            Log.d("EVENT_SYNC", "Sincronizzazione completata - Marker attivi: ${eventMarkers.size}")

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
        } finally {
            isSyncing.set(false)
            Log.d("EVENT_SYNC", "Sync finished.")
        }
    }

    /**
     * Cancella tutti i marker evento e resetta il manager interno.
     *
     * Da usare ad esempio al logout o nel reset completo dell’app.
     * La cache eventi viene invece gestita da [DataCache.clear].
     */
    fun clearAll() {
        eventAnnotationManager?.deleteAll()
        eventAnnotationManager = null
        eventMarkers.clear()
        // la cache globale viene svuotata da DataCache.clear() nello stato di reset
        listenerRegistered = false
        Log.d("EVENT_MARKER", "Tutti i marker e manager evento rimossi.")
    }

}