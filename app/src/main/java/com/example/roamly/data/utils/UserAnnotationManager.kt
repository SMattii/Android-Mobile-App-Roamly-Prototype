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

/**
 * Gestisce i marker utente (PointAnnotation) sulla mappa Mapbox.
 * Ogni utente visibile viene rappresentato da un marker con immagine profilo circolare.
 * Il marker può essere aggiornato o cliccato per mostrare/nascondere un tooltip utente.
 *
 * Usa una mappa per tenere traccia dei PointAnnotationManager e dei marker per ogni userId.
 *
 * Dipende da:
 * - Glide per il caricamento asincrono delle immagini
 * - Mapbox Annotation Plugin per la gestione dei marker
 * - HomeActivity per nascondere il tooltip evento (quando si interagisce con un marker utente)
 */
object UserAnnotationManager {

    // Singolo PointAnnotationManager per tutti i marker utente
    private var userAnnotationManager: PointAnnotationManager? = null
    private val userMarkers = mutableMapOf<String, PointAnnotation>()

    /**
     * Crea o aggiorna un marker per l'utente specificato.
     *
     * Se il marker esiste già, aggiorna solo la posizione.
     * Altrimenti, carica l'immagine profilo e crea un nuovo marker con listener click.
     *
     * @param context Il contesto usato per Glide
     * @param mapView Riferimento alla MapView
     * @param mapboxMap Istanza attiva della mappa
     * @param userId ID univoco dell'utente
     * @param imageUrl URL dell'immagine profilo da usare come icona
     * @param point Coordinate geografiche del marker
     * @param getCurrentShownProfileId Lambda per sapere quale profilo è attualmente visibile
     * @param onToggleCallout Callback che mostra/nasconde il tooltip per il profilo selezionato
     * @return Il PointAnnotationManager usato per questo utente
     */
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
        if (userAnnotationManager == null) {
            userAnnotationManager = mapView.annotations.createPointAnnotationManager()
        }

        val annotationManager = userAnnotationManager!!

        // Nessun listener globale: per ogni marker creato in seguito abbiamo il suo listener dedicato

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

                            // Registra listener click per questo marker specifico
                            annotationManager.addClickListener { clickedAnnotation ->
                                if (clickedAnnotation != marker) return@addClickListener false

                                val clickedUserId = clickedAnnotation.getData()?.asJsonObject?.get("userId")?.asString
                                val currentShownProfileId = getCurrentShownProfileId()

                                val newUserIdToDisplay = if (clickedUserId == currentShownProfileId) null else clickedUserId

                                if (clickedUserId != currentShownProfileId) onToggleCallout(null)

                                val activity = mapView.context as? HomeActivity
                                activity?.hideEventCallout()

                                mapboxMap.flyTo(
                                    CameraOptions.Builder()
                                        .center(clickedAnnotation.point)
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

    /**
     * Rimuove il marker dell'utente specificato dalla mappa.
     * Elimina anche il relativo PointAnnotationManager.
     *
     * @param userId ID dell’utente da rimuovere dalla mappa
     */
    fun removeUserMarker(userId: String) {
        userMarkers[userId]?.let { marker ->
            userAnnotationManager?.delete(marker)
            userMarkers.remove(userId)
        }
        Log.d("MARKER_REMOVE", "Rimosso marker e manager per userId=$userId")
    }

    /**
     * Rimuove tutti i marker e i relativi manager dalla mappa.
     * Chiamato quando la mappa viene resettata o aggiornata completamente.
     */
    fun clearAll() {
        userAnnotationManager?.deleteAll()
        userAnnotationManager = null
        // nessun listenerRegistered, nulla da fare
        userMarkers.clear()
    }

    /**
     * Restituisce la lista di userId attualmente gestiti da questo manager.
     * Utile per debug o sincronizzazione dati/mappa.
     *
     * @return Lista di ID utente presenti sulla mappa
     */
    fun getManagedUserIds(): List<String> = userMarkers.keys.toList()
}