package com.personal.apptruyen.data.repository

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.personal.apptruyen.BuildConfig
import com.personal.apptruyen.data.local.ChapterDao
import com.personal.apptruyen.data.local.ReadingProgressDao
import com.personal.apptruyen.data.local.ReadingStatsDao
import com.personal.apptruyen.data.local.SearchHistoryDao
import com.personal.apptruyen.data.local.StoryDao
import com.personal.apptruyen.data.local.entity.ChapterEntity
import com.personal.apptruyen.data.local.entity.ReadingProgressEntity
import com.personal.apptruyen.data.local.entity.ReadingStatsEntity
import com.personal.apptruyen.data.local.entity.SearchHistoryEntity
import com.personal.apptruyen.data.local.entity.StoryEntity
import com.personal.apptruyen.data.model.BackupData
import com.personal.apptruyen.data.model.ChapterBackup
import com.personal.apptruyen.data.model.ProgressBackup
import com.personal.apptruyen.data.model.SearchBackup
import com.personal.apptruyen.data.model.StatsBackup
import com.personal.apptruyen.data.model.StoryBackup
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository
    @Inject
    constructor(
        private val storyDao: StoryDao,
        private val chapterDao: ChapterDao,
        private val progressDao: ReadingProgressDao,
        private val statsDao: ReadingStatsDao,
        private val searchDao: SearchHistoryDao,
        private val dataStore: DataStore<Preferences>,
    ) {
        private val json =
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            }

        suspend fun exportBackup(
            outputStream: OutputStream,
            onProgress: (String) -> Unit = {},
        ) {
            val allStories = storyDao.getAllStoriesOnce()
            val allProgress = progressDao.getAllProgressOnce()
            val allStats = statsDao.getAllStatsOnce()
            val allSearches = searchDao.getAllSearches()
            val prefs = dataStore.data.first()

            // Collect settings
            val settingsMap = mutableMapOf<String, String>()
            prefs.asMap().forEach { (key, value) ->
                settingsMap[key.name] = value.toString()
            }

            // Build chapter metadata + track which have content
            val chapterBackups = mutableListOf<ChapterBackup>()
            val contentMap = mutableMapOf<String, String>() // "storyId/chapterNum" -> content

            for (story in allStories) {
                onProgress("Đang sao lưu: ${story.title}")
                val chapters = chapterDao.getChaptersByStoryOnce(story.id)
                for (ch in chapters) {
                    val hasContent = !ch.content.isNullOrBlank()
                    chapterBackups.add(
                        ChapterBackup(
                            storyId = ch.storyId,
                            chapterNumber = ch.chapterNumber,
                            title = ch.title,
                            url = ch.url,
                            isDownloaded = ch.isDownloaded,
                            downloadedTimestamp = ch.downloadedTimestamp,
                            hasContent = hasContent,
                        ),
                    )
                    if (hasContent) {
                        contentMap["${ch.storyId}/${ch.chapterNumber}"] = ch.content!!
                    }
                }
            }

            val metadata =
                BackupData(
                    version = 1,
                    createdAt = System.currentTimeMillis(),
                    appVersion = BuildConfig.VERSION_NAME,
                    stories = allStories.map { it.toBackup() },
                    chapters = chapterBackups,
                    readingProgress = allProgress.map { it.toBackup() },
                    readingStats = allStats.map { it.toBackup() },
                    searchHistory = allSearches.map { it.toBackup() },
                    settings = settingsMap,
                )

            // Write ZIP
            ZipOutputStream(BufferedOutputStream(outputStream)).use { zip ->
                // metadata.json
                zip.putNextEntry(ZipEntry("metadata.json"))
                zip.write(json.encodeToString(BackupData.serializer(), metadata).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // Chapter content files
                for ((path, content) in contentMap) {
                    zip.putNextEntry(ZipEntry("chapters/$path.txt"))
                    zip.write(content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }

            onProgress("Sao lưu hoàn tất!")
        }

        suspend fun importBackup(
            inputStream: InputStream,
            onProgress: (String) -> Unit = {},
        ) {
            val contentMap = mutableMapOf<String, String>()
            var metadata: BackupData? = null

            // Read ZIP
            ZipInputStream(BufferedInputStream(inputStream)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        name == "metadata.json" -> {
                            val bytes = zip.readBytes()
                            metadata = json.decodeFromString(BackupData.serializer(), String(bytes, Charsets.UTF_8))
                        }
                        name.startsWith("chapters/") && name.endsWith(".txt") -> {
                            val key = name.removePrefix("chapters/").removeSuffix(".txt")
                            contentMap[key] = String(zip.readBytes(), Charsets.UTF_8)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val data = metadata ?: throw IllegalStateException("File backup không hợp lệ")

            // Restore stories
            onProgress("Đang khôi phục truyện...")
            val storyEntities = data.stories.map { it.toEntity() }
            storyDao.insertStories(storyEntities)

            // Restore chapters (metadata + content)
            for (story in data.stories) {
                onProgress("Đang khôi phục: ${story.title}")
                val storyChapters = data.chapters.filter { it.storyId == story.id }
                val entities =
                    storyChapters.map { ch ->
                        val content = if (ch.hasContent) contentMap["${ch.storyId}/${ch.chapterNumber}"] else null
                        ChapterEntity(
                            storyId = ch.storyId,
                            chapterNumber = ch.chapterNumber,
                            title = ch.title,
                            url = ch.url,
                            content = content,
                            isDownloaded = ch.isDownloaded && content != null,
                            downloadedTimestamp = ch.downloadedTimestamp,
                        )
                    }
                if (entities.isNotEmpty()) {
                    chapterDao.insertChapters(entities)
                }
            }

            // Restore reading progress
            onProgress("Đang khôi phục tiến trình đọc...")
            val progressEntities = data.readingProgress.map { it.toEntity() }
            progressDao.insertAllProgress(progressEntities)

            // Restore reading stats
            val statsEntities = data.readingStats.map { it.toEntity() }
            statsDao.insertAllStats(statsEntities)

            // Restore search history
            val searchEntities = data.searchHistory.map { it.toEntity() }
            searchDao.insertAllSearches(searchEntities)

            // Restore settings
            onProgress("Đang khôi phục cài đặt...")
            dataStore.edit { prefs ->
                data.settings.forEach { (key, value) ->
                    try {
                        when {
                            value == "true" || value == "false" ->
                                prefs[booleanPreferencesKey(key)] = value.toBoolean()
                            value.toFloatOrNull() != null &&
                                (
                                    key.contains("speed", true) ||
                                        key.contains("size", true) ||
                                        key.contains("spacing", true) ||
                                        key.contains("pitch", true)
                                ) ->
                                prefs[floatPreferencesKey(key)] = value.toFloat()
                            value.toIntOrNull() != null && !value.contains(".") ->
                                prefs[intPreferencesKey(key)] = value.toInt()
                            else ->
                                prefs[stringPreferencesKey(key)] = value
                        }
                    } catch (e: Exception) {
                        Log.e("BackupRepository", "Failed to restore setting: $key", e)
                    }
                }
            }

            onProgress("Khôi phục hoàn tất!")
        }

        // ── Extension functions for mapping ──

        private fun StoryEntity.toBackup() =
            StoryBackup(
                id = id,
                slug = slug,
                title = title,
                author = author,
                genres = genres,
                description = description,
                url = url,
                totalChapters = totalChapters,
                coverImageUrl = coverImageUrl,
                isInLibrary = isInLibrary,
                addedTimestamp = addedTimestamp,
                sourceId = sourceId,
                status = status,
                storyReplacementsEnabled = storyReplacementsEnabled,
                storyCustomReplacements = storyCustomReplacements,
            )

        private fun StoryBackup.toEntity() =
            StoryEntity(
                id = id,
                slug = slug,
                title = title,
                author = author,
                genres = genres,
                description = description,
                url = url,
                totalChapters = totalChapters,
                coverImageUrl = coverImageUrl,
                isInLibrary = isInLibrary,
                addedTimestamp = addedTimestamp,
                sourceId = sourceId,
                status = status,
                storyReplacementsEnabled = storyReplacementsEnabled,
                storyCustomReplacements = storyCustomReplacements,
            )

        private fun ReadingProgressEntity.toBackup() =
            ProgressBackup(
                storyId = storyId,
                lastChapterNumber = lastChapterNumber,
                scrollPosition = scrollPosition,
                lastReadTimestamp = lastReadTimestamp,
            )

        private fun ProgressBackup.toEntity() =
            ReadingProgressEntity(
                storyId = storyId,
                lastChapterNumber = lastChapterNumber,
                scrollPosition = scrollPosition,
                lastReadTimestamp = lastReadTimestamp,
            )

        private fun ReadingStatsEntity.toBackup() =
            StatsBackup(
                date = date,
                totalReadingTimeMs = totalReadingTimeMs,
                chaptersRead = chaptersRead,
                storiesRead = storiesRead,
            )

        private fun StatsBackup.toEntity() =
            ReadingStatsEntity(
                date = date,
                totalReadingTimeMs = totalReadingTimeMs,
                chaptersRead = chaptersRead,
                storiesRead = storiesRead,
            )

        private fun SearchHistoryEntity.toBackup() =
            SearchBackup(
                query = query,
                timestamp = timestamp,
            )

        private fun SearchBackup.toEntity() =
            SearchHistoryEntity(
                query = query,
                timestamp = timestamp,
            )
    }
