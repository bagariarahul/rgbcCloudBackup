package com.rgbc.cloudBackup.core.network.api

import retrofit2.Response
import retrofit2.http.*

interface AuthApiService {

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/auth/logout")
    suspend fun logout(@Body request: LogoutRequest): Response<Unit>
}

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
    val deviceName: String,
    val deviceType: String,
    val deviceId: String
)

data class LogoutRequest(
    val sessionId: String?
)

data class AuthResponse(
    val message: String,
    val user: UserData,
    val tokens: TokenData
)

data class UserData(
    val id: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val storageQuota: String = "107374182400", // Default 100GB
    val storageUsed: String = "0",             // Default 0
    val createdAt: String = ""                 // Default empty
)

data class TokenData(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: String
)