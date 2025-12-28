package com.docs.scanner.domain.usecase.folder

import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.FolderRepository
import javax.inject.Inject

/**
 * Create a new folder.
 * 
 * Validation:
 * - Name cannot be blank
 * - Name max length: 100 characters
 * 
 * @return Result.Success with folder ID or Result.Error
 */
class CreateFolderUseCase @Inject constructor(
    private val folderRepository: FolderRepository
) {
    suspend operator fun invoke(name: String, description: String?): Result<Long> {
        if (name.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Folder name cannot be empty")
            )
        }
        
        if (name.length > MAX_NAME_LENGTH) {
            return Result.Error(
                IllegalArgumentException("Folder name too long (max $MAX_NAME_LENGTH characters)")
            )
        }
        
        return folderRepository.createFolder(name.trim(), description?.trim())
    }
    
    companion object {
        private const val MAX_NAME_LENGTH = 100
    }
}