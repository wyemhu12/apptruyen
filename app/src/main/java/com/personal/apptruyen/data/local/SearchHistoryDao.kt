package com.personal.apptruyen.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.personal.apptruyen.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSearches(limit: Int = 10): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun deleteSearch(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()

    @Query("SELECT * FROM search_history")
    suspend fun getAllSearches(): List<SearchHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSearches(list: List<SearchHistoryEntity>)
}
