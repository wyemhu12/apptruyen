package com.personal.apptruyen.ui.navigation

import android.util.Base64
import android.util.Log

/**
 * Utility for encoding/decoding URLs in navigation arguments.
 * Centralizes Base64 logic used by StoryDetail и Reader routes.
 */
object NavigationUtils {
    private const val TAG = "NavigationUtils"

    fun encodeUrl(url: String): String = Base64.encodeToString(url.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)

    fun decodeUrl(encoded: String): String =
        try {
            String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Failed to decode URL, using raw value", e)
            encoded
        }
}
