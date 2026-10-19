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
    ) : ScraperException(
            message =
                when (statusCode) {
                    403 -> "Máy chủ từ chối truy cập (HTTP 403)."
                    404 -> "Không tìm thấy nội dung yêu cầu (HTTP 404)."
                    408 -> "Hết thời gian chờ phản hồi từ máy chủ (HTTP 408)."
                    429 -> "Quá nhiều yêu cầu, vui lòng thử lại sau (HTTP 429)."
                    in 500..599 -> "Máy chủ nguồn truyện đang gặp sự cố (HTTP $statusCode)."
                    else -> "Lỗi phản hồi từ máy chủ (HTTP $statusCode)."
                },
            cause = cause,
        )

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

    /** Nguồn yêu cầu đăng nhập tài khoản để xem nội dung */
    class LoginRequiredException(
        val sourceId: String,
        message: String = "Nguồn truyện yêu cầu đăng nhập tài khoản để xem nội dung.",
        cause: Throwable? = null,
    ) : ScraperException(message, cause)

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
        is kotlinx.coroutines.CancellationException -> throw this
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
                message = "Không thể kết nối mạng. Vui lòng kiểm tra kết nối Internet.",
                cause = this,
            )
        else ->
            ScraperException.UnknownException(
                message = "Đã xảy ra lỗi không xác định khi tải dữ liệu.",
                cause = this,
            )
    }
