package com.personal.apptruyen.ui.navigation

import timber.log.Timber
import java.util.Base64

/**
 * Utility for encoding/decoding URLs and route parameters in navigation arguments.
 * Centralizes URL-safe Base64 logic used by Search, StoryDetail, and Reader routes.
 */
object NavigationUtils {
    private const val EMPTY_SEGMENT_SENTINEL = "_"
    private const val BASE64_PARAM_PREFIX = "b64_"

    fun encodeUrl(url: String): String {
        if (url.isBlank()) return EMPTY_SEGMENT_SENTINEL
        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(url.toByteArray(Charsets.UTF_8))
    }

    fun decodeUrl(encoded: String): String {
        if (encoded.isBlank() || encoded == EMPTY_SEGMENT_SENTINEL) return ""
        if (encoded.startsWith("http://") || encoded.startsWith("https://")) return encoded
        return try {
            String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Failed to decode URL, using raw value")
            encoded
        }
    }

    fun encodeRouteParam(value: String): String {
        if (value.isBlank()) return EMPTY_SEGMENT_SENTINEL
        val encodedBytes =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.toByteArray(Charsets.UTF_8))
        return "$BASE64_PARAM_PREFIX$encodedBytes"
    }

    fun decodeRouteParam(encoded: String): String {
        if (encoded.isBlank() || encoded == EMPTY_SEGMENT_SENTINEL) return ""
        if (!encoded.startsWith(BASE64_PARAM_PREFIX)) return encoded
        val payload = encoded.removePrefix(BASE64_PARAM_PREFIX)
        return try {
            String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Failed to decode route param, using raw value")
            encoded
        }
    }
}
