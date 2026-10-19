package com.personal.apptruyen

import android.webkit.CookieManager
import com.personal.apptruyen.data.remote.cookie.AndroidCookieStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class CookieTest {
    @Test
    fun `removeCookies with explicit cookieNames expires host and domain cookies and flushes`() {
        val mockCookieManager = mockk<CookieManager>(relaxed = true)
        val store = AndroidCookieStore { mockCookieManager }

        store.removeCookies("https://wikicv.org", listOf("express.sid"))

        verify {
            mockCookieManager.setCookie(
                "https://wikicv.org",
                "express.sid=; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT",
            )
        }
        verify {
            mockCookieManager.setCookie(
                "https://wikicv.org",
                "express.sid=; Path=/; Domain=.wikicv.org; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT",
            )
        }
        verify { mockCookieManager.flush() }
    }

    @Test
    fun `removeCookies with empty cookieNames and bare domain parses existing cookies and expires all`() {
        val mockCookieManager = mockk<CookieManager>(relaxed = true)
        every { mockCookieManager.getCookie("https://wikicv.org") } returns "express.sid=abc; cf_clearance=xyz"
        val store = AndroidCookieStore { mockCookieManager }

        store.removeCookies("wikicv.org", emptyList())

        verify {
            mockCookieManager.setCookie(
                "https://wikicv.org",
                "express.sid=; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT",
            )
        }
        verify {
            mockCookieManager.setCookie(
                "https://wikicv.org",
                "express.sid=; Path=/; Domain=.wikicv.org; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT",
            )
        }
        verify {
            mockCookieManager.setCookie(
                "https://wikicv.org",
                "cf_clearance=; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT",
            )
        }
        verify {
            mockCookieManager.setCookie(
                "https://wikicv.org",
                "cf_clearance=; Path=/; Domain=.wikicv.org; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT",
            )
        }
        verify { mockCookieManager.flush() }
    }
}
