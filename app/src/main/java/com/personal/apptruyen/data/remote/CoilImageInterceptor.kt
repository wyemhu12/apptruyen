package com.personal.apptruyen.data.remote

import com.personal.apptruyen.BuildConfig
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * CookieJar chỉ đọc dành riêng cho ImageLoader:
 * - Chỉ đính kèm cookie phiên cho tên miền wikicv.org.
 * - Không lưu bất kỳ Set-Cookie nào từ response ảnh vào kho cookie WebView.
 */
internal class ReadOnlyWikidichCookieJar(
    private val delegate: CookieJar,
) : CookieJar {
    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        // Chế độ chỉ đọc: không ghi cookie từ CDN ảnh bên thứ ba hay response ảnh vào kho cookie
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host.lowercase(Locale.ROOT)
        return if (host == "wikicv.org" || host.endsWith(".wikicv.org")) {
            delegate.loadForRequest(url)
        } else {
            emptyList()
        }
    }
}

/**
 * Application interceptor cho Coil ImageLoader:
 * - Gắn User-Agent đồng bộ với [WikidichScraper.DESKTOP_UA] để khớp với cookie cf_clearance.
 * - Gắn Referer chuẩn theo từng tên miền nguồn truyện.
 * - Thử lại tối đa 1 lần khi gặp HTTP 502/503/504 (đóng response cũ trước khi retry) hoặc [SocketTimeoutException],
 *   không thử lại với 429/403 hoặc khi request đã bị hủy.
 */
internal class CoilImageInterceptor(
    private val defaultUserAgent: String = WikidichScraper.DESKTOP_UA,
) : Interceptor {
    private val inFlightByHost = ConcurrentHashMap<String, AtomicInteger>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val call = chain.call()
        val request = chain.request()
        val rawHost = request.url.host
        if (isBlockedHost(rawHost)) {
            throw IOException("Đã chặn kết nối tải ảnh tới tên miền ngừng hoạt động: $rawHost")
        }
        val builder = request.newBuilder()

        if (request.header("User-Agent") == null) {
            builder.header("User-Agent", defaultUserAgent)
        }

        if (request.header("Referer") == null) {
            resolveRefererForHost(request.url.host)?.let { referer ->
                builder.header("Referer", referer)
            }
        }

        val newRequest = builder.build()
        val host = newRequest.url.host.lowercase(Locale.ROOT)
        val hostCounter = inFlightByHost.computeIfAbsent(host) { AtomicInteger(0) }

        val currentInFlight = hostCounter.incrementAndGet()
        var response: Response? = null
        var firstError: IOException? = null
        try {
            response = chain.proceed(newRequest)
            if (BuildConfig.DEBUG) {
                Timber.tag("CoilNet").d(
                    "host=%s path=%s attempt=1 code=%d inFlight=%d",
                    host,
                    newRequest.url.encodedPath,
                    response.code,
                    currentInFlight,
                )
            }
        } catch (e: IOException) {
            firstError = e
            if (BuildConfig.DEBUG) {
                Timber.tag("CoilNet").d(
                    "host=%s path=%s attempt=1 error=%s inFlight=%d",
                    host,
                    newRequest.url.encodedPath,
                    e.javaClass.simpleName,
                    currentInFlight,
                )
            }
        } finally {
            hostCounter.decrementAndGet()
        }

        val isCanceledOrInterrupted = call.isCanceled() || Thread.currentThread().isInterrupted
        val shouldRetryHttp = response != null && !response.isSuccessful && response.code in SERVER_ERROR_CODES
        val shouldRetryTimeout = firstError is SocketTimeoutException

        if ((shouldRetryHttp || shouldRetryTimeout) && !isCanceledOrInterrupted) {
            response?.close()
            val retryInFlight = hostCounter.incrementAndGet()
            try {
                val retryResponse = chain.proceed(newRequest)
                if (BuildConfig.DEBUG) {
                    Timber.tag("CoilNet").d(
                        "host=%s path=%s attempt=2 code=%d inFlight=%d",
                        host,
                        newRequest.url.encodedPath,
                        retryResponse.code,
                        retryInFlight,
                    )
                }
                return retryResponse
            } catch (retryError: IOException) {
                firstError?.let { retryError.addSuppressed(it) }
                if (BuildConfig.DEBUG) {
                    Timber.tag("CoilNet").d(
                        "host=%s path=%s attempt=2 error=%s inFlight=%d",
                        host,
                        newRequest.url.encodedPath,
                        retryError.javaClass.simpleName,
                        retryInFlight,
                    )
                }
                throw retryError
            } finally {
                hostCounter.decrementAndGet()
            }
        }

        if (firstError != null) throw firstError
        return response ?: throw IOException("Phản hồi ảnh không hợp lệ (null response)")
    }

    internal fun isBlockedHost(host: String): Boolean {
        val h = host.trim().lowercase(Locale.ROOT).trimEnd('.')
        if (h.isEmpty()) return false
        return BLOCKED_IMAGE_HOSTS.any { blocked ->
            h == blocked || h.endsWith(".$blocked")
        }
    }

    internal fun resolveRefererForHost(host: String): String? {
        val h = host.lowercase(Locale.ROOT)
        return when {
            h == "wikicv.org" || h.endsWith(".wikicv.org") -> "https://wikicv.org/"
            h == "sstruyen.pics" || h.endsWith(".sstruyen.pics") -> "https://sstruyen.pics/"
            else -> null
        }
    }

    companion object {
        private val SERVER_ERROR_CODES = setOf(502, 503, 504)
        private val BLOCKED_IMAGE_HOSTS = setOf("tangthuvien.org", "tangthuvien.vn")
    }
}

/**
 * Tạo OkHttpClient chuyên biệt cho Coil ImageLoader kế thừa cấu hình DNS DoH và timeout từ [baseClient].
 */
internal fun buildImageOkHttpClient(baseClient: OkHttpClient): OkHttpClient {
    val imageDispatcher =
        Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 4
        }
    return baseClient
        .newBuilder()
        .dispatcher(imageDispatcher)
        .cookieJar(ReadOnlyWikidichCookieJar(baseClient.cookieJar))
        .retryOnConnectionFailure(true)
        .addInterceptor(CoilImageInterceptor())
        .build()
}
