// Резервное копирование и восстановление с проверкой конфликтов
suspend fun uploadBackup(): Result<String> {
    return safeApiCall {
        driveService.uploadFile("backup.zip")
        Result.success("Backup uploaded successfully.")
    } ?: Result.failure("Failed to upload backup.")
}

private fun restoreFromZip(zipFile: File) {
    ZipInputStream(FileInputStream(zipFile)).use { zip ->
        var entry = zip.nextEntry
        
        while (entry != null) {
            when {
                entry.name == "database.db" -> {
                    val dbFile = context.getDatabasePath("document_scanner.db")
                    if (dbFile.exists()) {
                        println("Database already exists! Skipping...")
                        return
                    }
                    FileOutputStream(dbFile).use { output ->
                        zip.copyTo(output)
                    }
                }
                entry.name.startsWith("documents/") -> {
                    val file = File(context.filesDir, entry.name)
                    if (file.exists()) {
                        println("File conflict detected for ${file.name}, skipping restoration.")
                        return
                    }
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