package com.docs.scanner.data.remote.drive

import android.content.Context
import com.docs.scanner.data.remote.GoogleDriveService
import com.docs.scanner.domain.model.Result
import com.google.android.gms.auth.api.signin.GoogleSignIn
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveService: GoogleDriveService
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
        val res = driveService.uploadBackup { _, _ -> }
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
        Timber.w("Drive restore requested for $fileId (not implemented)")
        return when (val res = driveService.downloadBackup(fileId) { _, _ -> }) {
            is com.docs.scanner.domain.core.DomainResult.Success -> Result.Success(Unit)
            is com.docs.scanner.domain.core.DomainResult.Failure -> Result.Error(res.error.toException())
        }
    }

    override suspend fun signOut() {
        // Sign-out is handled by UI using GoogleSignIn client; keep repository stateless for now.
        Timber.d("DriveRepositoryImpl.signOut called")
    }
}

