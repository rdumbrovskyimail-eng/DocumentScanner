package com.docs.scanner.data.repository

import com.docs.scanner.data.local.database.dao.FolderDao
import com.docs.scanner.data.local.database.entities.FolderEntity
import com.docs.scanner.domain.model.Folder
import com.docs.scanner.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of FolderRepository.
 * 
 * Responsibilities:
 * - Map between FolderEntity (data) and Folder (domain)
 * - Handle database operations via FolderDao
 * - Convert exceptions to Result types
 */
@Singleton
class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao
) : FolderRepository {
    
    override fun getAllFolders(): Flow<List<Folder>> {
        return folderDao.getAllFoldersWithCounts().map { folders ->
            folders.map { it.toDomainModel() }
        }
    }
    
    override suspend fun getFolderById(id: Long): Folder? {
        return folderDao.getFolderWithCount(id)?.toDomainModel()
    }
    
    override suspend fun createFolder(name: String, description: String?): Result<Long> {
        return try {
            // Validate input
            require(name.isNotBlank()) { "Folder name cannot be blank" }
            require(name.length <= Folder.MAX_NAME_LENGTH) {
                "Folder name too long (max ${Folder.MAX_NAME_LENGTH} characters)"
            }
            
            // Check for duplicates
            if (folderDao.isFolderNameExists(name)) {
                return Result.failure(IllegalArgumentException("Folder with name '$name' already exists"))
            }
            
            val entity = FolderEntity(
                name = name.trim(),
                description = description?.trim(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            
            val id = folderDao.insertFolder(entity)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun updateFolder(folder: Folder): Result<Unit> {
        return try {
            // Validate
            require(folder.id > 0) { "Invalid folder ID: ${folder.id}" }
            require(folder.name.isNotBlank()) { "Folder name cannot be blank" }
            
            // Check for name conflicts (excluding current folder)
            if (folderDao.isFolderNameExists(folder.name, excludeId = folder.id)) {
                return Result.failure(
                    IllegalArgumentException("Folder with name '${folder.name}' already exists")
                )
            }
            
            val entity = FolderEntity(
                id = folder.id,
                name = folder.name.trim(),
                description = folder.description?.trim(),
                createdAt = folder.createdAt,
                updatedAt = System.currentTimeMillis()
            )
            
            folderDao.updateFolder(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun deleteFolder(id: Long): Result<Unit> {
        return try {
            require(id > 0) { "Invalid folder ID: $id" }
            
            folderDao.deleteFolderById(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ============================================
    // MAPPER: Entity → Domain
    // ============================================
    
    private fun com.docs.scanner.data.local.database.dao.FolderWithCount.toDomainModel(): Folder {
        return Folder(
            id = this.id,
            name = this.name,
            description = this.description,
            recordCount = this.recordCount,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }
}