package com.par9uet.jm.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiLineSelectorTest {
    @Test
    fun keepsCurrentApiWhenItIsAvailable() {
        val checks = listOf(
            ApiLineCheck("https://current.example", true, 300),
            ApiLineCheck("https://faster.example", true, 100),
        )

        assertEquals(
            "https://current.example",
            ApiLineSelector.choose("https://current.example", checks),
        )
    }

    @Test
    fun switchesToFastestAvailableApiWhenCurrentFails() {
        val checks = listOf(
            ApiLineCheck("https://current.example", false),
            ApiLineCheck("https://slow.example", true, 500),
            ApiLineCheck("https://fast.example", true, 120),
        )

        assertEquals(
            "https://fast.example",
            ApiLineSelector.choose("https://current.example", checks),
        )
        assertNull(ApiLineSelector.choose("https://current.example", emptyList()))
    }
}
