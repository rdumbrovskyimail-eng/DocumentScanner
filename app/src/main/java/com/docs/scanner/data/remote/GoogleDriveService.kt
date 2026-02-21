package com.docs.scanner.data.remote

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.media.MediaHttpDownloader
import com.google.api.client.googleapis.media.MediaHttpDownloaderProgressListener
import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.docs.scanner.domain.core.BackupInfo
import com.docs.scanner.domain.core.DomainError
import com.docs.scanner.domain.core.DomainResult
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import java.io.FileOutputStream
import java.util.Date

/**
 * Google Drive integration service.
 *
 * Stores backups in the appDataFolder to keep user Drive clean.
 */
@Singleton
class GoogleDriveService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun uploadBackup(
        localPath: String,
        onProgress: (uploaded: Long, total: Long) -> Unit
    ): DomainResult<String> {
        return DomainResult.catching {
            val drive = getDriveClient().getOrThrow()
            val localFile = java.io.File(localPath)
            if (!localFile.exists()) throw DomainError.FileNotFound(localPath).toException()

            val meta = File().apply {
                name = localFile.name
                parents = listOf("appDataFolder")
                mimeType = "application/zip"
                modifiedTime = com.google.api.client.util.DateTime(Date(localFile.lastModified()))
            }

            val mediaContent = FileContent("application/zip", localFile)
            val create = drive.files()
                .create(meta, mediaContent)
                .setFields("id,name,size,modifiedTime")

            val uploader: MediaHttpUploader = create.mediaHttpUploader
            uploader.isDirectUploadEnabled = false
            uploader.progressListener = MediaHttpUploaderProgressListener { u ->
                val total = localFile.length()
                val uploaded = when {
                    u.numBytesUploaded > 0 -> u.numBytesUploaded
                    else -> 0L
                }
                onProgress(uploaded, total)
            }

            val result = create.execute()
            Timber.i("✅ Drive upload complete: %s (%s)", result.id, result.name)
            result.id
        }
    }

    suspend fun downloadBackup(
        fileId: String,
        destDir: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): DomainResult<String> {
        return DomainResult.catching {
            val drive = getDriveClient().getOrThrow()

            val meta = drive.files()
                .get(fileId)
                .setFields("id,name,size,modifiedTime")
                .execute()

            val name = meta.name ?: "drive_backup_$fileId.zip"
            val outDir = java.io.File(destDir).apply { mkdirs() }
            val outFile = java.io.File(outDir, name)

            val get = drive.files().get(fileId)
            val downloader: MediaHttpDownloader = get.mediaHttpDownloader
            downloader.isDirectDownloadEnabled = false
            downloader.progressListener = MediaHttpDownloaderProgressListener { d ->
                val downloaded = d.numBytesDownloaded
                val total = (meta.size ?: -1L).toLong()
                onProgress(downloaded, total)
            }

            FileOutputStream(outFile).use { out ->
                get.executeMediaAndDownloadTo(out)
            }
            Timber.i("✅ Drive download complete: %s -> %s", fileId, outFile.absolutePath)
            outFile.absolutePath
        }
    }

    suspend fun listBackups(): DomainResult<List<BackupInfo>> {
        return DomainResult.catching {
            val drive = getDriveClient().getOrThrow()
            val res = drive.files()
                .list()
                .setSpaces("appDataFolder")
                .setQ("mimeType='application/zip' and trashed=false")
                .setOrderBy("modifiedTime desc")
                .setFields("files(id,name,modifiedTime,size)")
                .execute()

            (res.files ?: emptyList()).map { f ->
                BackupInfo(
                    id = f.id,
                    name = f.name ?: f.id,
                    timestamp = (f.modifiedTime?.value ?: 0L).toLong(),
                    sizeBytes = (f.size ?: 0L).toLong(),
                    folderCount = 0,
                    recordCount = 0,
                    documentCount = 0
                )
            }
        }
    }

    suspend fun deleteBackup(fileId: String): DomainResult<Unit> {
        return DomainResult.catching {
            val drive = getDriveClient().getOrThrow()
            drive.files().delete(fileId).execute()
            Timber.i("🗑️ Drive backup deleted: %s", fileId)
        }
    }

    private fun getDriveClient(): Result<Drive> = runCatching {
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: throw IllegalStateException("Not signed in to Google")

        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_APPDATA, DriveScopes.DRIVE_FILE)
        ).apply {
            selectedAccount = account.account
        }

        Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("DocumentScanner")
            .build()
    }
}

