package com.personal.apptruyen.data.remote.cookie

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CookieJar bridge giữa CookieStore và OkHttp.
 * Khi user đăng nhập qua WebView, cookies tự động sync sang OkHttp requests.
 */
@Singleton
class WebViewCookieHandler
    @Inject
    constructor(
        private val cookieStore: CookieStore,
    ) : CookieJar {

        override fun saveFromResponse(
            url: HttpUrl,
            cookies: List<Cookie>,
        ) {
            val urlString = url.toString()
            cookies.forEach { cookie ->
                cookieStore.setCookie(urlString, cookie.toString())
            }
            cookieStore.flush()
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val urlString = url.toString()
            val cookieString = cookieStore.getCookie(urlString) ?: return emptyList()
            return cookieString
                .split(";")
                .mapNotNull { it.trim() }
                .filter { it.isNotBlank() }
                .mapNotNull { Cookie.parse(url, it) }
        }

        /**
         * Xóa toàn bộ cookie cho domain cụ thể hoặc toàn bộ CookieStore.
         */
        fun clearCookies(domain: String? = null) {
            if (domain != null) {
                cookieStore.removeCookies(domain, emptyList())
            } else {
                cookieStore.removeAllCookies()
            }
            cookieStore.flush()
        }
    }
