package com.rgbc.cloudBackup.core.domain.usecase

import com.rgbc.cloudBackup.core.data.database.entity.FileIndex
import com.rgbc.cloudBackup.core.domain.repository.FileRepository
import javax.inject.Inject

class InsertFileUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(file: FileIndex): Long {
        return fileRepository.insertFile(file)
    }

    suspend operator fun invoke(files: List<FileIndex>): List<Long> {
        return fileRepository.insertFiles(files)
    }
}
