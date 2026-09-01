package com.par9uet.jm.ui.screens.localSettingScreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.par9uet.jm.store.LocalSettingManager
import com.par9uet.jm.store.ToastManager
import kotlinx.coroutines.launch
import org.koin.compose.getKoin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiSettingListItem(
    localSettingManager: LocalSettingManager = getKoin().get(),
    toastManager: ToastManager = getKoin().get(),
) {
    val localSetting by localSettingManager.localSettingState.collectAsState()
    val apiLineChecks by localSettingManager.apiLineChecks.collectAsState()
    val isRefreshing by localSettingManager.isApiLineRefreshing.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val checkMap = remember(apiLineChecks) { apiLineChecks.associateBy { it.api } }
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = {
            showBottomSheet = true
        }),
        headlineContent = {
            Text("API 接口")
        },
        supportingContent = {
            Text("${localSetting.api}\n自动检测并在当前线路失效时切换")
        },
        trailingContent = {
            Row {
                IconButton(
                    enabled = !isRefreshing,
                    onClick = {
                        coroutineScope.launch {
                            val success = localSettingManager.refreshApiLines()
                            toastManager.showAsync(
                                if (success) "API 线路检测完成" else "没有检测到可用 API 线路"
                            )
                        }
                    },
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "重新检测 API 线路")
                    }
                }
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = "弹出底部设置"
                )
            }
        }
    )
    if (showBottomSheet) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = {
                showBottomSheet = false
            }
        ) {
            localSetting.apiList.forEach { api ->
                val check = checkMap[api]
                ListItem(
                    colors = ListItemDefaults.colors().copy(
                        containerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .clickable(onClick = {
                            localSettingManager.updateApi(api)
                            showBottomSheet = false
                        }),
                    headlineContent = {
                        Text(api)
                    },
                    supportingContent = {
                        Text(
                            when {
                                check == null -> "未检测"
                                check.isAvailable -> "可用 · ${check.latencyMs} ms"
                                else -> "不可用"
                            }
                        )
                    },
                    trailingContent = {
                        if (localSetting.api == api) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = "选中$api")
                        }
                    }
                )
            }
        }
    }
}
