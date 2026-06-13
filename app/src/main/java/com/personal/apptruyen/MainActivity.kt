package com.personal.apptruyen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.personal.apptruyen.ui.navigation.AppNavigation
import com.personal.apptruyen.ui.settings.AppThemeMode
import com.personal.apptruyen.ui.theme.AppTruyenTheme
import com.personal.apptruyen.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val themeViewModel: ThemeViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { /* granted or denied — either way, foreground service notification still works */ }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        setContent {
            val themeMode by themeViewModel.appThemeMode.collectAsState()
            val dynamicColor by themeViewModel.dynamicColor.collectAsState()
            val windowSizeClass = calculateWindowSizeClass(this)
            val isDark =
                when (themeMode) {
                    AppThemeMode.LIGHT -> false
                    AppThemeMode.DARK -> true
                    AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
            AppTruyenTheme(darkTheme = isDark, dynamicColor = dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavigation(windowSizeClass = windowSizeClass)
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
