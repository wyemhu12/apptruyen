package com.personal.apptruyen.ui.settings

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.apptruyen.data.local.SettingsKeys
import com.personal.apptruyen.data.local.SourceIds
import com.personal.apptruyen.data.local.getIntSafe
import com.personal.apptruyen.data.local.healCorruptedIntPreferences
import com.personal.apptruyen.data.repository.BackupRepository
import com.personal.apptruyen.data.repository.ISourceAuthRepository
import com.personal.apptruyen.util.CrashHandler
import com.personal.apptruyen.util.TextReplacementHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

enum class ReaderThemeOption(
    val value: Int,
    val label: String,
) {
    LIGHT(0, "Sáng"),
    DARK(1, "Tối"),
    SEPIA(2, "Sepia"),
    ;

    companion object {
        fun fromValue(value: Int) = entries.firstOrNull { it.value == value } ?: LIGHT
    }
}

enum class AppThemeMode(
    val value: Int,
    val label: String,
) {
    SYSTEM(0, "Hệ thống"),
    LIGHT(1, "Sáng"),
    DARK(2, "Tối"),
    ;

    companion object {
        fun fromValue(value: Int) = entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

sealed class BackupState {
    data object Idle : BackupState()

    data object Exporting : BackupState()

    data object Importing : BackupState()

    data class Success(
        val message: String,
    ) : BackupState()

    data class Error(
        val message: String,
    ) : BackupState()
}

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        private val backupRepository: BackupRepository,
        private val authRepository: ISourceAuthRepository,
    ) : ViewModel() {

        data class SettingsState(
            val ttsSpeed: Float = 1.0f,
            val ttsPitch: Float = 1.0f,
            val ttsVoice: String = "",
            val fontSize: Float = 18f,
            val theme: ReaderThemeOption = ReaderThemeOption.LIGHT,
            val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
            val dynamicColor: Boolean = true,
            val textReplacementEnabled: Boolean = true,
            val autoFormatEnabled: Boolean = true,
            val pokemonLookupEnabled: Boolean = true,
            val customReplacements: ImmutableList<Pair<String, String>> = persistentListOf(),
            val enabledSources: Set<String> = ALL_SOURCES,
            val prefetchChapters: Int = 0,
            val isWikidichLoggedIn: Boolean = false,
        )

        companion object {
            /** Tất cả nguồn đang hoạt động. Key phải khớp với StorySource.sourceId */
            val ALL_SOURCES = SourceIds.ALL_ACTIVE_SOURCES
            val SOURCE_LABELS = SourceIds.SOURCE_LABELS
        }

        private val isWikidichLoggedInFlow = MutableStateFlow(false)

        init {
            viewModelScope.launch {
                try {
                    dataStore.healCorruptedIntPreferences()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Timber.w(e, "Failed to heal corrupted Int preferences")
                }
            }
            checkAuthStatus()
            viewModelScope.launch {
                authRepository.getAuthState(SourceIds.WIKIDICH).collect { loggedIn ->
                    isWikidichLoggedInFlow.value = loggedIn
                }
            }
        }

        fun checkAuthStatus() {
            viewModelScope.launch {
                isWikidichLoggedInFlow.value = authRepository.isLoggedIn(SourceIds.WIKIDICH)
            }
        }

        val state: StateFlow<SettingsState> =
            combine(dataStore.data, isWikidichLoggedInFlow) { prefs, isWikidichLoggedIn ->
                SettingsState(
                    ttsSpeed = prefs[SettingsKeys.TTS_SPEED] ?: 1.0f,
                    ttsPitch = prefs[SettingsKeys.TTS_PITCH] ?: 1.0f,
                    ttsVoice = prefs[SettingsKeys.TTS_VOICE] ?: "",
                    fontSize = prefs[SettingsKeys.FONT_SIZE] ?: 18f,
                    theme = ReaderThemeOption.fromValue(prefs.getIntSafe(SettingsKeys.THEME, 0)),
                    appThemeMode = AppThemeMode.fromValue(prefs.getIntSafe(SettingsKeys.APP_THEME_MODE, 0)),
                    dynamicColor = prefs[SettingsKeys.DYNAMIC_COLOR] ?: true,
                    textReplacementEnabled = prefs[SettingsKeys.TEXT_REPLACEMENT_ENABLED] ?: true,
                    autoFormatEnabled = prefs[SettingsKeys.AUTO_FORMAT_ENABLED] ?: true,
                    pokemonLookupEnabled = prefs[SettingsKeys.POKEMON_LOOKUP_ENABLED] ?: true,
                    customReplacements =
                        TextReplacementHelper
                            .parseCustomReplacements(
                                prefs[SettingsKeys.CUSTOM_REPLACEMENTS],
                            ).toImmutableList(),
                    enabledSources =
                        prefs[SettingsKeys.ENABLED_SOURCES]
                            ?.split(",")
                            ?.filter { it.isNotBlank() }
                            ?.toSet()
                            ?.intersect(ALL_SOURCES)
                            ?.ifEmpty { ALL_SOURCES }
                            ?: ALL_SOURCES,
                    prefetchChapters = prefs.getIntSafe(SettingsKeys.PREFETCH_CHAPTERS, 0),
                    isWikidichLoggedIn = isWikidichLoggedIn,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsState())

        fun setTtsSpeed(speed: Float) {
            viewModelScope.launch {
                dataStore.edit { it[SettingsKeys.TTS_SPEED] = speed }
            }
        }

        fun setTtsPitch(pitch: Float) {
            viewModelScope.launch {
                dataStore.edit { it[SettingsKeys.TTS_PITCH] = pitch }
            }
        }

        fun setTtsVoice(voice: String) {
            viewModelScope.launch {
                dataStore.edit { it[SettingsKeys.TTS_VOICE] = voice }
            }
        }

        fun setFontSize(size: Float) {
            viewModelScope.launch {
                dataStore.edit { it[SettingsKeys.FONT_SIZE] = size }
            }
        }

        fun setTheme(theme: ReaderThemeOption) {
            viewModelScope.launch {
                dataStore.edit { it[SettingsKeys.THEME] = theme.value }
            }
        }

        fun setAppThemeMode(mode: AppThemeMode) {
            viewModelScope.launch {
                dataStore.edit { it[SettingsKeys.APP_THEME_MODE] = mode.value }
            }
        }

        fun setDynamicColor(enabled: Boolean) {
            viewModelScope.launch {
                dataStore.edit { it[SettingsKeys.DYNAMIC_COLOR] = enabled }
            }
        }

        fun setTextReplacement(enabled: Boolean) {
            viewModelScope.launch {
                dataStore.edit { it[SettingsKeys.TEXT_REPLACEMENT_ENABLED] = enabled }
            }
        }

        fun setAutoFormat(enabled: Boolean) {
            viewModelScope.launch {
                dataStore.edit { it[SettingsKeys.AUTO_FORMAT_ENABLED] = enabled }
            }
        }

        fun setPokemonLookupEnabled(enabled: Boolean) {
            viewModelScope.launch {
                dataStore.edit { it[SettingsKeys.POKEMON_LOOKUP_ENABLED] = enabled }
            }
        }

        fun addCustomReplacement(
            from: String,
            to: String,
        ) {
            viewModelScope.launch {
                val current = state.value.customReplacements.toMutableList()
                current.add(from to to)
                val json = TextReplacementHelper.serializeCustomReplacements(current)
                dataStore.edit { it[SettingsKeys.CUSTOM_REPLACEMENTS] = json }
            }
        }

        fun removeCustomReplacement(index: Int) {
            viewModelScope.launch {
                val current = state.value.customReplacements.toMutableList()
                if (index in current.indices) {
                    current.removeAt(index)
                    val json = TextReplacementHelper.serializeCustomReplacements(current)
                    dataStore.edit { it[SettingsKeys.CUSTOM_REPLACEMENTS] = json }
                }
            }
        }

        fun toggleSource(sourceId: String) {
            viewModelScope.launch {
                dataStore.edit { prefs ->
                    val current =
                        (
                            prefs[SettingsKeys.ENABLED_SOURCES]
                                ?.split(",")
                                ?.filter { it.isNotBlank() }
                                ?.toSet()
                                ?.intersect(ALL_SOURCES)
                                ?.ifEmpty { ALL_SOURCES }
                                ?: state.value.enabledSources
                                    .intersect(ALL_SOURCES)
                                    .ifEmpty { ALL_SOURCES }
                        ).toMutableSet()
                    if (current.contains(sourceId)) {
                        // Không cho tắt nguồn cuối cùng
                        if (current.size > 1) current.remove(sourceId)
                    } else if (sourceId in ALL_SOURCES) {
                        current.add(sourceId)
                    }
                    prefs[SettingsKeys.ENABLED_SOURCES] = current.joinToString(",")
                }
            }
        }

        fun setPrefetchChapters(count: Int) {
            viewModelScope.launch {
                dataStore.edit { it[SettingsKeys.PREFETCH_CHAPTERS] = count }
            }
        }

        // ── Backup / Restore ──

        private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
        val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

        private val _backupProgressText = MutableStateFlow("")
        val backupProgressText: StateFlow<String> = _backupProgressText.asStateFlow()

        internal fun mapBackupErrorMessage(
            isExport: Boolean,
            error: Throwable,
        ): String {
            val msg = error.message?.trim().orEmpty()
            val isKnownVietnameseMessage =
                (error is IllegalArgumentException || error is IllegalStateException) &&
                    msg.isNotEmpty() &&
                    (
                        msg.startsWith("Tệp sao lưu") ||
                            msg.startsWith("File backup") ||
                            msg.startsWith("Phiên bản") ||
                            msg.startsWith("Đường dẫn") ||
                            msg.startsWith("Dữ liệu") ||
                            msg.startsWith("Chương ") ||
                            msg.startsWith("Tổng dung lượng") ||
                            msg.startsWith("Không thể")
                    )
            if (isKnownVietnameseMessage) return msg
            return if (isExport) {
                "Không thể sao lưu dữ liệu. Vui lòng kiểm tra dung lượng bộ nhớ và thử lại."
            } else {
                "Không thể khôi phục dữ liệu. Tệp sao lưu không hợp lệ hoặc bị hỏng."
            }
        }

        fun exportBackup(
            uri: Uri,
            context: Context,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                _backupState.value = BackupState.Exporting
                try {
                    val outputStream =
                        context.contentResolver.openOutputStream(uri)
                            ?: throw IllegalStateException("Không thể mở file để ghi")
                    outputStream.use { stream ->
                        backupRepository.exportBackup(stream) { progress ->
                            _backupProgressText.value = progress
                        }
                    }
                    _backupState.value = BackupState.Success("Sao lưu thành công!")
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Timber.e(e, "Export backup failed")
                    _backupState.value = BackupState.Error(mapBackupErrorMessage(isExport = true, error = e))
                }
            }
        }

        fun importBackup(
            uri: Uri,
            context: Context,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                _backupState.value = BackupState.Importing
                try {
                    val inputStream =
                        context.contentResolver.openInputStream(uri)
                            ?: throw IllegalStateException("Không thể mở file backup")
                    inputStream.use { stream ->
                        backupRepository.importBackup(stream) { progress ->
                            _backupProgressText.value = progress
                        }
                    }
                    _backupState.value = BackupState.Success("Khôi phục thành công!")
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Timber.e(e, "Import backup failed")
                    _backupState.value = BackupState.Error(mapBackupErrorMessage(isExport = false, error = e))
                }
            }
        }

        fun resetBackupState() {
            _backupState.value = BackupState.Idle
            _backupProgressText.value = ""
        }

        // ── Crash Logs ──

        private val _crashLogs = MutableStateFlow<List<CrashHandler.CrashLog>>(emptyList())
        val crashLogs: StateFlow<List<CrashHandler.CrashLog>> = _crashLogs.asStateFlow()

        fun loadCrashLogs(context: Context) {
            val appContext = context.applicationContext
            viewModelScope.launch {
                try {
                    _crashLogs.value =
                        withContext(Dispatchers.IO) {
                            CrashHandler.getCrashLogs(appContext)
                        }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Timber.e(e, "Failed to load crash logs")
                }
            }
        }

        fun clearCrashLogs(context: Context) {
            val appContext = context.applicationContext
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        CrashHandler.clearCrashLogs(appContext)
                    }
                    _crashLogs.value = emptyList()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Timber.e(e, "Failed to clear crash logs")
                }
            }
        }
    }
