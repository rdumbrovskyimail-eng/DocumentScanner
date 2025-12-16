package com.docs.scanner.di

import com.docs.scanner.data.repository.*
import com.docs.scanner.data.remote.drive.DriveRepository
import com.docs.scanner.data.remote.drive.DriveRepositoryImpl
import com.docs.scanner.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindFolderRepository(
        impl: FolderRepositoryImpl
    ): FolderRepository
    
    @Binds
    @Singleton
    abstract fun bindRecordRepository(
        impl: RecordRepositoryImpl
    ): RecordRepository
    
    @Binds
    @Singleton
    abstract fun bindDocumentRepository(
        impl: DocumentRepositoryImpl
    ): DocumentRepository
    
    @Binds
    @Singleton
    abstract fun bindScannerRepository(
        impl: ScannerRepositoryImpl
    ): ScannerRepository
    
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository
    
    @Binds
    @Singleton
    abstract fun bindDriveRepository(
        impl: DriveRepositoryImpl
    ): DriveRepository
}
