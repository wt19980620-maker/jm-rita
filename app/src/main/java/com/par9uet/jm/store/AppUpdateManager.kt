package com.par9uet.jm.store

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.par9uet.jm.BuildConfig
import com.par9uet.jm.repository.AppRelease
import com.par9uet.jm.repository.GitHubUpdateRepository
import com.par9uet.jm.repository.VersionComparator
import com.par9uet.jm.utils.AppUpdateVerifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppUpdateState(
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isReadyToInstall: Boolean = false,
    val latestRelease: AppRelease? = null,
    val hasUpdate: Boolean = false,
    val errorMsg: String = "",
)

class AppUpdateManager(
    private val context: Context,
    private val repository: GitHubUpdateRepository,
    private val scope: CoroutineScope,
    private val toastManager: ToastManager,
) {
    private val downloadManager = context.getSystemService(DownloadManager::class.java)
    private val updateVerifier = AppUpdateVerifier(context)
    private val preferences = context.getSharedPreferences("app_update_download", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(AppUpdateState())
    val state = _state.asStateFlow()
    @Volatile
    private var currentDownloadId: Long? = preferences.getLong(DOWNLOAD_ID_KEY, -1L).takeIf { it >= 0L }
    private var monitorJob: Job? = null

    init {
        val savedVersion = preferences.getString(DOWNLOAD_VERSION_KEY, null)
        if (savedVersion != null && !VersionComparator.isNewer(savedVersion, BuildConfig.VERSION_NAME)) {
            clearDownload()
        } else {
            currentDownloadId?.let { monitorDownload(it, openInstallerOnCompletion = false) }
        }
    }

    fun checkForUpdate() {
        if (_state.value.isChecking) return
        scope.launch(Dispatchers.IO) {
            _state.update { it.copy(isChecking = true, errorMsg = "") }
            runCatching { repository.getLatestRelease() }
                .onSuccess { release ->
                    _state.update {
                        it.copy(
                            isChecking = false,
                            latestRelease = release,
                            hasUpdate = VersionComparator.isNewer(
                                latest = release.versionName,
                                current = BuildConfig.VERSION_NAME,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isChecking = false,
                            errorMsg = error.message ?: "检查更新失败",
                        )
                    }
                }
        }
    }

    fun downloadLatest() {
        val release = _state.value.latestRelease ?: return
        if (_state.value.isDownloading || currentDownloadId != null) return
        _state.update { it.copy(isDownloading = true, downloadProgress = null, errorMsg = "") }
        scope.launch(Dispatchers.IO) {
            runCatching {
                val fileName = "jm-rita_update_${System.currentTimeMillis()}.apk"
                val request = DownloadManager.Request(Uri.parse(release.apkUrl))
                    .setTitle("JM RITA ${release.tagName}")
                    .setDescription("正在从 GitHub 下载最新版安装包")
                    .setMimeType(APK_MIME_TYPE)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                    .setAllowedOverMetered(true)
                downloadManager.enqueue(request)
            }.onSuccess { downloadId ->
                currentDownloadId = downloadId
                preferences.edit()
                    .putLong(DOWNLOAD_ID_KEY, downloadId)
                    .putString(DOWNLOAD_VERSION_KEY, release.versionName)
                    .commit()
                monitorDownload(downloadId, openInstallerOnCompletion = true)
                toastManager.showAsync("已开始下载，完成后将打开系统安装界面")
            }.onFailure { error ->
                _state.update {
                    it.copy(isDownloading = false, errorMsg = "无法开始下载：${error.message.orEmpty()}")
                }
            }
        }
    }

    fun installDownloaded() {
        val downloadId = currentDownloadId ?: return
        if (_state.value.isReadyToInstall) openInstaller(downloadId)
    }

    private fun monitorDownload(downloadId: Long, openInstallerOnCompletion: Boolean) {
        monitorJob?.cancel()
        monitorJob = scope.launch(Dispatchers.IO) {
            var queryFailures = 0
            while (true) {
                val snapshot = try {
                    queryDownload(downloadId).also { queryFailures = 0 }
                } catch (_: Exception) {
                    if (++queryFailures >= 3) {
                        failDownload("无法读取下载状态，请重新下载")
                        return@launch
                    }
                    delay(1000L)
                    continue
                }
                when (snapshot?.status) {
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PAUSED -> {
                        val totalBytes = snapshot.totalBytes.coerceAtLeast(0L)
                        val downloadedBytes = snapshot.downloadedBytes.coerceAtLeast(0L)
                        _state.update {
                            it.copy(
                                isDownloading = true,
                                downloadProgress = if (totalBytes > 0L) {
                                    (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                                } else null,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                            )
                        }
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        if (runCatching { downloadManager.getUriForDownloadedFile(downloadId) }.getOrNull() == null) {
                            failDownload("安装包文件不可用，请重新下载")
                        } else {
                            _state.update {
                                it.copy(
                                    isDownloading = false,
                                    isReadyToInstall = true,
                                    downloadProgress = 1f,
                                    downloadedBytes = snapshot.downloadedBytes.coerceAtLeast(0L),
                                    totalBytes = snapshot.totalBytes.coerceAtLeast(0L),
                                )
                            }
                            if (openInstallerOnCompletion) openInstaller(downloadId)
                        }
                        return@launch
                    }
                    DownloadManager.STATUS_FAILED -> {
                        failDownload("安装包下载失败，请重试")
                        return@launch
                    }
                    else -> {
                        failDownload("下载任务已不存在，请重新下载")
                        return@launch
                    }
                }
                delay(750L)
            }
        }
    }

    private fun queryDownload(downloadId: Long): DownloadSnapshot? {
        downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return DownloadSnapshot(
                status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                downloadedBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
            )
        }
    }

    private fun openInstaller(downloadId: Long) {
        scope.launch(Dispatchers.IO) {
            val uri = runCatching { downloadManager.getUriForDownloadedFile(downloadId) }.getOrNull()
            if (uri == null) {
                failDownload("安装包文件不可用，请重新下载")
                return@launch
            }
            val verifiedUri = try {
                updateVerifier.prepare(uri)
            } catch (error: Exception) {
                failDownload("安装包安全校验失败：${error.message.orEmpty()}")
                return@launch
            }
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(verifiedUri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { withContext(Dispatchers.Main) { context.startActivity(installIntent) } }
                .onFailure {
                    _state.update { state -> state.copy(errorMsg = "无法打开安装界面，请稍后重试") }
                }
        }
    }

    private fun failDownload(message: String) {
        clearDownload()
        _state.update {
            it.copy(
                isDownloading = false,
                isReadyToInstall = false,
                downloadProgress = null,
                downloadedBytes = 0L,
                totalBytes = 0L,
                errorMsg = message,
            )
        }
        toastManager.showAsync(message)
    }

    private fun clearDownload() {
        currentDownloadId = null
        preferences.edit().remove(DOWNLOAD_ID_KEY).remove(DOWNLOAD_VERSION_KEY).apply()
    }

    private data class DownloadSnapshot(
        val status: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
    )

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val DOWNLOAD_ID_KEY = "download_id"
        const val DOWNLOAD_VERSION_KEY = "download_version"
    }
}
