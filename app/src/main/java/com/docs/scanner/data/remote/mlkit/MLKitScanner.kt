package com.docs.scanner.data.remote.mlkit

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * ML Kit OCR Scanner с поддержкой ВСЕХ языков
 * 
 * ChineseTextRecognizerOptions - универсальный распознаватель:
 * ✅ Латиница (English, German, Polish, French, Spanish...)
 * ✅ Кириллица (Russian, Ukrainian, Serbian, Bulgarian...)
 * ✅ Китайский (Simplified, Traditional)
 * ✅ Японский (Hiragana, Katakana, Kanji)
 * ✅ Корейский (Hangul)
 * ✅ Деvanagari (Hindi, Marathi...)
 * ✅ Арабская вязь
 * 
 * ВАЖНО: Model весит ~10MB, скачивается автоматически при первом использовании
 */
class MLKitScanner @Inject constructor(
    private val context: Context
) {
    
    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )
    
    /**
     * Сканирует изображение и извлекает текст
     * 
     * @param imageUri URI изображения (file://, content://)
     * @return Result.Success(text) или Result.Error(exception)
     */
    suspend fun scanImage(imageUri: Uri): com.docs.scanner.domain.model.Result<String> {
        return try {
            val image = InputImage.fromFilePath(context, imageUri)
            val visionText = recognizer.process(image).await()
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
            val visionText = recognizer.process(image).await()
            
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
    
    fun close() {
        recognizer.close()
    }
    
    data class TextBlock(
        val text: String,
        val lines: Int,
        val boundingBox: android.graphics.Rect?
    )
}