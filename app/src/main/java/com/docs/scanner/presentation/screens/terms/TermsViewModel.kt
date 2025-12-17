package com.docs.scanner.presentation.screens.terms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.data.local.alarm.AlarmScheduler
import com.docs.scanner.data.local.database.dao.TermDao
import com.docs.scanner.data.local.database.entities.TermEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TermsViewModel @Inject constructor(
    private val termDao: TermDao,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {
    
    val upcomingTerms: StateFlow<List<TermEntity>> = termDao.getUpcomingTerms()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    val completedTerms: StateFlow<List<TermEntity>> = termDao.getCompletedTerms()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    fun createTerm(
        title: String,
        description: String?,
        dateTime: Long,
        reminderMinutesBefore: Int?
    ) {
        viewModelScope.launch {
            val term = TermEntity(
                title = title,
                description = description,
                dateTime = dateTime,
                reminderMinutesBefore = reminderMinutesBefore,
                isCompleted = false
            )
            
            val termId = termDao.insertTerm(term)
            
            // ✅ Планируем алерт
            alarmScheduler.scheduleTerm(term.copy(id = termId))
        }
    }
    
    fun completeTerm(termId: Long) {
        viewModelScope.launch {
            termDao.markCompleted(termId, true)
            alarmScheduler.cancelTerm(termId)
        }
    }
    
    fun deleteTerm(termId: Long) {
        viewModelScope.launch {
            termDao.deleteTermById(termId)
            alarmScheduler.cancelTerm(termId)
        }
    }
}
