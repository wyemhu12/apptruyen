package com.personal.apptruyen.data.remote.auth

import com.personal.apptruyen.data.model.SessionVerifyResult
import com.personal.apptruyen.data.remote.cookie.CookieStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SourceAuthManagerTest {

    private lateinit var server: MockWebServer
    private lateinit var cookieStore: FakeCookieStore
    private lateinit var authManager: SourceAuthManager

    class FakeCookieStore : CookieStore {
        private val cookies = mutableMapOf<String, MutableMap<String, String>>()

        override fun getCookie(url: String): String? {
            val domain = url.substringAfter("://").substringBefore("/")
            val domainCookies = cookies[domain] ?: return null
            if (domainCookies.isEmpty()) return null
            return domainCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }

        override fun setCookie(
            url: String,
            cookieValue: String,
        ) {
            val domain = url.substringAfter("://").substringBefore("/")
            val nameValue = cookieValue.substringBefore(";").trim()
            val name = nameValue.substringBefore("=").trim()
            val value = nameValue.substringAfter("=").trim()

            if (cookieValue.contains("Max-Age=0") || cookieValue.contains("Expires=Thu, 01 Jan 1970")) {
                cookies[domain]?.remove(name)
            } else {
                cookies.getOrPut(domain) { mutableMapOf() }[name] = value
            }
        }

        override fun removeCookies(
            url: String,
            cookieNames: List<String>,
        ) {
            val domain = url.substringAfter("://").substringBefore("/")
            for (name in cookieNames) {
                cookies[domain]?.remove(name)
            }
        }

        override fun removeAllCookies() {
            cookies.clear()
        }

        override fun flush() {}
    }

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()

        cookieStore = FakeCookieStore()

        // OkHttpClient với CookieJar trả cookieStore (giống app runtime)
        val appCookieJar =
            object : CookieJar {
                override fun saveFromResponse(
                    url: HttpUrl,
                    cookies: List<Cookie>,
                ) {
                    cookies.forEach { cookieStore.setCookie(url.toString(), it.toString()) }
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    val cookieString = cookieStore.getCookie(url.toString()) ?: return emptyList()
                    return cookieString.split(";").mapNotNull { Cookie.parse(url, it.trim()) }
                }
            }

        val client =
            OkHttpClient
                .Builder()
                .cookieJar(appCookieJar)
                .build()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        authManager = SourceAuthManager(cookieStore, client, scope)
    }

    @AfterEach
    fun teardown() {
        server.close()
    }

    @Test
    fun `importCookies validCookie cleans old cookies and writes new token`() =
        runTest {
            cookieStore.setCookie("https://wikicv.org", "express.sid=OLD_GUEST_TOKEN")

            val result = authManager.importCookies("wikidich", "other_cookie=123; theme=dark")
            assertTrue(result is SessionVerifyResult.InvalidCookie)
        }

    @Test
    fun `logout cleans cookies and updates auth state`() =
        runTest {
            cookieStore.setCookie("https://wikicv.org", "express.sid=ACTIVE_TOKEN")
            assertTrue(authManager.isLoggedIn("wikidich"))

            authManager.logout("wikidich")
            assertFalse(authManager.isLoggedIn("wikidich"))
            assertTrue(authManager.loginRequiredSources.value.contains("wikidich"))
        }
}
