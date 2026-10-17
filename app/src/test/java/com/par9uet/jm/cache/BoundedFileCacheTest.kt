package com.par9uet.jm.cache

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class BoundedFileCacheTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun trimsExistingCacheAndPreservesRecentlyReadPages() {
        val root = folder.newFolder("cache")
        File(root, "old.webp").apply { writeBytes(ByteArray(6)); setLastModified(1000) }
        File(root, "new.webp").apply { writeBytes(ByteArray(6)); setLastModified(2000) }
        val cache = BoundedFileCache(root, 12)
        assertNotNull(cache.read("old.webp") { it.readBytes() })
        cache.write("third.webp") { it.writeBytes(ByteArray(6)) }
        assertTrue(File(root, "old.webp").exists())
        assertFalse(File(root, "new.webp").exists())
        assertEquals(12L, cache.sizeBytes())
        val smallerCache = BoundedFileCache(root, 6)
        assertEquals(6L, smallerCache.sizeBytes())
    }

    @Test
    fun replacementAndOversizedImageStayWithinBudget() {
        val cache = BoundedFileCache(folder.newFolder("cache"), 10)
        cache.write("1/page.webp") { it.writeBytes(ByteArray(8)) }
        cache.write("1/page.webp") { it.writeBytes(ByteArray(4)) }
        assertEquals(4L, cache.sizeBytes())
        cache.write("large.webp") { it.writeBytes(ByteArray(11)) }
        assertTrue(cache.sizeBytes() <= 10)
    }

    @Test
    fun failedWritePreservesPreviousImageAndCleansTemporaryFile() {
        val root = folder.newFolder("cache")
        val cache = BoundedFileCache(root, 10)
        cache.write("page.webp") { it.writeText("old") }
        assertThrows(IOException::class.java) {
            cache.write("page.webp") {
                it.writeText("partial")
                throw IOException("write interrupted")
            }
        }
        assertEquals("old", cache.read("page.webp") { it.readText() })
        assertFalse(File(root, "page.webp.tmp").exists())
        assertEquals(3L, cache.sizeBytes())
    }

    @Test
    fun missingAndCorruptFilesAreRemovedFromAccounting() {
        val root = folder.newFolder("cache")
        val cache = BoundedFileCache(root, 10)
        cache.write("missing.webp") { it.writeBytes(ByteArray(4)) }
        File(root, "missing.webp").delete()
        assertNull(cache.read("missing.webp") { it.readBytes() })
        cache.write("corrupt.webp") { it.writeBytes(ByteArray(4)) }
        assertNull(cache.read<ByteArray>("corrupt.webp") { null })
        assertEquals(0L, cache.sizeBytes())
    }

    @Test
    fun clearingCacheCannotTouchOfflineDownloads() {
        val root = folder.newFolder("cache")
        val offline = folder.newFile("offline.webp").apply { writeText("offline") }
        val cache = BoundedFileCache(root, 10)
        cache.write("page.webp") { it.writeText("cache") }
        assertThrows(IllegalArgumentException::class.java) {
            cache.write("../offline.webp") { it.writeText("bad") }
        }
        cache.clear()
        assertEquals(0L, cache.sizeBytes())
        assertEquals("offline", offline.readText())
    }
}
