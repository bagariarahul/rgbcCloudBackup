package com.rgbc.cloudBackup.core.network.api

import com.rgbc.cloudBackup.core.domain.usecase.FileListResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface BackupApiService {

    @Multipart
    @POST("upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("metadata") metadata: String
    ): Response<FileUploadResponse>

    @GET("download")
    suspend fun downloadFile_test(
        @Query("user") user: String,
        @Query("file") fileId: String
    ): Response<ResponseBody>

    @GET("get")
    suspend fun getFileList(): Response<List<FileMetadata>>

    @DELETE("delete")
    suspend fun deleteFile(
        @Path("fileId") fileId: String
    ): Response<Unit>


    @GET("files/{userId}")
    suspend fun getUserFiles(
        @Path("userId") userId: String
    ): Response<UserFilesResponse>

    // 🆕 ADD: Get detailed file info
    @GET("files/{userId}/{fileName}")
    suspend fun getFileInfo(
        @Path("userId") userId: String,
        @Path("fileName") fileName: String
    ): Response<ServerFileInfo>

    // 🆕 ADD: Check server file integrity
    @GET("files/{userId}/{fileName}/integrity")
    suspend fun checkFileIntegrity(
        @Path("userId") userId: String,
        @Path("fileName") fileName: String
    ): Response<FileIntegrityInfo>

    // ADD TO YOUR BackupApiService.kt:



        // Existing method
        @GET("/download")
        suspend fun downloadFile(
            @Query("user") user: String,
            @Query("file") file: String
        ): Response<ResponseBody>

        // NEW: Add this method for file ID-based downloads
        @GET("/api/files/download/{fileId}")
        suspend fun downloadFileById(
            @Path("fileId") fileId: String
        ): Response<ResponseBody>

        // NEW: Add this method to list files
        @GET("/api/files/list")
        suspend fun listFiles(
            @Query("limit") limit: Int = 50,
            @Query("offset") offset: Int = 0
        ): Response<FileListResponse>


}

// Data classes for API responses

data class FileUploadResponse(
    val fileId: String,
    val uploadUrl: String,
    val status: String
)

data class FileMetadata(
    val fileId: String,
    val fileName: String,
    val fileSize: Long,
    val uploadDate: String,
    val checksum: String
)

// 🆕 ADD: Response models for server sync
data class UserFilesResponse(
    val success: Boolean,
    val files: List<ServerFileInfo>,
    val totalFiles: Int,
    val totalSize: Long
)

data class ServerFileInfo(
    val fileName: String,
    val originalName: String,
    val fileSize: Long,
    val encryptedSize: Long,
    val uploadedAt: String,
    val checksum: String?,
    val contentType: String?,
    val isCorrupted: Boolean = false,
    val lastModified: String
)

data class FileIntegrityInfo(
    val fileName: String,
    val isValid: Boolean,
    val expectedSize: Long,
    val actualSize: Long,
    val checksumMatch: Boolean,
    val canDecrypt: Boolean,
    val errorDetails: String?
)

