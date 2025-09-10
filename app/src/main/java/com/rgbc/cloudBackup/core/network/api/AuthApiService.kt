package com.rgbc.cloudBackup.core.network.api

import retrofit2.Response
import retrofit2.http.*

/**
 * 🔗 Authentication API Service - Simple Version
 */
interface AuthApiService {

    @POST("api/auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<AuthResponse>

    @GET("api/auth/me")
    suspend fun getCurrentUser(): Response<UserProfileResponse>

    @POST("api/auth/logout")
    suspend fun logout(
        @Body request: LogoutRequest
    ): Response<MessageResponse>
}

// Request/Response Data Classes
data class RegisterRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val deviceName: String,
    val deviceType: String,
    val deviceId: String
)

data class LoginRequest(
    val email: String,
    val password: String,
    val deviceName: String?,
    val deviceType: String,
    val deviceId: String
)

data class LogoutRequest(
    val sessionId: String?
)

data class AuthResponse(
    val message: String,
    val user: UserData,
    val device: DeviceData,
    val tokens: TokenData,
    val sessionId: String
)

data class UserData(
    val id: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val storageQuota: String,
    val storageUsed: String,
    val createdAt: String
)

data class DeviceData(
    val id: String,
    val deviceName: String,
    val deviceType: String
)

data class TokenData(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: String
)

data class UserProfileResponse(
    val user: UserProfileData
)

data class UserProfileData(
    val id: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val avatar: String?,
    val isAdmin: Boolean,
    val storageQuota: String,
    val storageUsed: String,
    val lastLoginAt: String?,
    val createdAt: String,
    val devices: List<DeviceData>
)

data class MessageResponse(
    val message: String
)