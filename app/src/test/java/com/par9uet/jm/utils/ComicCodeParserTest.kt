package com.par9uet.jm.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComicCodeParserTest {
    @Test
    fun parsesPlainAndPrefixedCodes() {
        assertEquals(123456, parseComicCode("123456"))
        assertEquals(123456, parseComicCode(" JM123456 "))
        assertEquals(123456, parseComicCode("jm # 123456"))
    }

    @Test
    fun leavesKeywordsAndInvalidValuesForNormalSearch() {
        assertNull(parseComicCode("3D"))
        assertNull(parseComicCode("推荐 123456"))
        assertNull(parseComicCode("0"))
        assertNull(parseComicCode("99999999999"))
    }
}
