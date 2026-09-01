package com.par9uet.jm.ui.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.par9uet.jm.database.dao.DownloadComicDao
import com.par9uet.jm.database.model.DownloadComic
import com.par9uet.jm.store.DownloadManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update

data class DownloadFilter(
    val status: String,
)

class DownloadViewModel(
    private val downloadComicDao: DownloadComicDao,
    private val downloadManager: DownloadManager,
) : ViewModel() {
    private val _downloadFilterState = MutableStateFlow(DownloadFilter("downloading"))
    val downloadFilterState = _downloadFilterState.asStateFlow()

    fun updateDownloadStatusFilter(status: String) {
        _downloadFilterState.update {
            it.copy(
                status = status
            )
        }
    }

    fun retryDownload(comic: DownloadComic) {
        downloadManager.retryDownload(comic)
    }

    fun deleteDownload(comic: DownloadComic) {
        downloadManager.deleteDownload(comic)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val downloadPager = _downloadFilterState.flatMapLatest { filter ->
        Pager(
            config = PagingConfig(
                pageSize = 20,
                prefetchDistance = 6,
                initialLoadSize = 20
            ),
        ) {
            if (filter.status == "downloading") {
                downloadComicDao.getDownloadingList()
            } else {
                downloadComicDao.getCompleteList()
            }
        }.flow
    }.cachedIn(viewModelScope)
}
