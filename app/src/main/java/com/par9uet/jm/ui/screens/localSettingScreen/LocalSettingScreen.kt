package com.par9uet.jm.ui.screens.localSettingScreen

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.par9uet.jm.config.SOURCE_NAME
import com.par9uet.jm.config.SOURCE_RELEASE_PAGE
import com.par9uet.jm.config.SOURCE_WEBSITE
import com.par9uet.jm.store.LocalSettingManager
import com.par9uet.jm.store.ToastManager
import com.par9uet.jm.ui.components.CommonScaffold
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.getKoin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalSettingScreen(
    localSettingManager: LocalSettingManager = getKoin().get(),
    toastManager: ToastManager = getKoin().get(),
) {
    val localSetting by localSettingManager.localSettingState.collectAsState()
    val isRefreshing by localSettingManager.isSourceWebsiteRefreshing.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    CommonScaffold(
        title = "设置"
    ) {
        Column {
            ListItem(
                headlineContent = { Text("内容来源") },
                supportingContent = {
                    Text("$SOURCE_NAME（${localSetting.sourceWebsite ?: SOURCE_WEBSITE}）\n永久发布页：$SOURCE_RELEASE_PAGE\n原生接口模式，不加载网页广告")
                },
                trailingContent = {
                    IconButton(
                        enabled = !isRefreshing,
                        onClick = {
                            coroutineScope.launch {
                                val success = localSettingManager.refreshSourceWebsite()
                                toastManager.showAsync(
                                    if (success) "发布页域名已更新" else "发布页域名检索失败，继续使用上次结果"
                                )
                            }
                        },
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "重新检索发布页域名")
                        }
                    }
                },
            )
            ThemeSettingListItem()
            ColorPaletteSettingListItem()
            ApiSettingListItem()
            AppUpdateSettingListItem()
        }
    }
}
