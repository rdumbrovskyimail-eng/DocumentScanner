package com.docs.scanner.domain.usecase

import android.net.Uri
import com.docs.scanner.domain.model.ProcessingStatus
import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.DocumentRepository
import com.docs.scanner.domain.repository.ScannerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Add document with progress reporting via Flow.
 * 
 * Session 6 Fix:
 * - ✅ Flow states for UI progress (Creating → OCR → Translating → Success)
 * - ✅ Proper error handling with detailed messages
 * - ✅ Status updates in database
 */
class AddDocumentUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val scannerRepository: ScannerRepository
) {
    operator fun invoke(recordId: Long, imageUri: Uri): Flow<AddDocumentState> = flow {
        try {
            // STEP 1: Create document
            emit(AddDocumentState.Creating(10, "Creating document..."))
            
            val createResult = documentRepository.createDocument(recordId, imageUri)
            if (createResult !is Result.Success) {
                emit(AddDocumentState.Error("Failed to create document"))
                return@flow
            }
            
            val documentId = createResult.data

            // STEP 2: OCR Processing
            emit(AddDocumentState.ProcessingOcr(40, "Reading text..."))
            
            documentRepository.updateProcessingStatus(
                documentId, 
                ProcessingStatus.OCR_IN_PROGRESS
            )

            when (val ocrResult = scannerRepository.scanImage(imageUri)) {
                is Result.Success -> {
                    documentRepository.updateOriginalText(documentId, ocrResult.data)
                    documentRepository.updateProcessingStatus(
                        documentId,
                        ProcessingStatus.OCR_COMPLETE
                    )
                    
                    // STEP 3: Translation
                    emit(AddDocumentState.Translating(70, "Translating..."))
                    
                    documentRepository.updateProcessingStatus(
                        documentId, 
                        ProcessingStatus.TRANSLATION_IN_PROGRESS
                    )

                    when (val translateResult = scannerRepository.translateText(ocrResult.data)) {
                        is Result.Success -> {
                            documentRepository.updateTranslatedText(
                                documentId, 
                                translateResult.data
                            )
                            documentRepository.updateProcessingStatus(
                                documentId, 
                                ProcessingStatus.COMPLETE
                            )
                            
                            emit(AddDocumentState.Success(
                                documentId = documentId,
                                originalText = ocrResult.data,
                                translatedText = translateResult.data
                            ))
                        }
                        is Result.Error -> {
                            documentRepository.updateProcessingStatus(
                                documentId, 
                                ProcessingStatus.ERROR
                            )
                            emit(AddDocumentState.Error(
                                "Translation failed: ${translateResult.exception.message}"
                            ))
                        }
                        is Result.Loading -> {
                            // Continue, status already set
                        }
                    }
                }
                is Result.Error -> {
                    documentRepository.updateProcessingStatus(
                        documentId, 
                        ProcessingStatus.ERROR
                    )
                    emit(AddDocumentState.Error(
                        "OCR failed: ${ocrResult.exception.message}"
                    ))
                }
                is Result.Loading -> {
                    // Continue, status already set
                }
            }
        } catch (e: Exception) {
            emit(AddDocumentState.Error("Unexpected error: ${e.message}"))
        }
    }
}

sealed class AddDocumentState {
    data class Creating(val progress: Int, val message: String) : AddDocumentState()
    data class ProcessingOcr(val progress: Int, val message: String) : AddDocumentState()
    data class Translating(val progress: Int, val message: String) : AddDocumentState()
    data class Success(
        val documentId: Long,
        val originalText: String,
        val translatedText: String
    ) : AddDocumentState()
    data class Error(val message: String) : AddDocumentState()
}