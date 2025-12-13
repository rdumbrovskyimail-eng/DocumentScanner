package com.docs.scanner.data.local.database.dao

import androidx.room.*
import com.docs.scanner.data.local.database.entities.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY createdAt DESC")
    fun getAllFolders(): Flow<List<FolderEntity>>
    
    @Query("SELECT * FROM folders WHERE id = :folderId")
    suspend fun getFolderById(folderId: Long): FolderEntity?
    
    @Query("SELECT * FROM folders WHERE id = :folderId")
    fun getFolderByIdFlow(folderId: Long): Flow<FolderEntity?>
    
    @Query("SELECT * FROM folders WHERE name LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchFolders(query: String): Flow<List<FolderEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity): Long
    
    @Update
    suspend fun updateFolder(folder: FolderEntity)
    
    @Delete
    suspend fun deleteFolder(folder: FolderEntity)
    
    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolderById(folderId: Long)
    
    @Query("SELECT COUNT(*) FROM folders")
    suspend fun getFolderCount(): Int
    
    @Query("SELECT EXISTS(SELECT 1 FROM folders WHERE name = :name AND id != :excludeId)")
    suspend fun isFolderNameExists(name: String, excludeId: Long = 0): Boolean
}