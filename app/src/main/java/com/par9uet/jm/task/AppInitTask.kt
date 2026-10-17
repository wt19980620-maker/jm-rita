package com.par9uet.jm.task

data class AppTaskInfo(
    val taskName: String,
    val sort: Int,
    val isError: Boolean = false,
    val errorMsg: String = "",
    val refreshStage: Int = 1,
)

interface AppInitTask {
    suspend fun init()

    suspend fun refresh() {}

    fun getAppTaskInfo(): AppTaskInfo
}
