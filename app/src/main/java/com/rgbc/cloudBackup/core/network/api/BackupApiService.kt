package com.rgbc.cloudBackup.core.network.api

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface BackupApiService {

    @Multipart
    @POST("post")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("metadata") metadata: String
    ): Response<FileUploadResponse>

    @GET("get")
    suspend fun downloadFile(
        @Path("fileId") fileId: String
    ): Response<ResponseBody>

    @GET("get")
    suspend fun getFileList(): Response<List<FileMetadata>>

    @DELETE("delete")
    suspend fun deleteFile(
        @Path("fileId") fileId: String
    ): Response<Unit>
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
