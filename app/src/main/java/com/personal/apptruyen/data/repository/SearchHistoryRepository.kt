package com.personal.apptruyen.data.repository

import com.personal.apptruyen.data.local.SearchHistoryDao
import com.personal.apptruyen.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository cho search history.
 * Tách từ StoryRepository để giảm coupling.
 */
@Singleton
class SearchHistoryRepository
    @Inject
    constructor(
        private val searchHistoryDao: SearchHistoryDao,
    ) {

        fun getRecentSearches(limit: Int = 10): Flow<List<String>> =
            searchHistoryDao.getRecentSearches(limit).map { list ->
                list.map { it.query }
            }

        suspend fun saveSearch(query: String) {
            searchHistoryDao.insertSearch(SearchHistoryEntity(query = query))
        }

        suspend fun deleteSearch(query: String) {
            searchHistoryDao.deleteSearch(query)
        }

        suspend fun clearSearchHistory() {
            searchHistoryDao.clearAll()
        }
    }
