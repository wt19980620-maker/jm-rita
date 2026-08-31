package com.par9uet.jm.utils

import kotlin.random.Random

internal fun removeSearchHistoryItem(history: List<String>, item: String): List<String> =
    history.filterNot { it == item }

internal fun pickSearchBlindBox(
    history: List<String>,
    random: Random = Random.Default
): String? = history.randomOrNull(random)
