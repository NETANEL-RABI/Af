package com.videodownloader.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videodownloader.model.VideoInfo
import com.videodownloader.repository.VideoRepository
import kotlinx.coroutines.launch

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val videoInfo: VideoInfo) : UiState()
    data class Error(val message: String) : UiState()
}

class MainViewModel : ViewModel() {

    private val repository = VideoRepository()

    private val _uiState = MutableLiveData<UiState>()
    val uiState: LiveData<UiState> = _uiState

    init {
        _uiState.value = UiState.Idle
    }

    fun fetchVideo(url: String) {
        if (url.isBlank()) {
            _uiState.value = UiState.Error("אנא הכנס קישור לסרטון")
            return
        }

        if (!url.startsWith("http")) {
            _uiState.value = UiState.Error("קישור לא תקין - חייב להתחיל עם http")
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val result = repository.fetchVideoInfo(url)
            result.fold(
                onSuccess = { videoInfo ->
                    _uiState.value = UiState.Success(videoInfo)
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "שגיאה לא ידועה")
                }
            )
        }
    }

    fun reset() {
        _uiState.value = UiState.Idle
    }
}
