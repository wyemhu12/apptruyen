package com.personal.apptruyen.data.local

import androidx.room3.Room
import androidx.room3.testing.MigrationTestHelper
import androidx.room3.withWriteTransaction
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.personal.apptruyen.data.local.entity.ChapterEntity
import com.personal.apptruyen.data.local.entity.ReadingProgressEntity
import com.personal.apptruyen.data.local.entity.StoryEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbFile = File(context.cacheDir, "migration_test_apptruyen.db")

    @get:Rule
    val migrationHelper =
        MigrationTestHelper(
            instrumentation = InstrumentationRegistry.getInstrumentation(),
            file = dbFile,
            driver = BundledSQLiteDriver(),
            databaseClass = AppDatabase::class,
        )

    @After
    fun tearDown() {
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    @Test
    fun migrate5To9_preservesDataAndAppliesAllSchemaChanges() =
        runBlocking {
            val connV5 = migrationHelper.createDatabase(5)
            connV5.execSQL(
                """
                INSERT INTO stories (id, slug, title, author, genres, description, url, totalChapters, coverImageUrl, isInLibrary, addedTimestamp)
                VALUES ('tien-nghich', 'tien-nghich', 'Tiên Nghịch', 'Nhĩ Căn', 'Tiên Hiệp', 'Mô tả', 'https://sstruyen.pics/truyen/tien-nghich', 100, '', 1, 1000)
                """.trimIndent(),
            )
            connV5.close()

            val connV9 =
                migrationHelper.runMigrationsAndValidate(
                    version = 9,
                    migrations =
                        listOf(
                            AppDatabase.MIGRATION_5_6,
                            AppDatabase.MIGRATION_6_7,
                            AppDatabase.MIGRATION_7_8,
                            AppDatabase.MIGRATION_8_9,
                        ),
                )

            connV9
                .prepare(
                    "SELECT sourceId, status, storyReplacementsEnabled, storyCustomReplacements FROM stories WHERE id = 'tien-nghich'",
                ).use { stmt ->
                    assertTrue(stmt.step())
                    assertEquals("truyencom", stmt.getText(0))
                    assertEquals("", stmt.getText(1))
                    assertEquals(0L, stmt.getLong(2))
                    assertEquals("", stmt.getText(3))
                }

            connV9.prepare("SELECT COUNT(*) FROM reading_stats").use { stmt ->
                assertTrue(stmt.step())
                assertEquals(0L, stmt.getLong(0))
            }
            connV9.close()
        }

    @Test
    fun withWriteTransaction_rollsBackAllDeletionsWhenExceptionThrownMidTransaction() =
        runBlocking {
            val inMemoryDb =
                Room
                    .inMemoryDatabaseBuilder<AppDatabase>(
                        context = context,
                        factory = { AppDatabase_Impl() },
                    ).setDriver(BundledSQLiteDriver())
                    .build()

            try {
                val storyId = "rollback-story"
                val storyDao = inMemoryDb.storyDao()
                val chapterDao = inMemoryDb.chapterDao()
                val progressDao = inMemoryDb.readingProgressDao()

                storyDao.insertStory(
                    StoryEntity(
                        id = storyId,
                        slug = storyId,
                        title = "Truyện Kiểm Thử Rollback",
                        url = "https://sstruyen.pics/truyen/rollback",
                        isInLibrary = true,
                    ),
                )
                chapterDao.insertChapter(
                    ChapterEntity(
                        storyId = storyId,
                        chapterNumber = 1,
                        title = "Chương 1",
                        url = "https://sstruyen.pics/doc-truyen/rollback-chuong-1",
                        content = "Nội dung chương 1",
                        isDownloaded = true,
                    ),
                )
                progressDao.saveProgress(
                    ReadingProgressEntity(
                        storyId = storyId,
                        lastChapterNumber = 1,
                        scrollPosition = 20,
                    ),
                )

                var caught = false
                try {
                    inMemoryDb.withWriteTransaction {
                        progressDao.deleteProgress(storyId)
                        chapterDao.deleteChaptersByStory(storyId)
                        throw IllegalStateException("Giả lập lỗi trước khi xóa story")
                    }
                } catch (_: IllegalStateException) {
                    caught = true
                }

                assertTrue(caught)
                assertNotNull(storyDao.getStoryById(storyId))
                assertNotNull(chapterDao.getChapter(storyId, 1))
                assertNotNull(progressDao.getProgress(storyId))
            } finally {
                inMemoryDb.close()
            }
        }
}
