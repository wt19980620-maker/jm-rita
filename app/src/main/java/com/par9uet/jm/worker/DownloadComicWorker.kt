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
import com.par9uet.jm.database.model.UpdateComicStatus
import com.par9uet.jm.database.model.UpdateComicZipPath
import com.par9uet.jm.repository.ComicRepository
import com.par9uet.jm.retrofit.model.ComicPicListResponse
import com.par9uet.jm.retrofit.model.NetWorkResult
import com.par9uet.jm.store.LocalSettingManager
import com.par9uet.jm.store.RemoteSettingManager
import com.par9uet.jm.store.ToastManager
import com.par9uet.jm.utils.tryCreateDir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val comicId = inputData.getInt("comicId", -1)
        if (comicId == -1) {
            return Result.failure()
        }
        return try {
            downloadComicDao.updateStatus(
                UpdateComicStatus(
                    comicId,
                    "downloading"
                )
            )
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
            downloadComicDao.updateStatus(
                UpdateComicStatus(
                    comicId,
                    "complete"
                )
            )
            toastManager.showAsync("下载成功")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                downloadComicDao.updateStatus(UpdateComicStatus(comicId, "pending"))
                Result.retry()
            } else {
                downloadComicDao.updateStatus(UpdateComicStatus(comicId, "error"))
                toastManager.showAsync("下载失败，可在我的下载中重试")
                Result.failure()
            }
        }
    }

    private suspend fun downloadCover(comicId: Int): String {
        return withContext(Dispatchers.IO) {
            val coverUrl =
                "${remoteSettingManager.remoteSettingState.value.imgHost}/media/albums/${comicId}_3x4.jpg"
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
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 50, out)
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
                    dir.listFiles()?.forEach { it.delete() }
                    data.data.list.mapIndexed { index, url ->
                        val imageState = ComicPicImageState(
                            index = index,
                            comicId = comicId,
                            originSrc = url,
                            __scrambleId = data.data.__scrambleId,
                            __speed = data.data.__speed,
                            picImageLoader = imageLoader,
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
                                val file = File(dir, "offline_%05d.webp".format(index))
                                FileOutputStream(file).use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 50, out)
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
