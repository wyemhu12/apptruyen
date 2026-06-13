package com.personal.apptruyen.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.personal.apptruyen.ui.detail.StoryDetailScreen
import com.personal.apptruyen.ui.downloads.DownloadsScreen
import com.personal.apptruyen.ui.explore.ExploreScreen
import com.personal.apptruyen.ui.home.HomeScreen
import com.personal.apptruyen.ui.reader.ReaderScreen
import com.personal.apptruyen.ui.search.SearchScreen
import com.personal.apptruyen.ui.settings.SettingsScreen
import com.personal.apptruyen.ui.splash.SplashScreen
import com.personal.apptruyen.ui.stats.ReadingStatsScreen

sealed class Screen(
    val route: String,
) {
    data object Splash : Screen("splash")

    data object Home : Screen("home")

    data object Explore : Screen("explore")

    data object Search : Screen("search?query={query}") {
        fun createRoute(query: String = "") = "search?query=$query"
    }

    data object StoryDetail : Screen("story/{storyId}/{storyUrl}") {
        fun createRoute(
            storyId: String,
            storyUrl: String,
        ): String {
            val encoded = NavigationUtils.encodeUrl(storyUrl)
            return "story/$storyId/$encoded"
        }

        fun decodeUrl(encoded: String): String = NavigationUtils.decodeUrl(encoded)
    }

    data object Reader : Screen("reader/{storyId}/{chapterNumber}/{chapterUrl}") {
        fun createRoute(
            storyId: String,
            chapterNumber: Int,
            chapterUrl: String,
        ): String {
            val encoded = NavigationUtils.encodeUrl(chapterUrl)
            return "reader/$storyId/$chapterNumber/$encoded"
        }

        fun decodeUrl(encoded: String): String = NavigationUtils.decodeUrl(encoded)
    }

    data object Downloads : Screen("downloads")

    data object Settings : Screen("settings")

    data object ReadingStats : Screen("reading_stats")
}

data class BottomNavItem(
    val label: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: String,
)

@Composable
fun AppNavigation(windowSizeClass: WindowSizeClass? = null) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isExpandedScreen = windowSizeClass?.widthSizeClass != WindowWidthSizeClass.Compact

    val bottomNavItems =
        listOf(
            BottomNavItem("Trang chủ", Icons.Filled.Home, Icons.Outlined.Home, Screen.Home.route),
            BottomNavItem("Khám Phá", Icons.Filled.Explore, Icons.Outlined.Explore, Screen.Explore.route),
            BottomNavItem("Đã tải", Icons.Filled.Download, Icons.Outlined.Download, Screen.Downloads.route),
            BottomNavItem("Cài đặt", Icons.Filled.Settings, Icons.Outlined.Settings, Screen.Settings.route),
        )

    // Show bottom bar only on main screens
    val showBottomBar =
        currentRoute in
            listOf(
                Screen.Home.route,
                Screen.Explore.route,
                Screen.Downloads.route,
                Screen.Settings.route,
            )

    // Adaptive layout: NavigationRail for expanded screens, NavigationBar for compact
    if (isExpandedScreen && showBottomBar) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight(),
            ) {
                bottomNavItems.forEach { item ->
                    val selected = currentRoute == item.route
                    NavigationRailItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label,
                            )
                        },
                        label = { Text(item.label) },
                    )
                }
            }
            AppNavContent(navController, padding = null, bottomNavItems, showBottomBar = false)
        }
    } else {
        Scaffold(
            bottomBar = {
                AnimatedVisibility(
                    visible = showBottomBar,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                ) {
                    NavigationBar(
                        tonalElevation = 4.dp,
                        modifier =
                            Modifier
                                .shadow(8.dp, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                    ) {
                        bottomNavItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(Screen.Home.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label,
                                    )
                                },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            AppNavContent(navController, padding, bottomNavItems, showBottomBar)
        }
    }
}

@Composable
private fun AppNavContent(
    navController: androidx.navigation.NavHostController,
    padding: androidx.compose.foundation.layout.PaddingValues?,
    bottomNavItems: List<BottomNavItem>,
    showBottomBar: Boolean,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = if (padding != null) Modifier.padding(padding) else Modifier,
        // Default transitions for tab screens: fade crossfade
        enterTransition = { fadeIn(tween(250)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(250)) },
        popExitTransition = { fadeOut(tween(200)) },
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onSplashFinished = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onSearch = { navController.navigate(Screen.Search.createRoute()) },
                onStoryClick = { id, url ->
                    navController.navigate(Screen.StoryDetail.createRoute(id, url))
                },
                onDownloadsClick = { navController.navigate(Screen.Downloads.route) },
            )
        }

        composable(Screen.Explore.route) {
            ExploreScreen(
                onStoryClick = { id, url ->
                    navController.navigate(Screen.StoryDetail.createRoute(id, url))
                },
                onSearchClick = {
                    navController.navigate(Screen.Search.createRoute())
                },
            )
        }

        // Sub-screens: slide in from right + fade
        composable(
            route = "search?query={query}",
            arguments =
                listOf(
                    navArgument("query") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            enterTransition = {
                slideInHorizontally(tween(350)) { it / 3 } + fadeIn(tween(300))
            },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(250)) },
            popExitTransition = {
                slideOutHorizontally(tween(300)) { it / 3 } + fadeOut(tween(250))
            },
        ) { backStackEntry ->
            val query = backStackEntry.arguments?.getString("query") ?: ""
            SearchScreen(
                onBack = { navController.popBackStack() },
                onStoryClick = { id, url ->
                    navController.navigate(Screen.StoryDetail.createRoute(id, url))
                },
                initialQuery = query,
            )
        }

        composable(
            route = "story/{storyId}/{storyUrl}",
            arguments =
                listOf(
                    navArgument("storyId") { type = NavType.StringType },
                    navArgument("storyUrl") { type = NavType.StringType },
                ),
            enterTransition = {
                slideInHorizontally(tween(350)) { it / 3 } + fadeIn(tween(300))
            },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(250)) },
            popExitTransition = {
                slideOutHorizontally(tween(300)) { it / 3 } + fadeOut(tween(250))
            },
        ) {
            StoryDetailScreen(
                onBack = { navController.popBackStack() },
                onReadChapter = { storyId, chapterNum, chapterUrl ->
                    navController.navigate(Screen.Reader.createRoute(storyId, chapterNum, chapterUrl))
                },
            )
        }

        composable(
            route = "reader/{storyId}/{chapterNumber}/{chapterUrl}",
            arguments =
                listOf(
                    navArgument("storyId") { type = NavType.StringType },
                    navArgument("chapterNumber") { type = NavType.IntType },
                    navArgument("chapterUrl") { type = NavType.StringType },
                ),
            enterTransition = {
                slideInHorizontally(tween(350)) { it / 3 } + fadeIn(tween(300))
            },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(250)) },
            popExitTransition = {
                slideOutHorizontally(tween(300)) { it / 3 } + fadeOut(tween(250))
            },
        ) {
            ReaderScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Downloads.route) {
            DownloadsScreen(
                onStoryClick = { id, url ->
                    navController.navigate(Screen.StoryDetail.createRoute(id, url))
                },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToStats = {
                    navController.navigate(Screen.ReadingStats.route)
                },
            )
        }

        composable(
            route = Screen.ReadingStats.route,
            enterTransition = {
                slideInHorizontally(tween(350)) { it / 3 } + fadeIn(tween(300))
            },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(250)) },
            popExitTransition = {
                slideOutHorizontally(tween(300)) { it / 3 } + fadeOut(tween(250))
            },
        ) {
            ReadingStatsScreen(onBack = { navController.popBackStack() })
        }
    }
}
