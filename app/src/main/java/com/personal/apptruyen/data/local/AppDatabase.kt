package com.personal.apptruyen.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.personal.apptruyen.data.local.entity.ChapterEntity
import com.personal.apptruyen.data.local.entity.ReadingProgressEntity
import com.personal.apptruyen.data.local.entity.ReadingStatsEntity
import com.personal.apptruyen.data.local.entity.SearchHistoryEntity
import com.personal.apptruyen.data.local.entity.StoryEntity

@Database(
    entities = [
        StoryEntity::class,
        ChapterEntity::class,
        ReadingProgressEntity::class,
        SearchHistoryEntity::class,
        ReadingStatsEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storyDao(): StoryDao

    abstract fun chapterDao(): ChapterDao

    abstract fun readingProgressDao(): ReadingProgressDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    abstract fun readingStatsDao(): ReadingStatsDao

    companion object {
        /** Migration v5→v6: Add reading_stats table */
        val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS reading_stats (
                            date TEXT NOT NULL PRIMARY KEY,
                            totalReadingTimeMs INTEGER NOT NULL DEFAULT 0,
                            chaptersRead INTEGER NOT NULL DEFAULT 0,
                            storiesRead INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent(),
                    )
                }
            }

        /** Migration v6→v7: Add sourceId column to stories table */
        val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE stories ADD COLUMN sourceId TEXT NOT NULL DEFAULT 'truyencom'")
                }
            }

        /** Migration v7→v8: Add status column to stories table */
        val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE stories ADD COLUMN status TEXT NOT NULL DEFAULT ''")
                }
            }

        /** Migration v8→v9: Add per-story text replacement columns */
        val MIGRATION_8_9 =
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE stories ADD COLUMN storyReplacementsEnabled INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE stories ADD COLUMN storyCustomReplacements TEXT NOT NULL DEFAULT ''")
                }
            }
    }
}
