package com.personal.apptruyen.data.remote

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebViewContentLoaderTest {

    @Test
    fun `isAllowedDomain validates exact domain and dot-subdomain boundaries`() {
        assertTrue(WebViewContentLoader.isAllowedDomain("tangthuvien.vn"))
        assertTrue(WebViewContentLoader.isAllowedDomain("tangthuvien.org"))
        assertTrue(WebViewContentLoader.isAllowedDomain("truyen.tangthuvien.vn"))
        assertTrue(WebViewContentLoader.isAllowedDomain("m.sstruyen.pics"))
        assertTrue(WebViewContentLoader.isAllowedDomain("wikicv.org"))
        assertTrue(WebViewContentLoader.isAllowedDomain("WIKICV.NET."))

        // Blocked domains: prefix/suffix spoofing, null, blank
        assertFalse(WebViewContentLoader.isAllowedDomain("evil-tangthuvien.vn"))
        assertFalse(WebViewContentLoader.isAllowedDomain("tangthuvien.vn.evil.com"))
        assertFalse(WebViewContentLoader.isAllowedDomain("evilwikicv.org"))
        assertFalse(WebViewContentLoader.isAllowedDomain(null))
        assertFalse(WebViewContentLoader.isAllowedDomain("   "))
        assertFalse(WebViewContentLoader.isAllowedDomain("."))
    }
}
