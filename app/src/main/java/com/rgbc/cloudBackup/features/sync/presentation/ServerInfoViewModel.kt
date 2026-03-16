package com.rgbc.cloudBackup.features.sync.presentation

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.rgbc.cloudBackup.core.network.api.BackupApiService
import com.rgbc.cloudBackup.core.network.api.ServerInfoResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ServerInfoUiState(
    val isLoading: Boolean = false,
    val serverInfo: ServerInfoResponse? = null,
    val error: String? = null,
    // Sprint 2.5: Offline caching state
    val isCachedData: Boolean = false,
    val lastFetchedAt: String? = null
)

@HiltViewModel
class ServerInfoViewModel @Inject constructor(
    private val apiService: BackupApiService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerInfoUiState())
    val uiState: StateFlow<ServerInfoUiState> = _uiState.asStateFlow()

    private val gson = Gson()
    private val cachePrefs: SharedPreferences by lazy {
        context.getSharedPreferences("server_info_cache", Context.MODE_PRIVATE)
    }

    init {
        fetchServerInfo()
    }

    fun fetchServerInfo() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val response = apiService.getServerInfo()

                if (response.isSuccessful && response.body() != null) {
                    val serverInfo = response.body()!!

                    // Cache the successful response
                    saveToCache(serverInfo)

                    _uiState.value = ServerInfoUiState(
                        isLoading = false,
                        serverInfo = serverInfo,
                        isCachedData = false,
                        lastFetchedAt = serverInfo.timestamp
                    )
                    Timber.i("🌐 Server info fetched live: ${serverInfo.status}")
                } else {
                    val errorMsg = "Server error: ${response.code()} — ${response.message()}"
                    Timber.w("🌐 Server info failed: $errorMsg")
                    loadCachedOrError(errorMsg)
                }
            } catch (e: Exception) {
                Timber.e(e, "🌐 Failed to fetch server info")
                loadCachedOrError("Connection failed: ${e.message}")
            }
        }
    }

    /**
     * On failure: try to load cached data. If cache exists, show it with
     * an offline banner. If no cache, show the error card.
     */
    private fun loadCachedOrError(errorMsg: String) {
        val cached = loadFromCache()

        if (cached != null) {
            val lastFetched = cachePrefs.getString("last_fetched_at", null)
            Timber.i("🌐 Loaded cached server info (last fetched: $lastFetched)")

            _uiState.value = ServerInfoUiState(
                isLoading = false,
                serverInfo = cached,
                error = null,
                isCachedData = true,
                lastFetchedAt = lastFetched
            )
        } else {
            _uiState.value = ServerInfoUiState(
                isLoading = false,
                error = errorMsg
            )
        }
    }

    private fun saveToCache(serverInfo: ServerInfoResponse) {
        try {
            val json = gson.toJson(serverInfo)
            cachePrefs.edit()
                .putString("server_info_json", json)
                .putString("last_fetched_at", serverInfo.timestamp)
                .apply()
            Timber.d("🌐 Server info cached")
        } catch (e: Exception) {
            Timber.w(e, "🌐 Failed to cache server info")
        }
    }

    private fun loadFromCache(): ServerInfoResponse? {
        return try {
            val json = cachePrefs.getString("server_info_json", null) ?: return null
            gson.fromJson(json, ServerInfoResponse::class.java)
        } catch (e: Exception) {
            Timber.w(e, "🌐 Failed to load cached server info")
            null
        }
    }
}