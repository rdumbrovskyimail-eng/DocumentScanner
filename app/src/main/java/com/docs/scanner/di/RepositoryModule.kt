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
     * Provides ScannerRepository for document scanning.
     */
    @Binds
    @Singleton
    abstract fun bindScannerRepository(
        impl: ScannerRepositoryImpl
    ): ScannerRepository
    
    /**
     * Provides OcrRepository for OCR operations.
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