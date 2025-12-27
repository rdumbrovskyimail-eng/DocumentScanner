package com.docs.scanner.domain.usecase.folder

import com.docs.scanner.domain.model.Folder
import com.docs.scanner.domain.repository.FolderRepository
import javax.inject.Inject

/**
 * Get folder by ID.
 * 
 * Session 6 addition: Required by EditorViewModel, RecordsViewModel.
 */
class GetFolderByIdUseCase @Inject constructor(
    private val folderRepository: FolderRepository
) {
    suspend operator fun invoke(folderId: Long): Folder? {
        return folderRepository.getFolderById(folderId)
    }
}