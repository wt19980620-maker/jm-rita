package com.par9uet.jm.ui.navigation

import android.net.Uri

fun comicSearchResultRoute(query: String): String =
    "comicSearchResult/${Uri.encode(query.trim())}"
