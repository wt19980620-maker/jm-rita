package com.par9uet.jm.ui.screens.tabScreen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.par9uet.jm.ui.screens.HomeScreen
import com.par9uet.jm.ui.screens.UserScreen
import com.par9uet.jm.ui.screens.AnimeScreen

sealed class BottomNav(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    object Home : BottomNav("home", "首页", Icons.Default.Home)
    object Anime : BottomNav("anime", "动漫", Icons.Default.LiveTv)
    object User : BottomNav("user", "我的", Icons.Default.Person)
}

val bottomNavList = listOf(BottomNav.Home, BottomNav.Anime, BottomNav.User)

@Composable
fun BottomNav.Content() {
    when (this) {
        BottomNav.Home -> HomeScreen()
        BottomNav.Anime -> AnimeScreen()
        BottomNav.User -> UserScreen()
    }
}
