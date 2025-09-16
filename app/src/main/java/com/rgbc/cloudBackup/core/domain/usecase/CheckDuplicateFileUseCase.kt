package com.rgbc.cloudBackup.core.domain.usecase

import com.rgbc.cloudBackup.core.data.database.entity.FileIndex
import com.rgbc.cloudBackup.core.domain.repository.FileRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheckDuplicateFileUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {

    data class DuplicateCheckResult(
        val isDuplicate: Boolean,
        val duplicateFiles: List<FileIndex>,
        val shouldSkip: Boolean,
        val reason: String
    )

    suspend operator fun invoke(
        filePath: String,
        fileName: String,
        fileSize: Long
    ): DuplicateCheckResult {
        return try {
            Timber.d("🔍 Checking for duplicates: $fileName ($fileSize bytes)")

            // Get all existing files
            val existingFiles = fileRepository.getAllFiles().first()

            // Check for exact name match
            val exactNameMatch = existingFiles.find { it.name == fileName }
            if (exactNameMatch != null) {
                if (exactNameMatch.size == fileSize) {
                    Timber.d("✅ Exact duplicate found: $fileName")
                    return DuplicateCheckResult(
                        isDuplicate = true,
                        duplicateFiles = listOf(exactNameMatch),
                        shouldSkip = true,
                        reason = "Identical file already exists"
                    )
                } else {
                    Timber.d("⚠️ Same name, different size: $fileName")
                    return DuplicateCheckResult(
                        isDuplicate = true,
                        duplicateFiles = listOf(exactNameMatch),
                        shouldSkip = false,
                        reason = "Same name, different size - version conflict"
                    )
                }
            }

            // Check for size-based duplicates
            val sameSizeFiles = existingFiles.filter { it.size == fileSize }
            if (sameSizeFiles.isNotEmpty()) {
                Timber.d("🔍 Found ${sameSizeFiles.size} files with same size")

                // For files with same size, check checksum if available
                val checksumMatches = mutableListOf<FileIndex>()

                sameSizeFiles.forEach { existingFile ->
                    if (existingFile.checksum.isNotEmpty()) {
                        // Calculate checksum for new file
                        val newFileChecksum = calculateFileChecksum(filePath)
                        if (newFileChecksum == existingFile.checksum) {
                            checksumMatches.add(existingFile)
                        }
                    }
                }

                if (checksumMatches.isNotEmpty()) {
                    Timber.d("🔍 Found ${checksumMatches.size} checksum matches")
                    return DuplicateCheckResult(
                        isDuplicate = true,
                        duplicateFiles = checksumMatches,
                        shouldSkip = true,
                        reason = "Identical content (checksum match)"
                    )
                }

                // Potential duplicates based on size only
                if (sameSizeFiles.isNotEmpty()) {
                    return DuplicateCheckResult(
                        isDuplicate = true,
                        duplicateFiles = sameSizeFiles,
                        shouldSkip = false,
                        reason = "Potential duplicate (same size)"
                    )
                }
            }

            // No duplicates found
            Timber.d("✅ No duplicates found for: $fileName")
            DuplicateCheckResult(
                isDuplicate = false,
                duplicateFiles = emptyList(),
                shouldSkip = false,
                reason = "No duplicates detected"
            )

        } catch (e: Exception) {
            Timber.e(e, "❌ Duplicate check failed for: $fileName")
            DuplicateCheckResult(
                isDuplicate = false,
                duplicateFiles = emptyList(),
                shouldSkip = false,
                reason = "Duplicate check failed: ${e.message}"
            )
        }
    }

    private suspend fun calculateFileChecksum(filePath: String): String {
        return try {
            val file = java.io.File(filePath)
            val digest = MessageDigest.getInstance("SHA-256")

            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }

            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "Checksum calculation failed")
            ""
        }
    }
}
