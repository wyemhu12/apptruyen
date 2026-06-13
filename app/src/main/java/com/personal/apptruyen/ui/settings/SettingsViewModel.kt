package com.personal.apptruyen.ui.settings

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.apptruyen.data.local.SettingsKeys
import com.personal.apptruyen.data.repository.BackupRepository
import com.personal.apptruyen.util.CrashHandler
import com.personal.apptruyen.util.TextReplacementHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
            val customReplacements: ImmutableList<Pair<String, String>> = persistentListOf(),
            val enabledSources: Set<String> = ALL_SOURCES,
            val prefetchChapters: Int = 0,
        )

        companion object {
            /** Tất cả nguồn. Key phải khớp với StorySource.sourceId */
            val ALL_SOURCES = setOf("truyencom", "tangthuvien", "sstruyen", "truyenfull")
            val SOURCE_LABELS =
                mapOf(
                    "truyencom" to "TruyenCom",
                    "tangthuvien" to "TàngThưViện",
                    "sstruyen" to "SsTruyện",
                    "truyenfull" to "TruyenFull",
                )
        }

        val state: StateFlow<SettingsState> =
            dataStore.data
                .map { prefs ->
                    SettingsState(
                        ttsSpeed = prefs[SettingsKeys.TTS_SPEED] ?: 1.0f,
                        ttsPitch = prefs[SettingsKeys.TTS_PITCH] ?: 1.0f,
                        ttsVoice = prefs[SettingsKeys.TTS_VOICE] ?: "",
                        fontSize = prefs[SettingsKeys.FONT_SIZE] ?: 18f,
                        theme = ReaderThemeOption.fromValue(prefs[SettingsKeys.THEME] ?: 0),
                        appThemeMode = AppThemeMode.fromValue(prefs[SettingsKeys.APP_THEME_MODE] ?: 0),
                        dynamicColor = prefs[SettingsKeys.DYNAMIC_COLOR] ?: true,
                        textReplacementEnabled = prefs[SettingsKeys.TEXT_REPLACEMENT_ENABLED] ?: true,
                        autoFormatEnabled = prefs[SettingsKeys.AUTO_FORMAT_ENABLED] ?: true,
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
                                ?: ALL_SOURCES,
                        prefetchChapters = prefs[SettingsKeys.PREFETCH_CHAPTERS] ?: 0,
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
                val current = state.value.enabledSources.toMutableSet()
                if (current.contains(sourceId)) {
                    // Không cho tắt nguồn cuối cùng
                    if (current.size > 1) current.remove(sourceId)
                } else {
                    current.add(sourceId)
                }
                dataStore.edit { it[SettingsKeys.ENABLED_SOURCES] = current.joinToString(",") }
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
                    _backupState.value = BackupState.Error("Lỗi sao lưu: ${e.message}")
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
                    _backupState.value = BackupState.Error("Lỗi khôi phục: ${e.message}")
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
            _crashLogs.value = CrashHandler.getCrashLogs(context)
        }

        fun clearCrashLogs(context: Context) {
            CrashHandler.clearCrashLogs(context)
            _crashLogs.value = emptyList()
        }
    }
