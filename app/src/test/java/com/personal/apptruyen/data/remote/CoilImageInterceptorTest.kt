package com.personal.apptruyen.data.remote

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Call
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicReference

class CoilImageInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private val interceptor = CoilImageInterceptor()

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
        client =
            OkHttpClient
                .Builder()
                .addInterceptor(interceptor)
                .build()
    }

    @AfterEach
    fun teardown() {
        server.close()
    }

    @Test
    fun `TC6 - intercept attaches synchronized WikidichScraper DESKTOP_UA when User-Agent is missing`() {
        server.enqueue(MockResponse(code = 200, body = "img"))
        val request =
            Request
                .Builder()
                .url(server.url("/photo/123"))
                .removeHeader("User-Agent")
                .build()

        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
        }

        val recorded = server.takeRequest()
        assertEquals(WikidichScraper.DESKTOP_UA, recorded.headers["User-Agent"])
        assertTrue(recorded.headers["User-Agent"]?.contains("Chrome/122.0.0.0") == true)
    }

    @Test
    fun `TC7 and TC8 - resolveRefererForHost returns matching source referer and null for third-party hosts`() {
        assertEquals("https://wikicv.org/", interceptor.resolveRefererForHost("wikicv.org"))
        assertEquals("https://wikicv.org/", interceptor.resolveRefererForHost("sub.wikicv.org"))
        assertEquals("https://sstruyen.pics/", interceptor.resolveRefererForHost("sstruyen.pics"))
        assertEquals("https://sstruyen.pics/", interceptor.resolveRefererForHost("file.sstruyen.pics"))
        assertEquals(null, interceptor.resolveRefererForHost("tangthuvien.org"))
        assertEquals(null, interceptor.resolveRefererForHost("www.tangthuvien.vn"))
        assertNull(interceptor.resolveRefererForHost("i.imgur.com"))
        assertNull(interceptor.resolveRefererForHost("evilwikicv.org"))
    }

    @Test
    fun `TC9 - intercept closes failed 503 response and retries once to succeed on 200`() {
        server.enqueue(MockResponse(code = 503, body = "Temporary Unavailable"))
        server.enqueue(MockResponse(code = 200, body = "image-bytes"))

        val request = Request.Builder().url(server.url("/photo/retry-503")).build()
        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("image-bytes", response.body.string())
        }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `TC9b - intercept retries once on SocketTimeoutException and attaches suppressed exception if retry also fails`() {
        var attempts = 0
        val timeoutInterceptor =
            Interceptor { _ ->
                attempts++
                throw SocketTimeoutException("Simulated timeout #$attempts")
            }
        val timeoutClient =
            OkHttpClient
                .Builder()
                .addInterceptor(CoilImageInterceptor())
                .addInterceptor(timeoutInterceptor)
                .build()

        val request = Request.Builder().url(server.url("/photo/timeout")).build()
        val thrown =
            assertThrows(SocketTimeoutException::class.java) {
                timeoutClient.newCall(request).execute()
            }
        assertEquals(2, attempts)
        assertEquals(1, thrown.suppressed.size)
        assertEquals("Simulated timeout #1", thrown.suppressed.first().message)
    }

    @Test
    fun `TC10 - intercept does not retry on HTTP 429 or 403`() {
        server.enqueue(MockResponse(code = 429, body = "Rate Limited"))
        val req429 = Request.Builder().url(server.url("/photo/rate-limited")).build()
        client.newCall(req429).execute().use { response ->
            assertEquals(429, response.code)
        }
        assertEquals(1, server.requestCount)

        server.enqueue(MockResponse(code = 403, body = "Forbidden"))
        val req403 = Request.Builder().url(server.url("/photo/forbidden")).build()
        client.newCall(req403).execute().use { response ->
            assertEquals(403, response.code)
        }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `TC11 - intercept does not retry when Call is canceled during first attempt`() {
        val callRef = AtomicReference<Call>()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    callRef.get()?.cancel()
                    return MockResponse(code = 503, body = "Canceled while in flight")
                }
            }

        val request = Request.Builder().url(server.url("/photo/canceled")).build()
        val call = client.newCall(request)
        callRef.set(call)

        try {
            val response: Response = call.execute()
            response.close()
        } catch (_: IOException) {
            // Expected when OkHttp aborts canceled call
        }

        assertTrue(call.isCanceled())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `TC12 - buildImageOkHttpClient configures dedicated dispatcher retryOnConnectionFailure and CoilImageInterceptor`() {
        val baseClient = OkHttpClient.Builder().build()
        val imageClient = buildImageOkHttpClient(baseClient)

        assertEquals(32, imageClient.dispatcher.maxRequests)
        assertEquals(4, imageClient.dispatcher.maxRequestsPerHost)
        assertTrue(imageClient.retryOnConnectionFailure)
        assertTrue(imageClient.interceptors.any { it is CoilImageInterceptor })
        assertTrue(imageClient.cookieJar is ReadOnlyWikidichCookieJar)
    }

    @Test
    fun `TC13 - ReadOnlyWikidichCookieJar only loads cookies for wikicv org and never saves cookies`() {
        val savedCookies = mutableListOf<Cookie>()
        val sampleCookie =
            Cookie
                .Builder()
                .name("cf_clearance")
                .value("token123")
                .domain("wikicv.org")
                .build()
        val fakeDelegate =
            object : CookieJar {
                override fun saveFromResponse(
                    url: HttpUrl,
                    cookies: List<Cookie>,
                ) {
                    savedCookies.addAll(cookies)
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> = listOf(sampleCookie)
            }

        val readOnlyJar = ReadOnlyWikidichCookieJar(fakeDelegate)

        val wikicvCookies = readOnlyJar.loadForRequest("https://wikicv.org/photo/123".toHttpUrl())
        val subWikicvCookies = readOnlyJar.loadForRequest("https://cdn.wikicv.org/photo/123".toHttpUrl())
        val imgurCookies = readOnlyJar.loadForRequest("https://i.imgur.com/abc.jpg".toHttpUrl())
        val sstruyenCookies = readOnlyJar.loadForRequest("https://file.sstruyen.pics/covers/1.jpg".toHttpUrl())

        assertEquals(listOf(sampleCookie), wikicvCookies)
        assertEquals(listOf(sampleCookie), subWikicvCookies)
        assertTrue(imgurCookies.isEmpty())
        assertTrue(sstruyenCookies.isEmpty())

        readOnlyJar.saveFromResponse("https://i.imgur.com/abc.jpg".toHttpUrl(), listOf(sampleCookie))
        readOnlyJar.saveFromResponse("https://wikicv.org/photo/123".toHttpUrl(), listOf(sampleCookie))
        assertTrue(savedCookies.isEmpty())
    }

    @Test
    fun `TC14 - intercept blocks requests to deprecated tangthuvien hosts before proceeding on network`() {
        assertTrue(interceptor.isBlockedHost("tangthuvien.org"))
        assertTrue(interceptor.isBlockedHost("cdn.tangthuvien.org"))
        assertTrue(interceptor.isBlockedHost("tangthuvien.vn"))
        assertTrue(interceptor.isBlockedHost("www.tangthuvien.vn"))
        assertTrue(interceptor.isBlockedHost("truyen.tangthuvien.vn"))
        assertTrue(interceptor.isBlockedHost("TANGTHUVIEN.ORG."))
        assertFalse(interceptor.isBlockedHost("wikicv.org"))
        assertFalse(interceptor.isBlockedHost("sstruyen.pics"))
        assertFalse(interceptor.isBlockedHost("evil-tangthuvien.org"))

        val reqOrg = Request.Builder().url("https://tangthuvien.org/covers/1.jpg").build()
        val thrownOrg =
            assertThrows(IOException::class.java) {
                client.newCall(reqOrg).execute()
            }
        assertTrue(thrownOrg.message?.contains("tangthuvien.org") == true)

        val reqVn = Request.Builder().url("https://truyen.tangthuvien.vn/covers/1.jpg").build()
        val thrownVn =
            assertThrows(IOException::class.java) {
                client.newCall(reqVn).execute()
            }
        assertTrue(thrownVn.message?.contains("truyen.tangthuvien.vn") == true)
        assertEquals(0, server.requestCount)
    }
}
