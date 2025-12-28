package com.docs.scanner.domain.usecase.folder

import com.docs.scanner.domain.model.Folder
import com.docs.scanner.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Get all folders with record counts.
 * Returns Flow for real-time updates.
 */
class GetFoldersUseCase @Inject constructor(
    private val folderRepository: FolderRepository
) {
    operator fun invoke(): Flow<List<Folder>> {
        return folderRepository.getAllFolders()
    }
}