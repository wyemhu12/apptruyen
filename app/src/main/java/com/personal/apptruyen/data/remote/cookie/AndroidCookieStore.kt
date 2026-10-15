package com.personal.apptruyen.data.remote.cookie

import android.webkit.CookieManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidCookieStore
    @Inject
    constructor() : CookieStore {
        private val cookieManager: CookieManager
            get() = CookieManager.getInstance()

        override fun getCookie(url: String): String? = cookieManager.getCookie(url)

        override fun setCookie(
            url: String,
            cookieValue: String,
        ) {
            cookieManager.setCookie(url, cookieValue)
        }

        override fun removeCookies(
            url: String,
            cookieNames: List<String>,
        ) {
            val domain = url.substringAfter("://").substringBefore("/")
            for (name in cookieNames) {
                // Xóa host-only
                cookieManager.setCookie(url, "$name=; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                // Xóa wildcard domain
                cookieManager.setCookie("https://$domain", "$name=; Path=/; Domain=.$domain; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
            }
        }

        override fun removeAllCookies() {
            cookieManager.removeAllCookies(null)
        }

        override fun flush() {
            cookieManager.flush()
        }
    }
