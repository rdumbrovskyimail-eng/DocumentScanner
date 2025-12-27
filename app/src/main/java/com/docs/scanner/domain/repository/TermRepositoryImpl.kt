package com.docs.scanner.data.repository

import com.docs.scanner.data.local.database.dao.TermDao
import com.docs.scanner.data.local.database.entities.TermEntity
import com.docs.scanner.domain.model.Term
import com.docs.scanner.domain.repository.TermRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of TermRepository using Room database.
 * 
 * Session 5 fixes:
 * - ✅ Implements domain interface (Clean Architecture)
 * - ✅ Returns domain models (Term), not entities (TermEntity)
 * - ✅ Maps between domain ↔ data layers
 * - ✅ All DAO calls wrapped in mappers
 * 
 * Architecture:
 * ```
 * ViewModel
 *    ↓ Term (domain)
 * TermRepository (interface)
 *    ↓ Term (domain)
 * TermRepositoryImpl (this)
 *    ↓ TermEntity (data)
 * TermDao
 *    ↓ Room database
 * ```
 */
@Singleton
class TermRepositoryImpl @Inject constructor(
    private val termDao: TermDao
) : TermRepository {
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // QUERY OPERATIONS
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    override fun getUpcomingTerms(): Flow<List<Term>> {
        return termDao.getUpcomingTerms()
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    override fun getCompletedTerms(): Flow<List<Term>> {
        return termDao.getCompletedTerms()
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    override suspend fun getTermById(id: Long): Term? {
        return termDao.getTermById(id)?.toDomain()
    }
    
    override fun getOverdueTerms(currentTime: Long): Flow<List<Term>> {
        return termDao.getOverdueTerms(currentTime)
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    override fun getTermsNeedingReminder(currentTime: Long): Flow<List<Term>> {
        return termDao.getTermsNeedingReminder(currentTime)
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    override suspend fun getUpcomingCount(): Int {
        return termDao.getUpcomingCount()
    }
    
    override suspend fun getOverdueCount(currentTime: Long): Int {
        return termDao.getOverdueCount(currentTime)
    }
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // WRITE OPERATIONS
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    override suspend fun insertTerm(term: Term): Long {
        return termDao.insert(term.toEntity())
    }
    
    override suspend fun updateTerm(term: Term) {
        termDao.update(term.toEntity())
    }
    
    override suspend fun deleteTerm(term: Term) {
        termDao.delete(term.toEntity())
    }
    
    override suspend fun deleteTermById(termId: Long) {
        termDao.deleteById(termId)
    }
    
    override suspend fun markCompleted(termId: Long, completed: Boolean) {
        termDao.markCompleted(termId, completed)
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// MAPPERS: Domain ↔ Data
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Convert TermEntity (data layer) to Term (domain layer).
 */
private fun TermEntity.toDomain() = Term(
    id = id,
    title = title,
    description = description,
    dueDate = dueDate,
    reminderMinutesBefore = reminderMinutesBefore,
    isCompleted = isCompleted,
    createdAt = createdAt
)

/**
 * Convert Term (domain layer) to TermEntity (data layer).
 */
private fun Term.toEntity() = TermEntity(
    id = id,
    title = title,
    description = description,
    dueDate = dueDate,
    reminderMinutesBefore = reminderMinutesBefore,
    isCompleted = isCompleted,
    createdAt = createdAt
)