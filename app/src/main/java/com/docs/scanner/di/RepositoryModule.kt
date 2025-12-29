package com.docs.scanner.di

import com.docs.scanner.data.remote.drive.DriveRepositoryImpl
import com.docs.scanner.data.repository.*
import com.docs.scanner.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Repository bindings.
 * 
 * ✅ ALL REPOSITORIES IMPLEMENTED (Session 5-7 complete):
 * - FolderRepository ✅
 * - RecordRepository ✅
 * - DocumentRepository ✅ (with FTS5 search)
 * - TermRepository ✅
 * - SettingsRepository ✅ (DataStore + EncryptedKeyStorage)
 * - ScannerRepository ✅ (facade)
 * - OcrRepository ✅ (ML Kit + Gemini)
 * - TranslationRepository ✅ (Gemini + cache)
 * - DriveRepository ✅ (Google Drive backup)
 * 
 * Architecture:
 * ```
 * ViewModel → Repository (domain interface) → RepositoryImpl → DAO/Remote
 *    ↓              ↓                            ↓                ↓
 * Domain         Domain                       Data             Data
 * ```
 * 
 * Why @Binds instead of @Provides:
 * - @Binds is more efficient (no wrapper function)
 * - @Binds generates less bytecode
 * - @Binds works when you just need interface → implementation binding
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // LOCAL DATA REPOSITORIES
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /**
     * Provides FolderRepository for folder management.
     * 
     * Features:
     * - CRUD operations
     * - Record count tracking
     * - Name uniqueness validation
     */
    @Binds
    @Singleton
    abstract fun bindFolderRepository(
        impl: FolderRepositoryImpl
    ): FolderRepository
    
    /**
     * Provides RecordRepository for record management.
     * 
     * Features:
     * - CRUD operations
     * - Folder relationships
     * - Move between folders
     * - Document count tracking
     */
    @Binds
    @Singleton
    abstract fun bindRecordRepository(
        impl: RecordRepositoryImpl
    ): RecordRepository
    
    /**
     * Provides DocumentRepository for document management.
     * 
     * Features:
     * - CRUD operations
     * - FTS5 full-text search
     * - OCR text updates
     * - Translation text updates
     * - Processing status tracking
     * - Image file management
     */
    @Binds
    @Singleton
    abstract fun bindDocumentRepository(
        impl: DocumentRepositoryImpl
    ): DocumentRepository
    
    /**
     * Provides TermRepository for term/deadline management.
     * 
     * Features:
     * - CRUD operations
     * - Overdue detection
     * - Reminder scheduling
     * - Completion tracking
     */
    @Binds
    @Singleton
    abstract fun bindTermRepository(
        impl: TermRepositoryImpl
    ): TermRepository
    
    /**
     * Provides SettingsRepository for app settings.
     * 
     * Features:
     * - API key management (encrypted)
     * - First launch tracking
     * - DataStore preferences
     */
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // SCANNER & OCR REPOSITORIES
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /**
     * Provides ScannerRepository for document scanning.
     * 
     * Features:
     * - Facade for OCR + Translation
     * - Coordinates scanning workflow
     * - Delegates to specialized repositories
     */
    @Binds
    @Singleton
    abstract fun bindScannerRepository(
        impl: ScannerRepositoryImpl
    ): ScannerRepository
    
    /**
     * Provides OcrRepository for OCR operations.
     * 
     * Features:
     * - ML Kit text recognition
     * - Detailed text block extraction
     * - OCR error correction via Gemini
     * - Multi-language support (Latin, Chinese)
     */
    @Binds
    @Singleton
    abstract fun bindOcrRepository(
        impl: OcrRepositoryImpl
    ): OcrRepository
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // TRANSLATION REPOSITORY
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /**
     * Provides TranslationRepository for translation operations.
     * 
     * Features:
     * - Gemini API translation
     * - Intelligent caching (language-aware)
     * - Batch translations
     * - Cache statistics
     * - Auto language detection
     */
    @Binds
    @Singleton
    abstract fun bindTranslationRepository(
        impl: TranslationRepositoryImpl
    ): TranslationRepository
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // REMOTE REPOSITORIES
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /**
     * Provides DriveRepository for Google Drive backup.
     * 
     * Features:
     * - Database backup/restore
     * - Image folder sync
     * - OAuth authentication
     * - Conflict resolution
     */
    @Binds
    @Singleton
    abstract fun bindDriveRepository(
        impl: DriveRepositoryImpl
    ): DriveRepository
}