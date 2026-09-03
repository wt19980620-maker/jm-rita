package com.par9uet.jm.data.models

data class AnimeSource(
    val packageName: String,
    val name: String,
    val version: String,
    val baseUrl: String,
)

data class Anime(
    val title: String,
    val url: String,
    val thumbnailUrl: String,
)

data class AnimeEpisode(
    val name: String,
    val url: String,
)

data class AnimeDetails(
    val anime: Anime,
    val description: String,
    val episodes: List<AnimeEpisode>,
)

data class AnimePlayback(
    val pageUrl: String,
    val streamUrl: String,
    val referer: String,
)
