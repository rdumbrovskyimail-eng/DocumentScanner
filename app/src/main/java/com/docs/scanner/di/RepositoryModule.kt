package com.docs.scanner.di

import com.docs.scanner.data.repository.BackupRepositoryImpl
import com.docs.scanner.data.repository.DocumentRepositoryImpl
import com.docs.scanner.data.repository.FileRepositoryImpl
import com.docs.scanner.data.repository.FolderRepositoryImpl
import com.docs.scanner.data.repository.OcrRepositoryImpl
import com.docs.scanner.data.repository.RecordRepositoryImpl
import com.docs.scanner.data.repository.ScannerRepositoryImpl
import com.docs.scanner.data.repository.SettingsRepositoryImpl
import com.docs.scanner.data.repository.TermRepositoryImpl
import com.docs.scanner.data.repository.TranslationRepositoryImpl
import com.docs.scanner.domain.repository.BackupRepository
import com.docs.scanner.domain.repository.DocumentRepository
import com.docs.scanner.domain.repository.FileRepository
import com.docs.scanner.domain.repository.FolderRepository
import com.docs.scanner.domain.repository.OcrRepository
import com.docs.scanner.domain.repository.RecordRepository
import com.docs.scanner.domain.repository.ScannerRepository
import com.docs.scanner.domain.repository.SettingsRepository
import com.docs.scanner.domain.repository.TermRepository
import com.docs.scanner.domain.repository.TranslationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for binding repository implementations to interfaces.
 * 
 * This module is CRITICAL for dependency injection to work.
 * All repository interfaces must be bound to their implementations here.
 * 
 * Architecture: Clean Architecture
 * - Interfaces defined in domain layer (DomainRepositories.kt)
 * - Implementations in data layer (DataRepositories.kt)
 * - Bindings here connect them via Hilt DI
 * 
 * Fixed issues:
 * - 🟠 Серьёзная #7: Added FileRepository and BackupRepository bindings
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * Binds FolderRepository interface to implementation.
     * Used by: GetFoldersUseCase, CreateFolderUseCase, DeleteFolderUseCase
     */
    @Binds
    @Singleton
    abstract fun bindFolderRepository(
        impl: FolderRepositoryImpl
    ): FolderRepository

    /**
     * Binds RecordRepository interface to implementation.
     * Used by: GetRecordsUseCase, CreateRecordUseCase, UpdateRecordUseCase
     */
    @Binds
    @Singleton
    abstract fun bindRecordRepository(
        impl: RecordRepositoryImpl
    ): RecordRepository

    /**
     * Binds DocumentRepository interface to implementation.
     * Used by: GetDocumentsUseCase, SearchDocumentsUseCase, UpdateDocumentUseCase
     */
    @Binds
    @Singleton
    abstract fun bindDocumentRepository(
        impl: DocumentRepositoryImpl
    ): DocumentRepository

    /**
     * Binds ScannerRepository interface to implementation.
     * Used by: QuickScanUseCase, RetryTranslationUseCase, ProcessingQueue
     */
    @Binds
    @Singleton
    abstract fun bindScannerRepository(
        impl: ScannerRepositoryImpl
    ): ScannerRepository

    /**
     * Binds SettingsRepository interface to implementation.
     * Used by: SettingsViewModel, OnboardingViewModel, App initialization
     */
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository

    /**
     * Binds TermRepository interface to implementation.
     * Used by: CreateTermUseCase, UpdateTermUseCase, GetTermsUseCase
     */
    @Binds
    @Singleton
    abstract fun bindTermRepository(
        impl: TermRepositoryImpl
    ): TermRepository

    /**
     * Binds OcrRepository interface to implementation.
     * Used by: AddDocumentUseCase, FixOcrUseCase, ML Kit Scanner
     */
    @Binds
    @Singleton
    abstract fun bindOcrRepository(
        impl: OcrRepositoryImpl
    ): OcrRepository

    /**
     * Binds TranslationRepository interface to implementation.
     * Used by: RetryTranslationUseCase, EditorViewModel, Gemini API
     */
    @Binds
    @Singleton
    abstract fun bindTranslationRepository(
        impl: TranslationRepositoryImpl
    ): TranslationRepository

    /**
     * Binds FileRepository interface to implementation.
     * Used by: SaveImageUseCase, DeleteImageUseCase, GetImageUseCase
     * 
     * FIXED: 🟠 Серьёзная #7 - Added missing binding
     */
    @Binds
    @Singleton
    abstract fun bindFileRepository(
        impl: FileRepositoryImpl
    ): FileRepository

    /**
     * Binds BackupRepository interface to implementation.
     * Used by: CreateBackupUseCase, RestoreBackupUseCase, Google Drive sync
     * 
     * FIXED: 🟠 Серьёзная #7 - Added missing binding
     */
    @Binds
    @Singleton
    abstract fun bindBackupRepository(
        impl: BackupRepositoryImpl
    ): BackupRepository
}