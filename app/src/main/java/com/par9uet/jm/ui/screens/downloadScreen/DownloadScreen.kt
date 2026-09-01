package com.par9uet.jm.ui.screens.downloadScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.AlertDialog
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
    val scrollState = rememberScrollState()
    val downloadFilter by downloadViewModel.downloadFilterState.collectAsState()
    val selectedTabIndex = tabList.indexOfFirst { it.second == downloadFilter.status }
        .coerceAtLeast(0)
    val downloadLazyPagingItems = downloadViewModel.downloadPager.collectAsLazyPagingItems()
    val onTabClick: (index: Int) -> Unit = {
        downloadViewModel.updateDownloadStatusFilter(tabList[it].second)
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
            val isRefreshing = downloadLazyPagingItems.loadState.refresh is LoadState.Loading
            PullToRefreshBox(
                modifier = Modifier.weight(1f),
                isRefreshing = isRefreshing,
                onRefresh = {
                    downloadLazyPagingItems.refresh()
                }
            ) {
                LazyVerticalGrid(
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
                                    if (item.status == "complete") {
                                        mainNavController.navigate("comicRead/${item.id}")
                                    }
                                },
                                onRetry = {
                                    downloadViewModel.retryDownload(item)
                                },
                                onDelete = {
                                    pendingDelete = item
                                }
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
