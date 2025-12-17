package com.docs.scanner.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.docs.scanner.data.local.database.entities.TermEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TermDao {
    
    // ✅ ИЗМЕНЕНО: dateTime → dueDate
    @Query("SELECT * FROM terms WHERE isCompleted = 0 AND dueDate >= :currentTime ORDER BY dueDate ASC")
    fun getUpcomingTerms(currentTime: Long = System.currentTimeMillis()): Flow<List<TermEntity>>
    
    // ✅ ИЗМЕНЕНО: dateTime → dueDate
    @Query("SELECT * FROM terms WHERE isCompleted = 1 ORDER BY dueDate DESC")
    fun getCompletedTerms(): Flow<List<TermEntity>>

    @Query("SELECT * FROM terms WHERE id = :termId")
    suspend fun getTermById(termId: Long): TermEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(term: TermEntity): Long  // ✅ ИЗМЕНЕНО: insertTerm → insert

    @Update
    suspend fun update(term: TermEntity)  // ✅ ИЗМЕНЕНО: updateTerm → update

    @Query("UPDATE terms SET isCompleted = :completed WHERE id = :termId")
    suspend fun markCompleted(termId: Long, completed: Boolean = true)

    @Delete
    suspend fun delete(term: TermEntity)  // ✅ ИЗМЕНЕНО: deleteTerm → delete

    @Query("DELETE FROM terms WHERE id = :termId")
    suspend fun deleteById(termId: Long)  // ✅ ИЗМЕНЕНО: deleteTermById → deleteById
}
