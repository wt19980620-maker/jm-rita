package com.par9uet.jm.utils

import com.par9uet.jm.database.model.DownloadComic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadStorageTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun countsPagesIndexAndCover() {
        val root = folder.newFolder("download")
        val pages = folder.newFolder("download", "123")
        folder.newFile("download/123/offline_00000.webp").writeBytes(ByteArray(8))
        folder.newFile("download/123/pages.properties").writeBytes(ByteArray(3))
        val cover = folder.newFile("download/cover.webp").apply { writeBytes(ByteArray(5)) }

        assertEquals(16L, downloadSizeBytes(root, comic(zipPath = pages.path, coverPath = cover.path)))
    }

    @Test
    fun countsLegacyArchiveAndExtractedPages() {
        val root = folder.newFolder("download")
        val archive = folder.newFile("download/123.zip").apply { writeBytes(ByteArray(10)) }
        folder.newFolder("download", "123")
        folder.newFile("download/123/offline_00000.webp").writeBytes(ByteArray(7))

        assertEquals(17L, downloadSizeBytes(root, comic(zipPath = archive.path)))
    }

    @Test
    fun missingPagesAreNotReportedAsCompletedStorage() {
        val root = folder.newFolder("download")
        val cover = folder.newFile("download/cover.webp")

        assertNull(downloadSizeBytes(root, comic(coverPath = cover.path)))
    }

    private fun comic(zipPath: String = "", coverPath: String = "") = DownloadComic(
        id = 123,
        name = "测试漫画",
        authorList = emptyList(),
        coverPath = coverPath,
        zipPath = zipPath,
        progress = 1f,
        status = "complete",
        createTime = 0L,
    )
}
