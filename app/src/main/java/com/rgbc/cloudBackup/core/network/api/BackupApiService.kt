package com.rgbc.cloudBackup.core.network.api

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface BackupApiService {

    // File Upload (simplified)
    @Multipart
    @POST("api/files/upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part
    ): Response<FileUploadResponse>

    // File Download by ID
    @Streaming
    @GET("api/files/download/{fileId}")
    suspend fun downloadFileById(
        @Path("fileId") fileId: String
    ): Response<ResponseBody>

    // File List
    @GET("api/files/list")
    suspend fun getFileList(): Response<FileListResponse>

    // Legacy endpoints for backward compatibility
    @GET("download")
    suspend fun downloadFile(
        @Query("user") user: String,
        @Query("file") file: String
    ): Response<ResponseBody>
}

// Response data classes
data class FileUploadResponse(
    val message: String,
    val file: UploadedFile
)

data class UploadedFile(
    val id: Long,
    val originalName: String,
    val filename: String,
    val size: Long,
    val uploadedAt: String
)

data class FileListResponse(
    val files: List<RemoteFile>,
    val total: Int
)

data class RemoteFile(
    val id: Int,
    val originalName: String,
    val fileSize: Long,
    val uploadedAt: String
)