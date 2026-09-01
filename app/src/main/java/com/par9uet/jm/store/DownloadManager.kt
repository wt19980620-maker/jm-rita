package com.par9uet.jm.store

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import androidx.work.workDataOf
import com.par9uet.jm.cache.getCommonPicDecodeCacheDir
import com.par9uet.jm.cache.getDownloadDir
import com.par9uet.jm.data.models.Comic
import com.par9uet.jm.database.dao.DownloadComicDao
import com.par9uet.jm.database.model.DownloadComic
import com.par9uet.jm.worker.DownloadComicWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class DownloadManager(
    private val context: Context,
    private val downloadComicDao: DownloadComicDao,
    private val scope: CoroutineScope,
    private val toastManager: ToastManager,
) {
    fun downloadComic(comic: Comic) {
        scope.launch(Dispatchers.IO) {
            if (downloadComicDao.getById(comic.id) != null) {
                toastManager.showAsync("该漫画已在下载列表中")
                return@launch
            }
            downloadComicDao.insert(
                DownloadComic(
                    id = comic.id,
                    name = comic.name,
                    authorList = comic.authorList,
                    coverPath = "",
                    zipPath = "",
                    progress = 0f,
                    status = "pending",
                    createTime = System.currentTimeMillis()
                )
            )
            toastManager.showAsync("创建下载任务成功")
            enqueue(comic.id, ExistingWorkPolicy.KEEP)
        }
    }

    fun retryDownload(comic: DownloadComic) {
        scope.launch(Dispatchers.IO) {
            downloadComicDao.update(
                comic.copy(status = "pending", progress = 0f, zipPath = "")
            )
            enqueue(comic.id, ExistingWorkPolicy.REPLACE)
            toastManager.showAsync("已重新创建下载任务")
        }
    }

    fun deleteDownload(comic: DownloadComic) {
        scope.launch(Dispatchers.IO) {
            val workManager = WorkManager.getInstance(context)
            runCatching {
                workManager.cancelUniqueWork(workName(comic.id)).result.get()
            }

            val downloadRoot = getDownloadDir(context)
            val storedPath = comic.zipPath.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
            val pageDir = storedPath?.takeIf { it.isDirectory }
                ?: storedPath?.parentFile?.resolve(comic.id.toString())
                ?: downloadRoot.resolve(comic.id.toString())
            val targets = listOfNotNull(
                pageDir,
                storedPath?.takeIf { it.isFile },
                comic.coverPath.takeIf { it.isNotBlank() }?.let { java.io.File(it) },
            )
            val filesDeleted = targets
                .map { runCatching { deleteWithin(downloadRoot, it) }.getOrDefault(false) }
                .all { it }
            runCatching { getCommonPicDecodeCacheDir(context, comic.id).deleteRecursively() }
            if (filesDeleted) {
                downloadComicDao.delete(comic)
                toastManager.showAsync("已删除下载")
            } else {
                toastManager.showAsync("部分下载文件删除失败，请重试")
            }
        }
    }

    private fun enqueue(comicId: Int, policy: ExistingWorkPolicy) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val downloadRequest = OneTimeWorkRequestBuilder<DownloadComicWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf("comicId" to comicId))
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(comicId),
            policy,
            downloadRequest
        )
    }

    private fun workName(comicId: Int) = "download-comic-$comicId"

    private fun deleteWithin(root: java.io.File, target: java.io.File): Boolean {
        val canonicalRoot = root.canonicalFile
        val canonicalTarget = target.canonicalFile
        if (canonicalTarget == canonicalRoot || !canonicalTarget.toPath().startsWith(canonicalRoot.toPath())) {
            return false
        }
        return !canonicalTarget.exists() || canonicalTarget.deleteRecursively()
    }
}
