package com.docs.scanner.data.remote.camera

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.IntentSender
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Wrapper для ML Kit Document Scanner
 * 
 * Автоматически:
 * ✅ Обнаруживает края документа
 * ✅ Обрезает и выравнивает (perspective correction)
 * ✅ Улучшает контраст и резкость
 * ✅ Batch scanning (до 20 страниц за раз)
 * ✅ Export в JPEG + PDF
 * 
 * ВАЖНО: Требует Google Play Services
 */
class DocumentScannerWrapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val scanner = GmsDocumentScanning.getClient(
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true) // разрешить импорт из галереи
            .setPageLimit(20) // максимум 20 страниц за раз
            .setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
            .setScannerMode(SCANNER_MODE_FULL) // полный режим с UI
            .build()
    )
    
    /**
     * Получает Activity из Context
     */
    private fun Context.getActivity(): Activity? {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }
    
    /**
     * Создает IntentSenderRequest для запуска сканера
     * 
     * Использование:
     * ```
     * val scannerLauncher = rememberLauncherForActivityResult(
     *     ActivityResultContracts.StartIntentSenderForResult()
     * ) { result ->
     *     val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
     *     // обработать результат
     * }
     * 
     * val request = documentScanner.getStartScanIntent()
     * scannerLauncher.launch(request)
     * ```
     */
    suspend fun getStartScanIntent(): IntentSenderRequest {
        return try {
            val activity = context.getActivity() 
                ?: throw IllegalStateException("Context is not an Activity")
            
            val intentSender: IntentSender = scanner.getStartScanIntent(activity).await()
            IntentSenderRequest.Builder(intentSender).build()
        } catch (e: Exception) {
            throw Exception("Failed to start document scanner: ${e.message}", e)
        }
    }
}
