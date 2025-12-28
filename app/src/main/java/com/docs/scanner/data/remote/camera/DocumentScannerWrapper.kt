package com.docs.scanner.data.remote.camera

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentScannerWrapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    // ✅ Lazy init options
    private val options by lazy {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            .setResultFormats(
                RESULT_FORMAT_JPEG,
                RESULT_FORMAT_PDF  // ✅ ДОБАВЛЕН PDF
            )
            .setScannerMode(SCANNER_MODE_FULL)
            .build()
    }
    
    private val scanner by lazy {
        GmsDocumentScanning.getClient(options)
    }
    
    /**
     * ✅ ДОБАВЛЕНО: Проверка доступности Document Scanner
     * Требует Google Play Services 23.0+
     */
    fun isAvailable(): Boolean {
        return try {
            GmsDocumentScanning.getClient(options)
            true
        } catch (e: Exception) {
            println("⚠️ Document Scanner not available: ${e.message}")
            false
        }
    }
    
    /**
     * Запускает Document Scanner
     * 
     * @throws UnsupportedOperationException если сканер недоступен
     */
    fun startScan(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        // ✅ ДОБАВЛЕНА проверка доступности
        if (!isAvailable()) {
            throw UnsupportedOperationException(
                "Document Scanner requires Google Play Services 23.0+. " +
                "Please update Google Play Services."
            )
        }
        
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                val request = IntentSenderRequest.Builder(intentSender).build()
                launcher.launch(request)
            }
            .addOnFailureListener { e ->
                throw Exception("Failed to start scanner: ${e.message}", e)
            }
    }
    
    /**
     * ✅ ДОБАВЛЕНО: Обработка результата сканирования
     * 
     * @param result результат от GmsDocumentScanning
     * @return ScanResult с изображениями и PDF
     */
    fun handleScanResult(result: GmsDocumentScanningResult): ScanResult {
        val pages = result.pages
        
        if (pages == null || pages.isEmpty()) {
            return ScanResult.Error("No pages scanned")
        }
        
        // Извлекаем URI изображений
        val imageUris = pages.mapNotNull { page -> page.imageUri }
        
        // Извлекаем PDF если доступен
        val pdfUri = result.pdf?.uri
        
        return if (imageUris.isNotEmpty()) {
            ScanResult.Success(
                imageUris = imageUris,
                pdfUri = pdfUri,
                pageCount = pages.size
            )
        } else {
            ScanResult.Error("No images extracted from scanned pages")
        }
    }
    
    /**
     * ✅ РЕЗУЛЬТАТ СКАНИРОВАНИЯ
     */
    sealed class ScanResult {
        data class Success(
            val imageUris: List<Uri>,
            val pdfUri: Uri?,
            val pageCount: Int
        ) : ScanResult()
        
        data class Error(val message: String) : ScanResult()
    }
}