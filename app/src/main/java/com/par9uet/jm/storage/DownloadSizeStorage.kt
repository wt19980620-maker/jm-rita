package com.par9uet.jm.storage

import com.par9uet.jm.database.model.DownloadComic
import com.par9uet.jm.utils.downloadSizeBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

class DownloadSizeStorage(private val downloadRoot: File, private val recordsRoot: File) {
    private val _sizes = MutableStateFlow<Map<Int, Long?>>(emptyMap())
    val sizes = _sizes.asStateFlow()
    private val refreshTokens = mutableMapOf<Int, Long>()

    @Synchronized
    fun load(comic: DownloadComic, refreshToken: Long = 0L) {
        val refresh = refreshToken != 0L && refreshTokens[comic.id] != refreshToken
        if (!refresh && _sizes.value.containsKey(comic.id)) return
        val record = File(recordsRoot, "${comic.id}.size")
        val saved = if (record.isFile) record.readText().trim().toLongOrNull() else null
        if (!refresh && saved != null && saved >= -1L) {
            publish(comic.id, saved.takeIf { it >= 0 }, persist = false)
        } else {
            reconcile(comic)
        }
        if (refresh) refreshTokens[comic.id] = refreshToken
    }

    @Synchronized
    fun reconcile(comic: DownloadComic) {
        publish(comic.id, downloadSizeBytes(downloadRoot, comic))
    }

    // 调用方先校验一次已有文件，后续只计算本次写入文件的大小变化。
    @Synchronized
    fun <T> trackWrite(comicId: Int, file: File, write: () -> T): T {
        check(_sizes.value.containsKey(comicId)) { "下载大小尚未初始化" }
        val before = file.length()
        try {
            return write()
        } finally {
            val bytes = ((_sizes.value[comicId] ?: 0L) + file.length() - before).coerceAtLeast(0L)
            publish(comicId, bytes)
        }
    }

    @Synchronized
    fun forget(comicId: Int) {
        val record = File(recordsRoot, "$comicId.size")
        if (record.exists() && !record.delete()) {
            throw java.io.IOException("无法清理下载大小记录")
        }
        _sizes.value = _sizes.value - comicId
        refreshTokens.remove(comicId)
    }

    private fun publish(comicId: Int, bytes: Long?, persist: Boolean = true) {
        if (persist) {
            if (!recordsRoot.isDirectory && !recordsRoot.mkdirs()) {
                throw java.io.IOException("无法创建下载大小记录目录")
            }
            val record = File(recordsRoot, "$comicId.size")
            val temporary = File(recordsRoot, "$comicId.size.tmp")
            try {
                temporary.writeText((bytes ?: -1L).toString())
                Files.move(temporary.toPath(), record.toPath(), REPLACE_EXISTING, ATOMIC_MOVE)
            } finally {
                temporary.delete()
            }
        }
        _sizes.value = _sizes.value + (comicId to bytes)
    }
}
