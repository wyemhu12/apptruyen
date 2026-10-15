package com.personal.apptruyen.data.model

/**
 * Cấu hình đăng nhập cho từng nguồn truyện.
 */
data class SourceAuthConfig(
    val sourceId: String,
    val sourceName: String,
    val loginUrl: String,
    val successRedirectPrefix: String,
    val targetDomain: String,
    val sessionCookieName: String,
    val customHeaders: Map<String, String> = emptyMap(),
    val loginInstructions: String = "",
    val requiresSso: Boolean = false,
    val ssoDomain: String? = null,
    val allowlistHosts: List<String> = emptyList(),
)

/**
 * Kết quả xác minh phiên làm việc / kiểm tra cookie.
 */
sealed interface SessionVerifyResult {
    data object Success : SessionVerifyResult

    data class InvalidCookie(
        val message: String,
    ) : SessionVerifyResult

    data class NetworkError(
        val message: String,
    ) : SessionVerifyResult
}
