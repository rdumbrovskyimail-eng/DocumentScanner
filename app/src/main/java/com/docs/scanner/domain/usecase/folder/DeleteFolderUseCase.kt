package com.docs.scanner.domain.usecase.folder

import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.FolderRepository
import javax.inject.Inject

/**
 * Delete a folder by ID.
 * 
 * Note: This will also delete all records and documents in the folder
 * due to CASCADE constraints in the database.
 */
class DeleteFolderUseCase @Inject constructor(
    private val folderRepository: FolderRepository
) {
    suspend operator fun invoke(folderId: Long): Result<Unit> {
        if (folderId <= 0) {
            return Result.Error(
                IllegalArgumentException("Invalid folder ID: $folderId")
            )
        }
        
        return folderRepository.deleteFolder(folderId)
    }
}