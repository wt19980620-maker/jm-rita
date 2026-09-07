package com.par9uet.jm.ui.screens.downloadScreen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.par9uet.jm.database.model.DownloadComic
import com.par9uet.jm.utils.shimmer
import org.koin.compose.getKoin
import java.io.File

@Composable
private fun ComicCoverImage(
    comic: DownloadComic,
    imageLoader: ImageLoader = getKoin().get()
) {
    if (comic.coverPath.isNotBlank()) {
        val file = File(comic.coverPath)
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = file,
                imageLoader = imageLoader,
                contentDescription = "${comic.name}的封面",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .aspectRatio(3f / 4f)
                    .fillMaxWidth(),
            )
        }
    } else {
        Box(modifier = Modifier
            .aspectRatio(3 / 4f)
            .shimmer()) {
        }
    }
}

@Composable
fun DownloadListItem(
    modifier: Modifier = Modifier,
    comic: DownloadComic,
    onClick: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onPause: () -> Unit,
    selected: Boolean = false,
    selecting: Boolean = false,
) {
    Card(
        onClick = onClick
    ) {
        Box(modifier = modifier) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ComicCoverImage(comic)
                Text(
                    modifier = Modifier
                        .padding(horizontal = 8.dp),
                    text = comic.name,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                )
                Text(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 8.dp),
                    text = comic.authorList.joinToString(",").ifBlank { "暂无作者" },
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (comic.status) {
                "pending", "downloading", "error", "paused" -> {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                    )
                }

                else -> {

                }
            }
            when (comic.status) {
                "pending" -> {
                    Text(
                        modifier = Modifier.align(Alignment.Center),
                        text = "等待中",
                        color = MaterialTheme.colorScheme.surface
                    )
                }

                "downloading" -> {
                    val animatedProgress by animateFloatAsState(
                        targetValue = comic.progress,
                        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                        label = "progressAnimation"
                    )
                    CircularProgressIndicator(
                        progress = {
                            animatedProgress
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                "error", "paused" -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        Text(
                            text = if (comic.status == "paused") "已暂停" else "下载失败",
                            color = MaterialTheme.colorScheme.surface
                        )
                        TextButton(onClick = {
                            onRetry()
                        }) {
                            Text("继续下载")
                        }
                    }

                }

                else -> {

                }
            }
            if (comic.status in listOf("pending", "downloading")) {
                TextButton(modifier = Modifier.align(Alignment.BottomCenter), onClick = onPause) {
                    Text("暂停 ${(comic.progress * 100).toInt()}%")
                }
            }
            if (selecting) {
                androidx.compose.material3.Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
            IconButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                onClick = onDelete,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除下载",
                    tint = Color.White,
                )
            }
        }
        if (comic.errorMessage.isNotBlank()) {
            Text(comic.errorMessage, modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall, maxLines = 4)
        }
        if (comic.status == "complete") {
            TextButton(onClick = onRetry) { Text("校验并修复") }
        }
    }
}
