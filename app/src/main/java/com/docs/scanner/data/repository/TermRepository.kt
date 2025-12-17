package com.docs.scanner.data.repository

import com.docs.scanner.data.local.database.dao.TermDao
import com.docs.scanner.data.local.database.entities.TermEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TermRepository @Inject constructor(
    private val termDao: TermDao
) {
    fun getUpcomingTerms(): Flow<List<TermEntity>> {
        return termDao.getUpcomingTerms()
    }

    fun getCompletedTerms(): Flow<List<TermEntity>> {
        return termDao.getCompletedTerms()
    }

    suspend fun insertTerm(term: TermEntity): Long {
        return termDao.insert(term)
    }

    suspend fun updateTerm(term: TermEntity) {
        termDao.update(term)
    }

    suspend fun deleteTerm(term: TermEntity) {
        termDao.delete(term)
    }
    
    suspend fun getTermById(id: Long): TermEntity? {
        return termDao.getTermById(id)
    }
    
    suspend fun markCompleted(termId: Long, completed: Boolean = true) {
        termDao.markCompleted(termId, completed)
    }
}
