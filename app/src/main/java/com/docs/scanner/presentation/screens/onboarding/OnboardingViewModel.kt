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

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val encryptedKeyStorage: EncryptedKeyStorage
) : ViewModel() {

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun updateApiKey(key: String) {
        _apiKey.value = key
    }

    fun checkFirstLaunch(onNotFirstLaunch: () -> Unit) {
        viewModelScope.launch {
            val completed = settingsRepository.isOnboardingCompleted()
            if (completed) onNotFirstLaunch()
        }
    }

    fun saveAndContinue(onComplete: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val key = _apiKey.value.trim()
                if (key.isNotEmpty()) {
                    encryptedKeyStorage.setActiveApiKey(key)
                }
                settingsRepository.setOnboardingCompleted(true)
                onComplete()
            } finally {
                _isLoading.value = false
            }
        }
    }
}