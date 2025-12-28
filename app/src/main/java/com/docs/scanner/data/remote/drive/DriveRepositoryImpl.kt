package com.docs.scanner.data.remote.drive

import android.content.Context
import android.content.Intent
import com.docs.scanner.BuildConfig
import com.docs.scanner.domain.model.Result
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

interface DriveRepository {
    suspend fun isSignedIn(): Boolean
    suspend fun signIn(): Result<String>
    suspend fun signOut()
    suspend fun uploadBackup(onProgress: ((Long, Long) -> Unit)? = null): Result<String>
    suspend fun listBackups(): Result<List<BackupInfo>>
    suspend fun restoreBackup(fileId: String): Result<Unit>
}

data class BackupInfo(
    val fileId: String,
    val fileName: String,
    val createdDate: Long,
    val sizeBytes: Long
)

// ✅ НОВЫЙ: Result с auth support
sealed class DriveResult<out T> {
    data class Success<T>(val data: T) : DriveResult<T>()
    data class Error(val exception: Exception) : DriveResult<Nothing>()
    data class RequiresAuth(val intent: Intent) : DriveResult<Nothing>()  // ✅ НОВЫЙ
}

@Singleton
class DriveRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DriveRepository {

    private var driveService: Drive? = null
    private val backupFolderName = "DocumentScanner Backup"
    private val databaseName = "document_scanner.db"

    // ✅ УЛУЧШЕНО: Обработка UserRecoverableAuthException
    private suspend fun <T> safeApiCall(call: suspend () -> T): DriveResult<T> {
        return try {
            DriveResult.Success(call())
        } catch (e: UserRecoverableAuthException) {
            // ✅ Специальная обработка для auth
            DriveResult.RequiresAuth(e.intent)
        } catch (e: GoogleJsonResponseException) {
            DriveResult.Error(Exception("Drive API error: ${e.message}", e))
        } catch (e: IOException) {
            DriveResult.Error(Exception("Network error: ${e.message}", e))
        } catch (e: Exception) {
            DriveResult.Error(Exception("Unknown error: ${e.message}", e))
        }
    }

    private fun getDriveService(): Drive? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_APPDATA)
        ).apply {
            selectedAccount = account.account
        }

        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("Document Scanner")
            .build()
    }

    override suspend fun isSignedIn(): Boolean = GoogleSignIn.getLastSignedInAccount(context) != null

    override suspend fun signIn(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
                ?: return@withContext Result.Error(Exception("Not signed in"))
            driveService = getDriveService()
            Result.Success(account.email ?: "Unknown")
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun signOut() = withContext(Dispatchers.IO) {
        val client = GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN)
        client.signOut()
        driveService = null
    }

    // ✅ УЛУЧШЕНО: Chunked upload + progress + auto-cleanup
    override suspend fun uploadBackup(
        onProgress: ((Long, Long) -> Unit)?
    ): Result<String> = withContext(Dispatchers.IO) {
        when (val result = safeApiCall {
            val drive = driveService ?: getDriveService() ?: throw Exception("Not signed in")

            val timestamp = System.currentTimeMillis()
            val backupZip = File(context.cacheDir, "backup_$timestamp.zip")

            try {
                createBackupZip(backupZip)
                
                val fileSize = backupZip.length()
                
                // ✅ ПРОВЕРИТЬ место в Drive
                if (!checkDriveSpace(drive, fileSize)) {
                    throw Exception("Not enough space in Google Drive")
                }
                
                val folderId = getOrCreateBackupFolder(drive)

                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = "backup_$timestamp.zip"
                    parents = listOf(folderId)
                }

                // ✅ CHUNKED UPLOAD для файлов > 5MB
                val fileId = if (fileSize > 5 * 1024 * 1024) {
                    val mediaContent = FileContent("application/zip", backupZip)
                    
                    val request = drive.files().create(fileMetadata, mediaContent)
                        .setFields("id")
                    
                    val uploader = request.mediaHttpUploader
                    uploader.isDirectUploadEnabled = false
                    uploader.chunkSize = 1024 * 1024  // 1MB chunks
                    
                    uploader.setProgressListener { progress ->
                        val uploaded = progress.numBytesUploaded
                        onProgress?.invoke(uploaded, fileSize)
                        println("📤 Upload progress: $uploaded / $fileSize bytes")
                    }
                    
                    request.execute().id
                } else {
                    val mediaContent = FileContent("application/zip", backupZip)
                    drive.files().create(fileMetadata, mediaContent)
                        .setFields("id")
                        .execute().id
                }
                
                // ✅ AUTO-CLEANUP старых backup'ов (оставить только 10)
                cleanupOldBackups(drive, folderId)
                
                fileId
            } finally {
                if (backupZip.exists()) backupZip.delete()
            }
        }) {
            is DriveResult.Success -> Result.Success(result.data)
            is DriveResult.Error -> Result.Error(result.exception)
            is DriveResult.RequiresAuth -> Result.Error(
                Exception("Authentication required. Please re-authorize the app.")
            )
        }
    }
    
    // ✅ НОВАЯ ФУНКЦИЯ: Проверка свободного места
    private suspend fun checkDriveSpace(drive: Drive, requiredBytes: Long): Boolean {
        return try {
            val about = drive.about().get()
                .setFields("storageQuota")
                .execute()
            
            val quota = about.storageQuota
            val used = quota.usage ?: 0L
            val total = quota.limit ?: Long.MAX_VALUE
            val available = total - used
            
            println("💾 Drive space: ${available / 1024 / 1024} MB available")
            available >= requiredBytes
        } catch (e: Exception) {
            println("⚠️ Failed to check Drive space: ${e.message}")
            true // Продолжаем если не можем проверить
        }
    }
    
    // ✅ НОВАЯ ФУНКЦИЯ: Автоочистка старых backup'ов
    private suspend fun cleanupOldBackups(drive: Drive, folderId: String) {
        try {
            val result = drive.files().list()
                .setQ("'$folderId' in parents and trashed = false")
                .setOrderBy("createdTime desc")
                .setFields("files(id, name, createdTime)")
                .execute()
            
            val allBackups = result.files
            if (allBackups.size > MAX_BACKUPS) {
                val toDelete = allBackups.drop(MAX_BACKUPS)
                
                toDelete.forEach { file ->
                    try {
                        drive.files().delete(file.id).execute()
                        println("🗑️ Deleted old backup: ${file.name}")
                    } catch (e: Exception) {
                        println("⚠️ Failed to delete backup ${file.name}: ${e.message}")
                    }
                }
                
                println("✅ Cleaned up ${toDelete.size} old backups")
            }
        } catch (e: Exception) {
            println("⚠️ Failed to cleanup old backups: ${e.message}")
        }
    }

    override suspend fun listBackups(): Result<List<BackupInfo>> = withContext(Dispatchers.IO) {
        when (val result = safeApiCall {
            val drive = driveService ?: getDriveService() ?: throw Exception("Not signed in")
            val folderId = getOrCreateBackupFolder(drive)

            val result = drive.files().list()
                .setQ("'$folderId' in parents and trashed = false")
                .setOrderBy("createdTime desc")
                .setFields("files(id, name, createdTime, size)")
                .execute()

            result.files.mapNotNull { file ->
                val date = file.createdTime?.value ?: return@mapNotNull null
                BackupInfo(
                    fileId = file.id,
                    fileName = file.name ?: "unknown",
                    createdDate = date,
                    sizeBytes = file.size?.toLong() ?: 0L
                )
            }
        }) {
            is DriveResult.Success -> Result.Success(result.data)
            is DriveResult.Error -> Result.Error(result.exception)
            is DriveResult.RequiresAuth -> Result.Error(
                Exception("Authentication required")
            )
        }
    }

    override suspend fun restoreBackup(fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        when (val result = safeApiCall {
            val drive = driveService ?: getDriveService() ?: throw Exception("Not signed in")

            val dbFile = context.getDatabasePath(databaseName)
            if (dbFile.exists()) {
                val backupDb = File(dbFile.parent, "${dbFile.name}.pre_restore_${System.currentTimeMillis()}")
                dbFile.copyTo(backupDb, overwrite = true)

                File(dbFile.parent, "$databaseName-wal").delete()
                File(dbFile.parent, "$databaseName-shm").delete()
            }

            val tempZip = File(context.cacheDir, "restore_${System.currentTimeMillis()}.zip")
            try {
                FileOutputStream(tempZip).use { output ->
                    drive.files().get(fileId).executeMediaAndDownloadTo(output)
                }

                restoreFromZip(tempZip)
            } finally {
                if (tempZip.exists()) tempZip.delete()
            }
        }) {
            is DriveResult.Success -> Result.Success(Unit)
            is DriveResult.Error -> Result.Error(result.exception)
            is DriveResult.RequiresAuth -> Result.Error(
                Exception("Authentication required")
            )
        }
    }

    private fun getOrCreateBackupFolder(drive: Drive): String {
        val result = drive.files().list()
            .setQ("mimeType='application/vnd.google-apps.folder' and name='$backupFolderName' and trashed=false")
            .setFields("files(id)")
            .execute()

        return if (result.files.isNotEmpty()) {
            result.files[0].id
        } else {
            val metadata = com.google.api.services.drive.model.File().apply {
                name = backupFolderName
                mimeType = "application/vnd.google-apps.folder"
            }
            drive.files().create(metadata).setFields("id").execute().id
        }
    }

    // ✅ ИСПОЛЬЗОВАТЬ BuildConfig.VERSION_NAME вместо hardcoded
    private fun createBackupZip(zipFile: File) {
        val dbFile = context.getDatabasePath(databaseName)
        val documentsDir = File(context.filesDir, "documents")

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zip ->
            if (dbFile.exists()) {
                addToZip(zip, dbFile, "database.db")
            }

            documentsDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val relativePath = file.relativeTo(context.filesDir).path
                addToZip(zip, file, relativePath)
            }

            val manifest = JSONObject().apply {
                put("app_version", BuildConfig.VERSION_NAME)  // ✅ ИСПОЛЬЗОВАТЬ BuildConfig
                put("backup_date", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                put("timestamp", System.currentTimeMillis())
                put("db_version", 4)
            }.toString()

            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
        }
    }

    private fun restoreFromZip(zipFile: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zip ->
            var entry: ZipEntry?
            var manifestValid = false

            while (zip.nextEntry.also { entry = it } != null) {
                val name = entry!!.name

                when {
                    name == "manifest.json" -> {
                        val content = zip.bufferedReader().use { it.readText() }
                        try {
                            val json = JSONObject(content)
                            val version = json.optInt("db_version", 0)
                            if (version in 1..4) manifestValid = true
                        } catch (e: Exception) {
                            throw Exception("Invalid manifest.json")
                        }
                    }

                    name == "database.db" -> {
                        if (!manifestValid) throw Exception("Invalid backup: missing or invalid manifest")
                        val dbFile = context.getDatabasePath(databaseName)
                        dbFile.parentFile?.mkdirs()
                        FileOutputStream(dbFile).use { output -> zip.copyTo(output) }
                    }

                    name.startsWith("documents/") -> {
                        if (!manifestValid) continue
                        val file = File(context.filesDir, name)
                        file.parentFile?.mkdirs()
                        FileOutputStream(file).use { output -> zip.copyTo(output) }
                    }
                }
                zip.closeEntry()
            }

            if (!manifestValid) throw Exception("Backup validation failed")
        }
    }

    private fun addToZip(zip: ZipOutputStream, file: File, entryName: String) {
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { it.copyTo(zip) }
        zip.closeEntry()
    }
    
    companion object {
        private const val MAX_BACKUPS = 10
    }
}