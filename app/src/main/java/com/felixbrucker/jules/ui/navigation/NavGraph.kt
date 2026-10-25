package com.felixbrucker.jules.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.felixbrucker.jules.ui.logging.LogViewerScreen
import com.felixbrucker.jules.ui.logging.LogViewerViewModel
import com.felixbrucker.jules.ui.login.LoginScreen
import com.felixbrucker.jules.ui.login.LoginViewModel
import com.felixbrucker.jules.ui.sessions.SessionListScreen
import com.felixbrucker.jules.ui.sessions.SessionListViewModel
import com.felixbrucker.jules.ui.settings.SettingsScreen
import com.felixbrucker.jules.ui.settings.SettingsViewModel

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object SessionList : Screen("session_list")
    object Settings : Screen("settings")
    object LogViewer : Screen("log_viewer")
}

private val slideInFromRight = {
    slideInHorizontally(
        initialOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(300))
}

private val slideOutToLeftParallax = {
    slideOutHorizontally(
        targetOffsetX = { fullWidth -> -fullWidth / 3 },
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(300))
}

private val slideInFromLeftParallax = {
    slideInHorizontally(
        initialOffsetX = { fullWidth -> -fullWidth / 3 },
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(300))
}

private val slideOutToRight = {
    slideOutHorizontally(
        targetOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(300))
}

@Composable
fun JulesNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String
) {
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
            enterTransition = { slideInFromLeftParallax() },
            exitTransition = { slideOutToLeftParallax() },
            popEnterTransition = { slideInFromLeftParallax() },
            popExitTransition = { slideOutToRight() }
        ) {
            val viewModel: SessionListViewModel = hiltViewModel()
            SessionListScreen(
                viewModel = viewModel,
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        composable(
            route = Screen.Settings.route,
            enterTransition = { slideInFromRight() },
            exitTransition = { slideOutToLeftParallax() },
            popEnterTransition = { slideInFromLeftParallax() },
            popExitTransition = { slideOutToRight() }
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
            enterTransition = { slideInFromRight() },
            exitTransition = { slideOutToLeftParallax() },
            popEnterTransition = { slideInFromLeftParallax() },
            popExitTransition = { slideOutToRight() }
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
