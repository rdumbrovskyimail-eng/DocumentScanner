package com.docs.scanner.domain.usecase.folder

import com.docs.scanner.domain.model.Folder
import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.FolderRepository
import javax.inject.Inject

/**
 * Update an existing folder.
 * 
 * Validation:
 * - Folder ID must be valid (> 0)
 * - Name cannot be blank
 * - Name max length: 100 characters
 */
class UpdateFolderUseCase @Inject constructor(
    private val folderRepository: FolderRepository
) {
    suspend operator fun invoke(folder: Folder): Result<Unit> {
        if (folder.id <= 0) {
            return Result.Error(
                IllegalArgumentException("Invalid folder ID: ${folder.id}")
            )
        }
        
        if (folder.name.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Folder name cannot be empty")
            )
        }
        
        if (folder.name.length > MAX_NAME_LENGTH) {
            return Result.Error(
                IllegalArgumentException("Folder name too long (max $MAX_NAME_LENGTH characters)")
            )
        }
        
        return folderRepository.updateFolder(folder)
    }
    
    companion object {
        private const val MAX_NAME_LENGTH = 100
    }
}