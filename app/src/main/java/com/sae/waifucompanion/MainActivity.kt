package com.sae.waifucompanion

import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

sealed class NavigationItem(val route: String, val title: String, val icon: ImageVector) {
    object Waifu : NavigationItem("waifu", "Companion", Icons.Default.Face)
    object Connection : NavigationItem("connection", "Connection", Icons.Default.Wifi)
    object Console : NavigationItem("console", "Logs", Icons.Default.BugReport)
    object Advanced : NavigationItem("advanced", "Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    private lateinit var settingsRepository: SettingsRepository
    private var sharedWebView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(applicationContext)

        setContent {
            val isDarkTheme by settingsRepository.isDarkThemeFlow.collectAsState(initial = true)
            val keepScreenOn by settingsRepository.keepScreenOnFlow.collectAsState(initial = false)

            LaunchedEffect(keepScreenOn) {
                if (keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            val colorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppScaffold(
                        repository = settingsRepository,
                        onAttachWebView = { sharedWebView = it },
                        currentWebView = sharedWebView
                    )
                }
            }
        }
    }
}

@Composable
fun MainAppScaffold(
    repository: SettingsRepository,
    onAttachWebView: (WebView) -> Unit,
    currentWebView: WebView?
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val ip by repository.serverIpFlow.collectAsState(initial = "127.0.0.1")
    val port by repository.serverPortFlow.collectAsState(initial = "8000")
    val currentTargetUrl = "http://$ip:$port"
    var reloadTrigger by remember { mutableLongStateOf(0L) }

    val navItems = listOf(
        NavigationItem.Waifu,
        NavigationItem.Connection,
        NavigationItem.Console,
        NavigationItem.Advanced
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                navItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        // The remote companion page places its action dock at the bottom of its
        // viewport. Scaffold already reserves the app navigation bar; reserve a
        // little additional space on this route so the dock remains above it on
        // devices where WebView's viewport sizing differs from the browser.
        val companionReserve = if (currentRoute == NavigationItem.Waifu.route) 96.dp else 0.dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(bottom = companionReserve)
                .clipToBounds()
        ) {
            NavHost(
                navController = navController,
                startDestination = NavigationItem.Waifu.route,
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
            ) {
                composable(NavigationItem.Waifu.route) {
                    WaifuDisplayScreen(
                        url = currentTargetUrl,
                        reloadTrigger = reloadTrigger,
                        onWebViewCreated = onAttachWebView
                    )
                }
                composable(NavigationItem.Connection.route) {
                    ConnectionSettingsScreen(
                        repository = repository,
                        currentWebView = currentWebView,
                        onReloadRequested = { reloadTrigger = System.currentTimeMillis() }
                    )
                }
                composable(NavigationItem.Console.route) {
                    WebConsoleLogScreen()
                }
                composable(NavigationItem.Advanced.route) {
                    AdvancedSettingsScreen(repository = repository)
                }
            }
        }
    }
}
