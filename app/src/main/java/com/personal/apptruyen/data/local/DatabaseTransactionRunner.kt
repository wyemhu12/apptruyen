package com.personal.apptruyen.data.local

/**
 * Seam mỏng bao bọc transaction ghi của Room 3 để cô lập phụ thuộc KMP runtime
 * và hỗ trợ kiểm thử đơn vị (JVM JUnit 5) không cần mockkStatic.
 */
interface DatabaseTransactionRunner {
    suspend fun <R> withWriteTransaction(block: suspend () -> R): R
}
