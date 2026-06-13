package com.personal.apptruyen.data.remote

/**
 * Sealed class hierarchy cho scraper errors.
 * Cho phép caller phân biệt loại lỗi và xử lý phù hợp.
 */
sealed class ScraperException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** Lỗi kết nối mạng (timeout, DNS, no internet) */
    class NetworkException(
        message: String = "Không thể kết nối. Kiểm tra kết nối mạng.",
        cause: Throwable? = null,
    ) : ScraperException(message, cause)

    /** Server trả HTTP error (4xx, 5xx) */
    class HttpException(
        val statusCode: Int,
        val url: String,
        cause: Throwable? = null,
    ) : ScraperException("Lỗi HTTP $statusCode từ $url", cause)

    /** Parse HTML thất bại (selector không match, format thay đổi) */
    class ParseException(
        message: String = "Không thể đọc nội dung trang.",
        val url: String = "",
        cause: Throwable? = null,
    ) : ScraperException(message, cause)

    /** Server trả 304 nhưng không có cache */
    class CacheException(
        message: String = "Lỗi cache nội bộ.",
        cause: Throwable? = null,
    ) : ScraperException(message, cause)

    /** Circuit breaker đang mở — source tạm thời bị disable */
    class CircuitOpenException(
        val sourceId: String,
        val retryAfterMs: Long = 0,
    ) : ScraperException("Nguồn $sourceId tạm thời không khả dụng. Thử lại sau.")

    /** Lỗi không xác định */
    class UnknownException(
        message: String = "Đã xảy ra lỗi không xác định.",
        cause: Throwable? = null,
    ) : ScraperException(message, cause)
}

/**
 * Extension để chuyển generic Exception thành ScraperException.
 */
fun Exception.toScraperException(url: String = ""): ScraperException =
    when (this) {
        is ScraperException -> this
        is java.net.UnknownHostException -> ScraperException.NetworkException(cause = this)
        is java.net.SocketTimeoutException ->
            ScraperException.NetworkException(
                message = "Kết nối quá chậm. Vui lòng thử lại.",
                cause = this,
            )
        is java.net.ConnectException -> ScraperException.NetworkException(cause = this)
        is java.io.IOException ->
            ScraperException.NetworkException(
                message = this.message ?: "Lỗi kết nối mạng.",
                cause = this,
            )
        else ->
            ScraperException.UnknownException(
                message = this.message ?: "Đã xảy ra lỗi không xác định.",
                cause = this,
            )
    }
