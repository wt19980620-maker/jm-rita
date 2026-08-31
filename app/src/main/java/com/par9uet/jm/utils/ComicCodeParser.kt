package com.par9uet.jm.utils

private val COMIC_CODE_PATTERN = Regex(
    pattern = """^(?:JM\s*#?\s*)?(\d{1,10})$""",
    option = RegexOption.IGNORE_CASE
)

/**
 * Parses a standalone comic code such as `123456` or `JM123456`.
 * Text containing other words is intentionally treated as a normal keyword/tag query.
 */
fun parseComicCode(input: String): Int? {
    val value = COMIC_CODE_PATTERN.matchEntire(input.trim())
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()
        ?: return null
    return value.takeIf { it in 1..Int.MAX_VALUE }?.toInt()
}
