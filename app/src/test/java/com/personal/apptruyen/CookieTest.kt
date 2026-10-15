package com.personal.apptruyen

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Test

class CookieTest {
    @Test
    fun testParsing() {
        val url = "https://www.lacatruyen.buzz".toHttpUrl()
        val cookieString = "cf_clearance=123; next-auth.session-token=456; __Secure-next-auth.session-token=789"
        val cookies =
            cookieString
                .split(";")
                .mapNotNull { it.trim() }
                .filter { it.isNotBlank() }
                .mapNotNull { Cookie.parse(url, it) }
        cookies.forEach { println("Cookie: name=${it.name}, value=${it.value}") }
    }
}
