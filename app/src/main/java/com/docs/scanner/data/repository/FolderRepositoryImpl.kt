package com.docs.scanner.data.repository

import com.docs.scanner.data.local.database.dao.FolderDao
import com.docs.scanner.data.local.database.dao.FolderWithCount
import com.docs.scanner.data.local.database.entities.FolderEntity
import com.docs.scanner.domain.model.Folder
import com.docs.scanner.domain.model.FolderConstants
import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.FolderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of FolderRepository.
 * 
 * Uses optimized JOIN queries via FolderWithCount to avoid N+1 problem.
 */
@Singleton
class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao
) : FolderRepository {

    override fun getAllFolders(): Flow<List<Folder>> {
        return folderDao.getAllWithRecordCount().map { foldersWithCount ->
            foldersWithCount.map { it.toDomain() }
        }
    }

    override suspend fun getFolderById(folderId: Long): Folder? {
        if (folderId <= 0) return null
        return folderDao.getByIdWithRecordCount(folderId)?.toDomain()
    }

    override suspend fun createFolder(
        name: String,
        description: String?
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            // Validation
            val trimmedName = name.trim()
            
            if (trimmedName.isBlank()) {
                return@withContext Result.Error(
                    IllegalArgumentException("Folder name cannot be empty")
                )
            }
            
            if (trimmedName.length > FolderConstants.MAX_NAME_LENGTH) {
                return@withContext Result.Error(
                    IllegalArgumentException(
                        "Folder name too long (max ${FolderConstants.MAX_NAME_LENGTH} chars)"
                    )
                )
            }
            
            // Check for duplicate name
            if (folderDao.isNameExists(trimmedName)) {
                return@withContext Result.Error(
                    IllegalArgumentException("Folder with name '$trimmedName' already exists")
                )
            }
            
            val entity = FolderEntity(
                name = trimmedName,
                description = description?.trim(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            
            val id = folderDao.insert(entity)
            Timber.d("Created folder: $trimmedName (id=$id)")
            
            Result.Success(id)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create folder")
            Result.Error(e)
        }
    }

    override suspend fun updateFolder(folder: Folder): Result<Unit> = 
        withContext(Dispatchers.IO) {
            try {
                // Validation
                if (folder.id <= 0) {
                    return@withContext Result.Error(
                        IllegalArgumentException("Invalid folder ID")
                    )
                }
                
                val trimmedName = folder.name.trim()
                
                if (trimmedName.isBlank()) {
                    return@withContext Result.Error(
                        IllegalArgumentException("Folder name cannot be empty")
                    )
                }
                
                if (trimmedName.length > FolderConstants.MAX_NAME_LENGTH) {
                    return@withContext Result.Error(
                        IllegalArgumentException(
                            "Folder name too long (max ${FolderConstants.MAX_NAME_LENGTH} chars)"
                        )
                    )
                }
                
                // Check for duplicate name (excluding current folder)
                if (folderDao.isNameExists(trimmedName, folder.id)) {
                    return@withContext Result.Error(
                        IllegalArgumentException("Folder with name '$trimmedName' already exists")
                    )
                }
                
                val entity = FolderEntity(
                    id = folder.id,
                    name = trimmedName,
                    description = folder.description?.trim(),
                    createdAt = folder.createdAt,
                    updatedAt = System.currentTimeMillis()
                )
                
                folderDao.update(entity)
                Timber.d("Updated folder: $trimmedName (id=${folder.id})")
                
                Result.Success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update folder")
                Result.Error(e)
            }
        }

    override suspend fun deleteFolder(folderId: Long): Result<Unit> = 
        withContext(Dispatchers.IO) {
            try {
                if (folderId <= 0) {
                    return@withContext Result.Error(
                        IllegalArgumentException("Invalid folder ID: $folderId")
                    )
                }
                
                // Note: CASCADE will delete all records and documents
                folderDao.deleteById(folderId)
                Timber.d("Deleted folder: $folderId")
                
                Result.Success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete folder")
                Result.Error(e)
            }
        }
}