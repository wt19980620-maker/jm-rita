package com.par9uet.jm.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThemePaletteTest {
    @Test
    fun `missing or unknown palette falls back to amber`() {
        assertEquals(DEFAULT_THEME_PALETTE, normalizeThemePalette(null))
        assertEquals(DEFAULT_THEME_PALETTE, normalizeThemePalette("unknown"))
    }

    @Test
    fun `each palette has distinct light primary color`() {
        val primaryColors = themePaletteOptions.map { themeColorScheme(it.id, false).primary }

        assertEquals(primaryColors.size, primaryColors.distinct().size)
    }

    @Test
    fun `dark mode uses a different surface and primary color`() {
        themePaletteOptions.forEach { palette ->
            val light = themeColorScheme(palette.id, false)
            val dark = themeColorScheme(palette.id, true)

            assertNotEquals(light.surface, dark.surface)
            assertNotEquals(light.primary, dark.primary)
        }
    }
}
