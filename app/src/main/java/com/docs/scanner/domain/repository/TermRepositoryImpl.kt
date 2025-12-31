package com.docs.scanner.data.repository

import com.docs.scanner.data.local.database.dao.TermDao
import com.docs.scanner.data.local.database.entities.TermEntity
import com.docs.scanner.domain.model.Term
import com.docs.scanner.domain.repository.TermRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of TermRepository.
 * 
 * Handles all database operations for Term entities.
 * Maps between TermEntity (data layer) and Term (domain layer).
 */
@Singleton
class TermRepositoryImpl @Inject constructor(
    private val termDao: TermDao
) : TermRepository {

    // ══════════════════════════════════════════════════════════════
    // FLOW OPERATIONS
    // ══════════════════════════════════════════════════════════════

    override fun getAllTerms(): Flow<List<Term>> {
        return termDao.getAllTerms().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getUpcomingTerms(): Flow<List<Term>> {
        return termDao.getUpcomingTerms().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getCompletedTerms(): Flow<List<Term>> {
        return termDao.getCompletedTerms().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getOverdueTerms(): Flow<List<Term>> {
        val currentTime = System.currentTimeMillis()
        return termDao.getOverdueTerms(currentTime).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getTermsNeedingReminder(): Flow<List<Term>> {
        val currentTime = System.currentTimeMillis()
        return termDao.getTermsNeedingReminder(currentTime).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getTermsInDateRange(startTime: Long, endTime: Long): Flow<List<Term>> {
        return termDao.getTermsInDateRange(startTime, endTime).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // SUSPEND OPERATIONS
    // ══════════════════════════════════════════════════════════════

    override suspend fun getTermById(termId: Long): Term? {
        return termDao.getById(termId)?.toDomain()
    }

    override suspend fun insertTerm(term: Term): Long {
        Timber.d("Inserting term: ${term.title}")
        return termDao.insert(TermEntity.fromDomain(term))
    }

    override suspend fun updateTerm(term: Term) {
        Timber.d("Updating term: ${term.id} - ${term.title}")
        termDao.update(TermEntity.fromDomain(term))
    }

    override suspend fun deleteTerm(term: Term) {
        Timber.d("Deleting term: ${term.id} - ${term.title}")
        termDao.delete(TermEntity.fromDomain(term))
    }

    override suspend fun deleteTermById(termId: Long) {
        Timber.d("Deleting term by ID: $termId")
        termDao.deleteById(termId)
    }

    override suspend fun markCompleted(termId: Long, completed: Boolean) {
        val completedAt = if (completed) System.currentTimeMillis() else null
        Timber.d("Marking term $termId as completed=$completed")
        termDao.markCompleted(termId, completed, completedAt)
    }

    override suspend fun deleteAllCompleted() {
        Timber.d("Deleting all completed terms")
        termDao.deleteAllCompleted()
    }

    // ══════════════════════════════════════════════════════════════
    // COUNT OPERATIONS
    // ══════════════════════════════════════════════════════════════

    override suspend fun getUpcomingCount(): Int {
        return termDao.getUpcomingCount()
    }

    override suspend fun getOverdueCount(): Int {
        val currentTime = System.currentTimeMillis()
        return termDao.getOverdueCount(currentTime)
    }

    override suspend fun getDueTodayCount(): Int {
        val today = LocalDate.now()
        val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        return termDao.getDueTodayCount(startOfDay, endOfDay)
    }

    // ══════════════════════════════════════════════════════════════
    // MAPPERS
    // ══════════════════════════════════════════════════════════════

    private fun TermEntity.toDomain(): Term = Term(
        id = id,
        title = title,
        description = description,
        dueDate = dueDate,
        reminderMinutesBefore = reminderMinutesBefore,
        isCompleted = isCompleted,
        completedAt = completedAt,
        createdAt = createdAt
    )
}

/**
 * Extension function to create TermEntity from Term domain model.
 */
private fun TermEntity.Companion.fromDomain(term: Term): TermEntity = TermEntity(
    id = term.id,
    title = term.title,
    description = term.description,
    dueDate = term.dueDate,
    reminderMinutesBefore = term.reminderMinutesBefore,
    isCompleted = term.isCompleted,
    completedAt = term.completedAt,
    createdAt = term.createdAt
)