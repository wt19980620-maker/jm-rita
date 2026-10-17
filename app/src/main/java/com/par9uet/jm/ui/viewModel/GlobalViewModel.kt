package com.par9uet.jm.ui.viewModel

import androidx.lifecycle.ViewModel
import com.par9uet.jm.store.InitManager
import com.par9uet.jm.task.AppInitTask
import kotlinx.coroutines.CoroutineScope

class GlobalViewModel(
    private val appInitTaskList: List<AppInitTask>,
    private val initManager: InitManager,
    private val scope: CoroutineScope,
) : ViewModel() {

    fun init() {
        initManager.start(appInitTaskList, scope)
    }
}
