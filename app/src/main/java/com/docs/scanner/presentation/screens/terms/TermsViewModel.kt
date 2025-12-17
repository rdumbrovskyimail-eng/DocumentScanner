package com.docs.scanner.presentation.screens.terms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.data.local.alarm.AlarmSchedulerWrapper
import com.docs.scanner.data.local.database.entities.TermEntity
import com.docs.scanner.data.repository.TermRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TermsViewModel @Inject constructor(
    private val termRepository: TermRepository,
    private val alarmScheduler: AlarmSchedulerWrapper  // ✅ Используем Wrapper
) : ViewModel() {

    val upcomingTerms: StateFlow<List<TermEntity>> = termRepository.getUpcomingTerms()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val completedTerms: StateFlow<List<TermEntity>> = termRepository.getCompletedTerms()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun createTerm(title: String, dueDate: Long, reminderMinutesBefore: Int) {
        viewModelScope.launch {
            val term = TermEntity(
                title = title,
                dueDate = dueDate,  // ✅ Правильное поле
                reminderMinutesBefore = reminderMinutesBefore,
                isCompleted = false,
                createdAt = System.currentTimeMillis()
            )
            
            val termId = termRepository.insertTerm(term)
            
            // Schedule alarm if reminder is set
            if (reminderMinutesBefore > 0) {
                val reminderTime = dueDate - (reminderMinutesBefore * 60 * 1000)
                if (reminderTime > System.currentTimeMillis()) {
                    alarmScheduler.schedule(
                        termId = termId.toInt(),
                        title = title,
                        triggerTime = reminderTime
                    )
                }
            }
        }
    }

    fun completeTerm(term: TermEntity) {
        viewModelScope.launch {
            termRepository.updateTerm(term.copy(isCompleted = true))
            // Cancel alarm when completed
            alarmScheduler.cancel(term.id.toInt())
        }
    }

    fun deleteTerm(term: TermEntity) {
        viewModelScope.launch {
            termRepository.deleteTerm(term)
            // Cancel alarm when deleted
            alarmScheduler.cancel(term.id.toInt())
        }
    }
}
