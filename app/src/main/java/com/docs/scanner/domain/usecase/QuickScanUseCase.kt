package com.docs.scanner.domain.usecase

import android.net.Uri
import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.FolderRepository
import com.docs.scanner.domain.repository.RecordRepository
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class QuickScanUseCase @Inject constructor(
    private val folderRepository: FolderRepository,
    private val recordRepository: RecordRepository,
    private val addDocumentUseCase: AddDocumentUseCase
) {
    suspend operator fun invoke(imageUri: Uri): Result<Long> {
        return try {
            // 1. Получить список папок из Flow
            val folders = folderRepository.getAllFolders().first()
            
            // 2. Найти или создать папку "New Folder"
            val newFolder = folders.firstOrNull { it.name == "New Folder" }
                ?: folderRepository.createFolder(
                    name = "New Folder",
                    description = "Quick scans"
                ).let { result ->
                    when (result) {
                        is Result.Success -> folderRepository.getFolderById(result.data)
                        else -> return Result.Error(Exception("Failed to create folder"))
                    }
                } ?: return Result.Error(Exception("Failed to create folder"))
            
            // 3. Создать форматированную дату и время
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val currentDateTime = dateFormat.format(Date())
            
            // 4. Создать запись с датой в ОПИСАНИИ
            val recordResult = recordRepository.createRecord(
                folderId = newFolder.id,
                name = "New document",
                description = "Created: $currentDateTime"
            )
            
            val recordId = when (recordResult) {
                is Result.Success -> recordResult.data
                is Result.Error -> return Result.Error(
                    Exception("Failed to create record: ${recordResult.exception.message}")
                )
                else -> return Result.Error(Exception("Failed to create record"))
            }
            
            // 5. Добавить документ с изображением
            when (val documentResult = addDocumentUseCase(recordId, imageUri)) {
                is Result.Success -> Result.Success(recordId)
                is Result.Error -> Result.Error(
                    Exception("Failed to add document: ${documentResult.exception.message}")
                )
                else -> Result.Error(Exception("Failed to add document"))
            }
            
        } catch (e: Exception) {
            android.util.Log.e("QuickScanUseCase", "❌ Error in quick scan", e)
            Result.Error(e)
        }
    }
}