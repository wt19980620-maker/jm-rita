package com.par9uet.jm.ui.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.par9uet.jm.data.models.Anime
import com.par9uet.jm.data.models.AnimeDetails
import com.par9uet.jm.data.models.AnimeEpisode
import com.par9uet.jm.data.models.AnimePlayback
import com.par9uet.jm.data.models.AnimeSource
import com.par9uet.jm.repository.AnimeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AnimeUiState(
    val sources: List<AnimeSource> = emptyList(),
    val selectedSource: AnimeSource? = null,
    val query: String = "",
    val anime: List<Anime> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val isLoading: Boolean = true,
    val isSyncingSources: Boolean = false,
    val sourceMessage: String? = null,
    val error: String? = null,
    val selectedAnime: Anime? = null,
    val details: AnimeDetails? = null,
    val isDetailsLoading: Boolean = false,
    val selectedEpisode: AnimeEpisode? = null,
    val playback: AnimePlayback? = null,
    val isPlaybackLoading: Boolean = false,
    val playbackError: String? = null,
)

class AnimeViewModel(
    private val repository: AnimeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AnimeUiState())
    val state = _state.asStateFlow()
    private var detailsJob: Job? = null
    private var playbackJob: Job? = null

    init {
        syncSources(force = false)
    }

    fun syncSources(force: Boolean = true) {
        if (_state.value.isSyncingSources) return
        viewModelScope.launch {
            _state.update { it.copy(isSyncingSources = true, sourceMessage = null) }
            runCatching { repository.syncSources(force) }
                .onSuccess { sources ->
                    val previous = _state.value.selectedSource
                    val selected = sources.firstOrNull { it.packageName == previous?.packageName }
                        ?: sources.first()
                    _state.update {
                        it.copy(
                            sources = sources,
                            selectedSource = selected,
                            isSyncingSources = false,
                            sourceMessage = if (force) "动漫源目录已更新" else null,
                        )
                    }
                    refresh()
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isSyncingSources = false,
                            error = throwable.message ?: "动漫源目录同步失败",
                        )
                    }
                }
        }
    }

    fun updateQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun submitSearch() = refresh()

    fun selectSource(source: AnimeSource) {
        if (source == _state.value.selectedSource) return
        _state.update { it.copy(selectedSource = source) }
        refresh()
    }

    fun refresh() = loadPage(page = 1, replace = true)

    fun loadMore() {
        val current = _state.value
        if (current.isLoading || !current.hasMore) return
        loadPage(current.page + 1, replace = false)
    }

    fun openAnime(anime: Anime) {
        detailsJob?.cancel()
        playbackJob?.cancel()
        val source = _state.value.selectedSource ?: return
        detailsJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    selectedAnime = anime,
                    details = null,
                    isDetailsLoading = true,
                    selectedEpisode = null,
                    playback = null,
                    playbackError = null,
                )
            }
            runCatching { repository.loadDetails(source, anime) }
                .onSuccess { details ->
                    _state.update { it.copy(details = details, isDetailsLoading = false) }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isDetailsLoading = false,
                            playbackError = throwable.message ?: "动漫详情加载失败",
                        )
                    }
                }
        }
    }

    fun playEpisode(episode: AnimeEpisode) {
        playbackJob?.cancel()
        val source = _state.value.selectedSource ?: return
        playbackJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    selectedEpisode = episode,
                    playback = null,
                    isPlaybackLoading = true,
                    playbackError = null,
                )
            }
            runCatching { repository.resolvePlayback(source, episode) }
                .onSuccess { playback ->
                    _state.update { it.copy(playback = playback, isPlaybackLoading = false) }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isPlaybackLoading = false,
                            playbackError = throwable.message ?: "播放地址解析失败",
                        )
                    }
                }
        }
    }

    fun closeDetails() {
        detailsJob?.cancel()
        playbackJob?.cancel()
        _state.update {
            it.copy(
                selectedAnime = null,
                details = null,
                isDetailsLoading = false,
                selectedEpisode = null,
                playback = null,
                isPlaybackLoading = false,
                playbackError = null,
            )
        }
    }

    private fun loadPage(page: Int, replace: Boolean) {
        val current = _state.value
        val source = current.selectedSource ?: return
        if (current.isLoading && current.page > 0) return
        val query = current.query
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, sourceMessage = null) }
            runCatching { repository.loadAnime(source, query, page) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            anime = if (replace) result.items else it.anime + result.items,
                            page = page,
                            hasMore = result.hasMore,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.message ?: "动漫列表加载失败",
                        )
                    }
                }
        }
    }
}
