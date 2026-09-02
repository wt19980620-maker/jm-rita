package com.par9uet.jm.data.models

data class PornhubVideo(
    val video_id: String = "",
    val title: String = "",
    val duration: String = "",
    val views: Long = 0,
    val rating: Double = 0.0,
    val default_thumb: String = "",
    val thumb: String = "",
    val publish_date: String = "",
    val thumbs: List<PornhubVideoThumb> = emptyList(),
)

data class PornhubVideoThumb(
    val size: String = "",
    val width: String = "",
    val height: String = "",
    val src: String = "",
)

data class PornhubPlayback(
    val pageUrl: String,
    val streams: List<PornhubMediaStream>,
)

data class PornhubMediaStream(
    val url: String,
    val format: String,
    val quality: Int?,
) {
    val label: String
        get() = quality?.let { "${it}p" }
            ?: if (format.equals("hls", ignoreCase = true)) "自动" else format.uppercase()
}
