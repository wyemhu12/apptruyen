package com.personal.apptruyen.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import com.personal.apptruyen.data.local.ChapterDao
import com.personal.apptruyen.data.local.DatabaseTransactionRunner
import com.personal.apptruyen.data.local.ReadingProgressDao
import com.personal.apptruyen.data.local.ReadingStatsDao
import com.personal.apptruyen.data.local.SearchHistoryDao
import com.personal.apptruyen.data.local.SettingsKeys
import com.personal.apptruyen.data.local.StoryDao
import com.personal.apptruyen.data.local.entity.ChapterEntity
import com.personal.apptruyen.data.local.entity.StoryEntity
import com.personal.apptruyen.data.local.getIntSafe
import com.personal.apptruyen.data.local.healCorruptedIntPreferences
import com.personal.apptruyen.data.model.BackupData
import com.personal.apptruyen.data.model.ChapterBackup
import com.personal.apptruyen.data.model.PokemonCatalog
import com.personal.apptruyen.data.model.PokemonEntry
import com.personal.apptruyen.data.model.PokemonForm
import com.personal.apptruyen.data.model.ProgressBackup
import com.personal.apptruyen.data.model.SearchBackup
import com.personal.apptruyen.data.model.StatsBackup
import com.personal.apptruyen.data.model.StoryBackup
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupRepositoryTest {

    private class FakePreferencesDataStore(
        initial: Preferences = preferencesOf(),
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }

        fun current(): Preferences = state.value
    }

    private lateinit var storyDao: StoryDao
    private lateinit var chapterDao: ChapterDao
    private lateinit var progressDao: ReadingProgressDao
    private lateinit var statsDao: ReadingStatsDao
    private lateinit var searchDao: SearchHistoryDao
    private lateinit var dataStore: FakePreferencesDataStore
    private lateinit var transactionRunner: DatabaseTransactionRunner
    private lateinit var backupRepository: BackupRepository
    private val writeTransactionCount = AtomicInteger(0)

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @BeforeEach
    fun setUp() {
        storyDao = mockk(relaxed = true)
        chapterDao = mockk(relaxed = true)
        progressDao = mockk(relaxed = true)
        statsDao = mockk(relaxed = true)
        searchDao = mockk(relaxed = true)
        dataStore = FakePreferencesDataStore()
        writeTransactionCount.set(0)
        transactionRunner =
            object : DatabaseTransactionRunner {
                override suspend fun <R> withWriteTransaction(block: suspend () -> R): R {
                    writeTransactionCount.incrementAndGet()
                    return block()
                }
            }
        coEvery { storyDao.getAllStoriesOnce() } returns emptyList()
        coEvery { progressDao.getAllProgressOnce() } returns emptyList()
        coEvery { statsDao.getAllStatsOnce() } returns emptyList()
        coEvery { searchDao.getAllSearches() } returns emptyList()

        backupRepository =
            BackupRepository(
                storyDao = storyDao,
                chapterDao = chapterDao,
                progressDao = progressDao,
                statsDao = statsDao,
                searchDao = searchDao,
                dataStore = dataStore,
                transactionRunner = transactionRunner,
            )
    }

    @Test
    fun `exportBackup streams chapter contents by story page and preserves storyReplacementsEnabled`() =
        runTest {
            val story =
                StoryEntity(
                    id = "s1",
                    slug = "s1",
                    title = "Truyện 1",
                    author = "Tác giả",
                    genres = "Tiên Hiệp",
                    description = "Mô tả",
                    url = "https://sstruyen.pics/s1",
                    totalChapters = 2,
                    coverImageUrl = "https://sstruyen.pics/cover.jpg",
                    sourceId = "sstruyen",
                    status = "Đang ra",
                    storyReplacementsEnabled = true,
                    storyCustomReplacements = """[{"from":"a","to":"b"}]""",
                )
            coEvery { storyDao.getAllStoriesOnce() } returns listOf(story)
            coEvery { chapterDao.getChaptersByStoryOnce("s1") } returns
                listOf(
                    ChapterEntity(
                        "s1",
                        1,
                        "Chương 1",
                        "https://sstruyen.pics/s1/c1",
                        content = null,
                        isDownloaded = true,
                        downloadedTimestamp = 100L,
                    ),
                    ChapterEntity("s1", 2, "Chương 2", "https://sstruyen.pics/s1/c2", content = null, isDownloaded = false, downloadedTimestamp = 0L),
                )
            coEvery {
                chapterDao.getDownloadedChaptersWithContentPaged("s1", BackupRepository.EXPORT_CHAPTER_PAGE_SIZE, 0)
            } returns
                listOf(
                    ChapterEntity(
                        "s1",
                        1,
                        "Chương 1",
                        "https://sstruyen.pics/s1/c1",
                        content = "Nội dung chương 1",
                        isDownloaded = true,
                        downloadedTimestamp = 100L,
                    ),
                )
            coEvery {
                chapterDao.getDownloadedChaptersWithContentPaged("s1", BackupRepository.EXPORT_CHAPTER_PAGE_SIZE, 1)
            } returns emptyList()

            val baos = ByteArrayOutputStream()
            backupRepository.exportBackup(baos)

            val entries = mutableMapOf<String, String>()
            ZipInputStream(ByteArrayInputStream(baos.toByteArray())).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            assertTrue(entries.containsKey("metadata.json"))
            assertEquals("Nội dung chương 1", entries["chapters/s1/1.txt"])
            assertNull(entries["chapters/s1/2.txt"])

            val parsed = json.decodeFromString<BackupData>(entries.getValue("metadata.json"))
            assertEquals(1, parsed.stories.size)
            assertTrue(parsed.stories[0].storyReplacementsEnabled)
            assertEquals("""[{"from":"a","to":"b"}]""", parsed.stories[0].storyCustomReplacements)
        }

    @Test
    fun `importBackup restores typed SettingsKeys correctly including AUTO_SCROLL_SPEED as Int and customReplacements`() =
        runTest {
            val corruptedInitial =
                mutablePreferencesOf().apply {
                    this[floatPreferencesKey(SettingsKeys.AUTO_SCROLL_SPEED.name)] = 5.0f
                }
            dataStore = FakePreferencesDataStore(corruptedInitial)
            backupRepository =
                BackupRepository(
                    storyDao = storyDao,
                    chapterDao = chapterDao,
                    progressDao = progressDao,
                    statsDao = statsDao,
                    searchDao = searchDao,
                    dataStore = dataStore,
                    transactionRunner = transactionRunner,
                )

            val backupData =
                BackupData(
                    settings =
                        mapOf(
                            SettingsKeys.TTS_SPEED.name to "1.25",
                            SettingsKeys.AUTO_SCROLL_SPEED.name to "6.0",
                            SettingsKeys.THEME.name to "2",
                            SettingsKeys.TEXT_REPLACEMENT_ENABLED.name to "false",
                            SettingsKeys.CUSTOM_REPLACEMENTS.name to """[{"find":"x","replace":"y"}]""",
                        ),
                )
            val zipBytes = createZipBytes("metadata.json" to json.encodeToString(backupData))

            backupRepository.importBackup(ByteArrayInputStream(zipBytes))

            val prefs = dataStore.current()
            assertEquals(1.25f, prefs[SettingsKeys.TTS_SPEED])
            assertEquals(6, prefs[SettingsKeys.AUTO_SCROLL_SPEED])
            assertEquals(2, prefs[SettingsKeys.THEME])
            assertEquals(false, prefs[SettingsKeys.TEXT_REPLACEMENT_ENABLED])
            assertEquals("""[{"find":"x","replace":"y"}]""", prefs[SettingsKeys.CUSTOM_REPLACEMENTS])
        }

    @Test
    fun `importBackup rejects zip entry exceeding MAX_JSON_ENTRY_BYTES or MAX_CHAPTER_ENTRY_BYTES`() =
        runTest {
            val oversizedChapter = "A".repeat((BackupRepository.MAX_CHAPTER_ENTRY_BYTES + 1024L).toInt())
            val backupData =
                BackupData(
                    stories =
                        listOf(
                            StoryBackup(
                                id = "s1",
                                slug = "s1",
                                title = "T1",
                                author = "A",
                                genres = "",
                                description = "",
                                url = "u",
                                totalChapters = 1,
                            ),
                        ),
                    chapters = listOf(ChapterBackup(storyId = "s1", chapterNumber = 1, title = "C1", url = "u1", isDownloaded = true)),
                )
            val zipBytes =
                createZipBytes(
                    "metadata.json" to json.encodeToString(backupData),
                    "chapters/s1/1.txt" to oversizedChapter,
                )

            val ex =
                assertThrows(IllegalArgumentException::class.java) {
                    kotlinx.coroutines.runBlocking {
                        backupRepository.importBackup(ByteArrayInputStream(zipBytes))
                    }
                }
            assertTrue(ex.message?.contains("vượt quá giới hạn dung lượng") == true)
            assertEquals(0, writeTransactionCount.get())
        }

    @Test
    fun `importBackup rejects malformed backup without mutating database when Phase 1 validation fails`() =
        runTest {
            val zipBytes =
                createZipBytes(
                    "metadata.json" to "{ malformed json",
                )

            assertThrows(Exception::class.java) {
                kotlinx.coroutines.runBlocking {
                    backupRepository.importBackup(ByteArrayInputStream(zipBytes))
                }
            }
            assertEquals(0, writeTransactionCount.get())
            coVerify(exactly = 0) { storyDao.insertStoryIgnore(any()) }
            coVerify(exactly = 0) { chapterDao.insertChaptersIgnore(any()) }
        }

    @Test
    fun `importBackup does not overwrite existing local downloaded chapter content`() =
        runTest {
            val backupData =
                BackupData(
                    stories =
                        listOf(
                            StoryBackup(
                                id = "s1",
                                slug = "s1",
                                title = "T1",
                                author = "A",
                                genres = "",
                                description = "",
                                url = "https://sstruyen.pics/s1",
                                totalChapters = 1,
                                storyReplacementsEnabled = true,
                                storyCustomReplacements = "rules",
                            ),
                        ),
                    chapters =
                        listOf(
                            ChapterBackup(
                                storyId = "s1",
                                chapterNumber = 1,
                                title = "C1",
                                url = "https://sstruyen.pics/s1/c1",
                                isDownloaded = true,
                                downloadedTimestamp = 999L,
                                hasContent = true,
                            ),
                        ),
                )
            coEvery { storyDao.getStoryById("s1") } returns null
            coEvery { chapterDao.restoreChapterContentIfEmpty("s1", 1, "Nội dung từ backup", 999L) } just runs

            val zipBytes =
                createZipBytes(
                    "metadata.json" to json.encodeToString(backupData),
                    "chapters/s1/1.txt" to "Nội dung từ backup",
                )

            backupRepository.importBackup(ByteArrayInputStream(zipBytes))

            coVerify(exactly = 1) { storyDao.insertStoryIgnore(any()) }
            coVerify(exactly = 1) { chapterDao.restoreChapterContentIfEmpty("s1", 1, "Nội dung từ backup", 999L) }
            coVerify(exactly = 0) { chapterDao.saveChapterContent(any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun `importBackup chunks Phase 2b chapter writes by MAX_CHAPTER_BATCH_COUNT and MAX_CHAPTER_BATCH_BYTES`() =
        runTest {
            val chaptersCount = 55
            val stories =
                listOf(
                    StoryBackup(
                        id = "s1",
                        slug = "s1",
                        title = "T1",
                        author = "A",
                        genres = "",
                        description = "",
                        url = "https://sstruyen.pics/s1",
                        totalChapters = chaptersCount,
                    ),
                )
            val chapters =
                (1..chaptersCount).map { num ->
                    ChapterBackup(
                        storyId = "s1",
                        chapterNumber = num,
                        title = "Chương $num",
                        url = "https://sstruyen.pics/s1/c$num",
                        isDownloaded = true,
                        downloadedTimestamp = 1000L + num,
                        hasContent = true,
                    )
                }
            val entries = mutableListOf("metadata.json" to json.encodeToString(BackupData(stories = stories, chapters = chapters)))
            for (num in 1..chaptersCount) {
                entries.add("chapters/s1/$num.txt" to "Nội dung chương $num")
            }

            val zipBytes = createZipBytes(*entries.toTypedArray())
            backupRepository.importBackup(ByteArrayInputStream(zipBytes))

            // 1 transaction for Phase 2a metadata + 2 transactions for Phase 2b (50 chapters + 5 chapters) = 3 transactions
            assertEquals(3, writeTransactionCount.get())
            coVerify(exactly = chaptersCount) { chapterDao.restoreChapterContentIfEmpty("s1", any(), any(), any()) }
        }

    @Test
    fun `healCorruptedIntPreferences repairs Float value in Int key and getIntSafe returns default on ClassCastException`() =
        runTest {
            val corrupted =
                mutablePreferencesOf().apply {
                    this[floatPreferencesKey(SettingsKeys.AUTO_SCROLL_SPEED.name)] = 7.0f
                    this[floatPreferencesKey(SettingsKeys.PREFETCH_CHAPTERS.name)] = 10.0f
                }
            assertEquals(7, corrupted.getIntSafe(SettingsKeys.AUTO_SCROLL_SPEED, 3))
            assertEquals(10, corrupted.getIntSafe(SettingsKeys.PREFETCH_CHAPTERS, 0))
            assertEquals(1, corrupted.getIntSafe(SettingsKeys.THEME, 1))

            val ds = FakePreferencesDataStore(corrupted)
            ds.healCorruptedIntPreferences()

            val healed = ds.current()
            assertEquals(7, healed[SettingsKeys.AUTO_SCROLL_SPEED])
            assertEquals(10, healed[SettingsKeys.PREFETCH_CHAPTERS])
        }

    @Test
    fun `serializableModelsHaveRuntimeSerializationDescriptorsForProGuardRule`() {
        val descriptors =
            listOf(
                BackupData.serializer().descriptor.serialName,
                StoryBackup.serializer().descriptor.serialName,
                ChapterBackup.serializer().descriptor.serialName,
                ProgressBackup.serializer().descriptor.serialName,
                StatsBackup.serializer().descriptor.serialName,
                SearchBackup.serializer().descriptor.serialName,
                PokemonCatalog.serializer().descriptor.serialName,
                PokemonEntry.serializer().descriptor.serialName,
                PokemonForm.serializer().descriptor.serialName,
            )
        assertEquals(9, descriptors.size)
        descriptors.forEach { serialName ->
            assertTrue(serialName.startsWith("com.personal.apptruyen.data.model."))
        }

        val sampleBackup =
            BackupData(
                version = 1,
                createdAt = 123456L,
                appVersion = "3.0.2",
                stories = listOf(StoryBackup(id = "s1", slug = "s1", title = "T1", url = "https://example.com")),
                chapters = listOf(ChapterBackup(storyId = "s1", chapterNumber = 1, title = "C1", url = "https://example.com/1")),
                readingProgress = listOf(ProgressBackup(storyId = "s1", lastChapterNumber = 1)),
                readingStats = listOf(StatsBackup(date = "2026-10-19", chaptersRead = 1)),
                searchHistory = listOf(SearchBackup(query = "tien nghich")),
            )
        val encodedBackup = json.encodeToString(sampleBackup)
        val decodedBackup = json.decodeFromString<BackupData>(encodedBackup)
        assertEquals(sampleBackup, decodedBackup)

        val sampleCatalog =
            PokemonCatalog(
                entries =
                    listOf(
                        PokemonEntry(
                            id = 25,
                            nameCn = "皮卡丘",
                            nameVn = "Pikachu",
                            nameHv = "Bì Khả Khâu",
                            forms =
                                listOf(
                                    PokemonForm(
                                        formCn = "皮卡丘",
                                        formVn = "Pikachu",
                                        formHv = "Bì Khả Khâu",
                                        types = listOf("Điện"),
                                        imageUrl = "https://vn.portal-pokemon.com/play/resources/pokedex/img/pm/25.png",
                                    ),
                                ),
                        ),
                    ),
            )
        val encodedCatalog = json.encodeToString(sampleCatalog)
        val decodedCatalog = json.decodeFromString<PokemonCatalog>(encodedCatalog)
        assertEquals(sampleCatalog, decodedCatalog)
    }

    private fun createZipBytes(vararg entries: Pair<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}
