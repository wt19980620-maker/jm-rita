package com.par9uet.jm.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {
    @Test
    fun comparesSemanticVersions() {
        assertTrue(VersionComparator.isNewer("1.3.0", "1.2.0"))
        assertTrue(VersionComparator.isNewer("v2.0", "1.9.9"))
        assertFalse(VersionComparator.isNewer("1.2.0", "1.2.0"))
        assertFalse(VersionComparator.isNewer("1.1.9", "1.2.0"))
    }
}
