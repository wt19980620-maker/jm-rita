package com.par9uet.jm.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

const val DEFAULT_THEME_PALETTE = "amber"

@Immutable
data class ThemePaletteOption(
    val id: String,
    val label: String,
    val previewColor: Color,
)

val themePaletteOptions = listOf(
    ThemePaletteOption(DEFAULT_THEME_PALETTE, "琥珀暖阳", Color(0xFF855318)),
    ThemePaletteOption("ocean", "海盐蓝", Color(0xFF315F8C)),
    ThemePaletteOption("sakura", "樱花粉", Color(0xFF984061)),
)

fun normalizeThemePalette(palette: String?): String =
    themePaletteOptions.firstOrNull { it.id == palette }?.id ?: DEFAULT_THEME_PALETTE

fun themePaletteLabel(palette: String?): String {
    val normalizedPalette = normalizeThemePalette(palette)
    return themePaletteOptions.first { it.id == normalizedPalette }.label
}

fun themeColorScheme(palette: String?, isDark: Boolean): ColorScheme =
    when (normalizeThemePalette(palette)) {
        "ocean" -> if (isDark) oceanDarkScheme else oceanLightScheme
        "sakura" -> if (isDark) sakuraDarkScheme else sakuraLightScheme
        else -> if (isDark) darkScheme else lightScheme
    }

private val oceanLightScheme = lightScheme.copy(
    primary = Color(0xFF315F8C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE5FF),
    onPrimaryContainer = Color(0xFF0B4771),
    secondary = Color(0xFF506070),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4E4F7),
    onSecondaryContainer = Color(0xFF394958),
    tertiary = Color(0xFF67587A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDDCFF),
    onTertiaryContainer = Color(0xFF4F4062),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41474D),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
    inverseSurface = Color(0xFF2E3135),
    inverseOnSurface = Color(0xFFEFF1F6),
    inversePrimary = Color(0xFF9CCBFA),
    surfaceDim = Color(0xFFD8DAE0),
    surfaceBright = Color(0xFFF8F9FF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F3F9),
    surfaceContainer = Color(0xFFECEEF3),
    surfaceContainerHigh = Color(0xFFE6E8ED),
    surfaceContainerHighest = Color(0xFFE1E2E8),
)

private val oceanDarkScheme = darkScheme.copy(
    primary = Color(0xFF9CCBFA),
    onPrimary = Color(0xFF003354),
    primaryContainer = Color(0xFF0B4771),
    onPrimaryContainer = Color(0xFFCFE5FF),
    secondary = Color(0xFFB8C8DA),
    onSecondary = Color(0xFF233240),
    secondaryContainer = Color(0xFF394958),
    onSecondaryContainer = Color(0xFFD4E4F7),
    tertiary = Color(0xFFD1BFE7),
    onTertiary = Color(0xFF382A4A),
    tertiaryContainer = Color(0xFF4F4062),
    onTertiaryContainer = Color(0xFFEDDCFF),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF41474D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    outline = Color(0xFF8B9198),
    outlineVariant = Color(0xFF41474D),
    inverseSurface = Color(0xFFE1E2E8),
    inverseOnSurface = Color(0xFF2E3135),
    inversePrimary = Color(0xFF315F8C),
    surfaceDim = Color(0xFF101418),
    surfaceBright = Color(0xFF36393E),
    surfaceContainerLowest = Color(0xFF0B0F13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF272A2E),
    surfaceContainerHighest = Color(0xFF323539),
)

private val sakuraLightScheme = lightScheme.copy(
    primary = Color(0xFF984061),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E3),
    onPrimaryContainer = Color(0xFF7A2949),
    secondary = Color(0xFF765661),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E3),
    onSecondaryContainer = Color(0xFF5C3F49),
    tertiary = Color(0xFF7C5734),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC0),
    onTertiaryContainer = Color(0xFF613F1E),
    background = Color(0xFFFFF8F9),
    onBackground = Color(0xFF211A1C),
    surface = Color(0xFFFFF8F9),
    onSurface = Color(0xFF211A1C),
    surfaceVariant = Color(0xFFF2DDE2),
    onSurfaceVariant = Color(0xFF514347),
    outline = Color(0xFF837377),
    outlineVariant = Color(0xFFD5C2C6),
    inverseSurface = Color(0xFF362F31),
    inverseOnSurface = Color(0xFFFBEEF0),
    inversePrimary = Color(0xFFFFB0C8),
    surfaceDim = Color(0xFFE5D6D9),
    surfaceBright = Color(0xFFFFF8F9),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF0F3),
    surfaceContainer = Color(0xFFF9EAED),
    surfaceContainerHigh = Color(0xFFF3E4E7),
    surfaceContainerHighest = Color(0xFFEDDFE2),
)

private val sakuraDarkScheme = darkScheme.copy(
    primary = Color(0xFFFFB0C8),
    onPrimary = Color(0xFF5E1135),
    primaryContainer = Color(0xFF7A2949),
    onPrimaryContainer = Color(0xFFFFD9E3),
    secondary = Color(0xFFE5BDC8),
    onSecondary = Color(0xFF432933),
    secondaryContainer = Color(0xFF5C3F49),
    onSecondaryContainer = Color(0xFFFFD9E3),
    tertiary = Color(0xFFEFBD91),
    onTertiary = Color(0xFF48290B),
    tertiaryContainer = Color(0xFF613F1E),
    onTertiaryContainer = Color(0xFFFFDCC0),
    background = Color(0xFF1B1114),
    onBackground = Color(0xFFEDDFE2),
    surface = Color(0xFF1B1114),
    onSurface = Color(0xFFEDDFE2),
    surfaceVariant = Color(0xFF514347),
    onSurfaceVariant = Color(0xFFD5C2C6),
    outline = Color(0xFF9E8C90),
    outlineVariant = Color(0xFF514347),
    inverseSurface = Color(0xFFEDDFE2),
    inverseOnSurface = Color(0xFF362F31),
    inversePrimary = Color(0xFF984061),
    surfaceDim = Color(0xFF1B1114),
    surfaceBright = Color(0xFF42383B),
    surfaceContainerLowest = Color(0xFF150C0F),
    surfaceContainerLow = Color(0xFF241A1D),
    surfaceContainer = Color(0xFF281E21),
    surfaceContainerHigh = Color(0xFF33282B),
    surfaceContainerHighest = Color(0xFF3E3336),
)
