package com.rgbc.cloudBackup.core.domain.usecase

import com.rgbc.cloudBackup.core.data.database.entity.FileIndex
import com.rgbc.cloudBackup.core.domain.repository.FileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetFilesToBackupUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    operator fun invoke(): Flow<List<FileIndex>> {
        return fileRepository.getFilesToBackup()
    }
}
