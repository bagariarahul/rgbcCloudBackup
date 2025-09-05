package com.rgbc.cloudBackup.core.data.database.entity

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rgbc.cloudBackup.core.data.database.CloudBackupDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class FileIndexDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: CloudBackupDatabase
    private lateinit var dao: FileIndexDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CloudBackupDatabase::class.java
        ).allowMainThreadQueries().build()

        dao = database.fileIndexDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndGetFile_returnsCorrectFile() = runTest {
        // Given
        val testFile = createTestFileIndex(
            name = "test.txt",
            path = "/test/test.txt",
            size = 1024L
        )

        // When
        val fileId = dao.insertFile(testFile)
        val retrievedFile = dao.findByPath("/test/test.txt")

        // Then
        assertNotNull(retrievedFile)
        assertEquals("test.txt", retrievedFile.name)
        assertEquals(1024L, retrievedFile.size)
        assertTrue(fileId > 0)
    }

    @Test
    fun getAllFiles_returnsFilesOrderedByModifiedDate() = runTest {
        // Given
        val oldFile = createTestFileIndex(
            name = "old.txt",
            modifiedAt = Date(System.currentTimeMillis() - 10000)
        )
        val newFile = createTestFileIndex(
            name = "new.txt",
            modifiedAt = Date(System.currentTimeMillis())
        )

        dao.insertFile(oldFile)
        dao.insertFile(newFile)

        // When
        val files = dao.getAllFiles().first()

        // Then
        assertEquals(2, files.size)
        assertEquals("new.txt", files[0].name) // Most recent first
        assertEquals("old.txt", files[1].name)
    }

    @Test
    fun getFilesToBackup_returnsOnlyUnbackedFiles() = runTest {
        // Given
        val needsBackup = createTestFileIndex(
            name = "needs_backup.txt",
            shouldBackup = true,
            isBackedUp = false
        )
        val alreadyBacked = createTestFileIndex(
            name = "backed_up.txt",
            shouldBackup = true,
            isBackedUp = true
        )
        val shouldNotBackup = createTestFileIndex(
            name = "no_backup.txt",
            shouldBackup = false,
            isBackedUp = false
        )

        dao.insertFile(needsBackup)
        dao.insertFile(alreadyBacked)
        dao.insertFile(shouldNotBackup)

        // When
        val filesToBackup = dao.getFilesToBackup().first()

        // Then
        assertEquals(1, filesToBackup.size)
        assertEquals("needs_backup.txt", filesToBackup[0].name)
    }

    @Test
    fun countFilesWithErrors_returnsCorrectCount() = runTest {
        // Given
        val fileWithError = createTestFileIndex(
            name = "error.txt",
            errorMessage = "Upload failed"
        )
        val fileWithoutError = createTestFileIndex(name = "good.txt")

        dao.insertFile(fileWithError)
        dao.insertFile(fileWithoutError)

        // When
        val errorCount = dao.countFilesWithErrors()

        // Then
        assertEquals(1, errorCount)
    }

    @Test
    fun countFailedBackups_returnsCorrectCount() = runTest {
        // Given
        val failedFile = createTestFileIndex(
            name = "failed.txt",
            isBackedUp = false,
            lastAttemptedAt = Date() // Has attempt but not backed up
        )
        val notAttempted = createTestFileIndex(
            name = "not_attempted.txt",
            isBackedUp = false,
            lastAttemptedAt = null // No attempt yet
        )

        dao.insertFile(failedFile)
        dao.insertFile(notAttempted)

        // When
        val failedCount = dao.countFailedBackups()

        // Then
        assertEquals(1, failedCount)
    }

    @Test
    fun markAsBackedUp_updatesFileCorrectly() = runTest {
        // Given
        val file = createTestFileIndex(name = "test.txt", isBackedUp = false)
        val fileId = dao.insertFile(file)

        // When
        dao.markAsBackedUp(fileId, Date())

        // Then
        val updatedFile = dao.findByPath(file.path)
        assertNotNull(updatedFile)
        assertTrue(updatedFile.isBackedUp)
        assertNotNull(updatedFile.backedUpAt)
    }

    @Test
    fun findByChecksum_findsCorrectFile() = runTest {
        // Given
        val file = createTestFileIndex(
            name = "test.txt",
            checksum = "abc123def456"
        )
        dao.insertFile(file)

        // When
        val found = dao.findByChecksum("abc123def456")
        val notFound = dao.findByChecksum("different_checksum")

        // Then
        assertNotNull(found)
        assertEquals("test.txt", found.name)
        assertNull(notFound)
    }

    @Test
    fun getTotalSize_calculatesCorrectly() = runTest {
        // Given
        dao.insertFile(createTestFileIndex(size = 1000L))
        dao.insertFile(createTestFileIndex(size = 2000L))
        dao.insertFile(createTestFileIndex(size = 3000L))

        // When
        val totalSize = dao.getTotalSize()

        // Then
        assertEquals(6000L, totalSize)
    }

    private fun createTestFileIndex(
        name: String = "test.txt",
        path: String = "/test/$name",
        size: Long = 1024L,
        checksum: String = "test_checksum_$name",
        modifiedAt: Date = Date(),
        shouldBackup: Boolean = true,
        isBackedUp: Boolean = false,
        errorMessage: String? = null,
        lastAttemptedAt: Date? = null
    ) = FileIndex(
        name = name,
        path = path,
        size = size,
        checksum = checksum,
        createdAt = modifiedAt,
        modifiedAt = modifiedAt,
        shouldBackup = shouldBackup,
        isBackedUp = isBackedUp,
        fileType = name.substringAfterLast('.', ""),
        errorMessage = errorMessage,
        lastAttemptedAt = lastAttemptedAt
    )
}