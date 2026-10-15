package com.personal.apptruyen.data.repository

import com.personal.apptruyen.data.model.SessionVerifyResult
import com.personal.apptruyen.data.model.SourceAuthConfig
import com.personal.apptruyen.data.remote.auth.SourceAuthManager
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceAuthRepository
    @Inject
    constructor(
        private val sourceAuthManager: SourceAuthManager,
    ) : ISourceAuthRepository {

        override val loginRequiredSources: StateFlow<Set<String>> = sourceAuthManager.loginRequiredSources

        override fun getConfig(sourceId: String): SourceAuthConfig? = sourceAuthManager.getConfig(sourceId)

        override fun getAllConfigs(): List<SourceAuthConfig> = sourceAuthManager.getAllConfigs()

        override fun getAuthState(sourceId: String): StateFlow<Boolean> = sourceAuthManager.getAuthState(sourceId)

        override fun isLoggedIn(sourceId: String): Boolean = sourceAuthManager.isLoggedIn(sourceId)

        override fun markLoginRequired(sourceId: String) = sourceAuthManager.markLoginRequired(sourceId)

        override fun clearLoginRequired(sourceId: String) = sourceAuthManager.clearLoginRequired(sourceId)

        override suspend fun logout(sourceId: String) = sourceAuthManager.logout(sourceId)

        override suspend fun verifySessionOnline(sourceId: String): SessionVerifyResult = sourceAuthManager.verifySessionOnline(sourceId)

        override suspend fun importCookies(
            sourceId: String,
            rawCookies: String,
        ): SessionVerifyResult = sourceAuthManager.importCookies(sourceId, rawCookies)
    }
