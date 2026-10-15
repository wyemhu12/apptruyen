package com.personal.apptruyen.ui.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.apptruyen.data.model.SessionVerifyResult
import com.personal.apptruyen.data.model.SourceAuthConfig
import com.personal.apptruyen.data.repository.ISourceAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SourceLoginUiState(
    val config: SourceAuthConfig? = null,
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean = false,
    val showPasteDialog: Boolean = false,
    val isVerifyingPaste: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class SourceLoginViewModel
    @Inject
    constructor(
        private val authRepository: ISourceAuthRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {

        val sourceId: String = savedStateHandle.get<String>("sourceId") ?: "wikidich"

        private val _uiState = MutableStateFlow(SourceLoginUiState())
        val uiState: StateFlow<SourceLoginUiState> = _uiState.asStateFlow()

        init {
            val config = authRepository.getConfig(sourceId)
            val initialLoggedIn = authRepository.isLoggedIn(sourceId)
            _uiState.update {
                it.copy(
                    config = config,
                    isLoggedIn = initialLoggedIn,
                    isLoading = false,
                )
            }

            viewModelScope.launch {
                authRepository.getAuthState(sourceId).collect { loggedIn ->
                    _uiState.update { it.copy(isLoggedIn = loggedIn) }
                }
            }
        }

        fun onLoginDetected() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                when (val result = authRepository.verifySessionOnline(sourceId)) {
                    is SessionVerifyResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isLoggedIn = true,
                                successMessage = "Đăng nhập nguồn truyện thành công!",
                            )
                        }
                    }
                    is SessionVerifyResult.InvalidCookie -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = result.message,
                            )
                        }
                    }
                    is SessionVerifyResult.NetworkError -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = result.message,
                            )
                        }
                    }
                }
            }
        }

        fun onShowPasteDialog() {
            _uiState.update { it.copy(showPasteDialog = true, errorMessage = null) }
        }

        fun onDismissPasteDialog() {
            _uiState.update { it.copy(showPasteDialog = false) }
        }

        fun onPasteCookie(rawCookie: String) {
            viewModelScope.launch {
                _uiState.update { it.copy(isVerifyingPaste = true, errorMessage = null) }
                when (val result = authRepository.importCookies(sourceId, rawCookie)) {
                    is SessionVerifyResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isVerifyingPaste = false,
                                showPasteDialog = false,
                                isLoggedIn = true,
                                successMessage = "Dán cookie và xác thực thành công!",
                            )
                        }
                    }
                    is SessionVerifyResult.InvalidCookie -> {
                        _uiState.update {
                            it.copy(
                                isVerifyingPaste = false,
                                errorMessage = result.message,
                            )
                        }
                    }
                    is SessionVerifyResult.NetworkError -> {
                        _uiState.update {
                            it.copy(
                                isVerifyingPaste = false,
                                errorMessage = result.message,
                            )
                        }
                    }
                }
            }
        }

        fun logout() {
            viewModelScope.launch {
                authRepository.logout(sourceId)
                _uiState.update {
                    it.copy(
                        isLoggedIn = false,
                        successMessage = "Đã đăng xuất khỏi nguồn truyện",
                    )
                }
            }
        }

        fun clearMessages() {
            _uiState.update { it.copy(errorMessage = null, successMessage = null) }
        }
    }
