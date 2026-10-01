package com.sammy.fbili

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sammy.fbili.data.Settings
import com.sammy.fbili.ui.fav.FavFoldersScreen
import com.sammy.fbili.ui.fav.FavResourcesScreen
import com.sammy.fbili.ui.history.HistoryScreen
import com.sammy.fbili.ui.home.HomeScreen
import com.sammy.fbili.ui.live.LivePlayScreen
import com.sammy.fbili.ui.login.LoginScreen
import com.sammy.fbili.ui.mine.MineScreen
import com.sammy.fbili.ui.downloads.DownloadsScreen
import com.sammy.fbili.ui.search.SearchScreen
import com.sammy.fbili.ui.settings.SettingsScreen
import com.sammy.fbili.ui.user.UserScreen
import com.sammy.fbili.ui.video.VideoScreen
import com.sammy.fbili.ui.theme.FBiliTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val mode by Settings.theme.collectAsState()
            FBiliTheme(mode) { AppRoot() }
        }
    }

    override fun onStart() {
        super.onStart()
        // 回到前台补一次登录态（一次轻量请求），修复登录成功但界面未同步的问题
        com.sammy.fbili.data.Account.refreshAsync()
    }

    fun setLandscape(on: Boolean) {
        requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "home"
    val roots = setOf("home", "mine")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (route in roots) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val items = listOf(
                    Triple("home", "首页", Icons.Filled.Home),
                    Triple("mine", "我的", Icons.Filled.Person),
                )
                items.forEach { (r, name, icon) ->
                    NavigationBarItem(
                        selected = route == r,
                        onClick = {
                            nav.navigate(r) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(icon, contentDescription = name) },
                        label = { Text(name) },
                    )
                }
            }
        },
    ) { pad ->
        NavHost(
            navController = nav, startDestination = "home",
            modifier = Modifier.padding(pad).consumeWindowInsets(pad),
        ) {
            composable("home") {
                HomeScreen(
                    onVideo = { b, a -> nav.navigate(videoRoute(b, a)) },
                    onSearch = { nav.navigate("search/") },
                    onLive = { id -> nav.navigate("live/$id") },
                    onLogin = { if (com.sammy.fbili.data.Account.isLogin) nav.navigate("user/${com.sammy.fbili.data.Account.uid()}") else nav.navigate("login") },
                )
            }
            composable("mine") {
                MineScreen(
                    onLogin = { nav.navigate("login") },
                    onSettings = { nav.navigate("settings") },
                    onHistory = { nav.navigate("history") },
                    onFav = { nav.navigate("favroot") },
                    onDownloads = { nav.navigate("downloads") },
                    onFollowing = { nav.navigate("following") },
                    onUser = { m -> nav.navigate("user/$m") },
                )
            }
            composable("search/{kw}") { e ->
                SearchScreen(
                    keyword = e.arguments?.getString("kw").orEmpty(),
                    onVideo = { b, a -> nav.navigate(videoRoute(b, a)) },
                    onSeason = { sid -> nav.navigate("season/$sid") },
                    onUser = { m -> nav.navigate("user/$m") },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("video/{bvid}/{aid}/{ep}") { e ->
                VideoScreen(
                    bvid = e.arguments?.getString("bvid")?.takeIf { it != "-" }.orEmpty(),
                    aid = e.arguments?.getString("aid")?.toLongOrNull() ?: 0,
                    epId = e.arguments?.getString("ep")?.toLongOrNull() ?: 0,
                    onVideo = { b, a -> nav.navigate(videoRoute(b, a)) },
                    onSeason = { sid -> nav.navigate("season/$sid") },
                    onUser = { m -> nav.navigate("user/$m") },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("season/{sid}") { e ->
                SeasonHost(
                    seasonId = e.arguments?.getString("sid")?.toLongOrNull() ?: 0,
                    onVideo = { b, a, ep -> nav.navigate(videoRoute(b, a, ep)) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("user/{mid}") { e ->
                UserScreen(
                    mid = e.arguments?.getString("mid")?.toLongOrNull() ?: 0,
                    onVideo = { b, a -> nav.navigate(videoRoute(b, a)) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("live/{roomId}") { e ->
                LivePlayScreen(
                    roomId = e.arguments?.getString("roomId")?.toLongOrNull() ?: 0,
                    onBack = { nav.popBackStack() },
                )
            }
            composable("login") {
                LoginScreen(onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
            }
            composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
            composable("history") {
                HistoryScreen(onVideo = { b, a -> nav.navigate(videoRoute(b, a)) }, onBack = { nav.popBackStack() })
            }
            composable("favroot") {
                FavFoldersScreen(onFolder = { fid, t -> nav.navigate("fav/$fid/${android.net.Uri.encode(t)}") },
                    onBack = { nav.popBackStack() })
            }
            composable("fav/{fid}/{title}") { e ->
                FavResourcesScreen(
                    fid = e.arguments?.getString("fid")?.toLongOrNull() ?: 0,
                    title = e.arguments?.getString("title").orEmpty(),
                    onVideo = { b, a -> nav.navigate(videoRoute(b, a)) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("downloads") {
                DownloadsScreen(onPlay = { path, t, a ->
                    nav.navigate("local/${android.net.Uri.encode(path)}/${android.net.Uri.encode(t)}/$a")
                }, onBack = { nav.popBackStack() })
            }
            composable("local/{path}/{title}/{aid}") { e ->
                com.sammy.fbili.ui.video.LocalPlayScreen(
                    path = e.arguments?.getString("path").orEmpty(),
                    title = e.arguments?.getString("title").orEmpty(),
                    aid = e.arguments?.getString("aid")?.toLongOrNull() ?: 0,
                    onBack = { nav.popBackStack() },
                )
            }
            composable("following") {
                FollowingScreen(onUser = { m -> nav.navigate("user/$m") }, onBack = { nav.popBackStack() })
            }
        }
    }
}

@Composable
private fun SeasonHost(seasonId: Long, onVideo: (String, Long, Long) -> Unit, onBack: () -> Unit) {
    com.sammy.fbili.ui.bangumi.SeasonScreen(seasonId, onVideo, onBack)
}

// 空 bvid 用占位符 "-" 填充，避免 "video//aid/0" 出现连续斜杠导致路由段错配
private fun videoRoute(b: String, a: Long, ep: Long = 0): String =
    "video/${b.ifEmpty { "-" }}/$a/$ep"

@Composable
private fun FollowingScreen(onUser: (Long) -> Unit, onBack: () -> Unit) {
    com.sammy.fbili.ui.user.FollowingScreen(onUser, onBack)
}
