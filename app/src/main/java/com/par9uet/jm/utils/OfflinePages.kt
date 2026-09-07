package com.par9uet.jm.utils

import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.Properties
import java.security.MessageDigest

object OfflinePages {
    fun page(dir: File, index: Int) = File(dir, String.format(Locale.ROOT, "offline_%05d.webp", index))

    fun readIndex(dir: File): Properties = Properties().apply {
        val file = File(dir, "pages.properties")
        if (file.isFile) {
            try {
                file.inputStream().use { load(it) }
            } catch (e: IllegalArgumentException) {
                throw IOException("本地下载索引损坏，请在我的下载中修复", e)
            }
            if (!containsKey("count")) throw IOException("本地下载索引缺少页数")
        }
    }

    fun writeIndex(dir: File, index: Properties) {
        val temp = File(dir, "pages.properties.tmp")
        temp.outputStream().use { index.store(it, null) }
        java.nio.file.Files.move(temp.toPath(), File(dir, "pages.properties").toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    }

    fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun reusable(dir: File, index: Properties, number: Int, source: String): Boolean {
        val file = page(dir, number)
        return file.isFile && file.length() > 0 && index.getProperty("source.$number") == source &&
            index.getProperty("hash.$number") == digest(file)
    }

    fun list(dir: File): List<File> {
        val index = readIndex(dir)
        val countText = index.getProperty("count")
        if (countText != null) {
            val count = countText.toIntOrNull()?.takeIf { it in 1..100000 }
                ?: throw IOException("本地下载索引损坏，请在我的下载中修复")
            return List(count) { page(dir, it) }.also { pages ->
                if (pages.any { !it.isFile || it.length() == 0L }) {
                    throw IOException("本地下载缺页，请在我的下载中修复")
                }
            }
        }
        return dir.listFiles { file -> file.isFile && file.name.matches(Regex("offline_[0-9]+\\.webp")) }
            ?.sortedBy { it.name }.orEmpty()
    }
}
