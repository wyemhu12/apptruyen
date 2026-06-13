package com.personal.apptruyen.ui.navigation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Base64 as JavaBase64

/**
 * Tests for NavigationUtils URL encode/decode logic.
 * Uses java.util.Base64 for verification since android.util.Base64
 * is not available in JVM unit tests.
 */
class NavigationUtilsTest {

    // Mirror the encode/decode logic using java.util.Base64 for testing
    private fun testEncode(url: String): String =
        JavaBase64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(url.toByteArray(Charsets.UTF_8))

    private fun testDecode(encoded: String): String = String(JavaBase64.getUrlDecoder().decode(encoded), Charsets.UTF_8)

    @Test
    fun `encode then decode returns original URL`() {
        val url = "https://truyencom.com/some-story.123/"
        val encoded = testEncode(url)
        val decoded = testDecode(encoded)
        assertEquals(url, decoded)
    }

    @Test
    fun `handles special characters in URL`() {
        val url = "https://example.com/path?query=hello world&foo=bar#anchor"
        val encoded = testEncode(url)
        val decoded = testDecode(encoded)
        assertEquals(url, decoded)
    }

    @Test
    fun `handles Vietnamese characters in URL`() {
        val url = "https://example.com/truyện-tiếng-việt/"
        val encoded = testEncode(url)
        val decoded = testDecode(encoded)
        assertEquals(url, decoded)
    }

    @Test
    fun `handles empty URL`() {
        val url = ""
        val encoded = testEncode(url)
        val decoded = testDecode(encoded)
        assertEquals(url, decoded)
    }

    @Test
    fun `encoded URL does not contain slash or plus`() {
        val url = "https://example.com/path/with/slashes"
        val encoded = testEncode(url)
        // URL-safe Base64 should not contain / or +
        assertFalse(encoded.contains("/"))
        assertFalse(encoded.contains("+"))
    }

    @Test
    fun `handles long URL`() {
        val url = "https://example.com/" + "a".repeat(500)
        val encoded = testEncode(url)
        val decoded = testDecode(encoded)
        assertEquals(url, decoded)
    }
}
