package com.personal.apptruyen.data.remote.cookie

import android.webkit.CookieManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidCookieStore
    internal constructor(
        private val cookieManagerProvider: () -> CookieManager,
    ) : CookieStore {
        @Inject
        constructor() : this({ CookieManager.getInstance() })

        private val cookieManager: CookieManager
            get() = cookieManagerProvider()

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
            val normalizedUrl =
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    url
                } else {
                    "https://${url.trimStart('/')}"
                }
            val domain = normalizedUrl.substringAfter("://").substringBefore("/").substringBefore(":")
            val namesToRemove =
                cookieNames.ifEmpty {
                    getCookie(normalizedUrl)
                        ?.split(";")
                        ?.mapNotNull { segment ->
                            segment.substringBefore("=", "").trim().takeIf { it.isNotEmpty() }
                        }?.distinct()
                        ?: emptyList()
                }
            for (name in namesToRemove) {
                if (name.isNotEmpty()) {
                    // Xóa host-only
                    cookieManager.setCookie(
                        normalizedUrl,
                        "$name=; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT",
                    )
                    // Xóa wildcard domain
                    if (domain.isNotEmpty()) {
                        cookieManager.setCookie(
                            "https://$domain",
                            "$name=; Path=/; Domain=.$domain; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT",
                        )
                    }
                }
            }
            cookieManager.flush()
        }

        override fun removeAllCookies() {
            cookieManager.removeAllCookies(null)
        }

        override fun flush() {
            cookieManager.flush()
        }
    }
