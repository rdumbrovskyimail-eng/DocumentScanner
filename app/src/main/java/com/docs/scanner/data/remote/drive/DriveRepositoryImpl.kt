package com.docs.scanner.data.remote.drive

import android.content.Context
import com.docs.scanner.data.remote.GoogleDriveService
import com.docs.scanner.domain.model.Result
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveService: GoogleDriveService,
    private val backupRepository: com.docs.scanner.domain.repository.BackupRepository
) : DriveRepository {

    override fun isSignedIn(): Boolean =
        GoogleSignIn.getLastSignedInAccount(context) != null

    override suspend fun signIn(): Result<String> {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return if (account?.email != null) {
            Result.Success(account.email!!)
        } else {
            Result.Error(IllegalStateException("Not signed in"))
        }
    }

    override suspend fun uploadBackup(): Result<Unit> {
        val local = backupRepository.createLocalBackup(includeImages = true).getOrElse {
            return Result.Error(it.toException())
        }
        val res = driveService.uploadBackup(local) { _, _ -> }
        return when (res) {
            is com.docs.scanner.domain.core.DomainResult.Success -> Result.Success(Unit)
            is com.docs.scanner.domain.core.DomainResult.Failure -> Result.Error(res.error.toException())
        }
    }

    override suspend fun listBackups(): Result<List<DriveBackup>> {
        // Placeholder: GoogleDriveService.listBackups currently returns empty list.
        return when (val res = driveService.listBackups()) {
            is com.docs.scanner.domain.core.DomainResult.Success -> {
                Result.Success(
                    res.data.map {
                        DriveBackup(
                            fileId = it.id,
                            fileName = it.name,
                            modifiedTimeMillis = it.timestamp
                        )
                    }.sortedByDescending { it.modifiedTimeMillis }
                )
            }
            is com.docs.scanner.domain.core.DomainResult.Failure -> Result.Error(res.error.toException())
        }
    }

    override suspend fun restoreBackup(fileId: String): Result<Unit> {
        val downloaded = driveService.downloadBackup(fileId, destDir = context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath) { _, _ -> }
            .getOrElse { return Result.Error(it.toException()) }

        // Default behavior for legacy UI: replace DB (merge=false)
        return when (val res = backupRepository.restoreFromLocal(downloaded, merge = false)) {
            is com.docs.scanner.domain.core.DomainResult.Success -> Result.Success(Unit)
            is com.docs.scanner.domain.core.DomainResult.Failure -> Result.Error(res.error.toException())
        }
    }

    override suspend fun deleteBackup(fileId: String): Result<Unit> {
        return when (val res = driveService.deleteBackup(fileId)) {
            is com.docs.scanner.domain.core.DomainResult.Success -> Result.Success(Unit)
            is com.docs.scanner.domain.core.DomainResult.Failure -> Result.Error(res.error.toException())
        }
    }

    override suspend fun signOut() {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DriveScopes.DRIVE_FILE), Scope(DriveScopes.DRIVE_APPDATA))
                .build()
            GoogleSignIn.getClient(context, gso).signOut()
            Timber.d("DriveRepositoryImpl.signOut: requested")
        } catch (e: Exception) {
            Timber.w(e, "DriveRepositoryImpl.signOut failed")
        }
    }
}

