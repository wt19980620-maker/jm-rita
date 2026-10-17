package com.par9uet.jm.cache

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

class BoundedFileCache(private val root: File, val maxBytes: Long) {
    private data class Entry(val file: File, val bytes: Long)
    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private var initialized = false
    private var bytes = 0L

    init { require(maxBytes > 0) }

    @Synchronized
    fun sizeBytes(): Long {
        initialize()
        return bytes
    }

    @Synchronized
    fun refresh(): Long {
        initialized = false
        initialize()
        return bytes
    }

    @Synchronized
    fun <T> read(key: String, reader: (File) -> T?): T? {
        val file = resolve(key)
        initialize()
        val entry = entries[key] ?: return null
        if (!file.isFile) {
            entries.remove(key)
            bytes -= entry.bytes
            return null
        }
        val result = reader(file)
        if (result == null) remove(key) else file.setLastModified(System.currentTimeMillis())
        return result
    }

    @Synchronized
    fun write(key: String, writer: (File) -> Unit) {
        val target = resolve(key)
        initialize()
        if (!target.parentFile!!.isDirectory && !target.parentFile!!.mkdirs()) {
            throw IOException("无法创建图片缓存目录")
        }
        val temporary = File(target.parentFile, "${target.name}.tmp")
        try {
            writer(temporary)
            Files.move(temporary.toPath(), target.toPath(), REPLACE_EXISTING, ATOMIC_MOVE)
            bytes -= entries.remove(key)?.bytes ?: 0L
            val entry = Entry(target, target.length())
            entries[key] = entry
            bytes += entry.bytes
            trim()
        } finally {
            temporary.delete()
        }
    }

    @Synchronized
    fun clear() {
        initialize()
        entries.keys.toList().forEach(::remove)
        if (entries.isNotEmpty()) throw IOException("部分缓存未能清理，请重试")
    }

    private fun initialize() {
        if (initialized) return
        entries.clear()
        bytes = 0L
        val canonicalRoot = root.canonicalFile.toPath()
        root.walkTopDown()
            .onEnter { it.canonicalFile.toPath().startsWith(canonicalRoot) }
            .filter { it.isFile && it.canonicalFile.toPath().startsWith(canonicalRoot) }
            .sortedBy { it.lastModified() }
            .forEach { file ->
                val key = file.relativeTo(root).invariantSeparatorsPath
                val entry = Entry(file, file.length())
                entries[key] = entry
                bytes += entry.bytes
            }
        initialized = true
        trim()
    }

    private fun resolve(key: String): File {
        val file = File(root, key)
        val canonicalRoot = root.canonicalFile.toPath()
        require(file.canonicalFile.toPath() != canonicalRoot &&
            file.canonicalFile.toPath().startsWith(canonicalRoot)) { "无效缓存路径" }
        return file
    }

    private fun trim() {
        if (bytes <= maxBytes) return
        for (key in entries.keys.toList()) {
            if (bytes <= maxBytes) break
            remove(key)
        }
    }

    private fun remove(key: String) {
        val entry = entries[key] ?: return
        if (!entry.file.exists() || entry.file.delete()) {
            bytes -= entry.bytes
            entries.remove(key)
        }
    }
}
