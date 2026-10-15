package com.personal.apptruyen.data.remote.auth

import com.personal.apptruyen.data.model.SessionVerifyResult
import com.personal.apptruyen.data.model.SourceAuthConfig
import com.personal.apptruyen.data.remote.cookie.CookieStore
import com.personal.apptruyen.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Quản lý xác thực nguồn truyện, cookie session, probe online và đồng bộ trạng thái đăng nhập.
 */
@Singleton
class SourceAuthManager
    @Inject
    constructor(
        private val cookieStore: CookieStore,
        private val okHttpClient: OkHttpClient,
        @param:ApplicationScope private val applicationScope: CoroutineScope,
    ) {
        private val probeClient: OkHttpClient =
            okHttpClient
                .newBuilder()
                .cookieJar(CookieJar.NO_COOKIES)
                .build()

        private val authConfigs =
            mapOf(
                "wikidich" to
                    SourceAuthConfig(
                        sourceId = "wikidich",
                        sourceName = "Wikidich",
                        loginUrl = "https://wikicv.org/auth/dth",
                        successRedirectPrefix = "https://wikicv.org",
                        targetDomain = "https://wikicv.org",
                        sessionCookieName = "express.sid",
                        customHeaders = mapOf("Referer" to "https://wikicv.org/"),
                        loginInstructions =
                            "Đăng nhập bằng tài khoản diễn đàn Dịch Tiếng Hoa (DTH). " +
                                "Sau khi đăng nhập thành công, hệ thống sẽ tự động chuyển hướng về Wikidich.",
                        requiresSso = true,
                        ssoDomain = "forum.dichtienghoa.com",
                        allowlistHosts = listOf("wikicv.org", "wikicv.net", "forum.dichtienghoa.com"),
                    ),
            )

        private val authStateMap = ConcurrentHashMap<String, MutableStateFlow<Boolean>>()
        private val _loginRequiredSources = MutableStateFlow<Set<String>>(emptySet())
        val loginRequiredSources: StateFlow<Set<String>> = _loginRequiredSources.asStateFlow()

        fun getConfig(sourceId: String): SourceAuthConfig? = authConfigs[sourceId]

        fun getAllConfigs(): List<SourceAuthConfig> = authConfigs.values.toList()

        fun getAuthState(sourceId: String): StateFlow<Boolean> =
            authStateMap
                .getOrPut(sourceId) {
                    MutableStateFlow(checkCookiePresent(sourceId))
                }.asStateFlow()

        private fun checkCookiePresent(sourceId: String): Boolean {
            val config = authConfigs[sourceId] ?: return false
            val cookie = cookieStore.getCookie(config.targetDomain) ?: return false
            return cookie.contains("${config.sessionCookieName}=")
        }

        fun isLoggedIn(sourceId: String): Boolean = checkCookiePresent(sourceId)

        fun markLoginRequired(sourceId: String) {
            _loginRequiredSources.update { it + sourceId }
            authStateMap[sourceId]?.value = false
        }

        fun clearLoginRequired(sourceId: String) {
            _loginRequiredSources.update { it - sourceId }
        }

        suspend fun logout(sourceId: String) =
            withContext(Dispatchers.IO) {
                val config = authConfigs[sourceId] ?: return@withContext
                cookieStore.removeCookies(config.targetDomain, listOf(config.sessionCookieName))
                cookieStore.removeCookies("https://wikicv.net", listOf(config.sessionCookieName))
                cookieStore.flush()
                authStateMap[sourceId]?.value = false
                _loginRequiredSources.update { it + sourceId }
            }

        suspend fun verifySessionOnline(sourceId: String): SessionVerifyResult =
            withContext(Dispatchers.IO) {
                val config =
                    authConfigs[sourceId]
                        ?: return@withContext SessionVerifyResult.InvalidCookie("Nguồn không được hỗ trợ")

                val probeUrl = "https://wikicv.org/tim-kiem?qs=1&q=tien"
                val request =
                    Request
                        .Builder()
                        .url(probeUrl)
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                        ).header("Referer", "https://wikicv.org/")
                        .build()

                try {
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            return@withContext SessionVerifyResult.NetworkError("Máy chủ phản hồi mã lỗi ${response.code}")
                        }
                        val html = response.body.string()
                        if (html.contains("Đăng nhập để xem nội dung")) {
                            markLoginRequired(sourceId)
                            SessionVerifyResult.InvalidCookie("Phiên đăng nhập đã hết hạn hoặc không hợp lệ")
                        } else {
                            clearLoginRequired(sourceId)
                            authStateMap.getOrPut(sourceId) { MutableStateFlow(true) }.value = true
                            SessionVerifyResult.Success
                        }
                    }
                } catch (e: IOException) {
                    Timber.w(e, "Lỗi mạng khi kiểm tra phiên $sourceId")
                    SessionVerifyResult.NetworkError("Không thể kết nối đến máy chủ để xác minh phiên")
                }
            }

        suspend fun importCookies(
            sourceId: String,
            rawCookies: String,
        ): SessionVerifyResult =
            withContext(Dispatchers.IO) {
                val config =
                    authConfigs[sourceId]
                        ?: return@withContext SessionVerifyResult.InvalidCookie("Nguồn không được hỗ trợ")

                val token = extractSessionToken(rawCookies, config.sessionCookieName)
                if (token.isBlank()) {
                    return@withContext SessionVerifyResult.InvalidCookie("Chuỗi cookie không chứa ${config.sessionCookieName} hợp lệ")
                }

                val probeUrl = "https://wikicv.org/tim-kiem?qs=1&q=tien"
                val probeRequest =
                    Request
                        .Builder()
                        .url(probeUrl)
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                        ).header("Referer", "https://wikicv.org/")
                        .header("Cookie", "${config.sessionCookieName}=$token")
                        .build()

                try {
                    probeClient.newCall(probeRequest).execute().use { response ->
                        if (!response.isSuccessful) {
                            return@withContext SessionVerifyResult.NetworkError("Máy chủ phản hồi mã lỗi ${response.code}")
                        }
                        val html = response.body.string()
                        if (html.contains("Đăng nhập để xem nội dung")) {
                            return@withContext SessionVerifyResult.InvalidCookie("Chuỗi cookie không hợp lệ hoặc phiên đã hết hạn")
                        }

                        // Xóa mọi biến thể cũ trước khi ghi cookie mới
                        cookieStore.removeCookies("https://wikicv.org", listOf(config.sessionCookieName))
                        cookieStore.removeCookies("https://wikicv.net", listOf(config.sessionCookieName))

                        // Ghi cookie duy nhất host-only
                        cookieStore.setCookie(
                            "https://wikicv.org",
                            "${config.sessionCookieName}=$token; Path=/; Secure; Max-Age=2592000",
                        )
                        cookieStore.flush()

                        clearLoginRequired(sourceId)
                        authStateMap.getOrPut(sourceId) { MutableStateFlow(true) }.value = true
                        SessionVerifyResult.Success
                    }
                } catch (e: IOException) {
                    Timber.w(e, "Lỗi mạng khi dán cookie cho $sourceId")
                    SessionVerifyResult.NetworkError("Không thể kết nối đến máy chủ để xác minh cookie")
                }
            }

        private fun extractSessionToken(
            raw: String,
            cookieName: String,
        ): String {
            val trimmed = raw.trim()
            if (!trimmed.contains("=") && !trimmed.contains(";")) {
                return trimmed
            }
            val pairs = trimmed.split(";")
            for (pair in pairs) {
                val parts = pair.trim().split("=", limit = 2)
                if (parts.size == 2 && parts[0].trim().equals(cookieName, ignoreCase = true)) {
                    return parts[1].trim()
                }
            }
            return ""
        }
    }
