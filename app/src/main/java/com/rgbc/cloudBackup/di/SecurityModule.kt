package com.rgbc.cloudBackup.di

import android.content.Context
import com.rgbc.cloudBackup.core.security.AndroidKeystoreKeyProvider
import com.rgbc.cloudBackup.core.security.CryptoManager
import com.rgbc.cloudBackup.core.security.CryptoManagerImpl
import com.rgbc.cloudBackup.core.security.KeyProvider
import com.rgbc.cloudBackup.core.security.SecureChecksumUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideCryptoManager(
        @ApplicationContext context: Context
    ): CryptoManager = CryptoManagerImpl(context)

    @Provides
    @Singleton
    fun provideSecureChecksumUtils(): SecureChecksumUtils = SecureChecksumUtils()

    @Provides
    @Singleton
    fun provideKeyProvider(): KeyProvider =
        AndroidKeystoreKeyProvider()
}
