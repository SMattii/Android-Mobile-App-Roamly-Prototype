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
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap

object EventTooltipManager {

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
        onJoinClick: ((eventId: String) -> Unit)? = null
    ) {
        tooltipContainer.removeAllViews()

        val screenCoord = mapboxMap.pixelForCoordinate(point)
        val screenPos = PointF(screenCoord.x.toFloat(), screenCoord.y.toFloat())
        val inflater = LayoutInflater.from(context)
        val view: View = inflater.inflate(R.layout.event_tooltip, tooltipContainer, false)

        view.findViewById<TextView>(R.id.txtEventType).text =
            "${event.event_type} - ${event.date} ${event.time}"

        view.findViewById<TextView>(R.id.txtParticipants).text =
            "${participants.size}/${event.max_participants ?: "?"} partecipanti"

        view.findViewById<TextView>(R.id.txtAgeRange).text =
            "Età: ${event.min_age ?: "-"} - ${event.max_age ?: "-"}"

        // Lingue
        val langContainer = view.findViewById<LinearLayout>(R.id.languagesContainer)
        event.languages.forEach { langCode ->
            val lang = allLanguages.find { it.code == langCode } ?: return@forEach
            val img = ImageView(context).apply {
                setImageResource(lang.flagResId)
                layoutParams = LinearLayout.LayoutParams(48, 32).apply {
                    setMargins(0, 0, 8, 0)
                }
            }
            langContainer.addView(img)
        }

        // Interessi
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

        // Partecipanti
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

        // JOIN button
        val joinBtn = view.findViewById<Button>(R.id.btnJoin)
        joinBtn.setOnClickListener {
            onJoinClick?.invoke(event.id ?: return@setOnClickListener)
        }

        // Posizionamento sulla mappa
        view.x = screenPos.x - view.measuredWidth / 2
        view.y = screenPos.y - view.measuredHeight
        tooltipContainer.addView(view)

        // Assicura che venga misurata e centrata dopo il layout
        view.post {
            view.x = screenPos.x - view.width / 2
            view.y = screenPos.y - view.height
        }

        Log.d("EVENT_TOOLTIP", "Tooltip evento mostrato per: ${event.id}")
    }
}