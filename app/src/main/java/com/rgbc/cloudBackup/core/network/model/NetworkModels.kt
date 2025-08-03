package com.rgbc.cloudBackup.core.network.model

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("data")
    val data: T? = null,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("error")
    val error: String? = null
)

data class FileUploadRequest(
    @SerializedName("fileName")
    val fileName: String,
    @SerializedName("fileSize")
    val fileSize: Long,
    @SerializedName("checksum")
    val checksum: String,
    @SerializedName("contentType")
    val contentType: String
)

data class FileUploadResponse(
    @SerializedName("fileId")
    val fileId: String,
    @SerializedName("uploadUrl")
    val uploadUrl: String,
    @SerializedName("status")
    val status: String
)

data class FileMetadata(
    @SerializedName("fileId")
    val fileId: String,
    @SerializedName("fileName")
    val fileName: String,
    @SerializedName("fileSize")
    val fileSize: Long,
    @SerializedName("uploadDate")
    val uploadDate: String,
    @SerializedName("checksum")
    val checksum: String,
    @SerializedName("status")
    val status: String
)
