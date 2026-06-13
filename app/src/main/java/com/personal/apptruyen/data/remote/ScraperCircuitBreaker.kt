package com.personal.apptruyen.data.remote

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Circuit breaker pattern cho scrapers.
 *
 * Khi 1 source liên tục fail (N lần liên tiếp), circuit sẽ "mở"
 * và block tất cả request tới source đó trong 1 khoảng thời gian.
 * Sau đó chuyển sang "half-open" để thử 1 request.
 * Nếu thành công → đóng circuit. Nếu fail → mở lại.
 *
 * States:
 * - CLOSED: Hoạt động bình thường, đếm failures
 * - OPEN: Block requests, đợi timeout
 * - HALF_OPEN: Cho phép 1 request thử
 */
class ScraperCircuitBreaker(
    /** Số lần fail liên tiếp trước khi mở circuit */
    private val failureThreshold: Int = 5,
    // Thời gian circuit mở (ms) trước khi chuyển half-open
    private val resetTimeoutMs: Long = 60_000L, // 1 phút
    // Tag cho logging
    private val tag: String = "CircuitBreaker",
) {
    enum class State { CLOSED, OPEN, HALF_OPEN }

    /**
     * Thread-safe circuit state — dùng synchronized cho compound operations
     * thay vì mix AtomicInteger/@Volatile gây race condition.
     */
    private class CircuitState {
        var failureCount: Int = 0
        var lastFailureTime: Long = 0
        var state: State = State.CLOSED
    }

    private val circuits = ConcurrentHashMap<String, CircuitState>()

    /**
     * Lấy state hiện tại của circuit cho sourceId.
     */
    fun getState(sourceId: String): State {
        val circuit = circuits[sourceId] ?: return State.CLOSED

        synchronized(circuit) {
            return when (circuit.state) {
                State.OPEN -> {
                    val elapsed = System.currentTimeMillis() - circuit.lastFailureTime
                    if (elapsed >= resetTimeoutMs) {
                        circuit.state = State.HALF_OPEN
                        Log.d(tag, "[$sourceId] OPEN → HALF_OPEN (${elapsed}ms elapsed)")
                        State.HALF_OPEN
                    } else {
                        State.OPEN
                    }
                }
                else -> circuit.state
            }
        }
    }

    /**
     * Kiểm tra xem có nên cho phép request không.
     * @throws ScraperException.CircuitOpenException nếu circuit đang mở
     */
    fun checkPermission(sourceId: String) {
        val state = getState(sourceId)
        if (state == State.OPEN) {
            val circuit = circuits[sourceId]!!
            val retryAfter =
                synchronized(circuit) {
                    resetTimeoutMs - (System.currentTimeMillis() - circuit.lastFailureTime)
                }
            throw ScraperException.CircuitOpenException(
                sourceId = sourceId,
                retryAfterMs = retryAfter.coerceAtLeast(0),
            )
        }
    }

    /**
     * Ghi nhận request thành công → reset circuit.
     */
    fun recordSuccess(sourceId: String) {
        val circuit = circuits[sourceId] ?: return
        synchronized(circuit) {
            val previousState = circuit.state
            circuit.failureCount = 0
            circuit.state = State.CLOSED
            if (previousState != State.CLOSED) {
                Log.d(tag, "[$sourceId] $previousState → CLOSED (success)")
            }
        }
    }

    /**
     * Ghi nhận request thất bại → tăng failure count, có thể mở circuit.
     */
    fun recordFailure(sourceId: String) {
        val circuit = circuits.getOrPut(sourceId) { CircuitState() }
        synchronized(circuit) {
            circuit.failureCount++
            circuit.lastFailureTime = System.currentTimeMillis()

            if (circuit.failureCount >= failureThreshold && circuit.state != State.OPEN) {
                circuit.state = State.OPEN
                Log.w(tag, "[$sourceId] → OPEN (${circuit.failureCount} consecutive failures)")
            } else {
                Log.d(tag, "[$sourceId] failure #${circuit.failureCount}/$failureThreshold")
            }
        }
    }

    /**
     * Reset circuit cho sourceId (manual override).
     */
    fun reset(sourceId: String) {
        circuits.remove(sourceId)
        Log.d(tag, "[$sourceId] circuit reset")
    }

    /**
     * Reset tất cả circuits.
     */
    fun resetAll() {
        circuits.clear()
        Log.d(tag, "All circuits reset")
    }

    /**
     * Execute block với circuit breaker protection.
     * Tự động ghi nhận success/failure.
     */
    suspend fun <T> execute(
        sourceId: String,
        block: suspend () -> T,
    ): T {
        checkPermission(sourceId)

        return try {
            val result = block()
            recordSuccess(sourceId)
            result
        } catch (e: Exception) {
            recordFailure(sourceId)
            throw e
        }
    }
}
