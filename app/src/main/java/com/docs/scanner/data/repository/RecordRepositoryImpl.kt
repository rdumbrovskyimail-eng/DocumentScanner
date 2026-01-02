package com.docs.scanner.data.repository

import com.docs.scanner.data.local.database.dao.RecordDao
import com.docs.scanner.data.local.database.dao.RecordWithCount
import com.docs.scanner.data.local.database.entities.RecordEntity
import com.docs.scanner.domain.model.Record
import com.docs.scanner.domain.model.RecordConstants
import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.RecordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of RecordRepository.
 * 
 * Uses optimized JOIN queries via RecordWithCount to avoid N+1 problem.
 */
@Singleton
class RecordRepositoryImpl @Inject constructor(
    private val recordDao: RecordDao
) : RecordRepository {

    override fun getRecordsByFolder(folderId: Long): Flow<List<Record>> {
        return recordDao.getByFolderIdWithDocCount(folderId).map { recordsWithCount ->
            recordsWithCount.map { it.toDomain() }
        }
    }

    override suspend fun getRecordById(recordId: Long): Record? {
        if (recordId <= 0) return null
        return recordDao.getByIdWithDocCount(recordId)?.toDomain()
    }

    override suspend fun createRecord(
        folderId: Long,
        name: String,
        description: String?
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            // Validation
            require(folderId > 0) { "Invalid folder ID: $folderId" }
            
            val trimmedName = name.trim()
            
            if (trimmedName.isBlank()) {
                return@withContext Result.Error(
                    IllegalArgumentException("Record name cannot be empty")
                )
            }
            
            if (trimmedName.length > RecordConstants.MAX_NAME_LENGTH) {
                return@withContext Result.Error(
                    IllegalArgumentException(
                        "Record name too long (max ${RecordConstants.MAX_NAME_LENGTH} chars)"
                    )
                )
            }
            
            // Check for duplicate name in same folder
            if (recordDao.isNameExistsInFolder(folderId, trimmedName)) {
                return@withContext Result.Error(
                    IllegalArgumentException(
                        "Record with name '$trimmedName' already exists in this folder"
                    )
                )
            }
            
            val entity = RecordEntity(
                folderId = folderId,
                name = trimmedName,
                description = description?.trim(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            
            val id = recordDao.insert(entity)
            Timber.d("Created record: $trimmedName (id=$id) in folder $folderId")
            
            Result.Success(id)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create record")
            Result.Error(e)
        }
    }

    override suspend fun updateRecord(record: Record): Result<Unit> = 
        withContext(Dispatchers.IO) {
            try {
                // Validation
                if (record.id <= 0) {
                    return@withContext Result.Error(
                        IllegalArgumentException("Invalid record ID")
                    )
                }
                
                if (record.folderId <= 0) {
                    return@withContext Result.Error(
                        IllegalArgumentException("Invalid folder ID")
                    )
                }
                
                val trimmedName = record.name.trim()
                
                if (trimmedName.isBlank()) {
                    return@withContext Result.Error(
                        IllegalArgumentException("Record name cannot be empty")
                    )
                }
                
                // Check for duplicate name in same folder (excluding current record)
                if (recordDao.isNameExistsInFolder(record.folderId, trimmedName, record.id)) {
                    return@withContext Result.Error(
                        IllegalArgumentException(
                            "Record with name '$trimmedName' already exists in this folder"
                        )
                    )
                }
                
                val entity = RecordEntity(
                    id = record.id,
                    folderId = record.folderId,
                    name = trimmedName,
                    description = record.description?.trim(),
                    createdAt = record.createdAt,
                    updatedAt = System.currentTimeMillis()
                )
                
                recordDao.update(entity)
                Timber.d("Updated record: $trimmedName (id=${record.id})")
                
                Result.Success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update record")
                Result.Error(e)
            }
        }

    override suspend fun deleteRecord(recordId: Long): Result<Unit> = 
        withContext(Dispatchers.IO) {
            try {
                if (recordId <= 0) {
                    return@withContext Result.Error(
                        IllegalArgumentException("Invalid record ID: $recordId")
                    )
                }
                
                // Note: CASCADE will delete all documents
                recordDao.deleteById(recordId)
                Timber.d("Deleted record: $recordId")
                
                Result.Success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete record")
                Result.Error(e)
            }
        }

    override suspend fun moveRecord(
        recordId: Long,
        targetFolderId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (recordId <= 0) {
                return@withContext Result.Error(
                    IllegalArgumentException("Invalid record ID: $recordId")
                )
            }
            
            if (targetFolderId <= 0) {
                return@withContext Result.Error(
                    IllegalArgumentException("Invalid target folder ID: $targetFolderId")
                )
            }
            
            // Get current record to check for name conflict
            val currentRecord = recordDao.getById(recordId)
                ?: return@withContext Result.Error(
                    IllegalArgumentException("Record not found: $recordId")
                )
            
            // Check for name conflict in target folder
            if (recordDao.isNameExistsInFolder(targetFolderId, currentRecord.name)) {
                return@withContext Result.Error(
                    IllegalArgumentException(
                        "Record with name '${currentRecord.name}' already exists in target folder"
                    )
                )
            }
            
            recordDao.moveToFolder(recordId, targetFolderId)
            Timber.d("Moved record $recordId to folder $targetFolderId")
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to move record")
            Result.Error(e)
        }
    }
}