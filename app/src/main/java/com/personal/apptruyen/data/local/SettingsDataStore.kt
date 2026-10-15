package com.personal.apptruyen.data.local

import android.content.Context
import androidx.datastore.core.DataStore
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

    val ALL_ACTIVE_SOURCES = setOf(TANGTHUVIEN, SSTRUYEN, WIKIDICH)
    val DEPRECATED_SOURCES = setOf("truyencom", "truyenfull")

    val SOURCE_LABELS: Map<String, String> =
        mapOf(
            TANGTHUVIEN to "TàngThưViện",
            SSTRUYEN to "SsTruyện",
            WIKIDICH to "Wikidich",
        )
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

    /** Danh sách nguồn truyện bật, lưu dạng CSV: "tangthuvien,sstruyen,wikidich" */
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
}

/**
 * Migration nguồn truyện một lần duy nhất sang Wikidich, loại bỏ TruyenCom & TruyenFull.
 */
suspend fun DataStore<Preferences>.migrateWikidichEnabledSourcesIfNeeded() {
    val isMigrated = data.first()[SettingsKeys.IS_WIKIDICH_MIGRATED] ?: false
    if (isMigrated) return

    edit { preferences ->
        if (preferences[SettingsKeys.IS_WIKIDICH_MIGRATED] == true) return@edit
        val currentSources =
            preferences[SettingsKeys.ENABLED_SOURCES]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toMutableSet()
                ?: SourceIds.ALL_ACTIVE_SOURCES.toMutableSet()

        currentSources.removeAll(SourceIds.DEPRECATED_SOURCES)
        currentSources.add("wikidich")

        preferences[SettingsKeys.ENABLED_SOURCES] = currentSources.joinToString(",")
        preferences[SettingsKeys.IS_WIKIDICH_MIGRATED] = true
    }
}
