package com.turni.calendario.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages persistent and secure storage of Google Gemini API credentials and user preferences.
 * Uses Context.MODE_PRIVATE SharedPreferences when an Android Context is available,
 * and maintains an in-memory fallback when Context is null (e.g. in JVM unit tests).
 */
object SecurePreferencesManager {
    private const val PREFS_NAME = "calediario_secure_settings"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    private const val KEY_DEFAULT_USER_NAME = "default_user_name"
    const val DEFAULT_USER_NAME = ""

    // In-memory fallback for testing or when Context is unavailable
    @Volatile
    private var inMemoryApiKey: String? = null

    @Volatile
    private var inMemoryUserName: String? = null

    /**
     * Retrieves the stored Google Gemini API Key.
     * Checks SharedPreferences if Context is provided; otherwise returns the in-memory fallback.
     */
    @JvmStatic
    @JvmOverloads
    fun getApiKey(context: Context? = null): String? {
        if (context == null) {
            return inMemoryApiKey
        }
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val stored = prefs.getString(KEY_GEMINI_API_KEY, null)
            if (!stored.isNullOrBlank()) stored else inMemoryApiKey
        } catch (_: Exception) {
            inMemoryApiKey
        }
    }

    /**
     * Persists the Google Gemini API Key into private SharedPreferences (if Context is provided)
     * and updates the in-memory fallback.
     */
    @JvmStatic
    @JvmOverloads
    fun saveApiKey(context: Context? = null, apiKey: String) {
        val trimmed = apiKey.trim()
        inMemoryApiKey = trimmed.ifBlank { null }
        if (context != null) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_GEMINI_API_KEY, trimmed).apply()
            } catch (_: Exception) {
                // Fallback retained in memory
            }
        }
    }

    /**
     * Returns whether an API Key is currently set.
     */
    @JvmStatic
    @JvmOverloads
    fun hasApiKey(context: Context? = null): Boolean {
        return !getApiKey(context).isNullOrBlank()
    }

    /**
     * Returns a visually masked version of the API Key (e.g. "AIzaSy...4xQ") for secure UI display.
     * If no key is set, returns an empty string.
     */
    @JvmStatic
    @JvmOverloads
    fun getMaskedApiKey(context: Context? = null): String {
        val key = getApiKey(context) ?: return ""
        if (key.isBlank()) return ""
        return if (key.length <= 8) {
            "••••••••"
        } else {
            "${key.take(6)}...${key.takeLast(3)}"
        }
    }

    /**
     * Retrieves the preferred worker name used for automatic squad and shift pre-selection.
     * Defaults to null or empty if not yet set by the user.
     */
    @JvmStatic
    @JvmOverloads
    fun getUserName(context: Context? = null): String? {
        if (context == null) {
            return inMemoryUserName ?: DEFAULT_USER_NAME.ifBlank { null }
        }
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val stored = prefs.getString(KEY_DEFAULT_USER_NAME, null)
            if (!stored.isNullOrBlank()) stored else inMemoryUserName
        } catch (_: Exception) {
            inMemoryUserName
        }
    }

    /**
     * Persists the default worker name into private SharedPreferences and memory.
     */
    @JvmStatic
    @JvmOverloads
    fun saveUserName(context: Context? = null, name: String) {
        val trimmed = name.trim()
        inMemoryUserName = trimmed.ifBlank { null }
        if (context != null) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_DEFAULT_USER_NAME, trimmed).apply()
            } catch (_: Exception) {
                // Fallback retained in memory
            }
        }
    }

    /**
     * Clears stored credentials and user preferences (both in SharedPreferences and in-memory).
     */
    @JvmStatic
    @JvmOverloads
    fun clear(context: Context? = null) {
        inMemoryApiKey = null
        inMemoryUserName = null
        if (context != null) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
            } catch (_: Exception) {
                // Ignore
            }
        }
    }
}
