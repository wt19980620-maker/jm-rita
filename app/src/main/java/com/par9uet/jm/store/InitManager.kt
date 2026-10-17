package com.par9uet.jm.store

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.supervisorScope
import com.par9uet.jm.task.AppInitTask
import com.par9uet.jm.utils.log

class InitManager {
    val deferred = CompletableDeferred<String>()
    private var job: Job? = null

    @Synchronized
    fun start(tasks: List<AppInitTask>, scope: CoroutineScope): Job {
        return job ?: scope.launch {
            val ordered = tasks.sortedBy { it.getAppTaskInfo().sort }
            try {
                withContext(Dispatchers.IO) { ordered.forEach { it.init() } }
                deferred.complete("")
            } catch (e: Exception) {
                deferred.completeExceptionally(e)
                throw e
            }
            // 网络刷新只在本地状态恢复后开始，不阻塞首屏请求。
            ordered.groupBy { it.getAppTaskInfo().refreshStage }.toSortedMap().values.forEach { stage ->
                supervisorScope {
                    stage.forEach { task ->
                        launch {
                            try {
                                task.refresh()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                log("后台初始化失败：${task.getAppTaskInfo().taskName}，${e.javaClass.simpleName}")
                            }
                        }
                    }
                }
            }
        }.also { job = it }
    }
}
