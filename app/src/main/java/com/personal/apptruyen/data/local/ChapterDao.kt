package com.personal.apptruyen.data.local

import androidx.room3.*
import com.personal.apptruyen.data.local.entity.ChapterEntity
import com.personal.apptruyen.data.local.entity.DownloadStats
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query(
        "SELECT storyId, chapterNumber, title, url, NULL AS content, isDownloaded, downloadedTimestamp FROM chapters WHERE storyId = :storyId ORDER BY chapterNumber ASC",
    )
    fun getChaptersByStory(storyId: String): Flow<List<ChapterEntity>>

    @Query(
        "SELECT storyId, chapterNumber, title, url, NULL AS content, isDownloaded, downloadedTimestamp FROM chapters WHERE storyId = :storyId ORDER BY chapterNumber ASC",
    )
    suspend fun getChaptersByStoryOnce(storyId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE storyId = :storyId AND chapterNumber = :chapterNumber")
    suspend fun getChapter(
        storyId: String,
        chapterNumber: Int,
    ): ChapterEntity?

    @Query(
        "SELECT storyId, chapterNumber, title, url, NULL AS content, isDownloaded, downloadedTimestamp FROM chapters WHERE storyId = :storyId AND isDownloaded = 1 ORDER BY chapterNumber ASC",
    )
    fun getDownloadedChapters(storyId: String): Flow<List<ChapterEntity>>

    @Query(
        """
        SELECT * FROM chapters 
        WHERE storyId = :storyId AND isDownloaded = 1 AND content IS NOT NULL AND TRIM(content) != '' 
        ORDER BY chapterNumber ASC 
        LIMIT :limit OFFSET :offset
    """,
    )
    suspend fun getDownloadedChaptersWithContentPaged(
        storyId: String,
        limit: Int,
        offset: Int,
    ): List<ChapterEntity>

    @Query("SELECT COUNT(*) FROM chapters WHERE storyId = :storyId AND isDownloaded = 1")
    fun getDownloadedChapterCount(storyId: String): Flow<Int>

    @Query("SELECT SUM(LENGTH(content)) FROM chapters WHERE storyId = :storyId AND isDownloaded = 1")
    fun getEstimatedSize(storyId: String): Flow<Long?>

    /**
     * Aggregate query: download count + total size per story in a single query.
     * Replaces N+1 pattern of getDownloadedChapterCount + getEstimatedSize per story.
     */
    @Query(
        """
        SELECT storyId, COUNT(*) as downloadedCount, 
               COALESCE(SUM(LENGTH(content)), 0) as totalSize
        FROM chapters 
        WHERE isDownloaded = 1 
        GROUP BY storyId
    """,
    )
    fun getDownloadStats(): Flow<List<DownloadStats>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChaptersIgnore(chapters: List<ChapterEntity>)

    @Query(
        """
        UPDATE chapters SET title = :title, url = :url 
        WHERE storyId = :storyId AND chapterNumber = :chapterNumber
    """,
    )
    suspend fun updateChapterMetadata(
        storyId: String,
        chapterNumber: Int,
        title: String,
        url: String,
    )

    /**
     * Save chapter content with UPSERT: insert row if not exists, then update content.
     * Đảm bảo content, title và url không bị mất nếu chapter row chưa tồn tại.
     */
    @Transaction
    suspend fun saveChapterContent(
        storyId: String,
        chapterNumber: Int,
        content: String,
        timestamp: Long = System.currentTimeMillis(),
        title: String = "Chương $chapterNumber",
        url: String = "",
    ) {
        val safeTitle = title.ifBlank { "Chương $chapterNumber" }
        // Ensure row exists (IGNORE if already present)
        insertChapterIfNotExists(
            ChapterEntity(
                storyId = storyId,
                chapterNumber = chapterNumber,
                title = safeTitle,
                url = url,
            ),
        )
        // Update content and fill blank metadata if needed
        updateChapterContent(
            storyId = storyId,
            chapterNumber = chapterNumber,
            content = content,
            timestamp = timestamp,
            title = safeTitle,
            url = url,
        )
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChapterIfNotExists(entity: ChapterEntity)

    @Query(
        """
        UPDATE chapters 
        SET content = :content,
            isDownloaded = 1,
            downloadedTimestamp = :timestamp,
            title = CASE WHEN TRIM(title) = '' THEN :title ELSE title END,
            url = CASE WHEN TRIM(url) = '' THEN :url ELSE url END
        WHERE storyId = :storyId AND chapterNumber = :chapterNumber
    """,
    )
    suspend fun updateChapterContent(
        storyId: String,
        chapterNumber: Int,
        content: String,
        timestamp: Long = System.currentTimeMillis(),
        title: String = "",
        url: String = "",
    )

    @Query(
        """
        UPDATE chapters 
        SET content = :content, isDownloaded = 1, downloadedTimestamp = :timestamp 
        WHERE storyId = :storyId AND chapterNumber = :chapterNumber 
          AND (isDownloaded = 0 OR content IS NULL OR content = '')
    """,
    )
    suspend fun restoreChapterContentIfEmpty(
        storyId: String,
        chapterNumber: Int,
        content: String,
        timestamp: Long,
    )

    @Query(
        "UPDATE chapters SET content = NULL, isDownloaded = 0, downloadedTimestamp = NULL WHERE storyId = :storyId AND chapterNumber = :chapterNumber",
    )
    suspend fun deleteChapterContent(
        storyId: String,
        chapterNumber: Int,
    )

    @Query("UPDATE chapters SET content = NULL, isDownloaded = 0, downloadedTimestamp = NULL WHERE storyId = :storyId")
    suspend fun deleteAllChapterContent(storyId: String)

    @Query("DELETE FROM chapters WHERE storyId = :storyId")
    suspend fun deleteChaptersByStory(storyId: String)

    @Query(
        "SELECT storyId, chapterNumber, title, url, NULL AS content, isDownloaded, downloadedTimestamp FROM chapters WHERE storyId = :storyId ORDER BY chapterNumber ASC LIMIT :limit OFFSET :offset",
    )
    suspend fun getChaptersPaged(
        storyId: String,
        limit: Int,
        offset: Int,
    ): List<ChapterEntity>

    @Query("SELECT COUNT(*) FROM chapters WHERE storyId = :storyId")
    suspend fun getChapterCount(storyId: String): Int

    @Query(
        "SELECT storyId, chapterNumber, title, url, NULL AS content, isDownloaded, downloadedTimestamp FROM chapters WHERE storyId = :storyId AND chapterNumber IN (:chapterNumbers) ORDER BY chapterNumber ASC",
    )
    suspend fun getChaptersByNumbers(
        storyId: String,
        chapterNumbers: List<Int>,
    ): List<ChapterEntity>
}
