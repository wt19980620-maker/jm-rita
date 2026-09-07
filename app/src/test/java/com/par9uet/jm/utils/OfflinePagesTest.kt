package com.par9uet.jm.utils

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.Properties

class OfflinePagesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun indexedPagesAreOrderedAndIgnoreTemporaryFiles() {
        val dir = temporary.newFolder()
        OfflinePages.page(dir, 1).writeText("second")
        OfflinePages.page(dir, 0).writeText("first")
        dir.resolve("offline_00002.webp.tmp").writeText("partial")
        OfflinePages.writeIndex(dir, Properties().apply { setProperty("count", "2") })
        assertEquals(listOf("first", "second"), OfflinePages.list(dir).map { it.readText() })
    }

    @Test fun resumeRequiresMatchingSourceAndHash() {
        val dir = temporary.newFolder()
        val page = OfflinePages.page(dir, 0).apply { writeText("valid") }
        val index = Properties().apply {
            setProperty("count", "1")
            setProperty("source.0", "/page|1|1")
            setProperty("hash.0", OfflinePages.digest(page))
        }
        OfflinePages.writeIndex(dir, index)
        val restored = OfflinePages.readIndex(dir)
        assertTrue(OfflinePages.reusable(dir, restored, 0, "/page|1|1"))
        assertFalse(OfflinePages.reusable(dir, restored, 0, "/different|1|1"))
        page.writeText("corrupt")
        assertFalse(OfflinePages.reusable(dir, restored, 0, "/page|1|1"))
        page.delete()
        assertFalse(OfflinePages.reusable(dir, restored, 0, "/page|1|1"))
    }

    @Test(expected = IOException::class) fun missingPageFailsExplicitly() {
        val dir = temporary.newFolder()
        OfflinePages.page(dir, 0).writeText("first")
        OfflinePages.writeIndex(dir, Properties().apply { setProperty("count", "2") })
        OfflinePages.list(dir)
    }

    @Test(expected = IOException::class) fun invalidCountFailsExplicitly() {
        val dir = temporary.newFolder()
        OfflinePages.writeIndex(dir, Properties().apply { setProperty("count", "100001") })
        OfflinePages.list(dir)
    }

    @Test(expected = IOException::class) fun malformedIndexIsReportedAsIoError() {
        val dir = temporary.newFolder()
        dir.resolve("pages.properties").writeText("count=\\uXXXX")
        OfflinePages.list(dir)
    }

    @Test(expected = IOException::class) fun truncatedIndexIsNotTreatedAsLegacy() {
        val dir = temporary.newFolder()
        dir.resolve("pages.properties").writeText("")
        OfflinePages.list(dir)
    }

    @Test fun legacyDownloadsRemainReadable() {
        val dir = temporary.newFolder()
        OfflinePages.page(dir, 0).writeText("legacy")
        dir.resolve("offline_00001.webp.tmp").writeText("partial")
        assertEquals(listOf(OfflinePages.page(dir, 0)), OfflinePages.list(dir))
    }

    @Test fun indexCanBeAtomicallyReplaced() {
        val dir = temporary.newFolder()
        val index = Properties().apply { setProperty("count", "1") }
        OfflinePages.writeIndex(dir, index)
        index.setProperty("count", "2")
        OfflinePages.writeIndex(dir, index)
        assertEquals("2", OfflinePages.readIndex(dir).getProperty("count"))
        assertFalse(dir.resolve("pages.properties.tmp").exists())
    }
}
