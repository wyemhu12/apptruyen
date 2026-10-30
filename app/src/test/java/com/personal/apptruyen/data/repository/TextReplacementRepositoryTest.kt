package com.personal.apptruyen.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import com.personal.apptruyen.data.local.SettingsKeys
import com.personal.apptruyen.data.local.StoryDao
import com.personal.apptruyen.data.local.StoryReplacementData
import com.personal.apptruyen.util.TextReplacementHelper
import com.personal.apptruyen.util.TextReplacementItem
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TextReplacementRepositoryTest {

    private lateinit var storyDao: StoryDao
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: TextReplacementRepository

    private var savedStoryJson: String = "[]"
    private val prefsFlow = MutableStateFlow<Preferences>(preferencesOf())

    @BeforeEach
    fun setup() {
        storyDao = mockk(relaxed = true)
        dataStore = mockk(relaxed = true)
        savedStoryJson = "[]"
        prefsFlow.value = preferencesOf()

        coEvery { storyDao.getStoryReplacements("story-1") } answers {
            StoryReplacementData(
                storyReplacementsEnabled = true,
                storyCustomReplacements = savedStoryJson,
            )
        }
        coEvery { storyDao.setStoryCustomReplacements("story-1", any()) } answers {
            savedStoryJson = secondArg()
        }

        every { dataStore.data } returns prefsFlow
        coEvery { dataStore.updateData(any()) } coAnswers {
            val transform = firstArg<suspend (Preferences) -> Preferences>()
            val updated = transform(prefsFlow.value)
            prefsFlow.value = updated
            updated
        }

        repository = TextReplacementRepository(storyDao, dataStore)
    }

    @Test
    fun `add update remove story replacements preserve caseSensitive and concurrent updates are serialized`() =
        runTest {
            repository.addStoryReplacement("story-1", "Mây tía", "Tử Vân", caseSensitive = false)
            repository.addStoryReplacement("story-1", "Tu Liên", "tu tiên", caseSensitive = false)
            repository.addStoryReplacement("story-1", "   ", "bỏ qua", caseSensitive = true)

            val initialItems = TextReplacementHelper.parseReplacementItems(savedStoryJson)
            assertEquals(
                listOf(
                    TextReplacementItem("Mây tía", "Tử Vân", caseSensitive = false),
                    TextReplacementItem("Tu Liên", "tu tiên", caseSensitive = false),
                ),
                initialItems,
            )

            // Concurrent updates on index 0 and index 1 must both be preserved via storyReplacementMutex
            val job1 =
                async {
                    repository.updateStoryReplacement(
                        storyId = "story-1",
                        index = 0,
                        from = "Mây tía",
                        to = "Tử Vân",
                        caseSensitive = true,
                        expected = initialItems[0],
                    )
                }
            val job2 =
                async {
                    repository.updateStoryReplacement(
                        storyId = "story-1",
                        index = 1,
                        from = "Tu Liên",
                        to = "tu tiên",
                        caseSensitive = true,
                        expected = initialItems[1],
                    )
                }
            job1.await()
            job2.await()

            val updatedItems = TextReplacementHelper.parseReplacementItems(savedStoryJson)
            assertEquals(
                listOf(
                    TextReplacementItem("Mây tía", "Tử Vân", caseSensitive = true),
                    TextReplacementItem("Tu Liên", "tu tiên", caseSensitive = true),
                ),
                updatedItems,
            )

            // Remove index 0, then attempt stale update on index 1 with old expected item at index 1
            repository.removeStoryReplacement("story-1", 0, expected = updatedItems[0])
            repository.updateStoryReplacement(
                storyId = "story-1",
                index = 1,
                from = "Ghi đè sai",
                to = "Lỗi",
                caseSensitive = false,
                expected = updatedItems[1],
            )

            val afterRemove = TextReplacementHelper.parseReplacementItems(savedStoryJson)
            assertEquals(
                listOf(TextReplacementItem("Tu Liên", "tu tiên", caseSensitive = true)),
                afterRemove,
            )
        }

    @Test
    fun `sendReplacementToGlobal adds new item rejects duplicate and updates caseSensitive in place`() =
        runTest {
            assertTrue(repository.sendReplacementToGlobal("Mây tía", "Tử Vân", caseSensitive = false))
            assertFalse(repository.sendReplacementToGlobal("Mây tía", "Tử Vân", caseSensitive = false))

            // Same (from, to) but different caseSensitive -> updates in place and returns true
            assertTrue(repository.sendReplacementToGlobal("Mây tía", "Tử Vân", caseSensitive = true))

            val stored =
                TextReplacementHelper.parseReplacementItems(
                    prefsFlow.value[SettingsKeys.CUSTOM_REPLACEMENTS],
                )
            assertEquals(
                listOf(TextReplacementItem("Mây tía", "Tử Vân", caseSensitive = true)),
                stored,
            )
        }
}
