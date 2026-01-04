package com.docs.scanner.data.remote.drive

import com.docs.scanner.domain.model.Result

interface DriveRepository {
    fun isSignedIn(): Boolean
    suspend fun signIn(): Result<String>
    suspend fun uploadBackup(): Result<Unit>
    suspend fun listBackups(): Result<List<DriveBackup>>
    suspend fun restoreBackup(fileId: String): Result<Unit>
    suspend fun signOut()
}

data class DriveBackup(
    val fileId: String,
    val fileName: String,
    val modifiedTimeMillis: Long
)

