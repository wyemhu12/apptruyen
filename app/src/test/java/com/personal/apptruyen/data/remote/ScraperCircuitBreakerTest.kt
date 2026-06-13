package com.personal.apptruyen.data.remote

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ScraperCircuitBreakerTest {

    private lateinit var breaker: ScraperCircuitBreaker
    private val sourceId = "test-source"

    @BeforeEach
    fun setup() {
        breaker =
            ScraperCircuitBreaker(
                failureThreshold = 3,
                resetTimeoutMs = 1000L, // 1 second for testing
            )
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.d(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    // ═══════ State transitions ═══════

    @Test
    fun `initial state is CLOSED`() {
        assertEquals(ScraperCircuitBreaker.State.CLOSED, breaker.getState(sourceId))
    }

    @Test
    fun `stays CLOSED under threshold`() {
        breaker.recordFailure(sourceId)
        breaker.recordFailure(sourceId)
        assertEquals(ScraperCircuitBreaker.State.CLOSED, breaker.getState(sourceId))
    }

    @Test
    fun `opens after reaching threshold`() {
        repeat(3) { breaker.recordFailure(sourceId) }
        assertEquals(ScraperCircuitBreaker.State.OPEN, breaker.getState(sourceId))
    }

    @Test
    fun `success resets failure count`() {
        breaker.recordFailure(sourceId)
        breaker.recordFailure(sourceId)
        breaker.recordSuccess(sourceId)
        breaker.recordFailure(sourceId) // count should be 1 now
        assertEquals(ScraperCircuitBreaker.State.CLOSED, breaker.getState(sourceId))
    }

    @Test
    fun `transitions to HALF_OPEN after timeout`() {
        repeat(3) { breaker.recordFailure(sourceId) }
        assertEquals(ScraperCircuitBreaker.State.OPEN, breaker.getState(sourceId))

        // Wait for timeout
        Thread.sleep(1100)
        assertEquals(ScraperCircuitBreaker.State.HALF_OPEN, breaker.getState(sourceId))
    }

    @Test
    fun `success in HALF_OPEN closes circuit`() {
        repeat(3) { breaker.recordFailure(sourceId) }
        Thread.sleep(1100) // Wait for half-open
        breaker.getState(sourceId) // Trigger transition

        breaker.recordSuccess(sourceId)
        assertEquals(ScraperCircuitBreaker.State.CLOSED, breaker.getState(sourceId))
    }

    // ═══════ checkPermission ═══════

    @Test
    fun `checkPermission passes when CLOSED`() {
        // Should not throw
        breaker.checkPermission(sourceId)
    }

    @Test
    fun `checkPermission throws when OPEN`() {
        repeat(3) { breaker.recordFailure(sourceId) }
        assertThrows(ScraperException.CircuitOpenException::class.java) {
            breaker.checkPermission(sourceId)
        }
    }

    @Test
    fun `checkPermission passes when HALF_OPEN`() {
        repeat(3) { breaker.recordFailure(sourceId) }
        Thread.sleep(1100)
        // Should not throw in HALF_OPEN
        breaker.checkPermission(sourceId)
    }

    // ═══════ execute ═══════

    @Test
    fun `execute records success on completion`() =
        runTest {
            val result = breaker.execute(sourceId) { "ok" }
            assertEquals("ok", result)
            assertEquals(ScraperCircuitBreaker.State.CLOSED, breaker.getState(sourceId))
        }

    @Test
    fun `execute records failure on exception`() =
        runTest {
            try {
                breaker.execute(sourceId) { throw RuntimeException("fail") }
            } catch (_: RuntimeException) {
            }

            // Should have 1 failure recorded
            assertEquals(ScraperCircuitBreaker.State.CLOSED, breaker.getState(sourceId))
        }

    @Test
    fun `execute throws CircuitOpenException when OPEN`() =
        runTest {
            repeat(3) { breaker.recordFailure(sourceId) }
            try {
                breaker.execute(sourceId) { "should not run" }
                fail("Expected CircuitOpenException")
            } catch (e: ScraperException.CircuitOpenException) {
                // Expected
            }
        }

    // ═══════ reset ═══════

    @Test
    fun `reset clears circuit state`() {
        repeat(3) { breaker.recordFailure(sourceId) }
        assertEquals(ScraperCircuitBreaker.State.OPEN, breaker.getState(sourceId))

        breaker.reset(sourceId)
        assertEquals(ScraperCircuitBreaker.State.CLOSED, breaker.getState(sourceId))
    }

    @Test
    fun `resetAll clears all circuits`() {
        repeat(3) { breaker.recordFailure("source1") }
        repeat(3) { breaker.recordFailure("source2") }

        breaker.resetAll()
        assertEquals(ScraperCircuitBreaker.State.CLOSED, breaker.getState("source1"))
        assertEquals(ScraperCircuitBreaker.State.CLOSED, breaker.getState("source2"))
    }

    // ═══════ Multiple sources ═══════

    @Test
    fun `circuits are independent per source`() {
        repeat(3) { breaker.recordFailure("source1") }
        assertEquals(ScraperCircuitBreaker.State.OPEN, breaker.getState("source1"))
        assertEquals(ScraperCircuitBreaker.State.CLOSED, breaker.getState("source2"))
    }
}
