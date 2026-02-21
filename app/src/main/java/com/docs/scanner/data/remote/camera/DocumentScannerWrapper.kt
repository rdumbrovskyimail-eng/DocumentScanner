package com.docs.scanner.data.remote.camera

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentScannerWrapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    sealed interface ScanResult {
        data class Success(val imageUris: List<Uri>, val pdfUri: Uri?) : ScanResult
        data class Error(val message: String) : ScanResult
    }

    private val options: GmsDocumentScannerOptions =
        GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            .build()

    private val client = GmsDocumentScanning.getClient(options)

    fun isAvailable(): Boolean = true

    fun startScan(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        onError: (String) -> Unit
    ) {
        client.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                launcher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                Timber.e(e, "Failed to start document scan")
                onError(e.message ?: "Failed to start scanner")
            }
    }

    fun handleScanResult(result: GmsDocumentScanningResult): ScanResult {
        val pages = result.pages
        val imageUris = pages?.mapNotNull { it.imageUri } ?: emptyList()
        return if (imageUris.isNotEmpty()) {
            ScanResult.Success(imageUris, result.pdf?.uri)
        } else {
            ScanResult.Error("No pages returned from scanner")
        }
    }
}

