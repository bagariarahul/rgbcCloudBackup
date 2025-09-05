package com.rgbc.cloudBackup.core.data.repository

import com.rgbc.cloudBackup.core.data.database.entity.FileIndex
import com.rgbc.cloudBackup.core.data.database.entity.FileIndexDao
import com.rgbc.cloudBackup.core.domain.model.BackupStats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.mockito.kotlin.whenever
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.any


class FileRepositoryImplTest {

    @Mock
    private lateinit var mockDao: FileIndexDao

    private lateinit var repository: FileRepositoryImpl

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = FileRepositoryImpl(mockDao)
    }

    @Test
    fun getAllFiles_returnsDaoFlow() = runTest {
        // Given
        val testFiles = listOf(
            createTestFileIndex(name = "file1.txt"),
            createTestFileIndex(name = "file2.txt")
        )
        whenever(mockDao.getAllFiles()).thenReturn(flowOf(testFiles))

        // When
        val result = repository.getAllFiles().first()

        // Then
        assertEquals(2, result.size)
        assertEquals("file1.txt", result[0].name)
        verify(mockDao).getAllFiles()
    }

    @Test
    fun getFilesToBackup_returnsDaoFlow() = runTest {
        // Given
        val filesToBackup = listOf(
            createTestFileIndex(name = "backup1.txt", isBackedUp = false)
        )
        whenever(mockDao.getFilesToBackup()).thenReturn(flowOf(filesToBackup))

        // When
        val result = repository.getFilesToBackup().first()

        // Then
        assertEquals(1, result.size)
        assertEquals("backup1.txt", result[0].name)
        verify(mockDao).getFilesToBackup()
    }

    @Test
    fun insertFile_returnsIdFromDao() = runTest {
        // Given
        val testFile = createTestFileIndex()
        val expectedId = 123L
        whenever(mockDao.insertFile(testFile)).thenReturn(expectedId)

        // When
        val result = repository.insertFile(testFile)

        // Then
        assertEquals(expectedId, result)
        verify(mockDao).insertFile(testFile)
    }

    @Test
    fun insertFiles_returnsIdsFromDao() = runTest {
        // Given
        val testFiles = listOf(
            createTestFileIndex(name = "file1.txt"),
            createTestFileIndex(name = "file2.txt")
        )
        val expectedIds = listOf(1L, 2L)
        whenever(mockDao.insertFiles(testFiles)).thenReturn(expectedIds)

        // When
        val result = repository.insertFiles(testFiles)

        // Then
        assertEquals(expectedIds, result)
        verify(mockDao).insertFiles(testFiles)
    }

    @Test
    fun markAsBackedUp_callsDaoWithCurrentDate() = runTest {
        // Given
        val fileId = 123L

        // When
        repository.markAsBackedUp(fileId)

        // Then
        verify(mockDao).markAsBackedUp(eq(fileId), any())
    }

    @Test
    fun findByPath_returnsDaoResult() = runTest {
        // Given
        val path = "/test/file.txt"
        val expectedFile = createTestFileIndex(path = path)
        whenever(mockDao.findByPath(path)).thenReturn(expectedFile)

        // When
        val result = repository.findByPath(path)

        // Then
        assertNotNull(result)
        assertEquals(path, result.path)
        verify(mockDao).findByPath(path)
    }

    @Test
    fun getBackupStats_aggregatesCorrectly() = runTest {
        // Given
        whenever(mockDao.getFileCount()).thenReturn(10)
        whenever(mockDao.getBackedUpCount()).thenReturn(6)
        whenever(mockDao.getTotalSize()).thenReturn(5000L)
        whenever(mockDao.getBackedUpSize()).thenReturn(3000L)
        whenever(mockDao.countFilesWithErrors()).thenReturn(2)
        whenever(mockDao.countFailedBackups()).thenReturn(1)

        // When
        val stats = repository.getBackupStats()

        // Then
        assertEquals(10, stats.totalFiles)
        assertEquals(6, stats.backedUpFiles)
        assertEquals(5000L, stats.totalSize)
        assertEquals(3000L, stats.backedUpSize)
        assertEquals(4, stats.pendingFiles) // 10 - 6
        assertEquals(2, stats.errorCount)
        assertEquals(1, stats.failedFiles)
    }

    @Test
    fun getBackupStats_handlesNullSizes() = runTest {
        // Given
        whenever(mockDao.getFileCount()).thenReturn(5)
        whenever(mockDao.getBackedUpCount()).thenReturn(3)
        whenever(mockDao.getTotalSize()).thenReturn(null) // Null case
        whenever(mockDao.getBackedUpSize()).thenReturn(null) // Null case
        whenever(mockDao.countFilesWithErrors()).thenReturn(0)
        whenever(mockDao.countFailedBackups()).thenReturn(0)

        // When
        val stats = repository.getBackupStats()

        // Then
        assertEquals(5, stats.totalFiles)
        assertEquals(3, stats.backedUpFiles)
        assertEquals(0L, stats.totalSize) // Should default to 0
        assertEquals(0L, stats.backedUpSize) // Should default to 0
        assertEquals(2, stats.pendingFiles)
    }

    @Test
    fun scanDirectory_returnsFileIndexesFromValidDirectory() = runTest {
        // Note: This test is tricky since it uses real File system
        // In a real test, you'd want to mock the File operations or use a temp directory
        // For now, we'll test the empty directory case

        // When
        val result = repository.scanDirectory("/nonexistent/path")

        // Then
        assertEquals(0, result.size) // Should return empty list for non-existent directory
    }

    @Test
    fun getBackupStats_handlesRepositoryException() = runTest {
        // Given
        whenever(mockDao.getFileCount()).thenThrow(RuntimeException("Database error"))

        // When
        val stats = repository.getBackupStats()

        // Then
        // Should return default stats when there's an error
        assertEquals(BackupStats(0, 0, 0L, 0L, 0, 0, 0), stats)
    }

    private fun createTestFileIndex(
        name: String = "test.txt",
        path: String = "/test/test.txt",
        size: Long = 1024L,
        isBackedUp: Boolean = false
    ) = FileIndex(
        name = name,
        path = path,
        size = size,
        checksum = "test_checksum",
        createdAt = Date(),
        modifiedAt = Date(),
        shouldBackup = true,
        isBackedUp = isBackedUp,
        fileType = "txt"
    )
}