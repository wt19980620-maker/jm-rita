package com.par9uet.jm.ui.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.par9uet.jm.data.models.PornhubPlayback
import com.par9uet.jm.data.models.PornhubVideo
import com.par9uet.jm.repository.PornhubVideoRepository
import com.par9uet.jm.storage.AdultContentConsentStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class VideoSort(val apiValue: String, val label: String) {
    MostViewed("mostviewed", "最多观看"),
    Newest("newest", "最新发布"),
    Rating("rating", "最高评分"),
}

data class VideoUiState(
    val ageAccepted: Boolean = false,
    val query: String = "",
    val sort: VideoSort = VideoSort.MostViewed,
    val videos: List<PornhubVideo> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
    val playbackVideoId: String? = null,
    val playback: PornhubPlayback? = null,
    val isPlaybackLoading: Boolean = false,
    val playbackError: String? = null,
)

class VideoViewModel(
    private val repository: PornhubVideoRepository,
    private val consentStorage: AdultContentConsentStorage,
) : ViewModel() {
    private var playbackJob: Job? = null
    private val _state = MutableStateFlow(
        VideoUiState(ageAccepted = consentStorage.isAccepted())
    )
    val state = _state.asStateFlow()

    init {
        if (_state.value.ageAccepted) refresh()
    }

    fun acceptAgeGate() {
        consentStorage.accept()
        _state.update { it.copy(ageAccepted = true) }
        refresh()
    }

    fun updateQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun submitSearch() = refresh()

    fun updateSort(sort: VideoSort) {
        if (_state.value.sort == sort) return
        _state.update { it.copy(sort = sort) }
        refresh()
    }

    fun refresh() {
        loadPage(page = 1, replace = true)
    }

    fun loadMore() {
        val current = _state.value
        if (current.isLoading || !current.hasMore) return
        loadPage(page = current.page + 1, replace = false)
    }

    fun loadPlayback(videoId: String) {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    playbackVideoId = videoId,
                    playback = null,
                    isPlaybackLoading = true,
                    playbackError = null,
                )
            }
            runCatching { repository.resolvePlayback(videoId) }
                .onSuccess { playback ->
                    _state.update { current ->
                        if (current.playbackVideoId != videoId) current
                        else current.copy(playback = playback, isPlaybackLoading = false)
                    }
                }
                .onFailure { throwable ->
                    _state.update { current ->
                        if (current.playbackVideoId != videoId) current
                        else current.copy(
                            isPlaybackLoading = false,
                            playbackError = throwable.message ?: "视频地址解析失败",
                        )
                    }
                }
        }
    }

    fun clearPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        _state.update {
            it.copy(
                playbackVideoId = null,
                playback = null,
                isPlaybackLoading = false,
                playbackError = null,
            )
        }
    }

    private fun loadPage(page: Int, replace: Boolean) {
        val current = _state.value
        if (!current.ageAccepted || current.isLoading) return
        val query = current.query
        val sort = current.sort
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                repository.search(query, sort.apiValue, page)
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        videos = if (replace) result else it.videos + result,
                        page = page,
                        hasMore = result.size >= 30,
                        isLoading = false,
                    )
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = throwable.message ?: "视频加载失败",
                    )
                }
            }
        }
    }
}
