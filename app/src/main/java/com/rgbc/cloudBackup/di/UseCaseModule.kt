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
    ): GetBackupStatisticsUseCase {
        return GetBackupStatisticsUseCase(fileRepository)
    }

    @Provides
    @Singleton
    fun provideUploadFileUseCase(
        apiService: BackupApiService,
        fileRepository: FileRepository,
        auditLogger: SecurityAuditLogger,
        cryptoManager: CryptoManagerImpl
    ): UploadFileUseCase {
        return UploadFileUseCase(apiService,auditLogger)
    }

    @Provides
    @Singleton
    fun provideDownloadFileUseCase(
        apiService: BackupApiService
    ): DownloadFileUseCase {
        return DownloadFileUseCase(apiService)
    }
}