package com.personal.apptruyen.ui.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.apptruyen.data.local.SettingsKeys
import com.personal.apptruyen.ui.settings.AppThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Global ViewModel — dùng ở MainActivity để quyết định dark/light theme.
 * M4: Sử dụng AppThemeMode enum thay vì magic numbers.
 */
@HiltViewModel
class ThemeViewModel
    @Inject
    constructor(
        dataStore: DataStore<Preferences>,
    ) : ViewModel() {

        val appThemeMode: StateFlow<AppThemeMode> =
            dataStore.data
                .map { prefs -> AppThemeMode.fromValue(prefs[SettingsKeys.APP_THEME_MODE] ?: 0) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, AppThemeMode.SYSTEM)

        val dynamicColor: StateFlow<Boolean> =
            dataStore.data
                .map { prefs -> prefs[SettingsKeys.DYNAMIC_COLOR] ?: true }
                .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    }
