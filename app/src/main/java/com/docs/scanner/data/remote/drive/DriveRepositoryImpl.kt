package com.docs.scanner.data.remote.drive

import android.content.Context
import com.docs.scanner.domain.model.Result
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
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
    suspend fun uploadBackup(): Result<String>
    suspend fun listBackups(): Result<List<BackupInfo>>
    suspend fun restoreBackup(fileId: String): Result<Unit>
}

data class BackupInfo(
    val fileId: String,
    val fileName: String,
    val createdDate: Long,
    val sizeBytes: Long
)

@Singleton
class DriveRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DriveRepository {

    private var driveService: Drive? = null
    private val backupFolderName = "DocumentScanner Backup"
    private val databaseName = "document_scanner.db"

    private suspend fun <T> safeApiCall(call: suspend () -> T): Result<T> {
        return try {
            Result.Success(call())
        } catch (e: Exception) {
            Result.Error(Exception("Drive API error: ${e.message}", e))
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

    override suspend fun uploadBackup(): Result<String> = withContext(Dispatchers.IO) {
        safeApiCall {
            val drive = driveService ?: getDriveService() ?: throw Exception("Not signed in")

            val timestamp = System.currentTimeMillis()
            val backupZip = File(context.cacheDir, "backup_$timestamp.zip")

            try {
                createBackupZip(backupZip)
                val folderId = getOrCreateBackupFolder(drive)

                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = "backup_$timestamp.zip"
                    parents = listOf(folderId)
                }

                val mediaContent = com.google.api.client.http.FileContent("application/zip", backupZip)
                val file = drive.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()

                file.id
            } finally {
                if (backupZip.exists()) backupZip.delete()
            }
        }
    }

    override suspend fun listBackups(): Result<List<BackupInfo>> = withContext(Dispatchers.IO) {
        safeApiCall {
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
        }
    }

    override suspend fun restoreBackup(fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val drive = driveService ?: getDriveService() ?: throw Exception("Not signed in")

            // ✅ Создаём резервную копию текущей БД перед восстановлением
            val dbFile = context.getDatabasePath(databaseName)
            if (dbFile.exists()) {
                val backupDb = File(dbFile.parent, "${dbFile.name}.pre_restore_${System.currentTimeMillis()}")
                dbFile.copyTo(backupDb, overwrite = true)

                // Очищаем WAL/SHM файлы
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

    private fun createBackupZip(zipFile: File) {
        val dbFile = context.getDatabasePath(databaseName)
        val documentsDir = File(context.filesDir, "documents")

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zip ->
            // База данных
            if (dbFile.exists()) {
                addToZip(zip, dbFile, "database.db")
            }

            // Документы
            documentsDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val relativePath = file.relativeTo(context.filesDir).path
                addToZip(zip, file, relativePath)
            }

            // manifest.json
            val manifest = JSONObject().apply {
                put("app_version", "2.1.0")
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
                        if (!manifestValid) continue // Пропускаем файлы если manifest не валиден
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
}