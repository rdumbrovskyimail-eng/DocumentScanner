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
            val folders = folderRepository.getAllFolders().first()

            val quickFolder = folders.firstOrNull { it.name == "Quick Scans" }
                ?: when (val createResult = folderRepository.createFolder("Quick Scans", "Automatically created for quick scans")) {
                    is Result.Success -> {
                        folderRepository.getFolderById(createResult.data)
                            ?: return Result.Error(Exception("Failed to get created folder"))
                    }
                    is Result.Error -> return createResult
                    is Result.Loading -> return Result.Error(Exception("Unexpected loading state"))
                }

            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

            val recordId = when (val recordResult = recordRepository.createRecord(
                folderId = quickFolder.id,
                name = "Scan $dateStr",
                description = "Quick scan at $dateStr"
            )) {
                is Result.Success -> recordResult.data
                is Result.Error -> return recordResult
                is Result.Loading -> return Result.Error(Exception("Unexpected loading state"))
            }

            when (val docResult = addDocumentUseCase(recordId, imageUri)) {
                is Result.Success -> Result.Success(recordId)
                is Result.Error -> Result.Error(docResult.exception)
                is Result.Loading -> Result.Error(Exception("Unexpected loading state"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}