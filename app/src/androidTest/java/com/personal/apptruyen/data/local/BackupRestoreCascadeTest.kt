package com.personal.apptruyen.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.room3.Room
import androidx.room3.withWriteTransaction
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personal.apptruyen.data.local.entity.ChapterEntity
import com.personal.apptruyen.data.local.entity.ReadingProgressEntity
import com.personal.apptruyen.data.local.entity.StoryEntity
import com.personal.apptruyen.data.model.BackupData
import com.personal.apptruyen.data.model.ChapterBackup
import com.personal.apptruyen.data.model.ProgressBackup
import com.personal.apptruyen.data.model.StoryBackup
import com.personal.apptruyen.data.repository.BackupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class BackupRestoreCascadeTest {

    private lateinit var db: AppDatabase
    private lateinit var storyDao: StoryDao
    private lateinit var chapterDao: ChapterDao
    private lateinit var progressDao: ReadingProgressDao
    private lateinit var statsDao: ReadingStatsDao
    private lateinit var searchDao: SearchHistoryDao
    private lateinit var backupRepository: BackupRepository

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder<AppDatabase>(
                    context = context,
                    factory = { AppDatabase_Impl() },
                ).setDriver(BundledSQLiteDriver())
                .build()
        storyDao = db.storyDao()
        chapterDao = db.chapterDao()
        progressDao = db.readingProgressDao()
        statsDao = db.readingStatsDao()
        searchDao = db.searchHistoryDao()

        val fakeDataStore =
            object : DataStore<Preferences> {
                private val state = MutableStateFlow(preferencesOf())
                override val data: Flow<Preferences> = state

                override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                    val updated = transform(state.value)
                    state.value = updated
                    return updated
                }
            }
        val transactionRunner =
            object : DatabaseTransactionRunner {
                override suspend fun <R> withWriteTransaction(block: suspend () -> R): R = db.withWriteTransaction { block() }
            }

        backupRepository =
            BackupRepository(
                storyDao = storyDao,
                chapterDao = chapterDao,
                progressDao = progressDao,
                statsDao = statsDao,
                searchDao = searchDao,
                dataStore = fakeDataStore,
                transactionRunner = transactionRunner,
            )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun importBackup_doesNotCascadeDeleteExistingLocalChaptersOrReadingProgressWhenStoryAlreadyExists() =
        runBlocking {
            val existingStory =
                StoryEntity(
                    id = "s1",
                    slug = "s1",
                    title = "Truyện Gốc",
                    author = "Tác Giả",
                    genres = "Tiên Hiệp",
                    description = "Mô tả gốc",
                    url = "https://sstruyen.pics/s1",
                    totalChapters = 10,
                    coverImageUrl = "https://sstruyen.pics/cover-local.jpg",
                    isInLibrary = false,
                    addedTimestamp = 1000L,
                    sourceId = "sstruyen",
                    status = "Đang ra",
                    storyReplacementsEnabled = false,
                    storyCustomReplacements = "",
                )
            storyDao.insertStory(existingStory)

            // Chương 1 và Chương 2 đã tải trên máy (Chương 2 không nằm trong bản backup)
            chapterDao.insertChapters(
                listOf(
                    ChapterEntity(
                        storyId = "s1",
                        chapterNumber = 1,
                        title = "Chương 1 Local",
                        url = "https://sstruyen.pics/s1/c1",
                        content = "Nội dung chương 1 đã tải trên máy",
                        isDownloaded = true,
                        downloadedTimestamp = 2000L,
                    ),
                    ChapterEntity(
                        storyId = "s1",
                        chapterNumber = 2,
                        title = "Chương 2 Local",
                        url = "https://sstruyen.pics/s1/c2",
                        content = "Nội dung chương 2 chỉ có trên máy",
                        isDownloaded = true,
                        downloadedTimestamp = 3000L,
                    ),
                ),
            )
            progressDao.saveProgress(
                ReadingProgressEntity(
                    storyId = "s1",
                    lastChapterNumber = 2,
                    scrollPosition = 500,
                    lastReadTimestamp = 5000L,
                ),
            )

            // Bản backup có cùng story s1 nhưng chỉ có Chương 1 và tiến độ cũ hơn (timestamp = 1500L)
            val backupData =
                BackupData(
                    stories =
                        listOf(
                            StoryBackup(
                                id = "s1",
                                slug = "s1",
                                title = "Truyện Backup",
                                author = "Tác Giả",
                                genres = "Tiên Hiệp",
                                description = "Mô tả backup",
                                url = "https://sstruyen.pics/s1",
                                totalChapters = 15,
                                isInLibrary = true,
                                addedTimestamp = 4000L,
                                sourceId = "sstruyen",
                                status = "Đang ra",
                                storyReplacementsEnabled = true,
                                storyCustomReplacements = "a=b",
                            ),
                        ),
                    chapters =
                        listOf(
                            ChapterBackup(
                                storyId = "s1",
                                chapterNumber = 1,
                                title = "Chương 1 Backup",
                                url = "https://sstruyen.pics/s1/c1",
                                isDownloaded = true,
                                downloadedTimestamp = 1500L,
                                hasContent = true,
                            ),
                        ),
                    readingProgress =
                        listOf(
                            ProgressBackup(
                                storyId = "s1",
                                lastChapterNumber = 1,
                                scrollPosition = 100,
                                lastReadTimestamp = 1500L,
                            ),
                        ),
                )

            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("metadata.json"))
                zos.write(json.encodeToString(backupData).toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("chapters/s1/1.txt"))
                zos.write("Nội dung chương 1 từ backup".toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            backupRepository.importBackup(ByteArrayInputStream(baos.toByteArray()))

            // Kiểm tra: Chương 2 trên máy KHÔNG bị xóa do ON DELETE CASCADE, và Chương 1 giữ nguyên nội dung trên máy
            val ch1 = chapterDao.getChapter("s1", 1)
            val ch2 = chapterDao.getChapter("s1", 2)
            assertNotNull(ch1)
            assertNotNull(ch2)
            assertEquals("Nội dung chương 1 đã tải trên máy", ch1?.content)
            assertEquals("Nội dung chương 2 chỉ có trên máy", ch2?.content)

            // Tiến độ đọc mới hơn trên máy (timestamp 5000L) được giữ nguyên
            val progress = progressDao.getProgress("s1")
            assertNotNull(progress)
            assertEquals(2, progress?.lastChapterNumber)
            assertEquals(5000L, progress?.lastReadTimestamp)
        }
}
