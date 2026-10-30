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
    private lateinit var authRepository: com.personal.apptruyen.data.repository.ISourceAuthRepository
    private lateinit var viewModel: SettingsViewModel
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(dispatcher)
        dataStore = mockk(relaxed = true)
        backupRepository = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        coEvery { authRepository.isLoggedIn(any()) } returns false
        every { authRepository.getAuthState(any()) } answers {
            kotlinx.coroutines.flow.MutableStateFlow(authRepository.isLoggedIn(firstArg()))
        }
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

            viewModel = SettingsViewModel(dataStore, backupRepository, authRepository)
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

            viewModel = SettingsViewModel(dataStore, backupRepository, authRepository)
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
    fun `checkAuthStatus updates isWikidichLoggedIn in state`() =
        runTest {
            coEvery { authRepository.isLoggedIn("wikidich") } returns true
            every { dataStore.data } returns flowOf(preferencesOf())

            viewModel = SettingsViewModel(dataStore, backupRepository, authRepository)
            advanceUntilIdle()

            val state = viewModel.state.first()
            assertTrue(state.isWikidichLoggedIn)
        }

    @Test
    fun `SettingsState default constructor has correct defaults`() {
        val defaultState = SettingsViewModel.SettingsState()
        assertEquals(1.0f, defaultState.ttsSpeed, 0.01f)
        assertEquals(18f, defaultState.fontSize, 0.01f)
        assertEquals(AppThemeMode.SYSTEM, defaultState.appThemeMode)
        assertTrue(defaultState.dynamicColor)
    }

    @Test
    fun `mapBackupErrorMessage hides raw English exception message and returns Vietnamese error`() =
        runTest {
            every { dataStore.data } returns flowOf(preferencesOf())
            viewModel = SettingsViewModel(dataStore, backupRepository, authRepository)

            val rawException = java.io.IOException("Unexpected EOF at offset 128 in stream")
            assertEquals(
                "Không thể khôi phục dữ liệu. Tệp sao lưu không hợp lệ hoặc bị hỏng.",
                viewModel.mapBackupErrorMessage(isExport = false, error = rawException),
            )

            val vietnameseIllegalArg = IllegalArgumentException("Tệp sao lưu không hợp lệ: thiếu metadata.json")
            assertEquals(
                "Tệp sao lưu không hợp lệ: thiếu metadata.json",
                viewModel.mapBackupErrorMessage(isExport = false, error = vietnameseIllegalArg),
            )
        }

    @Test
    fun `enabledSources filters out legacy source IDs and toggleSource protects last active source`() =
        runTest {
            val prefsFlow =
                kotlinx.coroutines.flow.MutableStateFlow<Preferences>(
                    preferencesOf(SettingsKeys.ENABLED_SOURCES to "truyencom,truyenfull,tangthuvien,sstruyen"),
                )
            every { dataStore.data } returns prefsFlow
            coEvery { dataStore.updateData(any()) } coAnswers {
                val transform = firstArg<suspend (Preferences) -> Preferences>()
                val updated = transform(prefsFlow.value)
                prefsFlow.value = updated
                updated
            }

            viewModel = SettingsViewModel(dataStore, backupRepository, authRepository)
            val initialState = viewModel.state.first()
            assertEquals(setOf("sstruyen"), initialState.enabledSources)
            assertEquals(1, initialState.enabledSources.size)

            // Trying to disable the last remaining source must keep it enabled
            viewModel.toggleSource("sstruyen")
            advanceUntilIdle()
            assertEquals(setOf("sstruyen"), viewModel.state.first().enabledSources)

            // Switch to wikidich first, then verify fallback to DEFAULT_SINGLE_SOURCE ("sstruyen") when prefs only contain legacy IDs
            prefsFlow.value = preferencesOf(SettingsKeys.ENABLED_SOURCES to "wikidich")
            advanceUntilIdle()
            assertEquals(setOf("wikidich"), viewModel.state.first().enabledSources)

            prefsFlow.value = preferencesOf(SettingsKeys.ENABLED_SOURCES to "truyencom,truyenfull,tangthuvien")
            advanceUntilIdle()
            assertEquals(setOf("sstruyen"), viewModel.state.first().enabledSources)
            assertEquals(
                1,
                viewModel.state
                    .first()
                    .enabledSources.size,
            )
        }

    @Test
    fun `resolveSingleSource normalizes null blank multi-source CSV legacy IDs and whitespace to single active source`() {
        assertEquals(
            "sstruyen",
            com.personal.apptruyen.data.local.SourceIds
                .resolveSingleSource(null),
        )
        assertEquals(
            "sstruyen",
            com.personal.apptruyen.data.local.SourceIds
                .resolveSingleSource(""),
        )
        assertEquals(
            "sstruyen",
            com.personal.apptruyen.data.local.SourceIds
                .resolveSingleSource("  "),
        )
        assertEquals(
            "sstruyen",
            com.personal.apptruyen.data.local.SourceIds
                .resolveSingleSource("truyenfull,truyencom,tangthuvien"),
        )
        assertEquals(
            "sstruyen",
            com.personal.apptruyen.data.local.SourceIds
                .resolveSingleSource(" sstruyen , wikidich "),
        )
        assertEquals(
            "wikidich",
            com.personal.apptruyen.data.local.SourceIds
                .resolveSingleSource("wikidich,tangthuvien"),
        )
    }

    @Test
    fun `selectSource switches exclusively to selected source and ignores invalid sourceId`() =
        runTest {
            val prefsFlow =
                kotlinx.coroutines.flow.MutableStateFlow<Preferences>(
                    preferencesOf(SettingsKeys.ENABLED_SOURCES to "sstruyen"),
                )
            every { dataStore.data } returns prefsFlow
            coEvery { dataStore.updateData(any()) } coAnswers {
                val transform = firstArg<suspend (Preferences) -> Preferences>()
                val updated = transform(prefsFlow.value)
                prefsFlow.value = updated
                updated
            }

            viewModel = SettingsViewModel(dataStore, backupRepository, authRepository)
            assertEquals(setOf("sstruyen"), viewModel.state.first().enabledSources)

            viewModel.selectSource("wikidich")
            advanceUntilIdle()
            assertEquals(setOf("wikidich"), viewModel.state.first().enabledSources)
            assertEquals(
                1,
                viewModel.state
                    .first()
                    .enabledSources.size,
            )

            viewModel.selectSource("invalid")
            advanceUntilIdle()
            assertEquals(setOf("wikidich"), viewModel.state.first().enabledSources)
            assertEquals(
                1,
                viewModel.state
                    .first()
                    .enabledSources.size,
            )

            viewModel.selectSource("tangthuvien")
            advanceUntilIdle()
            assertEquals(setOf("wikidich"), viewModel.state.first().enabledSources)
            assertEquals(
                1,
                viewModel.state
                    .first()
                    .enabledSources.size,
            )
        }

    @Test
    fun `observing authRepository getAuthState updates isWikidichLoggedIn reactively`() =
        runTest {
            val authFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
            every { authRepository.getAuthState("wikidich") } returns authFlow
            coEvery { authRepository.isLoggedIn("wikidich") } answers { authFlow.value }
            every { dataStore.data } returns flowOf(preferencesOf())

            viewModel = SettingsViewModel(dataStore, backupRepository, authRepository)
            assertFalse(viewModel.state.first().isWikidichLoggedIn)

            authFlow.value = true
            advanceUntilIdle()
            assertTrue(viewModel.state.first().isWikidichLoggedIn)

            authFlow.value = false
            advanceUntilIdle()
            assertFalse(viewModel.state.first().isWikidichLoggedIn)
        }

    @Test
    fun `add update remove custom replacements persist caseSensitive and guard against stale expected item`() =
        runTest {
            val prefsFlow =
                kotlinx.coroutines.flow.MutableStateFlow<Preferences>(
                    preferencesOf(),
                )
            every { dataStore.data } returns prefsFlow
            coEvery { dataStore.updateData(any()) } coAnswers {
                val transform = firstArg<suspend (Preferences) -> Preferences>()
                val updated = transform(prefsFlow.value)
                prefsFlow.value = updated
                updated
            }

            viewModel = SettingsViewModel(dataStore, backupRepository, authRepository)
            val itemA =
                com.personal.apptruyen.util
                    .TextReplacementItem("Mây tía", "Tử Vân", false)
            val itemB =
                com.personal.apptruyen.util
                    .TextReplacementItem("Tu Liên", "tu tiên", false)
            val itemC =
                com.personal.apptruyen.util
                    .TextReplacementItem("nàn", "nàng", true)

            viewModel.addCustomReplacement(itemA.from, itemA.to, itemA.caseSensitive)
            viewModel.addCustomReplacement(itemB.from, itemB.to, itemB.caseSensitive)
            viewModel.addCustomReplacement(itemC.from, itemC.to, itemC.caseSensitive)
            viewModel.addCustomReplacement("   ", "bỏ qua", false)
            advanceUntilIdle()

            assertEquals(
                listOf(itemA, itemB, itemC),
                viewModel.state.first().customReplacements,
            )

            // TOCTOU simulation: remove itemA at index 0, then attempt stale update on index 1 expecting itemB
            // (after removing index 0, index 1 is now itemC, so stale update must be safely ignored)
            viewModel.removeCustomReplacement(0, expected = itemA)
            viewModel.updateCustomReplacement(
                index = 1,
                from = "Tu Liên",
                to = "tu tiên",
                caseSensitive = true,
                expected = itemB,
            )
            advanceUntilIdle()

            assertEquals(
                listOf(itemB, itemC),
                viewModel.state.first().customReplacements,
            )

            // Valid update on index 0 expecting itemB
            viewModel.updateCustomReplacement(
                index = 0,
                from = "Tu Liên",
                to = "tu tiên",
                caseSensitive = true,
                expected = itemB,
            )
            advanceUntilIdle()

            assertEquals(
                listOf(
                    com.personal.apptruyen.util
                        .TextReplacementItem("Tu Liên", "tu tiên", true),
                    itemC,
                ),
                viewModel.state.first().customReplacements,
            )
        }
}
