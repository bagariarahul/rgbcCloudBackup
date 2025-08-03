package com.rgbc.cloudBackup.di

import com.rgbc.cloudBackup.core.security.SecureChecksumUtils
import com.rgbc.cloudBackup.core.utils.DirectoryProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UtilityModule {

    @Provides
    @Singleton
    fun provideDirectoryProvider(@dagger.hilt.android.qualifiers.ApplicationContext ctx: android.content.Context): DirectoryProvider {
        return DirectoryProvider(ctx)
    }

    @Provides
    @Singleton
    fun provideChecksumUtils(): SecureChecksumUtils {
        return SecureChecksumUtils()
    }
}
