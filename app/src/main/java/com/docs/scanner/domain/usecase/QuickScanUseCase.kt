package com.docs.scanner.domain.usecase

import android.net.Uri
import com.docs.scanner.domain.model.*
import com.docs.scanner.domain.repository.*
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * ✅ Use Case для быстрого сканирования
 * Создает папку "New Folder" и документ "New document" с timestamp
 */
class QuickScanUseCase @Inject constructor(
    private val folderRepository: FolderRepository,
    private val recordRepository: RecordRepository,
    private val addDocumentUseCase: AddDocumentUseCase,
    private val getFoldersUseCase: GetFoldersUseCase
) {
    suspend operator fun invoke(imageUri: Uri): Result<Long> {
        return try {
            // ✅ Поиск или создание папки "New Folder"
            val foldersFlow = getFoldersUseCase()
            var targetFolder: Folder? = null
            
            foldersFlow.first().let { folders ->
                targetFolder = folders.find { it.name == "New Folder" }
            }
            
            // Создаем папку "New Folder" если не существует
            val folderId = if (targetFolder == null) {
                when (val result = folderRepository.createFolder("New Folder", "Auto-created folder for quick scans")) {
                    is Result.Success -> result.data
                    is Result.Error -> return Result.Error(result.exception)
                    else -> return Result.Error(Exception("Unknown error creating folder"))
                }
            } else {
                targetFolder!!.id
            }
            
            // ✅ Создание имени "New document" + timestamp
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val timestamp = dateFormat.format(Date())
            val recordName = "New document $timestamp"
            
            // Создаем запись
            val recordId = when (val result = recordRepository.createRecord(folderId, recordName, null)) {
                is Result.Success -> result.data
                is Result.Error -> return Result.Error(result.exception)
                else -> return Result.Error(Exception("Unknown error creating record"))
            }
            
            // Добавляем документ
            when (val result = addDocumentUseCase(recordId, imageUri)) {
                is Result.Success -> Result.Success(recordId)
                is Result.Error -> result
                else -> Result.Error(Exception("Unknown error adding document"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Quick scan failed: ${e.message}", e))
        }
    }
}
