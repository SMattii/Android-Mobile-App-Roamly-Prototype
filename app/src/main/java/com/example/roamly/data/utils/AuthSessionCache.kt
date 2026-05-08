package com.example.roamly.data.utils

import android.content.Context
import com.example.roamly.data.models.Profile

object AuthSessionCache {

    private const val PREFS_NAME = "roamly_auth_session"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_LOGIN_AT = "login_at"
    private const val KEY_HAS_LOGGED_BEFORE = "has_logged_before"
    private const val SESSION_DURATION_MS = 30L * 24L * 60L * 60L * 1000L

    fun rememberProfile(context: Context, profile: Profile) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previousUserId = prefs.getString(KEY_USER_ID, null)
        val previousLoginAt = prefs.getLong(KEY_LOGIN_AT, 0L)
        val loginAt = if (previousUserId == profile.id && previousLoginAt > 0L) {
            previousLoginAt
        } else {
            System.currentTimeMillis()
        }

        prefs.edit()
            .putString(KEY_USER_ID, profile.id)
            .putLong(KEY_LOGIN_AT, loginAt)
            .putBoolean(KEY_HAS_LOGGED_BEFORE, profile.has_logged_before)
            .apply()
    }

    fun cachedHasLoggedBefore(context: Context, userId: String): Boolean? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_USER_ID, null) != userId) return null
        if (!prefs.contains(KEY_HAS_LOGGED_BEFORE)) return null
        return prefs.getBoolean(KEY_HAS_LOGGED_BEFORE, false)
    }

    fun isExpired(context: Context, userId: String): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_USER_ID, null) != userId) return false
        val loginAt = prefs.getLong(KEY_LOGIN_AT, 0L)
        if (loginAt <= 0L) return false
        return System.currentTimeMillis() - loginAt > SESSION_DURATION_MS
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
