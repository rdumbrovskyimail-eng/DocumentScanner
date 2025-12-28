package com.docs.scanner.data.remote.mlkit

import android.content.Context
import android.net.Uri
import com.docs.scanner.data.local.preferences.SettingsDataStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML Kit OCR Scanner с поддержкой ВСЕХ языков
 * - LATIN: English, German, Polish, French, Spanish, Russian, Ukrainian...
 * - CHINESE: 繁體中文, 简体中文 (~10MB)
 * - JAPANESE: 日本語
 * - KOREAN: 한국어
 * - DEVANAGARI: हिन्दी, नेपाली, मराठी...
 * 
 * Model скачивается автоматически при первом использовании
 */
@Singleton
class MLKitScanner @Inject constructor(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore // ✅ ДОБАВЛЕНО для выбора языка
) {
    
    @Volatile
    private var recognizer: TextRecognizer? = null
    
    @Volatile
    private var currentLanguage: OcrLanguage? = null
    
    /**
     * ✅ ЯЗЫКОВЫЕ МОДЕЛИ
     */
    enum class OcrLanguage {
        LATIN,      // Default: Latin + Cyrillic
        CHINESE,    // + Chinese characters (~10MB)
        JAPANESE,   // + Japanese
        KOREAN,     // + Korean
        DEVANAGARI  // + Indian scripts
    }
    
    private fun getRecognizerForLanguage(language: OcrLanguage): TextRecognizer {
        return when (language) {
            OcrLanguage.LATIN -> TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
            )
            OcrLanguage.CHINESE -> TextRecognition.getClient(
                ChineseTextRecognizerOptions.Builder().build()
            )
            OcrLanguage.JAPANESE -> TextRecognition.getClient(
                JapaneseTextRecognizerOptions.Builder().build()
            )
            OcrLanguage.KOREAN -> TextRecognition.getClient(
                KoreanTextRecognizerOptions.Builder().build()
            )
            OcrLanguage.DEVANAGARI -> TextRecognition.getClient(
                DevanagariTextRecognizerOptions.Builder().build()
            )
        }
    }
    
    /**
     * ✅ ПОЛУЧИТЬ RECOGNIZER с учетом настроек
     */
    private suspend fun getRecognizer(): TextRecognizer {
        // Получить выбранный язык из настроек
        val languageStr = try {
            settingsDataStore.ocrLanguage.first()
        } catch (e: Exception) {
            "CHINESE" // Default
        }
        
        val language = try {
            OcrLanguage.valueOf(languageStr)
        } catch (e: Exception) {
            OcrLanguage.CHINESE // Default fallback
        }
        
        // Если язык изменился - переинициализировать
        if (currentLanguage != language) {
            recognizer?.close()
            recognizer = null
            currentLanguage = language
        }
        
        return recognizer ?: synchronized(this) {
            recognizer ?: getRecognizerForLanguage(language).also { 
                recognizer = it 
                println("✅ MLKit initialized: $language")
            }
        }
    }
    
    /**
     * Сканирует изображение и извлекает текст
     */
    suspend fun scanImage(imageUri: Uri): com.docs.scanner.domain.model.Result<String> {
        return try {
            val image = InputImage.fromFilePath(context, imageUri)
            val recognizer = getRecognizer()
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
     */
    suspend fun scanImageDetailed(imageUri: Uri): com.docs.scanner.domain.model.Result<List<TextBlock>> {
        return try {
            val image = InputImage.fromFilePath(context, imageUri)
            val recognizer = getRecognizer()
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
    
    /**
     * Освобождает ресурсы (~10MB для Chinese model)
     */
    fun close() {
        recognizer?.close()
        recognizer = null
        currentLanguage = null
        println("✅ MLKitScanner: Resources released")
    }
    
    /**
     * Принудительная переинициализация
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
    
    // ❌ УДАЛЕНО deprecated finalize()
}