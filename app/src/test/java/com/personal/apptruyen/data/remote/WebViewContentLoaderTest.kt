package com.personal.apptruyen.data.remote

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebViewContentLoaderTest {

    @Test
    fun `isAllowedDomain validates exact domain and dot-subdomain boundaries`() {
        assertTrue(WebViewContentLoader.isAllowedDomain("m.sstruyen.pics"))
        assertTrue(WebViewContentLoader.isAllowedDomain("sstruyen.vn"))
        assertTrue(WebViewContentLoader.isAllowedDomain("sstruyen.net"))
        assertTrue(WebViewContentLoader.isAllowedDomain("wikicv.org"))
        assertTrue(WebViewContentLoader.isAllowedDomain("WIKICV.NET."))

        // Deprecated domains: tangthuvien is blocked
        assertFalse(WebViewContentLoader.isAllowedDomain("tangthuvien.vn"))
        assertFalse(WebViewContentLoader.isAllowedDomain("tangthuvien.org"))
        assertFalse(WebViewContentLoader.isAllowedDomain("truyen.tangthuvien.vn"))

        // Blocked domains: prefix/suffix spoofing, null, blank
        assertFalse(WebViewContentLoader.isAllowedDomain("evil-tangthuvien.vn"))
        assertFalse(WebViewContentLoader.isAllowedDomain("tangthuvien.vn.evil.com"))
        assertFalse(WebViewContentLoader.isAllowedDomain("evilwikicv.org"))
        assertFalse(WebViewContentLoader.isAllowedDomain(null))
        assertFalse(WebViewContentLoader.isAllowedDomain("   "))
        assertFalse(WebViewContentLoader.isAllowedDomain("."))
    }
}
