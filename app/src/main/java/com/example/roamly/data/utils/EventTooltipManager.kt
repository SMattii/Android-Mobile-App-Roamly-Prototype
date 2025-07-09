package com.example.roamly.data.utils

import android.content.Context
import android.graphics.PointF
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.example.roamly.R
import com.example.roamly.data.models.Event
import com.example.roamly.data.models.Interest
import com.example.roamly.data.models.Language
import com.example.roamly.data.models.Profile
import com.example.roamly.fragment.EventEditFragment
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap

/**
 * Gestisce la visualizzazione del tooltip per gli eventi sulla mappa.
 *
 * Il tooltip mostra informazioni dettagliate sull'evento selezionato, tra cui:
 * - Tipo, data, ora e descrizione dell’evento
 * - Numero partecipanti e range di età
 * - Lingue e interessi tramite icone
 * - Avatar dei partecipanti
 * - Azioni dinamiche tramite pulsanti (JOIN, LEAVE, EDIT, DELETE)
 *
 * Il tooltip viene visualizzato in una posizione calcolata dinamicamente sulla mappa,
 * e supporta callback per modifiche alla partecipazione o gestione eventi.
 */
object EventTooltipManager {

    /**
     * Mostra un tooltip grafico e interattivo sopra un punto specifico sulla mappa.
     * Supporta azioni dinamiche in base al ruolo dell’utente (partecipante o creatore).
     */
    fun show(
        context: Context,
        mapView: MapView,
        mapboxMap: MapboxMap,
        tooltipContainer: FrameLayout,
        point: Point,
        event: Event,
        participants: List<Profile>,
        allLanguages: List<Language>,
        allInterests: List<Interest>,
        currentUserId: String, // 👈 aggiunto
        onJoinClick: ((eventId: String) -> Unit)? = null,
        onLeaveClick: ((eventId: String) -> Unit)? = null,
        onEditClick: ((event: Event) -> Unit)? = null,
        onDeleteClick: ((eventId: String) -> Unit)? = null,
    ) {

        // Rimuove eventuali tooltip esistenti prima di mostrarne uno nuovo
        tooltipContainer.removeAllViews()

        // Calcola la posizione sullo schermo dove posizionare il tooltip (in base al punto mappa)
        val screenCoord = mapboxMap.pixelForCoordinate(point)
        val screenPos = PointF(screenCoord.x.toFloat(), screenCoord.y.toFloat())

        // Infla il layout XML del tooltip evento
        val inflater = LayoutInflater.from(context)
        val view: View = inflater.inflate(R.layout.event_tooltip, tooltipContainer, false)

        // Mostra tipo di evento con data e orario
        view.findViewById<TextView>(R.id.txtEventType).text =
            "${event.event_type} - ${event.date} ${event.time}"

        // Descrizione testuale dell'evento
        view.findViewById<TextView>(R.id.txtDescription).text = event.desc

        // Mostra numero partecipanti attuali su massimo consentito
        view.findViewById<TextView>(R.id.txtParticipants).text =
            "${participants.size}/${event.max_participants ?: "?"} partecipanti"

        // Mostra range di età richiesto per partecipare
        view.findViewById<TextView>(R.id.txtAgeRange).text =
            "Età: ${event.min_age ?: "-"} - ${event.max_age ?: "-"}"

        // Aggiunge le bandiere delle lingue selezionate per l’evento
        val langContainer = view.findViewById<LinearLayout>(R.id.languagesContainer)
        event.languages.forEach { langCode ->
            val lang = allLanguages.find { it.id == langCode } ?: return@forEach
            val img = ImageView(context).apply {
                setImageResource(lang.getFlagResId(context))
                layoutParams = LinearLayout.LayoutParams(48, 32).apply {
                    setMargins(0, 0, 8, 0)
                }
            }
            langContainer.addView(img)
        }

        // Aggiunge le icone degli interessi associati all’evento
        val intContainer = view.findViewById<LinearLayout>(R.id.interestsContainer)
        event.interests.forEach { interestId ->
            val interest = allInterests.find { it.id == interestId } ?: return@forEach
            val iconRes = InterestProvider.getIconResIdFor(interest.name)
            val iconView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                    setMargins(0, 0, 8, 0)
                }
                iconRes?.let { setImageResource(it) }
            }
            intContainer.addView(iconView)
        }

        // Mostra gli avatar dei partecipanti all’evento
        val avatarContainer = view.findViewById<LinearLayout>(R.id.avatarsContainer)
        participants.forEach { profile ->
            val img = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(90, 90).apply {
                    setMargins(0, 0, 8, 0)
                }
            }
            Glide.with(context)
                .load(profile.profile_image_url)
                .circleCrop()
                .into(img)
            avatarContainer.addView(img)
        }

        // Inizializza pulsanti di azione (Join/Edit e Delete)
        val joinBtn = view.findViewById<Button>(R.id.btnJoin)
        val deleteBtn = view.findViewById<Button>(R.id.btnDelete)

        // Determina se l’utente è creatore o partecipante
        val isCreator = event.profile_id == currentUserId
        val isParticipant = participants.any { it.id == currentUserId }

        when {
            // Se l'utente è il creatore → mostra "EDIT" e pulsante DELETE
            isCreator -> {
                joinBtn.text = "EDIT"

                joinBtn.setOnClickListener {
                    val activity = context as? androidx.appcompat.app.AppCompatActivity ?: return@setOnClickListener

                    val fragment = EventEditFragment.newInstance(event)

                    activity.supportFragmentManager.beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        .add(R.id.eventFragmentContainer, fragment)
                        .addToBackStack("EditEventFragment")
                        .commit()

                    activity.findViewById<FrameLayout>(R.id.eventFragmentContainer)?.visibility = View.VISIBLE
                }

                deleteBtn.visibility = View.VISIBLE
                deleteBtn.setOnClickListener {
                    onDeleteClick?.invoke(event.id ?: return@setOnClickListener)
                }
            }

            // Se l’utente è partecipante → mostra "LEAVE"
            isParticipant -> {
                joinBtn.text = "LEAVE"
                joinBtn.setOnClickListener {
                    onLeaveClick?.invoke(event.id ?: return@setOnClickListener)
                }
            }

            // Altrimenti → mostra "JOIN" o "FULL" se i posti sono esauriti
            else -> {
                val maxParticipants = event.max_participants
                if (maxParticipants != null && participants.size >= maxParticipants) {
                    joinBtn.text = "FULL"
                    joinBtn.isEnabled = false
                } else {
                    joinBtn.text = "JOIN"
                    joinBtn.setOnClickListener {
                        onJoinClick?.invoke(event.id ?: return@setOnClickListener)
                    }
                }
            }
        }

        // Posiziona il tooltip sulla mappa, centrato sopra il marker
        view.x = screenPos.x - view.measuredWidth / 2
        view.y = screenPos.y - view.measuredHeight
        tooltipContainer.addView(view)

        // Ricalcola posizione dopo che il layout è stato misurato
        view.post {
            view.x = screenPos.x - view.width / 2
            view.y = screenPos.y - view.height
        }

        Log.d("EVENT_TOOLTIP", "Tooltip evento mostrato per: ${event.id}")
    }
}