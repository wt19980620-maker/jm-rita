package com.par9uet.jm.store

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

// Cancellation alone does not guarantee a worker has stopped writing files.
object DownloadLocks {
    private val locks = ConcurrentHashMap<Int, Mutex>()
    fun forComic(id: Int): Mutex = locks.getOrPut(id) { Mutex() }
}
