package com.docs.scanner.data.remote.drive

import android.content.Context
import com.docs.scanner.domain.model.Result
import com.google.android.gms.auth.api.signin.GoogleSignIn
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    
    private suspend fun <T> safeApiCall(call: suspend () -> T): Result<T> {
        return try {
            Result.Success(call())
        } catch (e: Exception) {
            Result.Error(Exception("API Error: ${e.message}", e))
        }
    }
    
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
            safeApiCall {
                val drive = driveService ?: getDriveService() 
                    ?: throw Exception("Not signed in to Google Drive")
                
                val timestamp = System.currentTimeMillis()
                val backupZip = File(context.cacheDir, "backup_$timestamp.zip")
                
                try {
                    createBackupZip(backupZip)
                    
                    val folderId = getOrCreateBackupFolder(drive)
                    
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
                    
                    file.id
                } finally {
                    if (backupZip.exists()) {
                        backupZip.delete()
                    }
                }
            }
        }
    }
    
    override suspend fun listBackups(): Result<List<BackupInfo>> {
        return withContext(Dispatchers.IO) {
            safeApiCall {
                val drive = driveService ?: getDriveService() 
                    ?: throw Exception("Not signed in to Google Drive")
                
                val folderId = getOrCreateBackupFolder(drive)
                
                val result = drive.files().list()
                    .setQ("'$folderId' in parents and trashed=false")
                    .setSpaces("drive")
                    .setFields("files(id, name, createdTime, size)")
                    .setOrderBy("createdTime desc")
                    .execute()
                
                result.files.map { file ->
                    BackupInfo(
                        fileId = file.id,
                        fileName = file.name,
                        createdDate = file.createdTime.value,
                        sizeBytes = file.getSize() ?: 0L
                    )
                }
            }
        }
    }
    
    override suspend fun restoreBackup(fileId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            safeApiCall {
                val drive = driveService ?: getDriveService() 
                    ?: throw Exception("Not signed in to Google Drive")
                
                // Create backup of current database before restore
                val dbFile = context.getDatabasePath("document_scanner.db")
                if (dbFile.exists()) {
                    val backupDbFile = File(dbFile.parent, "${dbFile.name}.backup_${System.currentTimeMillis()}")
                    dbFile.copyTo(backupDbFile, overwrite = true)
                    
                    // Clean up WAL files
                    val walFile = File(dbFile.parent, "${dbFile.name}-wal")
                    val shmFile = File(dbFile.parent, "${dbFile.name}-shm")
                    walFile.delete()
                    shmFile.delete()
                }
                
                val tempZip = File(context.cacheDir, "restore_temp_${System.currentTimeMillis()}.zip")
                
                try {
                    val outputStream = FileOutputStream(tempZip)
                    drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
                    outputStream.close()
                    
                    restoreFromZip(tempZip)
                } finally {
                    if (tempZip.exists()) {
                        tempZip.delete()
                    }
                }
            }
        }
    }
    
    private fun getOrCreateBackupFolder(drive: Drive): String {
        val result = drive.files().list()
            .setQ("mimeType='application/vnd.google-apps.folder' and name='$backupFolderName' and trashed=false")
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()
        
        return if (result.files.isNotEmpty()) {
            result.files[0].id
        } else {
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
            // Add database
            if (dbFile.exists()) {
                FileInputStream(dbFile).use { input ->
                    zip.putNextEntry(ZipEntry("database.db"))
                    input.copyTo(zip)
                    zip.closeEntry()
                }
            }
            
            // Add document files
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
            
            // Add manifest
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val manifest = """
                {
                  "app_version": "2.0.0",
                  "backup_date": "${dateFormat.format(Date())}",
                  "backup_timestamp": ${System.currentTimeMillis()},
                  "db_version": 2
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
                        val dbFile = context.getDatabasePath("document_scanner.db")
                        
                        // Create parent directory if needed
                        dbFile.parentFile?.mkdirs()
                        
                        FileOutputStream(dbFile).use { output ->
                            zip.copyTo(output)
                        }
                    }
                    
                    entry.name.startsWith("documents/") -> {
                        val file = File(context.filesDir, entry.name)
                        
                        // Skip if file already exists and has same size
                        if (file.exists() && file.length() == entry.size) {
                            println("Skipping existing file: ${file.name}")
                        } else {
                            file.parentFile?.mkdirs()
                            
                            FileOutputStream(file).use { output ->
                                zip.copyTo(output)
                            }
                        }
                    }
                    
                    entry.name == "manifest.json" -> {
                        // Read and validate manifest
                        val manifestContent = zip.bufferedReader().use { it.readText() }
                        println("Restore manifest: $manifestContent")
                    }
                }
                
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}