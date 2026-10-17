package com.par9uet.jm.ui.screens.localSettingScreen

import android.text.format.Formatter
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.par9uet.jm.store.ImageCacheManager
import com.par9uet.jm.store.ToastManager
import kotlinx.coroutines.launch
import org.koin.compose.getKoin
import java.io.IOException

@Composable
fun CacheSettingListItem(
    imageCacheManager: ImageCacheManager = getKoin().get(),
    toastManager: ToastManager = getKoin().get(),
) {
    val context = LocalContext.current
    val size by imageCacheManager.sizeBytes.collectAsState()
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        try {
            imageCacheManager.refreshUsage()
        } catch (e: IOException) {
            toastManager.showAsync("缓存统计失败，请重新进入设置重试")
        }
    }
    ListItem(
        headlineContent = { Text("图片缓存") },
        supportingContent = {
            val usage = size?.let { Formatter.formatShortFileSize(context, it) } ?: "计算中"
            val limit = imageCacheManager.maxBytes / (1024 * 1024)
            Text("已用 $usage / 上限 $limit MiB\n自动清理较早访问的图片，已下载漫画不受影响")
        },
        trailingContent = {
            TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    try {
                        imageCacheManager.clear()
                        toastManager.showAsync("图片缓存已清理")
                    } catch (e: IOException) {
                        toastManager.showAsync("部分缓存未能清理，请重试")
                    } finally {
                        busy = false
                    }
                }
            }) { Text(if (busy) "清理中" else "清理") }
        },
    )
}
