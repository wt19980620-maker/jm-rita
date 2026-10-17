package com.par9uet.jm.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections

class PreferredApiCheckTest {
    @Test
    fun healthyPreferredLineSkipsAllOtherProbes() {
        runBlocking {
            val calls = mutableListOf<String>()
            val result = checkPreferredApi("current", listOf("current", "slow")) {
                calls.add(it)
                ApiLineCheck(it, true, 1)
            }
            assertEquals(listOf("current"), calls)
            assertEquals("current", ApiLineSelector.choose("current", result))
        }
    }

    @Test
    fun failedPreferredLineChecksUniqueAlternatives() {
        runBlocking {
            val calls = Collections.synchronizedList(mutableListOf<String>())
            val result = checkPreferredApi("current", listOf("current", "backup", "backup")) {
                calls.add(it)
                ApiLineCheck(it, it == "backup", 10)
            }
            assertEquals(listOf("current", "backup"), calls.toList())
            assertEquals("backup", ApiLineSelector.choose("current", result))
        }
    }
}
