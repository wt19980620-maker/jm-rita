package com.par9uet.jm.ui.screens.localSettingScreen

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
            Text(
                when {
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
        },
        trailingContent = {
            when {
                state.isChecking || state.isDownloading -> {
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
