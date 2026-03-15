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

    // NEW: Server Info
    @GET("api/server-info")
    suspend fun getServerInfo(): Response<ServerInfoResponse>
}

// --- Response Data Classes ---

data class FileUploadResponse(
    val message: String,
    val file: UploadedFile
)

data class UploadedFile(
    val id: String,
    val originalName: String,
    @SerializedName("fileName")
    val filename: String,
    val size: Long,
    val mimetype: String?
)

data class FileListResponse(
    val files: List<RemoteFile>,
    val total: Int
)

data class RemoteFile(
    val id: String,
    val originalName: String,
    @SerializedName("fileSize")
    val fileSize: Long,
    val uploadedAt: String?
)

// NEW: Server Info Response
data class ServerInfoResponse(
    val status: String,
    val uptime: UptimeInfo,
    val os: OsInfo,
    val memory: ResourceInfo,
    val disk: DiskInfo,
    val node: NodeInfo,
    val timestamp: String
)

data class UptimeInfo(
    val seconds: Long,
    val formatted: String
)

data class OsInfo(
    val type: String,
    val platform: String,
    val release: String,
    val arch: String,
    val hostname: String
)

data class ResourceInfo(
    val total: Long,
    val free: Long,
    val used: Long,
    val usedPercent: Int
)

data class DiskInfo(
    val total: Long,
    val free: Long,
    val used: Long,
    val usedPercent: Int
)

data class NodeInfo(
    val version: String,
    val env: String
)