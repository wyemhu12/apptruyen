package com.personal.apptruyen.data.remote.cookie

/**
 * Trừu tượng hóa việc lưu trữ và truy xuất cookie để dễ dàng kiểm thử trên JVM.
 */
interface CookieStore {
    fun getCookie(url: String): String?

    fun setCookie(
        url: String,
        cookieValue: String,
    )

    fun removeCookies(
        url: String,
        cookieNames: List<String>,
    )

    fun removeAllCookies()

    fun flush()
}
