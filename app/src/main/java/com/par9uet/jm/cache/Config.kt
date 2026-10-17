package com.par9uet.jm.cache

import android.content.Context
import com.par9uet.jm.utils.tryCreateDir
import java.io.File

const val ORIGINAL_IMAGE_CACHE_BYTES = 200L * 1024 * 1024
const val DECODED_IMAGE_CACHE_BYTES = 300L * 1024 * 1024
const val IMAGE_CACHE_BYTES = ORIGINAL_IMAGE_CACHE_BYTES + DECODED_IMAGE_CACHE_BYTES

fun getCommonCacheDir(context: Context) = tryCreateDir(File(context.cacheDir, "common"))
fun getCommonPicDecodeCacheDir(context: Context, comicId: Int) = tryCreateDir(File(context.cacheDir, "pic_decode/$comicId"))
fun getDownloadDir(context: Context) = tryCreateDir(File(context.filesDir, "download"))
