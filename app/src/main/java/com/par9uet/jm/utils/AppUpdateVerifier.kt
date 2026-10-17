package com.par9uet.jm.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class AppUpdateVerifier(private val context: Context) {
    @Synchronized
    @Suppress("DEPRECATION")
    fun prepare(downloadUri: Uri): Uri {
        val directory = File(context.cacheDir, "verified_updates")
        check(directory.isDirectory || directory.mkdirs()) { "无法创建安装包校验目录" }
        val file = File.createTempFile("update-", ".apk", directory)
        try {
            val input = context.contentResolver.openInputStream(downloadUri)
                ?: throw IOException("无法读取安装包")
            input.use { source ->
                file.outputStream().use { target ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 256L * 1024 * 1024) { "安装包过大" }
                        target.write(buffer, 0, count)
                    }
                }
            }
            val manager = context.packageManager
            val installed = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val candidate = manager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
                ?: throw IOException("安装包无效或已损坏")
            UpdatePackagePolicy.validate(installed.identity(), candidate.identity())
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            return uri
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    private fun PackageInfo.identity() = UpdatePackageIdentity(
        packageName = packageName,
        versionCode = longVersionCode,
        signerDigests = signingInfo?.apkContentsSigners.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.toSet(),
    )
}
