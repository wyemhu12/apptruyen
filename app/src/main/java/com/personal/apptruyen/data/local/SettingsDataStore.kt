package com.personal.apptruyen.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/**
 * Single DataStore instance for app settings.
 * Must be a top-level extension to avoid duplicate delegate crash.
 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("settings")

object SourceIds {
    const val TANGTHUVIEN = "tangthuvien"
    const val SSTRUYEN = "sstruyen"
    const val WIKIDICH = "wikidich"

    const val DEFAULT_SINGLE_SOURCE = SSTRUYEN
    val ORDERED_SOURCES: List<String> = listOf(SSTRUYEN, WIKIDICH)

    val ALL_ACTIVE_SOURCES = setOf(SSTRUYEN, WIKIDICH)
    val DEPRECATED_SOURCES = setOf("truyencom", "truyenfull", TANGTHUVIEN)

    val SOURCE_LABELS: Map<String, String> =
        mapOf(
            SSTRUYEN to "SsTruyện",
            WIKIDICH to "Wikidich",
        )

    /**
     * Normalizes any stored CSV string into a single valid source ID.
     * Picks the first valid source present in [rawCsv], or falls back to [DEFAULT_SINGLE_SOURCE].
     */
    fun resolveSingleSource(rawCsv: String?): String {
        if (rawCsv.isNullOrBlank()) return DEFAULT_SINGLE_SOURCE
        return rawCsv
            .split(",")
            .map { it.trim() }
            .firstOrNull { it in ALL_ACTIVE_SOURCES }
            ?: DEFAULT_SINGLE_SOURCE
    }
}

object SettingsKeys {
    val TTS_SPEED = floatPreferencesKey("tts_speed")
    val FONT_SIZE = floatPreferencesKey("font_size")
    val THEME = intPreferencesKey("theme")

    /** Giao diện app: 0=Hệ thống, 1=Sáng, 2=Tối */
    val APP_THEME_MODE = intPreferencesKey("app_theme_mode")

    /** Bật/tắt thay chữ tự động (mặc định: bật) */
    val TEXT_REPLACEMENT_ENABLED = booleanPreferencesKey("text_replacement_enabled")

    /** Bật/tắt tự động format chương truyện (mặc định: bật) */
    val AUTO_FORMAT_ENABLED = booleanPreferencesKey("auto_format_enabled")

    /** Danh sách thay thế tùy chỉnh, lưu dạng JSON */
    val CUSTOM_REPLACEMENTS = stringPreferencesKey("custom_replacements")

    /** Khoảng cách dòng khi đọc (1.2f, 1.5f, 1.8f) */
    val LINE_SPACING = floatPreferencesKey("line_spacing")

    /** Chế độ Lật trang (vào Pager) thay vì cuộn dọc */
    val PAGED_MODE = booleanPreferencesKey("paged_mode")

    /** Cao độ giọng TTS (0.5f–2.0f, mặc định 1.0f) */
    val TTS_PITCH = floatPreferencesKey("tts_pitch")

    /** Tên giọng TTS đã chọn (Voice.name) */
    val TTS_VOICE = stringPreferencesKey("tts_voice")

    /** Font chữ đọc truyện: 0=NotoSerif, 1=Roboto, 2=BeVietnamPro, 3=System */
    val FONT_FAMILY = intPreferencesKey("font_family")

    /** Nguồn truyện đang hoạt động, lưu dạng ID đơn: "sstruyen" hoặc "wikidich" */
    val ENABLED_SOURCES = stringPreferencesKey("enabled_sources")

    /** Cờ đánh dấu đã di chuyển sang nguồn Wikidich (chỉ chạy 1 lần cho user cũ) */
    val IS_WIKIDICH_MIGRATED = booleanPreferencesKey("is_wikidich_migrated")

    /** Số chương tải trước khi đọc (0=tắt, 5/10/20) */
    val PREFETCH_CHAPTERS = intPreferencesKey("prefetch_chapters")

    /** Bật/tắt màu động Material You (Android 12+) */
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")

    /** Tốc độ tự cuộn (1–10, mặc định 3) */
    val AUTO_SCROLL_SPEED = intPreferencesKey("auto_scroll_speed")

    /** Bật/tắt từ điển tra cứu hình ảnh & hệ Pokémon khi đọc truyện (mặc định: bật = true) */
    val POKEMON_LOOKUP_ENABLED = booleanPreferencesKey("pokemon_lookup_enabled")

    fun restoreSettingFromBackup(
        prefs: MutablePreferences,
        key: String,
        rawValue: String,
    ) {
        when (key) {
            TTS_SPEED.name, FONT_SIZE.name, LINE_SPACING.name, TTS_PITCH.name -> {
                rawValue.toFloatOrNull()?.let { prefs[floatPreferencesKey(key)] = it }
            }
            THEME.name, APP_THEME_MODE.name, FONT_FAMILY.name, PREFETCH_CHAPTERS.name, AUTO_SCROLL_SPEED.name -> {
                // Remove any legacy Float entry under the same key name before writing Int
                prefs.remove(floatPreferencesKey(key))
                (rawValue.toIntOrNull() ?: rawValue.toFloatOrNull()?.toInt())?.let {
                    prefs[intPreferencesKey(key)] = it
                }
            }
            TEXT_REPLACEMENT_ENABLED.name, AUTO_FORMAT_ENABLED.name, PAGED_MODE.name,
            IS_WIKIDICH_MIGRATED.name, DYNAMIC_COLOR.name, POKEMON_LOOKUP_ENABLED.name,
            -> {
                rawValue.toBooleanStrictOrNull()?.let { prefs[booleanPreferencesKey(key)] = it }
            }
            CUSTOM_REPLACEMENTS.name, TTS_VOICE.name -> {
                prefs[stringPreferencesKey(key)] = rawValue
            }
            ENABLED_SOURCES.name -> {
                prefs[ENABLED_SOURCES] = SourceIds.resolveSingleSource(rawValue)
            }
        }
    }
}

private val INT_SETTING_KEYS =
    listOf(
        SettingsKeys.THEME,
        SettingsKeys.APP_THEME_MODE,
        SettingsKeys.FONT_FAMILY,
        SettingsKeys.PREFETCH_CHAPTERS,
        SettingsKeys.AUTO_SCROLL_SPEED,
    )

fun Preferences.getIntSafe(
    key: Preferences.Key<Int>,
    defaultValue: Int,
): Int {
    val raw = asMap().entries.firstOrNull { it.key.name == key.name }?.value ?: return defaultValue
    return when (raw) {
        is Int -> raw
        is Number -> raw.toInt()
        is String -> raw.toIntOrNull() ?: raw.toFloatOrNull()?.toInt() ?: defaultValue
        else -> defaultValue
    }
}

suspend fun DataStore<Preferences>.healCorruptedIntPreferences() {
    edit { prefs ->
        val rawByName = prefs.asMap().entries.associate { it.key.name to it.value }
        for (intKey in INT_SETTING_KEYS) {
            val raw = rawByName[intKey.name] ?: continue
            if (raw !is Int) {
                prefs.remove(floatPreferencesKey(intKey.name))
                prefs.remove(stringPreferencesKey(intKey.name))
                val healed =
                    when (raw) {
                        is Number -> raw.toInt()
                        is String -> raw.toIntOrNull() ?: raw.toFloatOrNull()?.toInt()
                        else -> null
                    }
                if (healed != null) {
                    prefs[intKey] = healed
                }
            }
        }
    }
}

/**
 * Migration nguồn truyện về đúng 1 nguồn hoạt động duy nhất, loại bỏ TruyenCom & TruyenFull.
 */
suspend fun DataStore<Preferences>.migrateWikidichEnabledSourcesIfNeeded() {
    val snapshot = data.first()
    val isMigrated = snapshot[SettingsKeys.IS_WIKIDICH_MIGRATED] ?: false
    val currentRaw = snapshot[SettingsKeys.ENABLED_SOURCES]
    if (isMigrated &&
        currentRaw != null &&
        !currentRaw.contains(",") &&
        currentRaw.trim() in SourceIds.ALL_ACTIVE_SOURCES
    ) {
        return
    }

    edit { preferences ->
        val rawInside = preferences[SettingsKeys.ENABLED_SOURCES]
        preferences[SettingsKeys.ENABLED_SOURCES] = SourceIds.resolveSingleSource(rawInside)
        preferences[SettingsKeys.IS_WIKIDICH_MIGRATED] = true
    }
}
