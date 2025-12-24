package com.docs.scanner.domain.usecase

import android.net.Uri
import com.docs.scanner.domain.model.*
import com.docs.scanner.domain.repository.*
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * ✅ ИСПРАВЛЕНО: Атомарные транзакции + откат при ошибках
 * 
 * Use Case для быстрого сканирования
 * Создает папку "New Folder" и документ "New document" с timestamp
 */
class QuickScanUseCase @Inject constructor(
    private val folderRepository: FolderRepository,
    private val recordRepository: RecordRepository,
    private val addDocumentUseCase: AddDocumentUseCase,
    private val getFoldersUseCase: GetFoldersUseCase
) {
    suspend operator fun invoke(imageUri: Uri): Result<Long> {
        var createdFolderId: Long? = null
        var createdRecordId: Long? = null
        
        return try {
            // ✅ ШАГ 1: Поиск или создание папки "New Folder"
            val foldersFlow = getFoldersUseCase()
            var targetFolder: Folder? = null
            
            foldersFlow.first().let { folders ->
                targetFolder = folders.find { it.name == NEW_FOLDER_NAME }
            }
            
            val folderId = if (targetFolder == null) {
                when (val result = folderRepository.createFolder(
                    name = NEW_FOLDER_NAME,
                    description = NEW_FOLDER_DESCRIPTION
                )) {
                    is Result.Success -> {
                        createdFolderId = result.data
                        result.data
                    }
                    is Result.Error -> {
                        return Result.Error(
                            Exception("Failed to create folder: ${result.exception.message}", result.exception)
                        )
                    }
                    else -> return Result.Error(Exception("Unknown error creating folder"))
                }
            } else {
                targetFolder!!.id
            }
            
            // ✅ ШАГ 2: Создание записи с timestamp
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val timestamp = dateFormat.format(Date())
            val recordName = "$NEW_RECORD_PREFIX $timestamp"
            
            val recordId = when (val result = recordRepository.createRecord(
                folderId = folderId,
                name = recordName,
                description = null
            )) {
                is Result.Success -> {
                    createdRecordId = result.data
                    result.data
                }
                is Result.Error -> {
                    // ✅ ОТКАТ: Удаляем папку если она была создана
                    rollbackFolder(createdFolderId)
                    return Result.Error(
                        Exception("Failed to create record: ${result.exception.message}", result.exception)
                    )
                }
                else -> {
                    rollbackFolder(createdFolderId)
                    return Result.Error(Exception("Unknown error creating record"))
                }
            }
            
            // ✅ ШАГ 3: Добавление документа
            when (val result = addDocumentUseCase(recordId, imageUri)) {
                is Result.Success -> {
                    println("✅ QuickScan: Success! RecordId=$recordId")
                    Result.Success(recordId)
                }
                is Result.Error -> {
                    // ✅ ОТКАТ: Удаляем запись и папку
                    rollbackRecord(createdRecordId)
                    rollbackFolder(createdFolderId)
                    Result.Error(
                        Exception("Failed to add document: ${result.exception.message}", result.exception)
                    )
                }
                else -> {
                    rollbackRecord(createdRecordId)
                    rollbackFolder(createdFolderId)
                    Result.Error(Exception("Unknown error adding document"))
                }
            }
            
        } catch (e: Exception) {
            // ✅ ОТКАТ при любой ошибке
            rollbackRecord(createdRecordId)
            rollbackFolder(createdFolderId)
            Result.Error(Exception("QuickScan failed: ${e.message}", e))
        }
    }
    
    /**
     * ✅ ДОБАВЛЕНО: Откат создания папки
     */
    private suspend fun rollbackFolder(folderId: Long?) {
        if (folderId != null) {
            try {
                folderRepository.deleteFolder(folderId)
                println("♻️ QuickScan: Rolled back folder $folderId")
            } catch (e: Exception) {
                println("⚠️ QuickScan: Failed to rollback folder: ${e.message}")
            }
        }
    }
    
    /**
     * ✅ ДОБАВЛЕНО: Откат создания записи
     */
    private suspend fun rollbackRecord(recordId: Long?) {
        if (recordId != null) {
            try {
                recordRepository.deleteRecord(recordId)
                println("♻️ QuickScan: Rolled back record $recordId")
            } catch (e: Exception) {
                println("⚠️ QuickScan: Failed to rollback record: ${e.message}")
            }
        }
    }
    
    companion object {
        private const val NEW_FOLDER_NAME = "New Folder"
        private const val NEW_FOLDER_DESCRIPTION = "Auto-created folder for quick scans"
        private const val NEW_RECORD_PREFIX = "New document"
    }
}
