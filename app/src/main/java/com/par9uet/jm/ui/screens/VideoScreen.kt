package com.par9uet.jm.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.par9uet.jm.config.VIDEO_PLAYBACK_SOURCE_WEBSITE
import com.par9uet.jm.config.VIDEO_SOURCE_NAME
import com.par9uet.jm.data.models.PornhubPlayback
import com.par9uet.jm.data.models.PornhubVideo
import com.par9uet.jm.repository.PornhubVideoUrl
import com.par9uet.jm.store.ToastManager
import com.par9uet.jm.ui.screens.tabScreen.BottomNav
import com.par9uet.jm.ui.screens.tabScreen.LocalTabNavController
import com.par9uet.jm.ui.viewModel.VideoSort
import com.par9uet.jm.ui.viewModel.VideoViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.getKoin

@Composable
fun VideoScreen(
    viewModel: VideoViewModel = koinViewModel(),
    toastManager: ToastManager = getKoin().get(),
) {
    val state by viewModel.state.collectAsState()
    if (!state.ageAccepted) {
        VideoAgeGate(onAccept = viewModel::acceptAgeGate)
        return
    }

    val context = LocalContext.current
    var previewVideo by remember { mutableStateOf<PornhubVideo?>(null) }
    val openOfficialPage: (PornhubVideo) -> Unit = { video ->
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(PornhubVideoUrl.officialPage(video.video_id)),
        )
        runCatching { context.startActivity(intent) }
            .onFailure { toastManager.showAsync("没有可打开视频页面的浏览器") }
    }

    previewVideo?.let { video ->
        VideoPreviewDialog(
            video = video,
            playback = state.playback.takeIf { state.playbackVideoId == video.video_id },
            isLoading = state.isPlaybackLoading && state.playbackVideoId == video.video_id,
            error = state.playbackError.takeIf { state.playbackVideoId == video.video_id },
            onRetry = { viewModel.loadPlayback(video.video_id) },
            onDismiss = {
                previewVideo = null
                viewModel.clearPlayback()
            },
            onOpenOfficialPage = { openOfficialPage(video) },
        )
    }

    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.Adaptive(170.dp),
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = state.query,
                    onValueChange = viewModel::updateQuery,
                    singleLine = true,
                    label = { Text("搜索视频") },
                    trailingIcon = {
                        IconButton(onClick = viewModel::submitSearch) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VideoSort.entries.forEach { sort ->
                        FilterChip(
                            selected = state.sort == sort,
                            onClick = { viewModel.updateSort(sort) },
                            label = { Text(sort.label) },
                        )
                    }
                }
                Text(
                    text = "内容来自 $VIDEO_SOURCE_NAME；点击卡片可在应用内动态预览，再选择是否打开官方播放页。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(state.videos, key = { it.video_id }) { video ->
            VideoCard(video = video) {
                previewVideo = video
                viewModel.loadPlayback(video.video_id)
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.isLoading -> CircularProgressIndicator()
                    state.error != null -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = viewModel::refresh) { Text("重试") }
                    }
                    state.hasMore -> OutlinedButton(onClick = viewModel::loadMore) {
                        Text("加载更多")
                    }
                    state.videos.isNotEmpty() -> Text("已经到底了")
                    else -> Text("没有找到相关视频")
                }
            }
        }
    }
}

@Composable
private fun VideoPreviewDialog(
    video: PornhubVideo,
    playback: PornhubPlayback?,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onOpenOfficialPage: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        isLoading -> CircularProgressIndicator()
                        error != null -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Text(error, color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = onRetry) { Text("重新获取播放地址") }
                        }
                        playback != null -> PornhubPlayer(
                            playback = playback,
                            onRefreshAddress = onRetry,
                        )
                    }
                }
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = video.title,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${video.duration} · ${formatViews(video.views)}次观看 · 评分 ${video.rating}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "播放地址在每次打开时重新获取，失效后可点击重新获取。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismiss) { Text("关闭") }
                        Button(onClick = onOpenOfficialPage) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text("官方页面")
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(markerClass = [UnstableApi::class])
private fun PornhubPlayer(
    playback: PornhubPlayback,
    onRefreshAddress: () -> Unit,
) {
    val context = LocalContext.current
    var selectedIndex by remember(playback) { mutableIntStateOf(0) }
    var playbackMessage by remember(playback) { mutableStateOf<String?>(null) }
    var playbackFailed by remember(playback) { mutableStateOf(false) }
    val dataSourceFactory = remember(playback.pageUrl) {
        DefaultHttpDataSource.Factory()
            .setUserAgent(
                "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/138.0.0.0 Mobile Safari/537.36"
            )
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to playback.pageUrl,
                    "Origin" to VIDEO_PLAYBACK_SOURCE_WEBSITE,
                )
            )
    }
    val player = remember(playback) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
    }

    fun play(index: Int) {
        val stream = playback.streams[index]
        selectedIndex = index
        playbackFailed = false
        val mediaItem = MediaItem.Builder()
            .setUri(stream.url)
            .setMimeType(
                if (stream.format.equals("hls", ignoreCase = true)) {
                    MimeTypes.APPLICATION_M3U8
                } else {
                    MimeTypes.VIDEO_MP4
                }
            )
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    DisposableEffect(player, playback) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val nextIndex = selectedIndex + 1
                if (nextIndex < playback.streams.size) {
                    playbackMessage = "当前清晰度不可用，已自动切换"
                    play(nextIndex)
                } else {
                    playbackMessage = "播放失败，播放地址可能已失效，请重新获取"
                    playbackFailed = true
                }
            }
        }
        player.addListener(listener)
        play(0)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { playerContext ->
                PlayerView(playerContext).apply {
                    this.player = player
                    useController = true
                }
            },
            update = { it.player = player },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            playback.streams.forEachIndexed { index, stream ->
                FilterChip(
                    selected = selectedIndex == index,
                    onClick = {
                        playbackMessage = null
                        play(index)
                    },
                    label = { Text(stream.label) },
                )
            }
            playbackMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (playbackFailed) {
                    TextButton(onClick = onRefreshAddress) {
                        Text("重新获取")
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoAgeGate(onAccept: () -> Unit) {
    val tabNavController = LocalTabNavController.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "成人内容确认",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "该模块包含第三方成人内容。请确认你已达到所在地法定年龄，并遵守当地法律及内容来源网站的使用条款。",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onAccept,
        ) {
            Text("我已达到法定年龄，继续")
        }
        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            onClick = {
                tabNavController.navigate(BottomNav.Home.route) {
                    launchSingleTop = true
                }
            },
        ) {
            Text("返回首页")
        }
    }
}

@Composable
private fun VideoCard(
    video: PornhubVideo,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box {
            AsyncImage(
                model = video.default_thumb.ifBlank { video.thumb },
                contentDescription = video.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                contentScale = ContentScale.Crop,
            )
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(42.dp),
                tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            )
        }
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = video.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${video.duration} · ${formatViews(video.views)}次观看",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "应用内预览",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

private fun formatViews(views: Long): String = when {
    views >= 100_000_000 -> "${views / 100_000_000}亿"
    views >= 10_000 -> "${views / 10_000}万"
    else -> views.toString()
}
