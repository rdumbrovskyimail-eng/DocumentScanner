package com.docs.scanner.data.remote.mlkit

import android.content.Context
import android.net.Uri
import com.docs.scanner.data.local.preferences.SettingsDataStore
import com.docs.scanner.domain.core.DomainError
import com.docs.scanner.domain.core.DomainResult
import com.docs.scanner.domain.core.OcrResult
import com.docs.scanner.domain.repository.BoundingBox
import com.docs.scanner.domain.repository.DetailedOcrResult
import com.docs.scanner.domain.repository.TextBlock
import com.docs.scanner.domain.repository.TextLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class MLKitScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    @Suppress("UNUSED_PARAMETER") private val settingsDataStore: SettingsDataStore
) {
    suspend fun recognizeText(uri: Uri): DomainResult<OcrResult> {
        val start = System.currentTimeMillis()
        return try {
            val text = runOcr(uri)
            DomainResult.Success(
                OcrResult(
                    text = text.text,
                    detectedLanguage = null,
                    confidence = null,
                    processingTimeMs = System.currentTimeMillis() - start
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "MLKit OCR failed")
            DomainResult.failure(DomainError.OcrFailed(id = null, cause = e))
        }
    }

    suspend fun recognizeTextDetailed(uri: Uri): DomainResult<DetailedOcrResult> {
        val start = System.currentTimeMillis()
        return try {
            val text = runOcr(uri)
            DomainResult.Success(
                DetailedOcrResult(
                    fullText = text.text,
                    blocks = text.textBlocks.map { it.toDomain() },
                    detectedLanguage = null,
                    confidence = null,
                    processingTimeMs = System.currentTimeMillis() - start
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "MLKit detailed OCR failed")
            DomainResult.failure(DomainError.OcrFailed(id = null, cause = e))
        }
    }

    private suspend fun runOcr(uri: Uri): Text {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return recognizer.process(image).await()
    }
}

private fun Text.TextBlock.toDomain(): TextBlock =
    TextBlock(
        text = text,
        lines = lines.map { it.toDomain() },
        confidence = null,
        boundingBox = boundingBox?.toDomain()
    )

private fun Text.Line.toDomain(): TextLine =
    TextLine(
        text = text,
        confidence = null,
        boundingBox = boundingBox?.toDomain()
    )

private fun android.graphics.Rect.toDomain(): BoundingBox =
    BoundingBox(left = left, top = top, right = right, bottom = bottom)

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWith(Result.failure(it)) }
    }

