package com.par9uet.jm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.par9uet.jm.store.LocalSettingManager
import org.koin.compose.getKoin

// primary #FF9800
// content tag #EBEEFF
// role tag #F7FAFF
// work tag #FDE7FF

val LocalExtendedColors = staticCompositionLocalOf<ExtendedColorScheme> {
    error("未提供默认扩展主题变量")
}

object ExtendedTheme {
    val colors: ExtendedColorScheme
        @Composable
        get() = LocalExtendedColors.current
}

@Composable
fun AppTheme(
    localSettingManager: LocalSettingManager = getKoin().get(),
    content: @Composable () -> Unit
) {
    val localSetting by localSettingManager.localSettingState.collectAsState()
    val systemInDarkTheme = isSystemInDarkTheme()
    val isDarkTheme = when (localSetting.theme) {
        "light" -> false
        "dark" -> true
        else -> systemInDarkTheme
    }
    val colorScheme = themeColorScheme(localSetting.colorPalette, isDarkTheme)
    val extendedColorScheme = if (isDarkTheme) extendedDark else extendedLight

    CompositionLocalProvider(LocalExtendedColors provides extendedColorScheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
