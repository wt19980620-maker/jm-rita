package com.par9uet.jm.ui.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.par9uet.jm.data.models.ComicSearchOrderFilter
import com.par9uet.jm.repository.ComicRepository
import com.par9uet.jm.retrofit.model.ComicListResponse
import com.par9uet.jm.retrofit.model.NetWorkResult
import com.par9uet.jm.store.HistorySearchManager
import com.par9uet.jm.ui.models.CommonUIState
import com.par9uet.jm.utils.parseComicCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class ComicSearchViewModel(
    private val comicRepository: ComicRepository,
    private val historySearchManager: HistorySearchManager
) : ViewModel() {

    // 漫画代码在本地解析后直接跳详情；关键词和标签先请求搜索接口，
    // 由服务端决定展示列表还是重定向到唯一结果。
    data class ComicSearchResult(
        val type: String, // "redirect" | "page" 重定向或者跳转到列表页
        val redirect: Int?,
        val content: String
    )

    private val _comicSearchResultState = MutableStateFlow<CommonUIState<ComicSearchResult>>(
        CommonUIState()
    )
    val comicSearchResultState = _comicSearchResultState.asStateFlow()

    fun search(
        content: String
    ) {
        viewModelScope.launch {
            val normalizedContent = content.trim()
            _comicSearchResultState.update {
                it.copy(
                    isLoading = true,
                    isError = false,
                    errorMsg = "",
                    data = null
                )
            }
            if (normalizedContent.isEmpty()) {
                _comicSearchResultState.update {
                    it.copy(
                        isLoading = false,
                        isError = true,
                        errorMsg = "请输入关键词、标签或漫画代码"
                    )
                }
                return@launch
            }
            parseComicCode(normalizedContent)?.let { comicId ->
                _comicSearchResultState.update {
                    it.copy(
                        isLoading = false,
                        data = ComicSearchResult(
                            type = "redirect",
                            redirect = comicId,
                            content = normalizedContent
                        )
                    )
                }
                return@launch
            }
            when (val data =
                comicRepository.getComicList(1, ComicSearchOrderFilter.NEWEST, normalizedContent)) {
                is NetWorkResult.Error -> {
                    _comicSearchResultState.update {
                        it.copy(isError = true, errorMsg = data.message)
                    }
                }

                is NetWorkResult.Success<ComicListResponse> -> {
                    val r = data.data.redirect_aid?.toIntOrNull()
                    _comicSearchResultState.update {
                        it.copy(data = ComicSearchResult(
                            type = if (r != null) "redirect" else "page",
                            redirect = r,
                            content = normalizedContent,
                        ))
                    }
                }
            }
            _comicSearchResultState.update {
                it.copy(isLoading = false)
            }
        }
    }

    fun addHistoryItem(text: String) {
        viewModelScope.launch {
            delay(1000.milliseconds)
            historySearchManager.addItem(text)
        }
    }
}
