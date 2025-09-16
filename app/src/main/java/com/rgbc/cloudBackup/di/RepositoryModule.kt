package com.rgbc.cloudBackup.di

import com.rgbc.cloudBackup.core.data.repository.FileRepositoryImpl
import com.rgbc.cloudBackup.core.data.repository.PersistentDirectoryRepository
import com.rgbc.cloudBackup.core.data.repository.ServerSyncRepositoryImpl
import com.rgbc.cloudBackup.core.domain.repository.FileRepository
import com.rgbc.cloudBackup.core.domain.repository.ServerSyncRepository
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
    abstract fun bindFileRepository(
        fileRepositoryImpl: FileRepositoryImpl
    ): FileRepository

    // 🆕 ADD: Server sync repository binding
    @Binds
    @Singleton
    abstract fun bindServerSyncRepository(
        serverSyncRepositoryImpl: ServerSyncRepositoryImpl
    ): ServerSyncRepository

    // PersistentDirectoryRepository is already annotated with @Singleton
    // and @Inject constructor, so it doesn't need binding here
}
