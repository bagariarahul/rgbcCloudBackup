package com.rgbc.cloudBackup.di

import com.rgbc.cloudBackup.core.domain.repository.FileRepository
import com.rgbc.cloudBackup.core.domain.usecase.GetBackupStatisticsUseCase
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
}
