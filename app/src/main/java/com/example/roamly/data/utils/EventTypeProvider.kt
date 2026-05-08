package com.example.roamly.data.utils

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.roamly.R

object EventTypeProvider {

    data class EventTypeOption(
        val key: String,
        @StringRes val labelResId: Int,
        @DrawableRes val iconResId: Int
    )

    fun commonTypes(): List<EventTypeOption> = listOf(
        EventTypeOption("chill", R.string.event_type_chill, R.drawable.ic_event_chill),
        EventTypeOption("party", R.string.event_type_party, R.drawable.ic_event_party),
        EventTypeOption("aperitivo", R.string.event_type_aperitivo, R.drawable.ic_food),
        EventTypeOption("dinner", R.string.event_type_dinner, R.drawable.ic_food),
        EventTypeOption("coffee", R.string.event_type_coffee, R.drawable.ic_networking),
        EventTypeOption("walk", R.string.event_type_walk, R.drawable.ic_nature),
        EventTypeOption("sport", R.string.event_type_sport, R.drawable.ic_sport),
        EventTypeOption("culture", R.string.event_type_culture, R.drawable.ic_culture),
        EventTypeOption("networking", R.string.event_type_networking, R.drawable.ic_networking),
        EventTypeOption("other", R.string.event_type_other, R.drawable.ic_more_horizontal)
    )

    @DrawableRes
    fun iconResFor(type: String?): Int {
        val normalized = type.orEmpty().trim().lowercase()
        return commonTypes().firstOrNull { it.key == normalized }?.iconResId
            ?: when (normalized) {
                "festa" -> R.drawable.ic_event_party
                "relax" -> R.drawable.ic_event_chill
                "cena", "food", "food and drinks" -> R.drawable.ic_food
                "passeggiata", "nature" -> R.drawable.ic_nature
                "cultura" -> R.drawable.ic_culture
                "altro" -> R.drawable.ic_more_horizontal
                else -> R.drawable.ic_event_generic
            }
    }

    fun displayName(context: Context, type: String?): String {
        val raw = type.orEmpty().trim()
        if (raw.isBlank()) return context.getString(R.string.event_type_other)

        val normalized = raw.lowercase()
        val option = commonTypes().firstOrNull { option ->
            option.key == normalized || context.getString(option.labelResId).equals(raw, ignoreCase = true)
        }

        return option?.let { context.getString(it.labelResId) }
            ?: raw.replaceFirstChar { it.titlecase() }
    }
}
