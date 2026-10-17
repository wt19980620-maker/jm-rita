package com.par9uet.jm.ui.screens.localSettingScreen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.par9uet.jm.BuildConfig
import com.par9uet.jm.store.AppUpdateManager
import org.koin.compose.getKoin

@Composable
fun AppUpdateSettingListItem(
    appUpdateManager: AppUpdateManager = getKoin().get(),
) {
    val state by appUpdateManager.state.collectAsState()

    LaunchedEffect(Unit) {
        appUpdateManager.checkForUpdate()
    }

    ListItem(
        headlineContent = { Text("应用更新") },
        supportingContent = {
            Column {
                Text(
                    when {
                        state.isDownloading -> "当前版本：${BuildConfig.VERSION_NAME}\n正在下载更新"
                        state.isReadyToInstall ->
                            "当前版本：${BuildConfig.VERSION_NAME}\n安装包已下载，点击安装" +
                                state.errorMsg.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty()
                        state.errorMsg.isNotBlank() ->
                            "当前版本：${BuildConfig.VERSION_NAME}\n${state.errorMsg}"
                        state.latestRelease == null ->
                            "当前版本：${BuildConfig.VERSION_NAME}"
                        state.hasUpdate ->
                            "当前版本：${BuildConfig.VERSION_NAME}\n最新版本：${state.latestRelease!!.versionName}"
                        else ->
                            "当前版本：${BuildConfig.VERSION_NAME}\n已是最新版"
                    }
                )
                if (state.isDownloading) {
                    val progress = state.downloadProgress
                    Text(
                        if (progress == null) "正在获取安装包大小…"
                        else "下载进度：${(progress * 100).toInt()}%"
                    )
                    if (progress == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        trailingContent = {
            when {
                state.isDownloading -> Unit
                state.isReadyToInstall -> {
                    TextButton(onClick = appUpdateManager::installDownloaded) {
                        Text("安装")
                    }
                }
                state.isChecking -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
                state.hasUpdate -> {
                    TextButton(onClick = appUpdateManager::downloadLatest) {
                        Icon(Icons.Default.Download, contentDescription = "下载并安装")
                        Text("下载")
                    }
                }
                state.latestRelease != null -> {
                    Icon(Icons.Default.CheckCircle, contentDescription = "已是最新版")
                }
                else -> {
                    TextButton(onClick = appUpdateManager::checkForUpdate) {
                        Icon(Icons.Default.Refresh, contentDescription = "重新检查更新")
                        Text("重试")
                    }
                }
            }
        },
    )
}
