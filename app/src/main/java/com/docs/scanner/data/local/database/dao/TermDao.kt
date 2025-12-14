package com.docs.scanner.data.local.database.dao

import androidx.room.*
import com.docs.scanner.data.local.database.entities.TermEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TermDao {
    
    @Query("SELECT * FROM terms WHERE dateTime >= :currentTime ORDER BY dateTime ASC")
    fun getUpcomingTerms(currentTime: Long = System.currentTimeMillis()): Flow<List<TermEntity>>
    
    @Query("SELECT * FROM terms WHERE isCompleted = 1 ORDER BY dateTime DESC")
    fun getCompletedTerms(): Flow<List<TermEntity>>

    @Query("SELECT * FROM terms WHERE id = :termId")
    suspend fun getTermById(termId: Long): TermEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTerm(term: TermEntity): Long

    @Update
    suspend fun updateTerm(term: TermEntity)

    @Query("UPDATE terms SET isCompleted = :completed WHERE id = :termId")
    suspend fun markCompleted(termId: Long, completed: Boolean = true)

    @Delete
    suspend fun deleteTerm(term: TermEntity)

    @Query("DELETE FROM terms WHERE id = :termId")
    suspend fun deleteTermById(termId: Long)
}
