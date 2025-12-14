package com.docs.scanner.data.remote.drive

import android.content.Context
import android.net.Uri
import com.docs.scanner.domain.model.Result
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

interface DriveRepository {
    suspend fun isSignedIn(): Boolean
    suspend fun signIn(): Result<String> // email
    suspend fun signOut()
    suspend fun uploadBackup(): Result<String> // fileId
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
    
    private fun getDriveService(): Drive? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = account.account
        
        return Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("Document Scanner")
            .build()
    }
    
    override suspend fun isSignedIn(): Boolean {
        return GoogleSignIn.getLastSignedInAccount(context) != null
    }
    
    override suspend fun signIn(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                if (account != null) {
                    driveService = getDriveService()
                    Result.Success(account.email ?: "Unknown")
                } else {
                    Result.Error(Exception("Not signed in"))
                }
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }
    
    override suspend fun signOut() {
        withContext(Dispatchers.IO) {
            val client = GoogleSignIn.getClient(
                context,
                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestScopes(Scope(DriveScopes.DRIVE_FILE))
                    .build()
            )
            client.signOut()
            driveService = null
        }
    }
    
    override suspend fun uploadBackup(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val drive = driveService ?: getDriveService() 
                    ?: return@withContext Result.Error(Exception("Not signed in"))
                
                // 1. Создать ZIP архив
                val timestamp = System.currentTimeMillis()
                val backupZip = File(context.cacheDir, "backup_$timestamp.zip")
                
                createBackupZip(backupZip)
                
                // 2. Найти или создать папку "DocumentScanner Backup"
                val folderId = getOrCreateBackupFolder(drive)
                
                // 3. Загрузить ZIP в Drive
                val fileMetadata = com.google.api.services.drive.model.File()
                fileMetadata.name = "backup_$timestamp.zip"
                fileMetadata.parents = listOf(folderId)
                
                val mediaContent = com.google.api.client.http.FileContent(
                    "application/zip",
                    backupZip
                )
                
                val file = drive.files().create(fileMetadata, mediaContent)
                    .setFields("id, name")
                    .execute()
                
                // 4. Очистить кеш
                backupZip.delete()
                
                Result.Success(file.id)
                
            } catch (e: Exception) {
                Result.Error(Exception("Backup failed: ${e.message}", e))
            }
        }
    }
    
    override suspend fun listBackups(): Result<List<BackupInfo>> {
        return withContext(Dispatchers.IO) {
            try {
                val drive = driveService ?: getDriveService() 
                    ?: return@withContext Result.Error(Exception("Not signed in"))
                
                val folderId = getOrCreateBackupFolder(drive)
                
                val result = drive.files().list()
                    .setQ("'$folderId' in parents and trashed=false")
                    .setSpaces("drive")
                    .setFields("files(id, name, createdTime, size)")
                    .setOrderBy("createdTime desc")
                    .execute()
                
                val backups = result.files.map { file ->
                    BackupInfo(
                        fileId = file.id,
                        fileName = file.name,
                        createdDate = file.createdTime.value,
                        sizeBytes = file.getSize() ?: 0L
                    )
                }
                
                Result.Success(backups)
                
            } catch (e: Exception) {
                Result.Error(Exception("Failed to list backups: ${e.message}", e))
            }
        }
    }
    
    override suspend fun restoreBackup(fileId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val drive = driveService ?: getDriveService() 
                    ?: return@withContext Result.Error(Exception("Not signed in"))
                
                // 1. Скачать ZIP из Drive
                val tempZip = File(context.cacheDir, "restore_temp.zip")
                val outputStream = FileOutputStream(tempZip)
                
                drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
                outputStream.close()
                
                // 2. Распаковать и восстановить
                restoreFromZip(tempZip)
                
                // 3. Очистить кеш
                tempZip.delete()
                
                Result.Success(Unit)
                
            } catch (e: Exception) {
                Result.Error(Exception("Restore failed: ${e.message}", e))
            }
        }
    }
    
    private fun getOrCreateBackupFolder(drive: Drive): String {
        // Поиск существующей папки
        val result = drive.files().list()
            .setQ("mimeType='application/vnd.google-apps.folder' and name='$backupFolderName' and trashed=false")
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()
        
        return if (result.files.isNotEmpty()) {
            result.files[0].id
        } else {
            // Создать новую папку
            val folderMetadata = com.google.api.services.drive.model.File()
            folderMetadata.name = backupFolderName
            folderMetadata.mimeType = "application/vnd.google-apps.folder"
            
            val folder = drive.files().create(folderMetadata)
                .setFields("id")
                .execute()
            
            folder.id
        }
    }
    
    private fun createBackupZip(zipFile: File) {
        val dbFile = context.getDatabasePath("document_scanner.db")
        val documentsDir = File(context.filesDir, "documents")
        
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            // Добавить базу данных
            if (dbFile.exists()) {
                FileInputStream(dbFile).use { input ->
                    zip.putNextEntry(ZipEntry("database.db"))
                    input.copyTo(zip)
                    zip.closeEntry()
                }
            }
            
            // Добавить все фото документов
            if (documentsDir.exists()) {
                documentsDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val relativePath = file.relativeTo(context.filesDir).path
                        FileInputStream(file).use { input ->
                            zip.putNextEntry(ZipEntry(relativePath))
                            input.copyTo(zip)
                            zip.closeEntry()
                        }
                    }
                }
            }
            
            // Добавить manifest
            val manifest = """
                {
                  "app_version": "2.0.0",
                  "backup_date": "${System.currentTimeMillis()}",
                  "db_version": 1
                }
            """.trimIndent()
            
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
        }
    }
    
    private fun restoreFromZip(zipFile: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zip ->
            var entry = zip.nextEntry
            
            while (entry != null) {
                when {
                    entry.name == "database.db" -> {
                        // Восстановить базу данных (merge)
                        // TODO: Implement smart merge
                        val dbFile = context.getDatabasePath("document_scanner.db")
                        FileOutputStream(dbFile).use { output ->
                            zip.copyTo(output)
                        }
                    }
                    
                    entry.name.startsWith("documents/") -> {
                        // Восстановить файл документа
                        val file = File(context.filesDir, entry.name)
                        file.parentFile?.mkdirs()
                        
                        FileOutputStream(file).use { output ->
                            zip.copyTo(output)
                        }
                    }
                }
                
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}