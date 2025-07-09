package com.example.roamly.data.utils

import com.example.roamly.data.models.Event
import com.example.roamly.data.models.NearbyUserProfile

/**
 * Simple in-memory cache shared across the whole app. It stores
 *  • NearbyUserProfile per userId
 *  • Event per eventId
 * The cache is cleared on logout/reset and repopulated periodically.
 */
object DataCache {
    private val userCache = mutableMapOf<String, NearbyUserProfile>()
    private val eventCache = mutableMapOf<String, Event>()

    /* ------------ USERS ------------ */
    fun getUser(id: String): NearbyUserProfile? = userCache[id]

    fun putUsers(users: Collection<NearbyUserProfile>) {
        users.forEach { userCache[it.id] = it }
    }

    /* ------------ EVENTS ------------ */
    fun getEvent(id: String): Event? = eventCache[id]

    fun putEvents(events: Collection<Event>) {
        events.forEach { ev -> ev.id?.let { eventCache[it] = ev } }
    }

    /** Cancella completamente entrambe le cache */
    fun clear() {
        userCache.clear()
        eventCache.clear()
    }
} 