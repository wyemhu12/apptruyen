package com.personal.apptruyen.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.personal.apptruyen.data.local.SettingsKeys
import com.personal.apptruyen.data.local.StoryDao
import com.personal.apptruyen.data.local.StoryReplacementData
import com.personal.apptruyen.util.TextReplacementHelper
import kotlinx.coroutines.flow.first
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
        ) {
            val current = storyDao.getStoryReplacements(storyId) ?: return
            val list =
                TextReplacementHelper
                    .parseCustomReplacements(
                        current.storyCustomReplacements,
                    ).toMutableList()
            list.add(from to to)
            val json = TextReplacementHelper.serializeCustomReplacements(list)
            storyDao.setStoryCustomReplacements(storyId, json)
        }

        override suspend fun removeStoryReplacement(
            storyId: String,
            index: Int,
        ) {
            val current = storyDao.getStoryReplacements(storyId) ?: return
            val list =
                TextReplacementHelper
                    .parseCustomReplacements(
                        current.storyCustomReplacements,
                    ).toMutableList()
            if (index in list.indices) {
                list.removeAt(index)
                val json = TextReplacementHelper.serializeCustomReplacements(list)
                storyDao.setStoryCustomReplacements(storyId, json)
            }
        }

        override suspend fun sendReplacementToGlobal(
            from: String,
            to: String,
        ): Boolean {
            val prefs = dataStore.data.first()
            val currentJson = prefs[SettingsKeys.CUSTOM_REPLACEMENTS]
            val currentList = TextReplacementHelper.parseCustomReplacements(currentJson)

            if (currentList.any { it.first == from && it.second == to }) {
                return false
            }

            val newList = currentList + (from to to)
            val newJson = TextReplacementHelper.serializeCustomReplacements(newList)
            dataStore.edit { it[SettingsKeys.CUSTOM_REPLACEMENTS] = newJson }
            return true
        }
    }
