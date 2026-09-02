package com.par9uet.jm.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class PornhubVideoUrlTest {
    @Test
    fun buildsChineseOfficialVideoPage() {
        assertEquals(
            "https://cn.pornhub.com/view_video.php?viewkey=abc123",
            PornhubVideoUrl.officialPage(" abc123 "),
        )
    }

    @Test
    fun encodesUnexpectedCharactersInVideoId() {
        assertEquals(
            "https://cn.pornhub.com/view_video.php?viewkey=a%26b",
            PornhubVideoUrl.officialPage("a&b"),
        )
    }

    @Test
    fun buildsPlaybackPageOnExtractorHost() {
        assertEquals(
            "https://www.pornhub.com/view_video.php?viewkey=abc123",
            PornhubVideoUrl.playbackPage("abc123"),
        )
    }
}
