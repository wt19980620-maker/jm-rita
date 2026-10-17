package com.par9uet.jm.utils

import com.par9uet.jm.database.model.DownloadComic
import java.io.File

fun downloadSizeBytes(downloadRoot: File, comic: DownloadComic): Long? {
    val storedPath = comic.zipPath.takeIf { it.isNotBlank() }?.let(::File)
    val pageDir = when {
        storedPath == null -> File(downloadRoot, comic.id.toString())
        storedPath.isDirectory -> storedPath
        else -> storedPath.parentFile?.resolve(comic.id.toString())
    }
    val contentPaths = listOfNotNull(storedPath, pageDir).distinctBy { it.absolutePath }
    val cover = comic.coverPath.takeIf { it.isNotBlank() }?.let(::File)
        ?: File(downloadRoot, "cover/${comic.id}.jpg")
    if (contentPaths.none(File::exists) && cover.length() == 0L) return null
    return (contentPaths + listOfNotNull(cover)).distinctBy { it.absolutePath }.sumOf(::fileBytes)
}

private fun fileBytes(file: File): Long = when {
    file.isFile -> if (file.name.endsWith(".tmp")) 0L else file.length()
    file.isDirectory -> file.listFiles()?.sumOf(::fileBytes) ?: 0L
    else -> 0L
}
