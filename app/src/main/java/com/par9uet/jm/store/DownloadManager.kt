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
import com.par9uet.jm.storage.DownloadSizeStorage
import com.par9uet.jm.worker.DownloadComicWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

class DownloadManager(
    private val context: Context,
    private val downloadComicDao: DownloadComicDao,
    private val scope: CoroutineScope,
    private val toastManager: ToastManager,
    private val downloadSizeStorage: DownloadSizeStorage,
) {
    private val operations = kotlinx.coroutines.sync.Mutex()
    private val _availableBytes = MutableStateFlow(0L)
    val availableBytes = _availableBytes.asStateFlow()

    fun refreshStorage() {
        scope.launch(Dispatchers.IO) { _availableBytes.value = getDownloadDir(context).usableSpace }
    }

    fun pauseDownload(comic: DownloadComic) {
        scope.launch(Dispatchers.IO) { operations.withLock {
            if (downloadComicDao.getById(comic.id)?.status !in listOf("pending", "downloading")) return@withLock
            downloadComicDao.setState(comic.id, "paused")
            try {
                WorkManager.getInstance(context).cancelUniqueWork(workName(comic.id)).result.get()
            } catch (_: Exception) {
                toastManager.showAsync("停止下载失败，请重试")
                return@withLock
            }
            DownloadLocks.forComic(comic.id).withLock { refreshStorage() }
        } }
    }

    fun downloadComic(comic: Comic) {
        scope.launch(Dispatchers.IO) { operations.withLock {
            if (downloadComicDao.getById(comic.id) != null) {
                toastManager.showAsync("该漫画已在下载列表中")
                return@withLock
            }
            downloadSizeStorage.forget(comic.id)
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
        } }
    }

    fun retryDownload(comic: DownloadComic) {
        scope.launch(Dispatchers.IO) { operations.withLock {
            if (downloadComicDao.getById(comic.id) == null) return@withLock
            downloadComicDao.setState(comic.id, "paused")
            try {
                WorkManager.getInstance(context).cancelUniqueWork(workName(comic.id)).result.get()
            } catch (_: Exception) {
                toastManager.showAsync("停止旧任务失败，请重试")
                return@withLock
            }
            DownloadLocks.forComic(comic.id).withLock {
                downloadComicDao.setState(comic.id, "pending")
                enqueue(comic.id, ExistingWorkPolicy.REPLACE)
            }
            toastManager.showAsync("已继续下载，将保留校验通过的页面")
        } }
    }

    fun deleteDownload(comic: DownloadComic) {
        scope.launch(Dispatchers.IO) { operations.withLock {
            val workManager = WorkManager.getInstance(context)
            downloadComicDao.setState(comic.id, "paused")
            try {
                workManager.cancelUniqueWork(workName(comic.id)).result.get()
            } catch (e: Exception) {
                toastManager.showAsync("停止下载失败，请重试删除")
                return@withLock
            }
            DownloadLocks.forComic(comic.id).withLock {
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
                downloadSizeStorage.forget(comic.id)
                downloadComicDao.delete(comic)
                toastManager.showAsync("已删除下载")
            } else {
                downloadSizeStorage.reconcile(comic)
                toastManager.showAsync("部分下载文件删除失败，请重试")
            }
            refreshStorage()
            }
        } }
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
