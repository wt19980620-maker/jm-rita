package com.par9uet.jm.ui.screens

import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.par9uet.jm.config.ANIME_SOURCE_REPOSITORY
import com.par9uet.jm.data.models.Anime
import com.par9uet.jm.data.models.AnimePlayback
import com.par9uet.jm.repository.AnimeRepository
import com.par9uet.jm.ui.viewModel.AnimeViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AnimeScreen(viewModel: AnimeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()

    state.selectedAnime?.let {
        Dialog(onDismissRequest = viewModel::closeDetails) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        state.details?.anime?.title ?: it.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    when {
                        state.isDetailsLoading -> Box(
                            Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                        state.playback != null -> AnimePlayer(state.playback!!)
                        state.isPlaybackLoading -> Box(
                            Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                        state.playbackError != null -> Text(
                            state.playbackError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    state.details?.description?.takeIf(String::isNotBlank)?.let { description ->
                        Text(
                            description,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    state.details?.episodes?.let { episodes ->
                        Text("选择剧集（${episodes.size}）", style = MaterialTheme.typography.titleSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(episodes, key = { episode -> episode.url }) { episode ->
                                FilterChip(
                                    selected = state.selectedEpisode == episode,
                                    onClick = { viewModel.playEpisode(episode) },
                                    label = { Text(episode.name.ifBlank { "播放" }) },
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = viewModel::closeDetails) { Text("关闭") }
                    }
                }
            }
        }
    }

    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.Adaptive(150.dp),
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
                    label = { Text("搜索动漫") },
                    trailingIcon = {
                        IconButton(onClick = viewModel::submitSearch) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.sources, key = { source -> source.packageName }) { source ->
                            AssistChip(
                                onClick = { viewModel.selectSource(source) },
                                label = { Text("${source.name} · ${source.version}") },
                                leadingIcon = if (source == state.selectedSource) {
                                    { Icon(Icons.Default.PlayCircle, null, Modifier.size(18.dp)) }
                                } else null,
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.syncSources() }, enabled = !state.isSyncingSources) {
                        if (state.isSyncingSources) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "更新动漫源")
                        }
                    }
                }
                Text(
                    state.sourceMessage
                        ?: "源目录来自 $ANIME_SOURCE_REPOSITORY，每 6 小时自动同步；断网时使用缓存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        gridItems(state.anime, key = { it.url }) { anime ->
            AnimeCard(anime = anime, onClick = { viewModel.openAnime(anime) })
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.isLoading -> CircularProgressIndicator()
                    state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = viewModel::refresh) { Text("重试") }
                    }
                    state.hasMore -> OutlinedButton(onClick = viewModel::loadMore) { Text("加载更多") }
                    state.anime.isEmpty() -> Text("没有找到相关动漫")
                    else -> Text("已经到底了")
                }
            }
        }
    }
}

@Composable
private fun AnimeCard(anime: Anime, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        AsyncImage(
            model = anime.thumbnailUrl,
            contentDescription = anime.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
            contentScale = ContentScale.Crop,
        )
        Text(
            anime.title,
            modifier = Modifier.padding(10.dp).heightIn(min = 40.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
@OptIn(UnstableApi::class)
private fun AnimePlayer(playback: AnimePlayback) {
    val context = LocalContext.current
    val player = androidx.compose.runtime.remember(playback) {
        val dataSource = DefaultHttpDataSource.Factory()
            .setUserAgent(AnimeRepository.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf("Referer" to playback.referer))
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .build()
            .apply {
                setMediaItem(
                    MediaItem.Builder()
                        .setUri(playback.streamUrl)
                        .setMimeType(
                            if (playback.streamUrl.contains(".m3u8", true)) {
                                MimeTypes.APPLICATION_M3U8
                            } else {
                                MimeTypes.VIDEO_MP4
                            },
                        )
                        .build(),
                )
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        factory = { PlayerView(it).apply { this.player = player } },
        update = { it.player = player },
    )
}
