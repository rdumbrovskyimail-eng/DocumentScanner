package com.docs.scanner.data.repository

import com.docs.scanner.data.local.database.dao.RecordDao
import com.docs.scanner.data.local.database.entities.RecordEntity
import com.docs.scanner.domain.model.Record
import com.docs.scanner.domain.repository.RecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of RecordRepository.
 * 
 * Responsibilities:
 * - Map between RecordEntity (data) and Record (domain)
 * - Handle database operations via RecordDao
 * - Manage record-folder relationships
 */
@Singleton
class RecordRepositoryImpl @Inject constructor(
    private val recordDao: RecordDao
) : RecordRepository {
    
    override fun getRecordsByFolder(folderId: Long): Flow<List<Record>> {
        return recordDao.getRecordsByFolder(folderId).map { records ->
            records.map { it.toDomainModel() }
        }
    }
    
    override suspend fun getRecordById(id: Long): Record? {
        return recordDao.getRecordById(id)?.toDomainModel()
    }
    
    override suspend fun createRecord(
        folderId: Long,
        name: String,
        description: String?
    ): Result<Long> {
        return try {
            // Validate input
            require(folderId > 0) { "Invalid folder ID: $folderId" }
            require(name.isNotBlank()) { "Record name cannot be blank" }
            require(name.length <= Record.MAX_NAME_LENGTH) {
                "Record name too long (max ${Record.MAX_NAME_LENGTH} characters)"
            }
            
            // Check for duplicates in same folder
            if (recordDao.isRecordNameExistsInFolder(folderId, name)) {
                return Result.failure(
                    IllegalArgumentException("Record with name '$name' already exists in this folder")
                )
            }
            
            val entity = RecordEntity(
                folderId = folderId,
                name = name.trim(),
                description = description?.trim(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            
            val id = recordDao.insertRecord(entity)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun updateRecord(record: Record): Result<Unit> {
        return try {
            // Validate
            require(record.id > 0) { "Invalid record ID: ${record.id}" }
            require(record.folderId > 0) { "Invalid folder ID: ${record.folderId}" }
            require(record.name.isNotBlank()) { "Record name cannot be blank" }
            
            // Check for name conflicts in same folder (excluding current record)
            if (recordDao.isRecordNameExistsInFolder(
                    record.folderId,
                    record.name,
                    excludeId = record.id
                )
            ) {
                return Result.failure(
                    IllegalArgumentException("Record with name '${record.name}' already exists in this folder")
                )
            }
            
            val entity = RecordEntity(
                id = record.id,
                folderId = record.folderId,
                name = record.name.trim(),
                description = record.description?.trim(),
                createdAt = record.createdAt,
                updatedAt = System.currentTimeMillis()
            )
            
            recordDao.updateRecord(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun deleteRecord(id: Long): Result<Unit> {
        return try {
            require(id > 0) { "Invalid record ID: $id" }
            
            recordDao.deleteRecordById(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun moveRecord(recordId: Long, newFolderId: Long): Result<Unit> {
        return try {
            require(recordId > 0) { "Invalid record ID: $recordId" }
            require(newFolderId > 0) { "Invalid folder ID: $newFolderId" }
            
            recordDao.moveRecordToFolder(
                recordId = recordId,
                newFolderId = newFolderId,
                timestamp = System.currentTimeMillis()
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ============================================
    // MAPPER: Entity → Domain
    // ============================================
    
    private suspend fun RecordEntity.toDomainModel(): Record {
        // Get document count for this record
        val documentCount = 0 // TODO: Add documentDao.getDocumentCountInRecord(this.id)
        
        return Record(
            id = this.id,
            folderId = this.folderId,
            name = this.name,
            description = this.description,
            documentCount = documentCount,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }
}