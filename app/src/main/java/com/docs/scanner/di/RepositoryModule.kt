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
 * Session 5 & 7 fixes:
 * - ✅ Added TermRepository binding (was missing - critical!)
 * - ✅ Added TranslationRepository binding (NEW from Session 5)
 * - ✅ Added OcrRepository binding (NEW from Session 5)
 * - ✅ All bindings use domain interfaces
 * - ✅ @Binds for efficiency (no instance creation)
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
     */
    @Binds
    @Singleton
    abstract fun bindFolderRepository(
        impl: FolderRepositoryImpl
    ): FolderRepository
    
    /**
     * Provides RecordRepository for record management.
     */
    @Binds
    @Singleton
    abstract fun bindRecordRepository(
        impl: RecordRepositoryImpl
    ): RecordRepository
    
    /**
     * Provides DocumentRepository for document management.
     */
    @Binds
    @Singleton
    abstract fun bindDocumentRepository(
        impl: DocumentRepositoryImpl
    ): DocumentRepository
    
    /**
     * Provides TermRepository for term/deadline management.
     * 
     * ✅ NEW: Session 5 critical fix
     * This was MISSING - caused compilation errors!
     */
    @Binds
    @Singleton
    abstract fun bindTermRepository(
        impl: TermRepositoryImpl
    ): TermRepository
    
    /**
     * Provides SettingsRepository for app settings.
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
     * Provides ScannerRepository for document scanning and OCR.
     */
    @Binds
    @Singleton
    abstract fun bindScannerRepository(
        impl: ScannerRepositoryImpl
    ): ScannerRepository
    
    /**
     * Provides OcrRepository for OCR operations.
     * 
     * ✅ NEW: Session 5 addition
     * Separates OCR logic from ScannerRepository (SRP).
     * 
     * Responsibilities:
     * - ML Kit text recognition
     * - OCR text improvement via Gemini
     * - Language model selection
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
     * ✅ NEW: Session 5 addition
     * Separates translation logic from ScannerRepository (SRP).
     * 
     * Responsibilities:
     * - Gemini API translation
     * - Translation caching
     * - Batch translations
     * - Cache statistics
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
     */
    @Binds
    @Singleton
    abstract fun bindDriveRepository(
        impl: DriveRepositoryImpl
    ): DriveRepository
}