package com.docs.scanner.data.remote.camera

import android.app.Activity
import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class DocumentScannerWrapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    fun startScan(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            .setResultFormats(RESULT_FORMAT_JPEG)
            .setScannerMode(SCANNER_MODE_FULL)
            .build()

        val scanner = GmsDocumentScanning.getClient(options)
        
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                val request = IntentSenderRequest.Builder(intentSender).build()
                launcher.launch(request)
            }
            .addOnFailureListener { e ->
                throw Exception("Failed to start scanner: ${e.message}", e)
            }
    }
}