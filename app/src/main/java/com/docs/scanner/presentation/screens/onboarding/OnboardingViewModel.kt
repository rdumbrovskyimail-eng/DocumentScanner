package com.docs.scanner.presentation.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.data.local.security.EncryptedKeyStorage
import com.docs.scanner.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Onboarding Screen ViewModel.
 * 
 * Session 8 Fix:
 * - ✅ Moved from OnboardingScreen.kt to separate file
 * - ✅ Uses EncryptedKeyStorage for API key
 * - ✅ Added UI State pattern
 * - ✅ Removed callback parameters
 * - ✅ Auto-check first launch in init
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val encryptedKeyStorage: EncryptedKeyStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.CheckingFirstLaunch)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    init {
        checkFirstLaunch()
    }

    /**
     * Check if this is first launch.
     * If not, navigate directly to main screen.
     */
    private fun checkFirstLaunch() {
        viewModelScope.launch {
            _uiState.value = OnboardingUiState.CheckingFirstLaunch
            
            try {
                val isFirstLaunch = settingsRepository.isFirstLaunch()
                _uiState.value = if (isFirstLaunch) {
                    OnboardingUiState.ShowOnboarding
                } else {
                    OnboardingUiState.NavigateToMain
                }
            } catch (e: Exception) {
                _uiState.value = OnboardingUiState.Error(
                    "Failed to check first launch status: ${e.message}"
                )
            }
        }
    }

    /**
     * Update API key value.
     */
    fun updateApiKey(key: String) {
        _apiKey.value = key
        
        // Clear error when user types
        if (_uiState.value is OnboardingUiState.Error) {
            _uiState.value = OnboardingUiState.ShowOnboarding
        }
    }

    /**
     * Save API key and mark onboarding complete.
     */
    fun saveAndContinue() {
        val key = _apiKey.value.trim()

        // Validation
        if (key.isBlank()) {
            _uiState.value = OnboardingUiState.Error("API key cannot be empty")
            return
        }

        if (!isValidApiKey(key)) {
            _uiState.value = OnboardingUiState.Error(
                "Invalid API key format. Must start with 'AIza' and be 39 characters long."
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = OnboardingUiState.Saving

            try {
                // Save to encrypted storage
                encryptedKeyStorage.setActiveApiKey(key)

                // Mark onboarding complete
                settingsRepository.setFirstLaunchCompleted()

                _uiState.value = OnboardingUiState.Success
            } catch (e: Exception) {
                _uiState.value = OnboardingUiState.Error(
                    "Failed to save API key: ${e.message}"
                )
            }
        }
    }

    /**
     * Validate Gemini API key format.
     * Format: AIza[35 chars of A-Za-z0-9_-]
     */
    private fun isValidApiKey(key: String): Boolean {
        return key.matches(Regex("^AIza[A-Za-z0-9_-]{35}$"))
    }

    /**
     * Reset error state to show onboarding.
     */
    fun resetToOnboarding() {
        _uiState.value = OnboardingUiState.ShowOnboarding
    }
}

/**
 * UI State for Onboarding Screen.
 */
sealed interface OnboardingUiState {
    data object CheckingFirstLaunch : OnboardingUiState
    data object ShowOnboarding : OnboardingUiState
    data object Saving : OnboardingUiState
    data object Success : OnboardingUiState
    data object NavigateToMain : OnboardingUiState
    data class Error(val message: String) : OnboardingUiState
}