package com.personal.apptruyen.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import com.personal.apptruyen.data.local.SettingsKeys
import com.personal.apptruyen.data.repository.BackupRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var backupRepository: BackupRepository
    private lateinit var viewModel: SettingsViewModel
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(dispatcher)
        dataStore = mockk(relaxed = true)
        backupRepository = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `default state has correct values`() =
        runTest {
            // Empty prefs → defaults
            val prefs = preferencesOf()
            every { dataStore.data } returns flowOf(prefs)

            viewModel = SettingsViewModel(dataStore, backupRepository)
            val state = viewModel.state.first()

            assertEquals(1.0f, state.ttsSpeed, 0.01f)
            assertEquals(1.0f, state.ttsPitch, 0.01f)
            assertEquals(18f, state.fontSize, 0.01f)
            assertEquals(ReaderThemeOption.LIGHT, state.theme)
            assertEquals(AppThemeMode.SYSTEM, state.appThemeMode)
            assertTrue(state.dynamicColor)
            assertTrue(state.textReplacementEnabled)
            assertEquals(0, state.prefetchChapters)
        }

    @Test
    fun `state reads custom values from datastore`() =
        runTest {
            val customPrefs =
                preferencesOf(
                    SettingsKeys.TTS_SPEED to 1.5f,
                    SettingsKeys.TTS_PITCH to 0.8f,
                    SettingsKeys.FONT_SIZE to 24f,
                    SettingsKeys.THEME to 2, // SEPIA
                    SettingsKeys.APP_THEME_MODE to 2, // DARK
                    SettingsKeys.DYNAMIC_COLOR to false,
                    SettingsKeys.TEXT_REPLACEMENT_ENABLED to false,
                    SettingsKeys.PREFETCH_CHAPTERS to 5,
                )
            every { dataStore.data } returns flowOf(customPrefs)

            viewModel = SettingsViewModel(dataStore, backupRepository)
            val state = viewModel.state.first()

            assertEquals(1.5f, state.ttsSpeed, 0.01f)
            assertEquals(0.8f, state.ttsPitch, 0.01f)
            assertEquals(24f, state.fontSize, 0.01f)
            assertEquals(ReaderThemeOption.SEPIA, state.theme)
            assertEquals(AppThemeMode.DARK, state.appThemeMode)
            assertFalse(state.dynamicColor)
            assertFalse(state.textReplacementEnabled)
            assertEquals(5, state.prefetchChapters)
        }

    @Test
    fun `SettingsState default constructor has correct defaults`() {
        val defaultState = SettingsViewModel.SettingsState()
        assertEquals(1.0f, defaultState.ttsSpeed, 0.01f)
        assertEquals(18f, defaultState.fontSize, 0.01f)
        assertEquals(AppThemeMode.SYSTEM, defaultState.appThemeMode)
        assertTrue(defaultState.dynamicColor)
    }
}
