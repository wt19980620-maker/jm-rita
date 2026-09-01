package com.par9uet.jm.store

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import com.par9uet.jm.BuildConfig
import com.par9uet.jm.repository.AppRelease
import com.par9uet.jm.repository.GitHubUpdateRepository
import com.par9uet.jm.repository.VersionComparator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUpdateState(
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
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
    private val _state = MutableStateFlow(AppUpdateState())
    val state = _state.asStateFlow()
    private var currentDownloadId: Long? = null

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (completedId == currentDownloadId) {
                handleDownloadCompleted(completedId)
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            context,
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
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
        if (_state.value.isDownloading) return
        val fileName = "jm-rita_${release.tagName}_${System.currentTimeMillis()}.apk"
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("JM RITA ${release.tagName}")
            .setDescription("正在从 GitHub 下载最新版安装包")
            .setMimeType(APK_MIME_TYPE)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
        currentDownloadId = downloadManager.enqueue(request)
        _state.update { it.copy(isDownloading = true, errorMsg = "") }
        toastManager.showAsync("已开始下载，完成后将打开系统安装界面")
    }

    private fun handleDownloadCompleted(downloadId: Long) {
        _state.update { it.copy(isDownloading = false) }
        val uri = downloadManager.getUriForDownloadedFile(downloadId)
        if (uri == null) {
            toastManager.showAsync("安装包下载失败")
            return
        }
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(installIntent) }
            .onFailure { toastManager.showAsync("无法打开安装界面，请从系统下载目录安装") }
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
