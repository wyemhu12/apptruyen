package com.personal.apptruyen.util

import android.content.Context
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CrashHandlerTest {

    @BeforeEach
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `uncaughtException catches OutOfMemoryError Throwable and still delegates to defaultHandler`() {
        val context = mockk<Context>()
        every { context.filesDir } throws OutOfMemoryError("Simulated OOM while saving crash log")
        val defaultHandler = mockk<Thread.UncaughtExceptionHandler>(relaxed = true)

        val crashHandler = CrashHandler(context, defaultHandler)
        val currentThread = Thread.currentThread()
        val fatalError = OutOfMemoryError("Java heap space")

        crashHandler.uncaughtException(currentThread, fatalError)

        verify(exactly = 1) { defaultHandler.uncaughtException(currentThread, fatalError) }
    }
}
