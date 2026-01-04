package com.docs.scanner.data.remote

import com.docs.scanner.domain.core.BackupInfo
import com.docs.scanner.domain.core.DomainError
import com.docs.scanner.domain.core.DomainResult
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Drive integration service.
 *
 * Current implementation is a compilation-safe placeholder:
 * - The UI can initiate Google Sign-In on its own.
 * - Repositories can call upload/download/list/delete via this service.
 *
 * TODO(2026): Replace with a full Drive API implementation (upload ZIP, download ZIP, list backups).
 */
@Singleton
class GoogleDriveService @Inject constructor() {

    suspend fun uploadBackup(
        onProgress: (uploaded: Long, total: Long) -> Unit
    ): DomainResult<String> {
        Timber.w("GoogleDriveService.uploadBackup not implemented")
        onProgress(0, 0)
        return DomainResult.failure(DomainError.NetworkFailed(UnsupportedOperationException("Google Drive upload not implemented")))
    }

    suspend fun downloadBackup(
        fileId: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): DomainResult<String> {
        Timber.w("GoogleDriveService.downloadBackup not implemented: $fileId")
        onProgress(0, 0)
        return DomainResult.failure(DomainError.NetworkFailed(UnsupportedOperationException("Google Drive download not implemented")))
    }

    suspend fun listBackups(): DomainResult<List<BackupInfo>> {
        Timber.w("GoogleDriveService.listBackups not implemented")
        return DomainResult.Success(emptyList())
    }

    suspend fun deleteBackup(fileId: String): DomainResult<Unit> {
        Timber.w("GoogleDriveService.deleteBackup not implemented: $fileId")
        return DomainResult.failure(DomainError.NetworkFailed(UnsupportedOperationException("Google Drive delete not implemented")))
    }
}

