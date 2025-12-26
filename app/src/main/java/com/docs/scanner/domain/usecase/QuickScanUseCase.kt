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
                )
            
            // 3. Создать форматированную дату и время
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val currentDateTime = dateFormat.format(Date())
            
            // 4. Создать запись с датой в ОПИСАНИИ
            val record = recordRepository.createRecord(
                folderId = newFolder.id,  // ✅ Now newFolder is guaranteed to be non-null
                name = "New document",
                description = "Created: $currentDateTime"
            )
            
            // 5. Добавить документ с изображением
            addDocumentUseCase(record.id, imageUri)  // ✅ record is guaranteed to be non-null
            
            Result.Success(record.id)  // ✅ record.id is accessible
        } catch (e: Exception) {
            android.util.Log.e("QuickScanUseCase", "❌ Error in quick scan", e)
            Result.Error(e)
        }
    }
}