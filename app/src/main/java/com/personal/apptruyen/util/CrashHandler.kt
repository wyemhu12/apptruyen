package com.personal.apptruyen.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.personal.apptruyen.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Custom UncaughtExceptionHandler — lưu crash logs vào file local.
 * Giữ tối đa [MAX_CRASH_FILES] file, tự xóa cũ nhất.
 */
class CrashHandler private constructor(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "CrashHandler"
        private const val CRASH_DIR = "crash_logs"
        private const val MAX_CRASH_FILES = 10

        fun install(context: Context) {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(
                CrashHandler(context.applicationContext, defaultHandler),
            )
            Log.i(TAG, "CrashHandler installed")
        }

        /** Trả về danh sách crash logs, mới nhất trước */
        fun getCrashLogs(context: Context): List<CrashLog> {
            val dir = File(context.filesDir, CRASH_DIR)
            if (!dir.exists()) return emptyList()
            return dir
                .listFiles()
                ?.filter { it.extension == "txt" }
                ?.sortedByDescending { it.lastModified() }
                ?.map { file ->
                    CrashLog(
                        fileName = file.name,
                        timestamp = file.lastModified(),
                        content = file.readText(),
                    )
                }
                ?: emptyList()
        }

        /** Xóa tất cả crash logs */
        fun clearCrashLogs(context: Context) {
            val dir = File(context.filesDir, CRASH_DIR)
            dir.listFiles()?.forEach { it.delete() }
        }

        /** Đếm crash logs */
        fun getCrashCount(context: Context): Int {
            val dir = File(context.filesDir, CRASH_DIR)
            return dir.listFiles()?.size ?: 0
        }
    }

    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable,
    ) {
        try {
            saveCrashLog(throwable)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash log", e)
        }
        // Delegate to default handler (shows system crash dialog / kills process)
        defaultHandler?.uncaughtException(thread, throwable)
    }

    private fun saveCrashLog(throwable: Throwable) {
        val dir = File(context.filesDir, CRASH_DIR)
        dir.mkdirs()

        // Cleanup old files
        val existingFiles = dir.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
        if (existingFiles.size >= MAX_CRASH_FILES) {
            existingFiles.take(existingFiles.size - MAX_CRASH_FILES + 1).forEach { it.delete() }
        }

        // Build crash report
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        val fileName = "crash_$timestamp.txt"

        val stackTrace =
            StringWriter()
                .also { sw ->
                    throwable.printStackTrace(PrintWriter(sw))
                }.toString()

        val report =
            buildString {
                appendLine("═══════════════════════════════════════")
                appendLine("  CRASH REPORT — AppTruyen")
                appendLine("═══════════════════════════════════════")
                appendLine()
                appendLine("Thời gian: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Thread: ${Thread.currentThread().name}")
                appendLine()
                appendLine("───────────────────────────────────────")
                appendLine("  EXCEPTION")
                appendLine("───────────────────────────────────────")
                appendLine()
                appendLine(stackTrace)
                appendLine()
                appendLine("───────────────────────────────────────")
                appendLine("  MEMORY")
                appendLine("───────────────────────────────────────")
                appendLine()
                val runtime = Runtime.getRuntime()
                val usedMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                val maxMB = runtime.maxMemory() / (1024 * 1024)
                appendLine("Used: ${usedMB}MB / Max: ${maxMB}MB")
            }

        File(dir, fileName).writeText(report)
        Log.i(TAG, "Crash log saved: $fileName")
    }

    data class CrashLog(
        val fileName: String,
        val timestamp: Long,
        val content: String,
    )
}
