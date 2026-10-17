package com.par9uet.jm.storage

import com.par9uet.jm.database.model.DownloadComic
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class DownloadSizeStorageTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun legacyScanIsPersistedAndOnlyExplicitRefreshRescans() {
        val root = folder.newFolder("download")
        val records = folder.newFolder("sizes")
        val pages = File(root, "123").apply { mkdirs() }
        val page = File(pages, "page.webp").apply { writeBytes(ByteArray(8)) }
        val comic = comic(pages)
        val storage = DownloadSizeStorage(root, records)
        storage.load(comic)
        page.writeBytes(ByteArray(20))
        storage.load(comic)
        assertEquals(8L, storage.sizes.value[123])
        val restored = DownloadSizeStorage(root, records)
        restored.load(comic)
        assertEquals(8L, restored.sizes.value[123])
        restored.load(comic, refreshToken = 1L)
        assertEquals(20L, restored.sizes.value[123])
        page.writeBytes(ByteArray(30))
        restored.load(comic, refreshToken = 1L)
        assertEquals(20L, restored.sizes.value[123])
        restored.load(comic, refreshToken = 2L)
        assertEquals(30L, restored.sizes.value[123])
    }

    @Test
    fun replacementAndManifestWritesUseDeltasAndPersistAcrossRestart() {
        val root = folder.newFolder("download")
        val records = folder.newFolder("sizes")
        val pages = File(root, "123").apply { mkdirs() }
        val page = File(pages, "page.webp").apply { writeBytes(ByteArray(8)) }
        val index = File(pages, "pages.properties").apply { writeBytes(ByteArray(3)) }
        val storage = DownloadSizeStorage(root, records)
        storage.reconcile(comic(pages))
        storage.trackWrite(123, page) { page.writeBytes(ByteArray(4)) }
        storage.trackWrite(123, index) { index.writeBytes(ByteArray(5)) }
        assertEquals(9L, storage.sizes.value[123])
        val restored = DownloadSizeStorage(root, records)
        restored.load(comic(pages))
        assertEquals(9L, restored.sizes.value[123])
    }

    @Test
    fun interruptedWritesAreCountedAndResumeReconcilesDisk() {
        val root = folder.newFolder("download")
        val records = folder.newFolder("sizes")
        val pages = File(root, "123").apply { mkdirs() }
        val page = File(pages, "page.webp")
        val storage = DownloadSizeStorage(root, records)
        storage.reconcile(comic(pages))
        assertThrows(IOException::class.java) {
            storage.trackWrite(123, page) {
                page.writeBytes(ByteArray(4))
                throw IOException("interrupted")
            }
        }
        assertEquals(4L, storage.sizes.value[123])
        page.writeBytes(ByteArray(7))
        File(pages, "page.webp.tmp").writeBytes(ByteArray(100))
        storage.reconcile(comic(pages))
        assertEquals(7L, storage.sizes.value[123])
        storage.forget(123)
        assertFalse(File(records, "123.size").exists())
        assertFalse(storage.sizes.value.containsKey(123))
    }

    @Test
    fun resumesCoverWrittenBeforeDatabaseWasUpdated() {
        val root = folder.newFolder("download")
        val records = folder.newFolder("sizes")
        val cover = File(root, "cover/123.jpg").apply {
            parentFile!!.mkdirs()
            writeBytes(ByteArray(8))
        }
        val storage = DownloadSizeStorage(root, records)
        storage.reconcile(comic(File(root, "123")))
        storage.trackWrite(123, cover) { cover.writeBytes(ByteArray(5)) }
        assertEquals(5L, storage.sizes.value[123])
    }

    private fun comic(pages: File) = DownloadComic(
        id = 123, name = "测试", authorList = emptyList(), coverPath = "",
        zipPath = pages.path, progress = 1f, status = "complete", createTime = 0,
    )
}
