package com.docs.scanner.domain.usecase

import com.docs.scanner.domain.core.DocumentId
import com.docs.scanner.domain.core.DomainResult
import com.docs.scanner.domain.core.Language
import com.docs.scanner.domain.core.NewAnalyticsTranslation
import com.docs.scanner.domain.repository.AnalyticsTranslationRepository
import com.docs.scanner.domain.repository.DocumentRepository
import com.docs.scanner.domain.repository.FolderRepository
import com.docs.scanner.domain.repository.RecordRepository
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors a freshly produced translation into the Analytics Center archive.
 *
 *  - Called from the two existing translation pipelines:
 *      • ProcessDocumentUseCase (auto-translate after OCR on a new document)
 *      • TranslationUseCases.translateDocument (retry / manual re-translate)
 *
 *  - The archive entry is **autonomous**: editing or deleting the source
 *    document later does NOT propagate to the archive (no FKs by design).
 *
 *  - Best-effort: any failure here is logged and swallowed. We never let a
 *    storage hiccup in the archive break the main translation flow — the
 *    user still gets their translated text on the source document.
 *
 *  - Denormalized fields (`sourceRecordName`, `sourceFolderName`) are
 *    resolved lazily via two id-lookup queries. Both are cheap and run after
 *    an already-long Gemini call, so the extra cost is negligible.
 */
@Singleton
class MirrorTranslationToArchiveUseCase @Inject constructor(
    private val docRepo: DocumentRepository,
    private val recordRepo: RecordRepository,
    private val folderRepo: FolderRepository,
    private val archiveRepo: AnalyticsTranslationRepository
) {

    suspend operator fun invoke(
        docId: DocumentId,
        translatedText: String,
        sourceLang: Language,
        targetLang: Language
    ) {
        if (translatedText.isBlank()) return

        try {
            val doc = (docRepo.getDocumentById(docId) as? DomainResult.Success)?.data
            val record = doc?.let {
                (recordRepo.getRecordById(it.recordId) as? DomainResult.Success)?.data
            }
            val folder = record?.let {
                (folderRepo.getFolderById(it.folderId) as? DomainResult.Success)?.data
            }

            val payload = NewAnalyticsTranslation(
                translatedText = translatedText,
                originalText = doc?.originalText,
                sourceLanguage = sourceLang.code,
                targetLanguage = targetLang.code,
                sourceDocumentId = docId.value,
                sourceRecordId = record?.id?.value,
                sourceRecordName = record?.name,
                sourceFolderName = folder?.name
            )

            when (val r = archiveRepo.create(payload)) {
                is DomainResult.Success -> {
                    Timber.d(
                        "📦 Mirrored translation to archive: docId=%d, archiveId=%d",
                        docId.value, r.data.value
                    )
                }
                is DomainResult.Failure -> {
                    Timber.w("Archive insert failed (non-fatal): %s", r.error.message)
                }
            }
        } catch (e: CancellationException) {
            // Coroutine cancellation must propagate; never swallow.
            throw e
        } catch (e: Exception) {
            // Any other failure is non-fatal — main flow continues.
            Timber.w(e, "Failed to mirror translation to archive (non-fatal)")
        }
    }
}