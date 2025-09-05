package com.rgbc.cloudBackup.core.domain.usecase

import com.rgbc.cloudBackup.core.data.database.entity.FileIndex
import com.rgbc.cloudBackup.core.domain.repository.FileRepository
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject

class GetFilesToBackupUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    operator fun invoke(): Flow<List<FileIndex>> {
        Timber.d("GetFilesToBackupUseCase invoked")
        return fileRepository.getFilesToBackup()
    }
}
