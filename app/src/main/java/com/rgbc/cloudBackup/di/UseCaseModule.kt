package com.rgbc.cloudBackup.di

import com.rgbc.cloudBackup.core.domain.repository.FileRepository
import com.rgbc.cloudBackup.core.domain.usecase.GetBackupStatisticsUseCase
import com.rgbc.cloudBackup.core.domain.usecase.UploadFileUseCase
import com.rgbc.cloudBackup.core.domain.usecase.DownloadFileUseCase
import com.rgbc.cloudBackup.core.network.api.BackupApiService
import com.rgbc.cloudBackup.core.security.SecurityAuditLogger
import com.rgbc.cloudBackup.core.security.CryptoManagerImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun provideGetBackupStatisticsUseCase(
        fileRepository: FileRepository
    ): GetBackupStatisticsUseCase =
        GetBackupStatisticsUseCase(fileRepository)

    @Provides
    @Singleton
    fun provideUploadFileUseCase(
        apiService: BackupApiService,
        fileRepository: FileRepository,
        auditLogger: SecurityAuditLogger,
        cryptoManager: CryptoManagerImpl  // ← Add this parameter
    ): UploadFileUseCase =
        UploadFileUseCase(apiService, fileRepository, auditLogger, cryptoManager)

    @Provides
    @Singleton
    fun provideDownloadFileUseCase(
        apiService: BackupApiService,
        auditLogger: SecurityAuditLogger,
        cryptoManager: CryptoManagerImpl
    ): DownloadFileUseCase =
        DownloadFileUseCase(apiService, auditLogger,cryptoManager)
}
