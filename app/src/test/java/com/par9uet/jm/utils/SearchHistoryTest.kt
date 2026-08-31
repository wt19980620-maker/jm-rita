package com.par9uet.jm.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SearchHistoryTest {
    @Test
    fun removesOnlyTheRequestedHistoryItem() {
        val history = listOf("科幻", "百合", "科幻", "校园")

        assertEquals(
            listOf("百合", "校园"),
            removeSearchHistoryItem(history, "科幻")
        )
        assertEquals(history, removeSearchHistoryItem(history, "不存在"))
    }

    @Test
    fun blindBoxReturnsExistingItemOrNull() {
        val history = listOf("科幻", "百合", "校园")
        val picked = pickSearchBlindBox(history, Random(7))

        assertTrue(picked in history)
        assertNull(pickSearchBlindBox(emptyList(), Random(7)))
    }
}
