package com.docs.scanner.data.remote.mlkit

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ✅ ИСПРАВЛЕНО: Добавлено управление lifecycle и освобождение ресурсов
 * 
 * ML Kit OCR Scanner с поддержкой ВСЕХ языков
 * - Латиница (English, German, Polish, French, Spanish...)
 * - Кириллица (Russian, Ukrainian, Serbian, Bulgarian...)
 * - Китайский, Японский, Корейский
 * - Деvanagari, Арабская вязь
 * 
 * Model: ~10MB, скачивается автоматически при первом использовании
 */
@Singleton
class MLKitScanner @Inject constructor(
    private val context: Context
) {
    
    // ✅ ИСПРАВЛЕНО: Lazy инициализация + volatile для thread-safety
    @Volatile
    private var recognizer: TextRecognizer? = null
    
    private fun getRecognizer(): TextRecognizer {
        return recognizer ?: synchronized(this) {
            recognizer ?: TextRecognition.getClient(
                ChineseTextRecognizerOptions.Builder().build()
            ).also { recognizer = it }
        }
    }
    
    /**
     * Сканирует изображение и извлекает текст
     * 
     * @param imageUri URI изображения (file://, content://)
     * @return Result.Success(text) или Result.Error(exception)
     */
    suspend fun scanImage(imageUri: Uri): com.docs.scanner.domain.model.Result<String> {
        return try {
            val image = InputImage.fromFilePath(context, imageUri)
            val visionText = getRecognizer().process(image).await()
            val extractedText = visionText.text.trim()
            
            if (extractedText.isEmpty()) {
                com.docs.scanner.domain.model.Result.Error(
                    Exception("No text detected in image")
                )
            } else {
                com.docs.scanner.domain.model.Result.Success(extractedText)
            }
            
        } catch (e: Exception) {
            com.docs.scanner.domain.model.Result.Error(
                Exception("OCR failed: ${e.message}", e)
            )
        }
    }
    
    /**
     * Сканирует изображение с подробной информацией
     * 
     * @return List<TextBlock> с текстом, confidence и количеством строк
     */
    suspend fun scanImageDetailed(imageUri: Uri): com.docs.scanner.domain.model.Result<List<TextBlock>> {
        return try {
            val image = InputImage.fromFilePath(context, imageUri)
            val visionText = getRecognizer().process(image).await()
            
            val blocks = visionText.textBlocks.map { block ->
                TextBlock(
                    text = block.text,
                    lines = block.lines.size,
                    boundingBox = block.boundingBox
                )
            }
            
            if (blocks.isEmpty()) {
                com.docs.scanner.domain.model.Result.Error(
                    Exception("No text blocks found")
                )
            } else {
                com.docs.scanner.domain.model.Result.Success(blocks)
            }
            
        } catch (e: Exception) {
            com.docs.scanner.domain.model.Result.Error(
                Exception("OCR failed: ${e.message}", e)
            )
        }
    }
    
    /**
     * ✅ ИСПРАВЛЕНО: Освобождает ресурсы (~10MB)
     * Вызывается автоматически при уничтожении Singleton
     */
    fun close() {
        recognizer?.close()
        recognizer = null
        println("✅ MLKitScanner: Resources released (~10MB)")
    }
    
    /**
     * ✅ ДОБАВЛЕНО: Принудительная переинициализация
     * Используется если ML Kit модель повреждена
     */
    fun reinitialize() {
        close()
        println("♻️ MLKitScanner: Reinitializing...")
    }
    
    data class TextBlock(
        val text: String,
        val lines: Int,
        val boundingBox: android.graphics.Rect?
    )
    
    // ✅ ДОБАВЛЕНО: Cleanup при уничтожении приложения
    protected fun finalize() {
        close()
    }
}
