package com.personal.apptruyen.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.personal.apptruyen.BuildConfig
import com.personal.apptruyen.data.local.ChapterDao
import com.personal.apptruyen.data.local.DatabaseTransactionRunner
import com.personal.apptruyen.data.local.ReadingProgressDao
import com.personal.apptruyen.data.local.ReadingStatsDao
import com.personal.apptruyen.data.local.SearchHistoryDao
import com.personal.apptruyen.data.local.SettingsKeys
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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
        private val transactionRunner: DatabaseTransactionRunner,
    ) {
        private val json =
            Json {
                prettyPrint = false
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        companion object {
            const val CURRENT_BACKUP_VERSION = 1
            const val EXPORT_CHAPTER_PAGE_SIZE = 50
            const val IMPORT_CHAPTER_BATCH_SIZE = 500
            const val IMPORT_CONTENT_TRANSACTION_BATCH_SIZE = 50
            const val IMPORT_CONTENT_BATCH_MAX_BYTES = 8L * 1024 * 1024 // 8 MB / lô transaction
            const val MAX_METADATA_ENTRY_BYTES = 100L * 1024 * 1024 // 100 MB
            const val MAX_CHAPTER_ENTRY_BYTES = 5L * 1024 * 1024 // 5 MB / chương
            const val MAX_TOTAL_UNCOMPRESSED_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB tổng giải nén
            const val MAX_ZIP_ENTRIES = 500_000
            private const val IO_BUFFER_SIZE = 8 * 1024
            internal val CHAPTER_ENTRY_REGEX = Regex("^chapters/([^/]+)/(\\d+)\\.txt$")
        }

        private data class PendingChapterContent(
            val storyId: String,
            val chapterNumber: Int,
            val content: String,
            val timestamp: Long,
        )

        private class CountingNonClosingOutputStream(
            private val delegate: OutputStream,
            private val maxBytes: Long,
            private val limitErrorMessage: String,
        ) : OutputStream() {
            var bytesWritten: Long = 0L
                private set

            override fun write(b: Int) {
                checkLimit(1)
                delegate.write(b)
                bytesWritten += 1
            }

            override fun write(
                b: ByteArray,
                off: Int,
                len: Int,
            ) {
                checkLimit(len.toLong())
                delegate.write(b, off, len)
                bytesWritten += len
            }

            override fun flush() {
                delegate.flush()
            }

            override fun close() {
                // Không đóng ZipOutputStream bên dưới khi kết thúc 1 ZipEntry
                delegate.flush()
            }

            private fun checkLimit(additional: Long) {
                if (bytesWritten + additional > maxBytes) {
                    throw IllegalStateException(limitErrorMessage)
                }
            }
        }

        private class BoundedNonClosingInputStream(
            private val delegate: InputStream,
            private val maxBytes: Long,
            private val onBytesRead: (Int) -> Unit = {},
        ) : InputStream() {
            private var totalRead: Long = 0L

            override fun read(): Int {
                val b = delegate.read()
                if (b != -1) {
                    totalRead += 1
                    if (totalRead > maxBytes) {
                        throw IllegalArgumentException("Tệp sao lưu vượt quá giới hạn dung lượng an toàn")
                    }
                    onBytesRead(1)
                }
                return b
            }

            override fun read(
                b: ByteArray,
                off: Int,
                len: Int,
            ): Int {
                val n = delegate.read(b, off, len)
                if (n > 0) {
                    totalRead += n
                    if (totalRead > maxBytes) {
                        throw IllegalArgumentException("Tệp sao lưu vượt quá giới hạn dung lượng an toàn")
                    }
                    onBytesRead(n)
                }
                return n
            }

            override fun close() {
                // Không đóng ZipInputStream bên dưới
            }
        }

        internal fun readEntryBytesBounded(
            input: InputStream,
            maxEntryBytes: Long,
            onBytesRead: (Int) -> Unit = {},
        ): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(IO_BUFFER_SIZE)
            var entryBytes = 0L
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                if (read > 0) {
                    entryBytes += read
                    if (entryBytes > maxEntryBytes) {
                        throw IllegalArgumentException("Tệp sao lưu vượt quá giới hạn dung lượng an toàn")
                    }
                    onBytesRead(read)
                    out.write(buffer, 0, read)
                }
            }
            return out.toByteArray()
        }

        private fun drainEntryBounded(
            input: InputStream,
            maxEntryBytes: Long,
            onBytesRead: (Int) -> Unit,
        ) {
            val buffer = ByteArray(IO_BUFFER_SIZE)
            var entryBytes = 0L
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                if (read > 0) {
                    entryBytes += read
                    if (entryBytes > maxEntryBytes) {
                        throw IllegalArgumentException("Tệp sao lưu vượt quá giới hạn dung lượng an toàn")
                    }
                    onBytesRead(read)
                }
            }
        }

        @OptIn(ExperimentalSerializationApi::class)
        suspend fun exportBackup(
            outputStream: OutputStream,
            onProgress: (String) -> Unit = {},
        ) = withContext(Dispatchers.IO) {
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

            // Build lightweight chapter metadata without loading chapter text into RAM
            val chapterBackups = mutableListOf<ChapterBackup>()
            for (story in allStories) {
                currentCoroutineContext().ensureActive()
                onProgress("Đang sao lưu: ${story.title}")
                val chapters = chapterDao.getChaptersByStoryOnce(story.id)
                for (ch in chapters) {
                    chapterBackups.add(
                        ChapterBackup(
                            storyId = ch.storyId,
                            chapterNumber = ch.chapterNumber,
                            title = ch.title,
                            url = ch.url,
                            isDownloaded = ch.isDownloaded,
                            downloadedTimestamp = ch.downloadedTimestamp,
                            hasContent = ch.isDownloaded,
                        ),
                    )
                }
            }

            val metadata =
                BackupData(
                    version = CURRENT_BACKUP_VERSION,
                    createdAt = System.currentTimeMillis(),
                    appVersion = BuildConfig.VERSION_NAME,
                    stories = allStories.map { it.toBackup() },
                    chapters = chapterBackups,
                    readingProgress = allProgress.map { it.toBackup() },
                    readingStats = allStats.map { it.toBackup() },
                    searchHistory = allSearches.map { it.toBackup() },
                    settings = settingsMap,
                )

            // Stream ZIP entries directly without accumulating contentMap in heap
            ZipOutputStream(BufferedOutputStream(outputStream)).use { zip ->
                var totalUncompressedBytes = 0L

                // 1. Write metadata.json
                zip.putNextEntry(ZipEntry("metadata.json"))
                val countingMetadataOut =
                    CountingNonClosingOutputStream(
                        delegate = zip,
                        maxBytes = MAX_METADATA_ENTRY_BYTES,
                        limitErrorMessage = "Dữ liệu danh mục vượt quá giới hạn sao lưu an toàn (100 MB)",
                    )
                json.encodeToStream(BackupData.serializer(), metadata, countingMetadataOut)
                countingMetadataOut.flush()
                totalUncompressedBytes += countingMetadataOut.bytesWritten
                zip.closeEntry()

                // 2. Stream downloaded chapters page-by-page per story
                for (story in allStories) {
                    currentCoroutineContext().ensureActive()
                    var offset = 0
                    while (true) {
                        val page =
                            chapterDao.getDownloadedChaptersWithContentPaged(
                                storyId = story.id,
                                limit = EXPORT_CHAPTER_PAGE_SIZE,
                                offset = offset,
                            )
                        if (page.isEmpty()) break

                        for (ch in page) {
                            currentCoroutineContext().ensureActive()
                            val content = ch.content?.takeIf { it.isNotBlank() } ?: continue
                            val contentBytes = content.toByteArray(Charsets.UTF_8)
                            if (contentBytes.size > MAX_CHAPTER_ENTRY_BYTES) {
                                throw IllegalStateException(
                                    "Chương ${ch.chapterNumber} vượt quá giới hạn dung lượng sao lưu an toàn (5 MB)",
                                )
                            }
                            totalUncompressedBytes += contentBytes.size
                            if (totalUncompressedBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                throw IllegalStateException("Tổng dung lượng sao lưu vượt quá giới hạn an toàn (2 GB)")
                            }
                            zip.putNextEntry(ZipEntry("chapters/${ch.storyId}/${ch.chapterNumber}.txt"))
                            zip.write(contentBytes)
                            zip.closeEntry()
                        }

                        if (page.size < EXPORT_CHAPTER_PAGE_SIZE) break
                        offset += EXPORT_CHAPTER_PAGE_SIZE
                    }
                }
            }

            onProgress("Sao lưu hoàn tất!")
        }

        @OptIn(ExperimentalSerializationApi::class)
        suspend fun importBackup(
            inputStream: InputStream,
            onProgress: (String) -> Unit = {},
        ) = withContext(Dispatchers.IO) {
            val tempZipFile = File.createTempFile("apptruyen_import_", ".zip")
            try {
                // ── Pha 1: Spool ra file tạm & Kiểm chứng ZIP ngoài DB Transaction ──
                FileOutputStream(tempZipFile).use { fos ->
                    val buffer = ByteArray(IO_BUFFER_SIZE)
                    var copiedBytes = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = inputStream.read(buffer)
                        if (read == -1) break
                        if (read > 0) {
                            copiedBytes += read
                            if (copiedBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                throw IllegalArgumentException("Tệp sao lưu vượt quá giới hạn dung lượng an toàn")
                            }
                            fos.write(buffer, 0, read)
                        }
                    }
                }

                var parsedMetadata: BackupData? = null
                var totalUncompressedBytes = 0L
                var entryCount = 0

                val addUncompressedBytes: (Int) -> Unit = { delta ->
                    totalUncompressedBytes += delta
                    if (totalUncompressedBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                        throw IllegalArgumentException("Tệp sao lưu vượt quá giới hạn dung lượng an toàn")
                    }
                }

                ZipInputStream(BufferedInputStream(FileInputStream(tempZipFile))).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        currentCoroutineContext().ensureActive()
                        entryCount++
                        if (entryCount > MAX_ZIP_ENTRIES) {
                            throw IllegalArgumentException("Tệp sao lưu vượt quá số lượng mục cho phép")
                        }
                        val name = entry.name
                        if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                            throw IllegalArgumentException("Đường dẫn tệp trong bản sao lưu không hợp lệ")
                        }

                        when {
                            name == "metadata.json" -> {
                                val boundedStream =
                                    BoundedNonClosingInputStream(
                                        delegate = zip,
                                        maxBytes = MAX_METADATA_ENTRY_BYTES,
                                        onBytesRead = addUncompressedBytes,
                                    )
                                parsedMetadata = json.decodeFromStream(BackupData.serializer(), boundedStream)
                            }
                            name.startsWith("chapters/") -> {
                                val match =
                                    CHAPTER_ENTRY_REGEX.matchEntire(name)
                                        ?: throw IllegalArgumentException("Đường dẫn chương trong bản sao lưu không hợp lệ")
                                val chapterNum = match.groupValues[2].toIntOrNull()
                                if (chapterNum == null || chapterNum <= 0) {
                                    throw IllegalArgumentException("Số chương trong bản sao lưu không hợp lệ")
                                }
                                drainEntryBounded(zip, MAX_CHAPTER_ENTRY_BYTES, addUncompressedBytes)
                            }
                            else -> {
                                drainEntryBounded(zip, MAX_CHAPTER_ENTRY_BYTES, addUncompressedBytes)
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }

                val data =
                    parsedMetadata
                        ?: throw IllegalArgumentException("Tệp sao lưu không hợp lệ: thiếu metadata.json")
                if (data.version > CURRENT_BACKUP_VERSION) {
                    throw IllegalArgumentException(
                        "Phiên bản tệp sao lưu (${data.version}) mới hơn phiên bản ứng dụng hỗ trợ",
                    )
                }

                // ── Pha 2a: Ghi nguyên tử Metadata vào Room trong 1 Write Transaction ──
                onProgress("Đang khôi phục truyện...")
                transactionRunner.withWriteTransaction {
                    for (storyBackup in data.stories) {
                        val existing = storyDao.getStoryById(storyBackup.id)
                        if (existing == null) {
                            storyDao.insertStoryIgnore(storyBackup.toEntity())
                        } else {
                            storyDao.updateStory(
                                existing.copy(
                                    isInLibrary = existing.isInLibrary || storyBackup.isInLibrary,
                                    addedTimestamp =
                                        if (existing.addedTimestamp > 0L) {
                                            existing.addedTimestamp
                                        } else {
                                            storyBackup.addedTimestamp
                                        },
                                    totalChapters =
                                        resolveSafeTotalChapters(storyBackup.totalChapters, existing.totalChapters),
                                    coverImageUrl =
                                        resolveSafeCoverUrl(storyBackup.coverImageUrl, existing.coverImageUrl),
                                    author = existing.author.ifBlank { storyBackup.author },
                                    description = existing.description.ifBlank { storyBackup.description },
                                    genres = existing.genres.ifBlank { storyBackup.genres },
                                    status = existing.status.ifBlank { storyBackup.status },
                                    storyReplacementsEnabled =
                                        existing.storyReplacementsEnabled || storyBackup.storyReplacementsEnabled,
                                    storyCustomReplacements =
                                        existing.storyCustomReplacements.ifBlank { storyBackup.storyCustomReplacements },
                                ),
                            )
                        }
                    }

                    for (batch in data.chapters.chunked(IMPORT_CHAPTER_BATCH_SIZE)) {
                        val entities =
                            batch.map { ch ->
                                ChapterEntity(
                                    storyId = ch.storyId,
                                    chapterNumber = ch.chapterNumber,
                                    title = ch.title,
                                    url = ch.url,
                                    content = null,
                                    isDownloaded = false,
                                    downloadedTimestamp = null,
                                )
                            }
                        chapterDao.insertChaptersIgnore(entities)
                    }

                    onProgress("Đang khôi phục tiến trình đọc...")
                    for (progressBackup in data.readingProgress) {
                        val existingProgress = progressDao.getProgress(progressBackup.storyId)
                        if (existingProgress == null ||
                            progressBackup.lastReadTimestamp > existingProgress.lastReadTimestamp
                        ) {
                            progressDao.saveProgress(progressBackup.toEntity())
                        }
                    }

                    for (statBackup in data.readingStats) {
                        val existingStat = statsDao.getStatsForDate(statBackup.date)
                        if (existingStat == null) {
                            statsDao.upsertStats(statBackup.toEntity())
                        } else {
                            statsDao.upsertStats(
                                ReadingStatsEntity(
                                    date = statBackup.date,
                                    totalReadingTimeMs =
                                        maxOf(existingStat.totalReadingTimeMs, statBackup.totalReadingTimeMs),
                                    chaptersRead =
                                        maxOf(existingStat.chaptersRead, statBackup.chaptersRead),
                                    storiesRead =
                                        maxOf(existingStat.storiesRead, statBackup.storiesRead),
                                ),
                            )
                        }
                    }

                    if (data.searchHistory.isNotEmpty()) {
                        searchDao.insertAllSearches(data.searchHistory.map { it.toEntity() })
                    }
                }

                // ── Pha 2b: Ghi nội dung chương theo lô giới hạn số chương (<= 50) và dung lượng (<= 8 MB) ──
                val chapterTimestamps: Map<String, Long> =
                    data.chapters
                        .mapNotNull { ch ->
                            val ts = ch.downloadedTimestamp
                            if (ch.hasContent && ts != null) "${ch.storyId}/${ch.chapterNumber}" to ts else null
                        }.toMap()

                val pendingBatch = ArrayList<PendingChapterContent>(IMPORT_CONTENT_TRANSACTION_BATCH_SIZE)
                var pendingBatchBytes = 0L

                suspend fun flushPendingBatch() {
                    if (pendingBatch.isEmpty()) return
                    transactionRunner.withWriteTransaction {
                        for (item in pendingBatch) {
                            chapterDao.restoreChapterContentIfEmpty(
                                storyId = item.storyId,
                                chapterNumber = item.chapterNumber,
                                content = item.content,
                                timestamp = item.timestamp,
                            )
                        }
                    }
                    pendingBatch.clear()
                    pendingBatchBytes = 0L
                }

                ZipInputStream(BufferedInputStream(FileInputStream(tempZipFile))).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        currentCoroutineContext().ensureActive()
                        val match = CHAPTER_ENTRY_REGEX.matchEntire(entry.name)
                        if (match != null) {
                            val storyId = match.groupValues[1]
                            val chapterNumber = match.groupValues[2].toInt()
                            val contentBytes = readEntryBytesBounded(zip, MAX_CHAPTER_ENTRY_BYTES)
                            if (contentBytes.isNotEmpty()) {
                                if (pendingBatch.isNotEmpty() &&
                                    (
                                        pendingBatch.size >= IMPORT_CONTENT_TRANSACTION_BATCH_SIZE ||
                                            pendingBatchBytes + contentBytes.size > IMPORT_CONTENT_BATCH_MAX_BYTES
                                    )
                                ) {
                                    flushPendingBatch()
                                }
                                val contentText = String(contentBytes, Charsets.UTF_8)
                                if (contentText.isNotBlank()) {
                                    val timestamp =
                                        chapterTimestamps["$storyId/$chapterNumber"] ?: data.createdAt
                                    pendingBatch.add(
                                        PendingChapterContent(
                                            storyId = storyId,
                                            chapterNumber = chapterNumber,
                                            content = contentText,
                                            timestamp = timestamp,
                                        ),
                                    )
                                    pendingBatchBytes += contentBytes.size
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
                flushPendingBatch()

                // ── Khôi phục cài đặt ──
                if (data.settings.isNotEmpty()) {
                    onProgress("Đang khôi phục cài đặt...")
                    dataStore.edit { prefs ->
                        data.settings.forEach { (key, value) ->
                            SettingsKeys.restoreSettingFromBackup(prefs, key, value)
                        }
                    }
                }

                onProgress("Khôi phục hoàn tất!")
            } finally {
                tempZipFile.delete()
            }
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
