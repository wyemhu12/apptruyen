package com.personal.apptruyen.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.personal.apptruyen.data.local.SettingsKeys
import com.personal.apptruyen.data.local.StoryDao
import com.personal.apptruyen.data.local.StoryReplacementData
import com.personal.apptruyen.util.TextReplacementHelper
import com.personal.apptruyen.util.TextReplacementItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository cho per-story + global text replacement.
 * Tách từ StoryRepository để giảm coupling.
 */
@Singleton
class TextReplacementRepository
    @Inject
    constructor(
        private val storyDao: StoryDao,
        private val dataStore: DataStore<Preferences>,
    ) : ITextReplacementRepository {

        private val storyReplacementMutex = Mutex()

        override suspend fun getStoryReplacements(storyId: String): StoryReplacementData? = storyDao.getStoryReplacements(storyId)

        override suspend fun setStoryReplacementsEnabled(
            storyId: String,
            enabled: Boolean,
        ) {
            storyDao.setStoryReplacementsEnabled(storyId, enabled)
        }

        override suspend fun addStoryReplacement(
            storyId: String,
            from: String,
            to: String,
            caseSensitive: Boolean,
        ) {
            if (from.isBlank()) return
            storyReplacementMutex.withLock {
                val current = storyDao.getStoryReplacements(storyId) ?: return
                val list =
                    TextReplacementHelper
                        .parseReplacementItems(
                            current.storyCustomReplacements,
                        ).toMutableList()
                list.add(TextReplacementItem(from = from, to = to, caseSensitive = caseSensitive))
                val json = TextReplacementHelper.serializeReplacementItems(list)
                storyDao.setStoryCustomReplacements(storyId, json)
            }
        }

        override suspend fun updateStoryReplacement(
            storyId: String,
            index: Int,
            from: String,
            to: String,
            caseSensitive: Boolean,
            expected: TextReplacementItem?,
        ) {
            if (from.isBlank()) return
            storyReplacementMutex.withLock {
                val current = storyDao.getStoryReplacements(storyId) ?: return
                val list =
                    TextReplacementHelper
                        .parseReplacementItems(
                            current.storyCustomReplacements,
                        ).toMutableList()
                if (index in list.indices && (expected == null || list[index] == expected)) {
                    list[index] = TextReplacementItem(from = from, to = to, caseSensitive = caseSensitive)
                    val json = TextReplacementHelper.serializeReplacementItems(list)
                    storyDao.setStoryCustomReplacements(storyId, json)
                }
            }
        }

        override suspend fun removeStoryReplacement(
            storyId: String,
            index: Int,
            expected: TextReplacementItem?,
        ) {
            storyReplacementMutex.withLock {
                val current = storyDao.getStoryReplacements(storyId) ?: return
                val list =
                    TextReplacementHelper
                        .parseReplacementItems(
                            current.storyCustomReplacements,
                        ).toMutableList()
                if (index in list.indices && (expected == null || list[index] == expected)) {
                    list.removeAt(index)
                    val json = TextReplacementHelper.serializeReplacementItems(list)
                    storyDao.setStoryCustomReplacements(storyId, json)
                }
            }
        }

        override suspend fun sendReplacementToGlobal(
            from: String,
            to: String,
        ): Boolean = sendReplacementToGlobal(from, to, caseSensitive = false)

        override suspend fun sendReplacementToGlobal(
            from: String,
            to: String,
            caseSensitive: Boolean,
        ): Boolean {
            if (from.isBlank()) return false
            var modified = false
            dataStore.edit { prefs ->
                val currentJson = prefs[SettingsKeys.CUSTOM_REPLACEMENTS]
                val currentList = TextReplacementHelper.parseReplacementItems(currentJson).toMutableList()
                val existingIndex = currentList.indexOfFirst { it.from == from && it.to == to }

                if (existingIndex >= 0) {
                    if (currentList[existingIndex].caseSensitive != caseSensitive) {
                        currentList[existingIndex] = TextReplacementItem(from = from, to = to, caseSensitive = caseSensitive)
                        prefs[SettingsKeys.CUSTOM_REPLACEMENTS] = TextReplacementHelper.serializeReplacementItems(currentList)
                        modified = true
                    }
                } else {
                    currentList.add(TextReplacementItem(from = from, to = to, caseSensitive = caseSensitive))
                    prefs[SettingsKeys.CUSTOM_REPLACEMENTS] = TextReplacementHelper.serializeReplacementItems(currentList)
                    modified = true
                }
            }
            return modified
        }
    }
