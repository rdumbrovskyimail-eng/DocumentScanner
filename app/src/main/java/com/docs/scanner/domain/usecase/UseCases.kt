package com.docs.scanner.domain.usecase

import android.net.Uri
import com.docs.scanner.domain.model.*
import com.docs.scanner.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class QuickScanUseCase @Inject constructor(
    private val folderRepository: FolderRepository,
    private val recordRepository: RecordRepository,
    private val addDocumentUseCase: AddDocumentUseCase,
    private val getFoldersUseCase: GetFoldersUseCase
) {
    suspend operator fun invoke(imageUri: Uri): Result<Long> {
        return try {
            val foldersFlow = getFoldersUseCase()
            var testFolder: Folder? = null
            
            foldersFlow.first().let { folders ->
                testFolder = folders.find { it.name == "Test" }
            }
            
            val folderId = if (testFolder == null) {
                when (val result = folderRepository.createFolder("Test", "test")) {
                    is Result.Success -> result.data
                    is Result.Error -> return Result.Error(result.exception)
                    else -> return Result.Error(Exception("Unknown error creating folder"))
                }
            } else {
                testFolder!!.id
            }
            
            val recordName = "New documents"
            
            val recordId = when (val result = recordRepository.createRecord(folderId, recordName, null)) {
                is Result.Success -> result.data
                is Result.Error -> return Result.Error(result.exception)
                else -> return Result.Error(Exception("Unknown error creating record"))
            }
            
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
