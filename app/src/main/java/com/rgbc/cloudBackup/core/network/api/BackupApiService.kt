package com.rgbc.cloudBackup.core.network.api

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface BackupApiService {

    // File Upload
    @Multipart
    @POST("api/files/upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("metadata") metadata: String? = null
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

    // Legacy endpoints
    @GET("download")
    suspend fun downloadFile(
        @Query("user") user: String,
        @Query("file") file: String
    ): Response<ResponseBody>
}

// --- Response Data Classes ---

data class FileUploadResponse(
    val message: String,
    val file: UploadedFile
)

data class UploadedFile(
    // 1. CHANGE THIS TO STRING. The server uses the filename (e.g., "123_image.jpg") as the ID.
    val id: String,

    val originalName: String,

    // 2. Maps JSON "fileName" to this field
    @SerializedName("fileName")
    val filename: String,

    // 3. Server sends "size", matches this variable name (No annotation needed if names match)
    val size: Long,

    val mimetype: String?
)

data class FileListResponse(
    val files: List<RemoteFile>,
    val total: Int
)

data class RemoteFile(
    // Ensure ID is String here too
    val id: String,
    val originalName: String,
    @SerializedName("fileSize")
    val fileSize: Long,
    val uploadedAt: String?
)