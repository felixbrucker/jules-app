package com.felixbrucker.jules.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.felixbrucker.jules.ui.logging.LogViewerScreen
import com.felixbrucker.jules.ui.logging.LogViewerViewModel
import com.felixbrucker.jules.ui.login.LoginScreen
import com.felixbrucker.jules.ui.login.LoginViewModel
import com.felixbrucker.jules.ui.sessions.SessionDetailScreen
import com.felixbrucker.jules.ui.sessions.SessionDetailViewModel
import com.felixbrucker.jules.ui.sessions.SessionListScreen
import com.felixbrucker.jules.ui.sessions.SessionListViewModel
import com.felixbrucker.jules.ui.settings.SettingsScreen
import com.felixbrucker.jules.ui.settings.SettingsViewModel

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object SessionList : Screen("session_list")
    object SessionDetail : Screen("session_detail/{sessionId}") {
        fun createRoute(sessionId: String) = "session_detail/$sessionId"
    }
    object Settings : Screen("settings")
    object LogViewer : Screen("log_viewer")
}

@Composable
fun JulesNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String
) {
    SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable(
                route = Screen.Login.route,
                enterTransition = { fadeIn(tween(300)) },
                exitTransition = { fadeOut(tween(300)) }
            ) {
                val viewModel: LoginViewModel = hiltViewModel()
                LoginScreen(
                    viewModel = viewModel,
                    onLoginSuccess = {
                        navController.navigate(Screen.SessionList.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(
                route = Screen.SessionList.route,
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(300))
                },
                exitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(300))
                },
                popEnterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(300))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(300))
                }
            ) {
                val viewModel: SessionListViewModel = hiltViewModel()
                SessionListScreen(
                    viewModel = viewModel,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable,
                    onSettingsClick = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onSessionClick = { sessionId ->
                        navController.navigate(Screen.SessionDetail.createRoute(sessionId))
                    }
                )
            }
            composable(
                route = Screen.SessionDetail.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
            ) {
                val viewModel: SessionDetailViewModel = hiltViewModel()
                SessionDetailScreen(
                    viewModel = viewModel,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable,
                    onBackClick = {
                        navController.popBackStackSafely()
                    }
                )
            }
            composable(
                route = Screen.Settings.route,
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(300))
                },
                exitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(300))
                },
                popEnterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(300))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(300))
                }
            ) {
                val viewModel: SettingsViewModel = hiltViewModel()
                SettingsScreen(
                    viewModel = viewModel,
                    onBackClick = {
                        navController.popBackStackSafely()
                    },
                    onViewLogsClick = {
                        navController.navigate(Screen.LogViewer.route)
                    }
                )
            }
            composable(
                route = Screen.LogViewer.route,
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(300))
                },
                exitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(300))
                },
                popEnterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(300))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(300))
                }
            ) {
                val viewModel: LogViewerViewModel = hiltViewModel()
                LogViewerScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        navController.popBackStackSafely()
                    }
                )
            }
        }
    }
}

internal fun NavController.popBackStackSafely(): Boolean {
    val currentEntry = currentBackStackEntry ?: return false
    if (currentEntry.lifecycle.currentState != Lifecycle.State.RESUMED) {
        return false
    }
    if (previousBackStackEntry == null) {
        return false
    }
    return popBackStack()
}
