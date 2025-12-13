package com.docs.scanner.data.remote.mlkit

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class MLKitScanner @Inject constructor(
    private val context: Context
) {
    
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    suspend fun scanImage(imageUri: Uri): com.docs.scanner.domain.model.Result<String> {
        return try {
            val image = InputImage.fromFilePath(context, imageUri)
            val visionText = recognizer.process(image).await()
            val extractedText = visionText.text.trim()
            
            if (extractedText.isEmpty()) {
                com.docs.scanner.domain.model.Result.Error(Exception("No text found in image"))
            } else {
                com.docs.scanner.domain.model.Result.Success(extractedText)
            }
            
        } catch (e: Exception) {
            com.docs.scanner.domain.model.Result.Error(Exception("OCR failed: ${e.message}", e))
        }
    }
    
    suspend fun scanImageDetailed(imageUri: Uri): com.docs.scanner.domain.model.Result<List<TextBlock>> {
        return try {
            val image = InputImage.fromFilePath(context, imageUri)
            val visionText = recognizer.process(image).await()
            
            val blocks = visionText.textBlocks.map { block ->
                TextBlock(
                    text = block.text,
                    confidence = block.confidence ?: 0f,
                    lines = block.lines.size
                )
            }
            
            if (blocks.isEmpty()) {
                com.docs.scanner.domain.model.Result.Error(Exception("No text blocks found"))
            } else {
                com.docs.scanner.domain.model.Result.Success(blocks)
            }
            
        } catch (e: Exception) {
            com.docs.scanner.domain.model.Result.Error(Exception("OCR failed: ${e.message}", e))
        }
    }
    
    fun close() {
        recognizer.close()
    }
    
    data class TextBlock(
        val text: String,
        val confidence: Float,
        val lines: Int
    )
}