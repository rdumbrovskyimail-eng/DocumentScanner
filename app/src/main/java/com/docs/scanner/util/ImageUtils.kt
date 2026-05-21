package com.docs.scanner.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.FileNotFoundException

/**
 * Universal Image Utilities for Android 10-16+
 * Version: 2.0.0 (2026 Standards) - FULLY COMPILED
 * 
 * Handles:
 * - Photo Picker URIs (temporary access)
 * - Content URIs from gallery
 * - File URIs
 * - Camera captures
 * - EXIF rotation correction
 * - Memory-safe bitmap operations
 * 
 * Compatibility: Android 8.0 (API 26) - Android 16 (API 36)
 */
object ImageUtils {

    private const val TAG = "ImageUtils"
    
    // Размеры для оптимизации
    private const val MAX_IMAGE_DIMENSION = 2048
    private const val JPEG_QUALITY = 90
    
    // Директории
    private const val OCR_TEST_DIR = "ocr_test"
    private const val TEMP_IMAGES_DIR = "temp_images"

    /**
     * Копирует изображение из любого URI в стабильное внутреннее хранилище.
     * 
     * Решает проблемы:
     * - Photo Picker временные URI (Android 13+)
     * - Scoped Storage ограничения (Android 10+)
     * - Различные content providers
     * 
     * @param context Application context
     * @param sourceUri URI источника (content://, file://, etc.)
     * @param subDir Поддиректория в cache (например "ocr_test")
     * @param maxDimension Максимальный размер стороны (для оптимизации памяти)
     * @return Стабильный URI к скопированному файлу
     */
    @Throws(IOException::class)
    suspend fun copyToInternalStorage(
        context: Context,
        sourceUri: Uri,
        subDir: String = TEMP_IMAGES_DIR,
        maxDimension: Int = MAX_IMAGE_DIMENSION
    ): Uri = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        
        Timber.d("$TAG: Copying image from $sourceUri")
        
        // 1. Создаём директорию
        val outputDir = File(context.cacheDir, subDir).apply {
            if (!exists()) mkdirs()
        }
        
        // 2. Очищаем старые файлы в этой директории
        clearDirectory(outputDir, keepLast = 0)
        
        // 3. Создаём выходной файл
        val outputFile = File(outputDir, "image_${System.currentTimeMillis()}.jpg")
        
        try {
            // 4 & 5. Читаем только размеры и вычисляем inSampleSize
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxDimension, maxDimension)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            // Декодируем уменьшенную версию
            val bitmap = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: throw IOException("Failed to decode bitmap from URI: $sourceUri")

            // 6. Читаем EXIF напрямую из потока и корректируем ориентацию
            val rotatedBitmap = try {
                context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    val matrix = Matrix()
                    when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.preScale(-1f, 1f) }
                        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(-90f); matrix.preScale(-1f, 1f) }
                    }
                    if (!matrix.isIdentity) Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true) else bitmap
                } ?: bitmap
            } catch (e: Exception) {
                Timber.w(e, "$TAG: Failed to correct orientation, using original")
                bitmap
            }
            
            // 7. Сохраняем в файл
            FileOutputStream(outputFile).use { output ->
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }
            
            // Clean up heap
            if (rotatedBitmap !== bitmap) {
                bitmap.recycle()
            }
            
            Timber.d("$TAG: Image saved to ${outputFile.absolutePath} (${outputFile.length()} bytes)")
            
            getStableUri(context, outputFile)
        } catch (e: Exception) {
            outputFile.delete()
            throw IOException("Failed to copy image: ${e.message}", e)
        }
    }

    /**
     * Копирует изображение для OCR теста.
     * Использует отдельную директорию и очищает предыдущие тесты.
     */
    @Throws(IOException::class)
    suspend fun copyForOcrTest(
        context: Context,
        sourceUri: Uri
    ): Uri = copyToInternalStorage(
        context = context,
        sourceUri = sourceUri,
        subDir = OCR_TEST_DIR,
        maxDimension = MAX_IMAGE_DIMENSION
    )

    /**
     * Возвращает стабильный URI для файла.
     * 
     * Для файлов в cache директории используем file:// URI напрямую,
     * так как они читаются только внутри приложения.
     * FileProvider нужен только для sharing с другими приложениями.
     */
    fun getStableUri(context: Context, file: File): Uri {
        // Для внутренних файлов cache используем file:// напрямую
        // Это безопасно и работает на всех версиях Android
        return if (file.absolutePath.startsWith(context.cacheDir.absolutePath)) {
            Uri.fromFile(file)
        } else {
            // Для внешних файлов пробуем FileProvider
            try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } catch (e: IllegalArgumentException) {
                Timber.w("$TAG: FileProvider not configured, using file:// URI")
                Uri.fromFile(file)
            }
        }
    }

    /**
     * Вычисляет оптимальный inSampleSize.
     */
    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while ((halfHeight / inSampleSize) >= reqHeight &&
                   (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }



    /**
     * Очищает директорию, опционально оставляя последние N файлов.
     */
    fun clearDirectory(directory: File, keepLast: Int = 0) {
        try {
            val files = directory.listFiles()
                ?.filter { it.isFile }
                ?.sortedByDescending { it.lastModified() }
                ?: return
            
            files.drop(keepLast).forEach { file ->
                file.delete()
            }
            
            Timber.d("$TAG: Cleared directory ${directory.name}, kept $keepLast files")
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to clear directory")
        }
    }

    /**
     * Очищает кэш OCR тестовых изображений.
     */
    fun clearOcrTestCache(context: Context) {
        val testDir = File(context.cacheDir, OCR_TEST_DIR)
        if (testDir.exists()) {
            clearDirectory(testDir, keepLast = 0)
        }
    }

    /**
     * Очищает весь кэш временных изображений.
     */
    fun clearAllImageCache(context: Context) {
        clearOcrTestCache(context)
        val tempDir = File(context.cacheDir, TEMP_IMAGES_DIR)
        if (tempDir.exists()) {
            clearDirectory(tempDir, keepLast = 0)
        }
    }

    private fun openInputStreamForUri(context: Context, uri: Uri): InputStream {
        val scheme = uri.scheme?.lowercase()
        return when (scheme) {
            "content" -> {
                context.contentResolver.openInputStream(uri)
                    ?: throw IOException("ContentResolver returned null for: $uri")
            }
            "file" -> {
                val path = uri.path ?: throw IOException("File URI has no path: $uri")
                val file = File(path)
                if (!file.exists()) throw FileNotFoundException("File does not exist: $path")
                if (!file.canRead()) throw IOException("Cannot read file: $path")
                file.inputStream()
            }
            null, "" -> {
                val path = uri.toString()
                val file = File(path)
                if (!file.exists()) throw FileNotFoundException("File does not exist: $path")
                file.inputStream()
            }
            else -> {
                throw IOException("Unsupported URI scheme '$scheme': $uri")
            }
        }
    }
}