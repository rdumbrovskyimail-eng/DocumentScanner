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
 * - ✅ Uses EncryptedKeyStorage for API key (Session 3 fix)
 * - ✅ Added validation
 * - ✅ Better error handling
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val encryptedKeyStorage: EncryptedKeyStorage
) : ViewModel() {

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Check if this is first launch.
     * If not, navigate directly to main screen.
     */
    fun checkFirstLaunch(onNotFirstLaunch: () -> Unit) {
        viewModelScope.launch {
            try {
                val isFirstLaunch = settingsRepository.isFirstLaunch()
                if (!isFirstLaunch) {
                    onNotFirstLaunch()
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to check first launch status"
            }
        }
    }

    /**
     * Update API key value.
     */
    fun updateApiKey(key: String) {
        _apiKey.value = key
        
        // Clear error when user types
        if (_errorMessage.value != null) {
            _errorMessage.value = null
        }
    }

    /**
     * Save API key and mark onboarding complete.
     */
    fun saveAndContinue(onComplete: () -> Unit) {
        val key = _apiKey.value.trim()

        // Validation
        if (key.isBlank()) {
            _errorMessage.value = "API key cannot be empty"
            return
        }

        if (!isValidApiKey(key)) {
            _errorMessage.value = "Invalid API key format. Must start with 'AIza' and be 39 characters long."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // Save to encrypted storage (Session 3)
                encryptedKeyStorage.setActiveApiKey(key)

                // Save to DataStore for backward compatibility
                // ⚠️ NOTE: settingsRepository.setApiKey() should be REMOVED in Session 3
                // For now keeping for compatibility
                try {
                    settingsRepository.setApiKey(key)
                } catch (e: Exception) {
                    // Ignore if method doesn't exist after Session 3 fix
                }

                // Mark onboarding complete
                settingsRepository.setFirstLaunchCompleted()

                onComplete()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save API key: ${e.message}"
            } finally {
                _isLoading.value = false
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
     * Clear error message.
     */
    fun clearError() {
        _errorMessage.value = null
    }
}