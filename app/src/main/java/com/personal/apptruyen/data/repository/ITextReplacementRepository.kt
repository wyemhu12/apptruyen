package com.personal.apptruyen.data.repository

import com.personal.apptruyen.data.local.StoryReplacementData

/**
 * Interface cho text replacement operations.
 * Tách từ IStoryRepository để giảm coupling.
 */
interface ITextReplacementRepository {
    suspend fun getStoryReplacements(storyId: String): StoryReplacementData?

    suspend fun setStoryReplacementsEnabled(
        storyId: String,
        enabled: Boolean,
    )

    suspend fun addStoryReplacement(
        storyId: String,
        from: String,
        to: String,
    )

    suspend fun removeStoryReplacement(
        storyId: String,
        index: Int,
    )

    suspend fun sendReplacementToGlobal(
        from: String,
        to: String,
    ): Boolean
}
