package com.docs.scanner.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Universal Image Utilities for Android 10-16+
 * 
 * Handles:
 * - Photo Picker URIs (temporary access)
 * - Content URIs from gallery
 * - File URIs
 * - Camera captures
 * - EXIF rotation correction
 * - Memory-safe bitmap operations
 * 
 * @since 2026
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
        
        // 4. Открываем входной поток
        val inputStream = context.contentResolver.openInputStream(sourceUri)
            ?: throw IOException("Cannot open input stream for URI: $sourceUri")
        
        try {
            // 5. Декодируем с оптимизацией памяти
            val bitmap = decodeSampledBitmap(inputStream, maxDimension)
                ?: throw IOException("Failed to decode bitmap from URI: $sourceUri")
            
            // 6. Закрываем первый поток, открываем новый для EXIF
            inputStream.close()
            
            // 7. Корректируем ориентацию по EXIF
            val rotatedBitmap = try {
                context.contentResolver.openInputStream(sourceUri)?.use { exifStream ->
                    correctBitmapOrientation(bitmap, exifStream)
                } ?: bitmap
            } catch (e: Exception) {
                Timber.w(e, "$TAG: Failed to correct orientation, using original")
                bitmap
            }
            
            // 8. Сохраняем в файл
            FileOutputStream(outputFile).use { output ->
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }
            
            // 9. Освобождаем память
            if (rotatedBitmap !== bitmap) {
                bitmap.recycle()
            }
            rotatedBitmap.recycle()
            
            Timber.d("$TAG: Image saved to ${outputFile.absolutePath} (${outputFile.length()} bytes)")
            
            // 10. Возвращаем стабильный URI
            getStableUri(context, outputFile)
            
        } catch (e: Exception) {
            inputStream.close()
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
     * Android 7+ требует FileProvider для file:// URI.
     * Для внутренних файлов cache можно использовать file:// напрямую
     * если файл читается только внутри приложения.
     */
    fun getStableUri(context: Context, file: File): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Для Android 7+ пробуем FileProvider, fallback на file://
            try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
            } catch (e: IllegalArgumentException) {
                // FileProvider не настроен для этого пути - используем file://
                // Это безопасно для внутренних файлов приложения
                Timber.w("$TAG: FileProvider not configured, using file:// URI")
                Uri.fromFile(file)
            }
        } else {
            Uri.fromFile(file)
        }
    }

    /**
     * Декодирует Bitmap с оптимизацией памяти.
     * Использует inSampleSize для уменьшения потребления памяти.
     */
    private fun decodeSampledBitmap(
        inputStream: InputStream,
        maxDimension: Int
    ): Bitmap? {
        // Сначала читаем размеры
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        
        // Буферизуем поток для повторного чтения
        val bufferedBytes = inputStream.readBytes()
        
        BitmapFactory.decodeByteArray(bufferedBytes, 0, bufferedBytes.size, options)
        
        // Вычисляем inSampleSize
        options.inSampleSize = calculateInSampleSize(
            options.outWidth,
            options.outHeight,
            maxDimension,
            maxDimension
        )
        
        // Декодируем с уменьшением
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        
        return BitmapFactory.decodeByteArray(bufferedBytes, 0, bufferedBytes.size, options)
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
     * Корректирует ориентацию Bitmap по EXIF данным.
     */
    private fun correctBitmapOrientation(
        bitmap: Bitmap,
        inputStream: InputStream
    ): Bitmap {
        val exif = ExifInterface(inputStream)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        
        val matrix = Matrix()
        
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }
        
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: OutOfMemoryError) {
            Timber.e(e, "$TAG: OOM while rotating bitmap")
            bitmap
        }
    }

    /**
     * Очищает директорию, опционально оставляя последние N файлов.
     */
    fun clearDirectory(directory: File, keepLast: Int = 0) {
        try {
            val files = directory.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?: return
            
            files.drop(keepLast).forEach { file ->
                if (file.isFile) {
                    file.delete()
                }
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
        clearDirectory(testDir, keepLast = 0)
    }

    /**
     * Очищает весь кэш временных изображений.
     */
    fun clearAllImageCache(context: Context) {
        clearOcrTestCache(context)
        val tempDir = File(context.cacheDir, TEMP_IMAGES_DIR)
        clearDirectory(tempDir, keepLast = 0)
    }

    /**
     * Проверяет доступность URI.
     */
    fun isUriAccessible(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (e: Exception) {
            Timber.w("$TAG: URI not accessible: $uri")
            false
        }
    }

    /**
     * Получает размер файла по URI.
     */
    fun getUriFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                it.length
            } ?: -1
        } catch (e: Exception) {
            -1
        }
    }
}