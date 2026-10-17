package com.par9uet.jm.worker

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import coil.ImageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.par9uet.jm.cache.getDownloadDir
import com.par9uet.jm.data.models.ComicPicImageState
import com.par9uet.jm.data.models.ImageResultState
import com.par9uet.jm.database.dao.DownloadComicDao
import com.par9uet.jm.database.model.UpdateComicCover
import com.par9uet.jm.database.model.UpdateComicProgress
import com.par9uet.jm.database.model.UpdateComicZipPath
import com.par9uet.jm.repository.ComicRepository
import com.par9uet.jm.retrofit.model.ComicPicListResponse
import com.par9uet.jm.retrofit.model.NetWorkResult
import com.par9uet.jm.store.LocalSettingManager
import com.par9uet.jm.store.RemoteSettingManager
import com.par9uet.jm.store.ToastManager
import com.par9uet.jm.store.ImageCacheManager
import com.par9uet.jm.store.InitManager
import com.par9uet.jm.storage.DownloadSizeStorage
import com.par9uet.jm.utils.tryCreateDir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import com.par9uet.jm.store.DownloadLocks
import com.par9uet.jm.utils.OfflinePages
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class DownloadComicWorker(
    private val appContext: Context,
    params: WorkerParameters,
    private val downloadComicDao: DownloadComicDao,
    private val remoteSettingManager: RemoteSettingManager,
    private val localSettingManager: LocalSettingManager,
    private val comicRepository: ComicRepository,
    private val toastManager: ToastManager,
    private val imageLoader: ImageLoader,
    private val imageCacheManager: ImageCacheManager,
    private val downloadSizeStorage: DownloadSizeStorage,
    private val initManager: InitManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val comicId = inputData.getInt("comicId", -1)
        if (comicId == -1) {
            return Result.failure()
        }
        return DownloadLocks.forComic(comicId).withLock { try {
            initManager.deferred.await()
            if (downloadComicDao.setActiveState(comicId, "downloading") == 0) return@withLock Result.success()
            val comic = downloadComicDao.getById(comicId) ?: return@withLock Result.success()
            withContext(Dispatchers.IO) { downloadSizeStorage.reconcile(comic) }
            val coverPath = downloadCover(comicId)
            downloadComicDao.updateCover(
                UpdateComicCover(
                    comicId,
                    coverPath
                )
            )
            downloadPicList(comicId, localSettingManager.localSettingState.value.shunt)
            val downloadPath = getComicPicListDownloadDir(comicId).absolutePath
            downloadComicDao.updateZipPath(
                UpdateComicZipPath(
                    comicId,
                    downloadPath
                )
            )
            currentCoroutineContext().ensureActive()
            if (downloadComicDao.setActiveState(comicId, "complete") > 0) {
                toastManager.showAsync("下载成功")
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = if (e is LowStorageException) "存储空间不足，请清理后继续" else
                "下载失败：${e.message.orEmpty().take(160)}"
            if (runAttemptCount < 3 && e !is LowStorageException) {
                downloadComicDao.setActiveState(comicId, "pending", message)
                Result.retry()
            } else {
                downloadComicDao.setActiveState(comicId, "error", message)
                toastManager.showAsync("下载失败，可在我的下载中重试")
                Result.failure()
            }
        } }
    }

    private suspend fun downloadCover(comicId: Int): String {
        return withContext(Dispatchers.IO) {
            val imageHost = remoteSettingManager.imageHost()
            if (imageHost.isBlank()) return@withContext ""
            val coverUrl =
                "$imageHost/media/albums/${comicId}_3x4.jpg"
            val request = ImageRequest.Builder(appContext)
                .data(coverUrl)
                .allowHardware(false)
                .build()

            when (val result = imageLoader.execute(request)) {
                is ErrorResult -> {
                    ""
                }

                is SuccessResult -> {
                    val bitmap = result.drawable.toBitmap()
                    val dir = getComicCoverDownloadDir()
                    val file = File(dir, "${comicId}.jpg")
                    downloadSizeStorage.trackWrite(comicId, file) {
                        val temporary = File(dir, "${file.name}.tmp")
                        try {
                            FileOutputStream(temporary).use { out ->
                                if (!bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 50, out)) {
                                    throw IOException("封面保存失败")
                                }
                            }
                            java.nio.file.Files.move(temporary.toPath(), file.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                                java.nio.file.StandardCopyOption.ATOMIC_MOVE)
                        } finally {
                            temporary.delete()
                        }
                    }
                    file.absolutePath
                }
            }
        }
    }

    private suspend fun downloadPicList(comicId: Int, shunt: String): List<String> {
        return withContext(Dispatchers.IO) {
            when (val data = comicRepository.getComicPicList(comicId, shunt)) {
                is NetWorkResult.Error -> {
                    throw IOException(data.message)
                }

                is NetWorkResult.Success<ComicPicListResponse> -> {
                    if (data.data.list.isEmpty()) {
                        throw IOException("漫画图片列表为空")
                    }
                    val dir = getComicPicListDownloadDir(comicId)
                    val manifest = try {
                        OfflinePages.readIndex(dir)
                    } catch (_: IOException) {
                        java.util.Properties()
                    }
                    manifest.setProperty("count", data.data.list.size.toString())
                    downloadSizeStorage.trackWrite(comicId, File(dir, "pages.properties")) {
                        OfflinePages.writeIndex(dir, manifest)
                    }
                    val paths = data.data.list.mapIndexed { index, url ->
                        currentCoroutineContext().ensureActive()
                        val source = "${java.net.URI(url).path}|${data.data.__scrambleId}|${data.data.__speed}"
                        if (OfflinePages.reusable(dir, manifest, index, source)) {
                            downloadComicDao.updateProgress(UpdateComicProgress(comicId, (index + 1f) / data.data.list.size))
                            return@mapIndexed OfflinePages.page(dir, index).absolutePath
                        }
                        if (dir.usableSpace < 32L * 1024 * 1024) throw LowStorageException()
                        val imageState = ComicPicImageState(
                            index = index,
                            comicId = comicId,
                            originSrc = url,
                            __scrambleId = data.data.__scrambleId,
                            __speed = data.data.__speed,
                            picImageLoader = imageLoader,
                            imageCacheManager = imageCacheManager,
                        )
                        imageState.decode(appContext)
                        when (val result = imageState.imageResultState) {
                            is ImageResultState.Failure -> {
                                throw IOException("第 ${index + 1} 页下载失败：${result.reason}")
                            }

                            ImageResultState.Loading -> {
                                throw IOException("第 ${index + 1} 页下载未完成")
                            }

                            is ImageResultState.Success -> {
                                val bitmap = result.decodeImageBitmap.asAndroidBitmap()
                                val file = OfflinePages.page(dir, index)
                                val temporary = File(dir, "${file.name}.tmp")
                                try {
                                    FileOutputStream(temporary).use { out ->
                                        if (!bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 50, out)) {
                                            throw IOException("图片保存失败")
                                        }
                                        out.fd.sync()
                                    }
                                    currentCoroutineContext().ensureActive()
                                    downloadSizeStorage.trackWrite(comicId, file) {
                                        java.nio.file.Files.move(temporary.toPath(), file.toPath(),
                                            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                                            java.nio.file.StandardCopyOption.ATOMIC_MOVE)
                                    }
                                    manifest.setProperty("source.$index", source)
                                    manifest.setProperty("hash.$index", OfflinePages.digest(file))
                                    downloadSizeStorage.trackWrite(comicId, File(dir, "pages.properties")) {
                                        OfflinePages.writeIndex(dir, manifest)
                                    }
                                } finally {
                                    temporary.delete()
                                }
                                downloadComicDao.updateProgress(
                                    UpdateComicProgress(
                                        comicId,
                                        (index + 1).toFloat() / data.data.list.size
                                    )
                                )
                                file.absolutePath
                            }
                        }
                    }
                    paths.forEachIndexed { index, path ->
                        currentCoroutineContext().ensureActive()
                        if (manifest.getProperty("hash.$index") != OfflinePages.digest(File(path))) {
                            throw IOException("第 ${index + 1} 页完整性校验失败")
                        }
                    }
                    paths
                }
            }
        }
    }

    private fun getComicPicListDownloadDir(comicId: Int): File {
        val dir = getDownloadDir(appContext)
        return tryCreateDir(File(dir, "$comicId"))
    }

    private fun getComicCoverDownloadDir(): File {
        val dir = getDownloadDir(appContext)
        return tryCreateDir(File(dir, "cover"))
    }
}

private class LowStorageException : IOException("存储空间不足")
