package com.personal.apptruyen.ui.navigation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for NavigationUtils URL encode/decode logic.
 */
class NavigationUtilsTest {
    @Test
    fun `encode then decode returns original URL`() {
        val url = "https://truyencom.com/some-story.123/"
        val encoded = NavigationUtils.encodeUrl(url)
        val decoded = NavigationUtils.decodeUrl(encoded)
        assertEquals(url, decoded)
    }

    @Test
    fun `handles special characters in URL`() {
        val url = "https://example.com/path?query=hello world&foo=bar#anchor"
        val encoded = NavigationUtils.encodeUrl(url)
        val decoded = NavigationUtils.decodeUrl(encoded)
        assertEquals(url, decoded)
    }

    @Test
    fun `handles Vietnamese characters in URL`() {
        val url = "https://example.com/truyện-tiếng-việt/"
        val encoded = NavigationUtils.encodeUrl(url)
        val decoded = NavigationUtils.decodeUrl(encoded)
        assertEquals(url, decoded)
    }

    @Test
    fun `handles empty URL`() {
        val url = ""
        val encoded = NavigationUtils.encodeUrl(url)
        val decoded = NavigationUtils.decodeUrl(encoded)
        assertEquals("_", encoded)
        assertEquals(url, decoded)
    }

    @Test
    fun `encoded URL does not contain slash or plus`() {
        val url = "https://example.com/path/with/slashes"
        val encoded = NavigationUtils.encodeUrl(url)
        // URL-safe Base64 should not contain / or +
        assertFalse(encoded.contains("/"))
        assertFalse(encoded.contains("+"))
    }

    @Test
    fun `handles long URL`() {
        val url = "https://example.com/" + "a".repeat(500)
        val encoded = NavigationUtils.encodeUrl(url)
        val decoded = NavigationUtils.decodeUrl(encoded)
        assertEquals(url, decoded)
    }

    @Test
    fun `encodeUrl and encodeRouteParam handle blank strings and special route characters safely`() {
        assertEquals("_", NavigationUtils.encodeUrl(""))
        assertEquals("_", NavigationUtils.encodeUrl("   "))
        assertEquals("", NavigationUtils.decodeUrl("_"))
        assertEquals("", NavigationUtils.decodeUrl(""))

        val rawParam = "story/id?with=special#chars&more=1"
        val encodedParam = NavigationUtils.encodeRouteParam(rawParam)
        assertTrue(encodedParam.startsWith("b64_"))
        assertFalse(encodedParam.contains("/"))
        assertFalse(encodedParam.contains("?"))
        assertFalse(encodedParam.contains("#"))
        assertFalse(encodedParam.contains("&"))
        assertEquals(rawParam, NavigationUtils.decodeRouteParam(encodedParam))

        // Backward compatibility with plain unencoded IDs in SavedStateHandle
        assertEquals("plain_id", NavigationUtils.decodeRouteParam("plain_id"))

        // Route templates
        val detailRoute = Screen.StoryDetail.createRoute("wikidich_slug/1", "")
        assertTrue(detailRoute.startsWith("story/"))
        assertTrue(detailRoute.endsWith("/_"))
    }
}
