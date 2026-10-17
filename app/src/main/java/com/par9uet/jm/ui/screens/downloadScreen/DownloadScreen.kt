package com.par9uet.jm.ui.screens.downloadScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.par9uet.jm.ui.components.CommonScaffold
import com.par9uet.jm.ui.viewModel.DownloadViewModel
import com.par9uet.jm.ui.viewModel.DownloadSort
import com.par9uet.jm.ui.screens.LocalMainNavController
import com.par9uet.jm.database.model.DownloadComic
import org.koin.compose.viewmodel.koinActivityViewModel

private val tabList = listOf("下载中" to "downloading", "已下载" to "complete")

@Composable
fun DownloadScreen(
    downloadViewModel: DownloadViewModel = koinActivityViewModel()
) {
    val mainNavController = LocalMainNavController.current
    var pendingDelete by remember { mutableStateOf<DownloadComic?>(null) }
    var pendingRepair by remember { mutableStateOf<DownloadComic?>(null) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Map<Int, DownloadComic>>(emptyMap()) }
    var confirmBatch by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    var storageRefreshKey by remember { mutableStateOf(0L) }
    val availableBytes by downloadViewModel.availableBytes.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { downloadViewModel.refreshStorage() }
    val scrollState = rememberScrollState()
    val downloadFilter by downloadViewModel.downloadFilterState.collectAsState()
    val selectedTabIndex = tabList.indexOfFirst { it.second == downloadFilter.status }
        .coerceAtLeast(0)
    val downloadLazyPagingItems = downloadViewModel.downloadPager.collectAsLazyPagingItems()
    val onTabClick: (index: Int) -> Unit = {
        selected = emptyMap()
        downloadViewModel.updateDownloadStatusFilter(tabList[it].second)
    }
    pendingRepair?.let { comic ->
        AlertDialog(
            onDismissRequest = { pendingRepair = null },
            title = { Text("校验并修复下载") },
            text = { Text("需要联网获取页表，并重新下载缺失或损坏页面。修复完成前此项目将移至下载中；旧版下载首次修复可能需要全部重下。") },
            confirmButton = { TextButton(onClick = {
                downloadViewModel.retryDownload(comic)
                pendingRepair = null
            }) { Text("开始修复") } },
            dismissButton = { TextButton(onClick = { pendingRepair = null }) { Text("取消") } },
        )
    }
    if (confirmBatch) {
        AlertDialog(
            onDismissRequest = { confirmBatch = false },
            title = { Text("删除 ${selected.size} 本下载") },
            text = { Text("将删除选中漫画的本地文件，删除后需重新下载。") },
            confirmButton = { TextButton(onClick = {
                downloadViewModel.deleteDownloads(selected.values.toList())
                selected = emptyMap()
                selecting = false
                confirmBatch = false
            }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { confirmBatch = false }) { Text("取消") } },
        )
    }
    pendingDelete?.let { comic ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除下载") },
            text = { Text("确定删除《${comic.name}》及其本地文件吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        downloadViewModel.deleteDownload(comic)
                        pendingDelete = null
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            },
        )
    }
    CommonScaffold(title = "我的下载") {
        Column {
            Text("可用空间：${availableBytes / (1024 * 1024)} MB")
            androidx.compose.foundation.layout.Row {
                TextButton(onClick = { selecting = !selecting; selected = emptyMap() }) {
                    Text(if (selecting) "完成选择" else "批量管理")
                }
                if (selecting) {
                    TextButton(onClick = {
                        selected = downloadLazyPagingItems.itemSnapshotList.items.associateBy { it.id }
                    }) { Text("选择已加载项") }
                    TextButton(enabled = selected.isNotEmpty(), onClick = { confirmBatch = true }) {
                        Text("删除(${selected.size})")
                    }
                }
            }
            OutlinedTextField(
                value = downloadFilter.query,
                onValueChange = {
                    selected = emptyMap()
                    downloadViewModel.updateDownloadSearchQuery(it)
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                placeholder = { Text("搜索名称、作者或 JM 号") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (downloadFilter.query.isNotEmpty()) {
                        IconButton(onClick = {
                            selected = emptyMap()
                            downloadViewModel.updateDownloadSearchQuery("")
                        }) { Icon(Icons.Default.Close, contentDescription = "清除搜索") }
                    }
                },
                singleLine = true,
            )
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 0.dp,
                scrollState = scrollState
            ) {
                tabList.forEachIndexed { index, item ->
                    key(item.second) {
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = {
                                onTabClick(index)
                            },
                            text = {
                                Text(
                                    text = item.first,
                                    maxLines = 1,
                                )
                            }
                        )
                    }
                }
            }
            Box {
                TextButton(onClick = { sortExpanded = true }) {
                    Text("排序：${downloadFilter.sort.label}")
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                    DownloadSort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.label) },
                            onClick = {
                                selected = emptyMap()
                                sortExpanded = false
                                downloadViewModel.updateDownloadSort(sort)
                            },
                        )
                    }
                }
            }
            val isRefreshing = downloadLazyPagingItems.loadState.refresh is LoadState.Loading
            PullToRefreshBox(
                modifier = Modifier.weight(1f),
                isRefreshing = isRefreshing,
                onRefresh = {
                    downloadViewModel.refreshStorage()
                    storageRefreshKey = System.nanoTime()
                    downloadLazyPagingItems.refresh()
                }
            ) {
                if (downloadLazyPagingItems.itemCount == 0 && !isRefreshing) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (downloadLazyPagingItems.loadState.refresh is LoadState.Error) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("下载列表加载失败")
                                TextButton(onClick = downloadLazyPagingItems::retry) { Text("重试") }
                            }
                        } else {
                            Text(if (downloadFilter.query.isBlank()) "暂无下载" else "没有找到匹配的下载")
                        }
                    }
                } else LazyVerticalGrid(
                    modifier = Modifier.fillMaxSize(),
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Top),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(10.dp)
                ) {
                    items(
                        downloadLazyPagingItems.itemCount,
                        key = downloadLazyPagingItems.itemKey { it.id },
                    ) { index ->
                        val item = downloadLazyPagingItems[index]
                        if (item != null) {
                            DownloadListItem(
                                comic = item,
                                onClick = {
                                    if (selecting) {
                                        selected = if (item.id in selected) selected - item.id else selected + (item.id to item)
                                    } else if (item.status == "complete") {
                                        mainNavController.navigate("comicRead/${item.id}")
                                    }
                                },
                                onRetry = {
                                    if (item.status == "complete") pendingRepair = item
                                    else downloadViewModel.retryDownload(item)
                                },
                                onDelete = {
                                    pendingDelete = item
                                },
                                onPause = { downloadViewModel.pauseDownload(item) },
                                selected = item.id in selected,
                                selecting = selecting,
                                storageRefreshKey = storageRefreshKey,
                            )
                        }
                    }
//                    items(count = 4, key = { it }) {
//                        DownloadListItem(
//                            comic = DownloadComic(
//                                2,
//                                "test name",
//                                listOf("test author"),
//                                "none",
//                                listOf(),
//                                "",
//                                .5f,
//                                "pending"
//                            )
//                        )
//                    }
                }
            }

        }
    }
}
