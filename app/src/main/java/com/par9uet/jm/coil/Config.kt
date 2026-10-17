package com.par9uet.jm.coil

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import com.par9uet.jm.cache.getCommonCacheDir
import com.par9uet.jm.cache.ORIGINAL_IMAGE_CACHE_BYTES

fun createAsyncImageLoader(context: Context): ImageLoader {
    return ImageLoader.Builder(context)
        .diskCache {
            DiskCache.Builder()
                .directory(getCommonCacheDir(context)) // 自定义目录
                .maxSizeBytes(ORIGINAL_IMAGE_CACHE_BYTES)
                .build()
        }
        .build()
}
