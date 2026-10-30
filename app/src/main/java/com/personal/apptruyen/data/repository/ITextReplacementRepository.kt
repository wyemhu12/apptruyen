package com.personal.apptruyen.data.repository

import com.personal.apptruyen.data.local.StoryReplacementData
import com.personal.apptruyen.util.TextReplacementItem

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
        caseSensitive: Boolean = false,
    )

    suspend fun updateStoryReplacement(
        storyId: String,
        index: Int,
        from: String,
        to: String,
        caseSensitive: Boolean,
        expected: TextReplacementItem? = null,
    )

    suspend fun removeStoryReplacement(
        storyId: String,
        index: Int,
        expected: TextReplacementItem? = null,
    )

    suspend fun sendReplacementToGlobal(
        from: String,
        to: String,
    ): Boolean = sendReplacementToGlobal(from, to, caseSensitive = false)

    suspend fun sendReplacementToGlobal(
        from: String,
        to: String,
        caseSensitive: Boolean,
    ): Boolean
}
