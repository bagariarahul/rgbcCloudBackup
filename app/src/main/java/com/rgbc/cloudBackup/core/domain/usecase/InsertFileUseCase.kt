package com.rgbc.cloudBackup.core.domain.usecase

import com.rgbc.cloudBackup.core.data.database.entity.FileIndex
import com.rgbc.cloudBackup.core.domain.repository.FileRepository
import timber.log.Timber
import javax.inject.Inject

class InsertFileUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(file: FileIndex): Long {
        Timber.d("InsertFileUseCase invoked")
        return fileRepository.insertFile(file)
    }

    suspend operator fun invoke(files: List<FileIndex>): List<Long> {
        Timber.d("InsertFileUseCase invoked")
        return fileRepository.insertFiles(files)
    }
}
