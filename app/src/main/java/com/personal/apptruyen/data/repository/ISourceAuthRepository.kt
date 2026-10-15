package com.personal.apptruyen.data.repository

import com.personal.apptruyen.data.model.SessionVerifyResult
import com.personal.apptruyen.data.model.SourceAuthConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface cho repository quản lý xác thực nguồn truyện.
 * Là ranh giới kiến trúc duy nhất giữa tầng UI/Repository và hạ tầng auth bên dưới.
 */
interface ISourceAuthRepository {
    val loginRequiredSources: StateFlow<Set<String>>

    fun getConfig(sourceId: String): SourceAuthConfig?

    fun getAllConfigs(): List<SourceAuthConfig>

    fun getAuthState(sourceId: String): StateFlow<Boolean>

    fun isLoggedIn(sourceId: String): Boolean

    fun markLoginRequired(sourceId: String)

    fun clearLoginRequired(sourceId: String)

    suspend fun logout(sourceId: String)

    suspend fun verifySessionOnline(sourceId: String): SessionVerifyResult

    suspend fun importCookies(
        sourceId: String,
        rawCookies: String,
    ): SessionVerifyResult
}
