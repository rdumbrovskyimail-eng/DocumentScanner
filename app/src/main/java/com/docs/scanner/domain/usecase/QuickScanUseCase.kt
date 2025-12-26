package com.docs.scanner.domain.usecase

import android.net.Uri
import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.DocumentRepository
import com.docs.scanner.domain.repository.FolderRepository
import com.docs.scanner.domain.repository.RecordRepository
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * Quick Scan use case - быстрое сканирование без выбора папки
 * Создаёт папку "New Folder" если её нет, и запись с датой в описании
 */
class QuickScanUseCase @Inject constructor(
    private val folderRepository: FolderRepository,
    private val recordRepository: RecordRepository,
    private val documentRepository: DocumentRepository,
    private val addDocumentUseCase: AddDocumentUseCase
) {
    suspend operator fun invoke(imageUri: Uri): Result<Long> {
        return try {
            // 1. Найти или создать папку "New Folder"
            val folders = folderRepository.getAllFolders()
            val newFolder = folders.firstOrNull { it.name == "New Folder" }
                ?: folderRepository.createFolder(
                    name = "New Folder",
                    description = "Quick scans"
                )
            
            // 2. Создать форматированную дату и время
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val currentDateTime = dateFormat.format(Date())
            
            // 3. Создать запись с датой в ОПИСАНИИ (не в названии!)
            val record = recordRepository.createRecord(
                folderId = newFolder.id,
                name = "New document",                    // ✅ Чистое название
                description = "Created: $currentDateTime" // ✅ Дата в описании
            )
            
            // 4. Добавить документ с изображением
            addDocumentUseCase(record.id, imageUri)
            
            Result.Success(record.id)
        } catch (e: Exception) {
            android.util.Log.e("QuickScanUseCase", "Error in quick scan", e)
            Result.Error(e)
        }
    }
}