package com.par9uet.jm.store

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import coil.ImageLoader
import com.par9uet.jm.cache.BoundedFileCache
import com.par9uet.jm.cache.DECODED_IMAGE_CACHE_BYTES
import com.par9uet.jm.cache.IMAGE_CACHE_BYTES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import com.par9uet.jm.utils.log
import com.par9uet.jm.task.AppInitTask
import com.par9uet.jm.task.AppTaskInfo

@OptIn(coil.annotation.ExperimentalCoilApi::class)
class ImageCacheManager(context: Context, private val imageLoader: ImageLoader) : AppInitTask {
    private val decoded = BoundedFileCache(File(context.cacheDir, "pic_decode"), DECODED_IMAGE_CACHE_BYTES)
    private val operations = Mutex()
    private val _sizeBytes = MutableStateFlow<Long?>(null)
    val sizeBytes = _sizeBytes.asStateFlow()
    val maxBytes = IMAGE_CACHE_BYTES

    suspend fun read(comicId: Int, page: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            decoded.read("$comicId/$page.webp") { BitmapFactory.decodeFile(it.absolutePath) }
        } catch (e: IOException) {
            log("图片缓存读取失败：${e.javaClass.simpleName}")
            null
        }
    }

    suspend fun write(comicId: Int, page: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        decoded.write("$comicId/$page.webp") { file ->
            file.outputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 50, out)) {
                    throw IOException("图片缓存保存失败")
                }
            }
        }
    }

    suspend fun refreshUsage() = operations.withLock {
        withContext(Dispatchers.IO) {
            _sizeBytes.value = decoded.refresh() + (imageLoader.diskCache?.size ?: 0L)
        }
    }

    suspend fun clear() = operations.withLock {
        withContext(Dispatchers.IO) {
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
            decoded.clear()
            _sizeBytes.value = decoded.sizeBytes() + (imageLoader.diskCache?.size ?: 0L)
        }
    }

    override suspend fun init() {}

    override suspend fun refresh() { refreshUsage() }

    override fun getAppTaskInfo() = AppTaskInfo("整理图片缓存", 5)
}
