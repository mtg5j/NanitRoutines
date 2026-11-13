package com.droid.nanitroutines.data.nanit

import com.droid.nanitroutines.domain.model.NanitBabyDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

sealed class NanitLoginResult {
    data class Authenticated(
        val accessToken: String,
        val refreshToken: String
    ) : NanitLoginResult()

    data class MfaRequired(
        val mfaToken: String,
        val channel: String = "email"
    ) : NanitLoginResult()

    data class Error(
        val httpCode: Int,
        val message: String
    ) : NanitLoginResult()
}

class NanitAuthApi(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://api.nanit.com"
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val refreshMutex = Mutex()

    /**
     * Automatically checks if the current `accessToken` in [tokenStore] is valid,
     * and if it is expired (or within 10-15 minutes of expiring, or [forceRefresh] is true),
     * silently exchanges `tokenStore.refreshToken` at `POST /tokens/refresh` for a fresh
     * 60-minute `access_token` without requiring user interaction or 2FA.
     */
    suspend fun ensureValidAccessToken(
        tokenStore: TokenStore,
        forceRefresh: Boolean = false
    ): Result<String> = refreshMutex.withLock {
        val currentAccess = tokenStore.accessToken
        val currentRefresh = tokenStore.refreshToken

        if (!forceRefresh && currentAccess.isNotBlank() && !tokenStore.isAccessTokenExpiredOrExpiringSoon()) {
            return@withLock Result.success(currentAccess)
        }

        if (currentRefresh.isBlank()) {
            return@withLock if (currentAccess.isNotBlank() && !forceRefresh) {
                Result.success(currentAccess)
            } else {
                Result.failure(IllegalStateException("Please sign in to your Nanit account in Settings"))
            }
        }

        when (val refreshRes = refreshAccessToken(currentRefresh)) {
            is NanitLoginResult.Authenticated -> {
                if (refreshRes.accessToken.isNotBlank()) {
                    tokenStore.saveTokens(
                        newAccessToken = refreshRes.accessToken,
                        newRefreshToken = refreshRes.refreshToken.ifBlank { currentRefresh }
                    )
                    // Also auto-discover baby/device UIDs if not yet populated
                    val existingDevice = tokenStore.getSelectedDevice()
                    if (existingDevice == null || existingDevice.cameraUid.isBlank()) {
                        fetchBabiesAndDevices(refreshRes.accessToken).onSuccess { babies ->
                            val first = babies.firstOrNull()
                            if (first != null) {
                                tokenStore.saveSelectedDevice(first)
                            }
                        }
                    }
                    Result.success(refreshRes.accessToken)
                } else {
                    Result.failure(IllegalStateException("Nanit token refresh returned empty token"))
                }
            }
            is NanitLoginResult.Error -> {
                if (refreshRes.httpCode == -1 && currentAccess.isNotBlank()) {
                    // Temporary network hiccup during proactive refresh; try existing token
                    Result.success(currentAccess)
                } else {
                    Result.failure(
                        IllegalStateException(
                            "Nanit session expired (${refreshRes.message}). Please sign in again in Settings."
                        )
                    )
                }
            }
            is NanitLoginResult.MfaRequired -> {
                Result.failure(IllegalStateException("Nanit requires re-authentication in Settings"))
            }
        }
    }

    suspend fun login(email: String, password: String): NanitLoginResult =
        withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject()
                    .put("email", email.trim())
                    .put("password", password)
                    .toString()

                val request = Request.Builder()
                    .url("$baseUrl/login")
                    .header("nanit-api-version", "1")
                    .header("Content-Type", "application/json")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    val json = if (bodyText.isNotBlank()) JSONObject(bodyText) else JSONObject()
                    when (response.code) {
                        200, 201 -> {
                            val accessToken = json.optString("access_token")
                            val refreshToken = json.optString("refresh_token")
                            if (accessToken.isNotBlank()) {
                                NanitLoginResult.Authenticated(
                                    accessToken = accessToken,
                                    refreshToken = refreshToken
                                )
                            } else {
                                NanitLoginResult.Error(response.code, "Missing access_token in response")
                            }
                        }
                        482 -> {
                            val mfaToken = json.optString("mfa_token")
                            NanitLoginResult.MfaRequired(
                                mfaToken = mfaToken,
                                channel = json.optString("channel", "email").ifBlank { "email" }
                            )
                        }
                        else -> {
                            val errMsg = json.optString("message")
                                .ifBlank { json.optString("error", "HTTP ${response.code}") }
                            NanitLoginResult.Error(response.code, errMsg)
                        }
                    }
                }
            } catch (e: Exception) {
                NanitLoginResult.Error(-1, e.message ?: "Network error during login")
            }
        }

    suspend fun verifyMfa(
        email: String,
        password: String,
        mfaToken: String,
        mfaCode: String,
        channel: String = "email"
    ): NanitLoginResult = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .put("mfa_token", mfaToken.trim())
                .put("mfa_code", mfaCode.trim())
                .put("channel", channel)
                .toString()

            val request = Request.Builder()
                .url("$baseUrl/login")
                .header("nanit-api-version", "1")
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                val json = if (bodyText.isNotBlank()) JSONObject(bodyText) else JSONObject()
                if (response.code in 200..201) {
                    val accessToken = json.optString("access_token")
                    val refreshToken = json.optString("refresh_token")
                    NanitLoginResult.Authenticated(accessToken, refreshToken)
                } else {
                    val errMsg = json.optString("message")
                        .ifBlank { json.optString("error", "2FA verification failed (${response.code})") }
                    NanitLoginResult.Error(response.code, errMsg)
                }
            }
        } catch (e: Exception) {
            NanitLoginResult.Error(-1, e.message ?: "Network error during 2FA verification")
        }
    }

    suspend fun refreshAccessToken(refreshToken: String): NanitLoginResult =
        withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject()
                    .put("refresh_token", refreshToken)
                    .toString()

                val request = Request.Builder()
                    .url("$baseUrl/tokens/refresh")
                    .header("nanit-api-version", "1")
                    .header("Content-Type", "application/json")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    val json = if (bodyText.isNotBlank()) JSONObject(bodyText) else JSONObject()
                    if (response.code in 200..201) {
                        val newAccess = json.optString("access_token")
                        val newRefresh = json.optString("refresh_token", refreshToken).ifBlank { refreshToken }
                        if (newAccess.isNotBlank()) {
                            NanitLoginResult.Authenticated(
                                accessToken = newAccess,
                                refreshToken = newRefresh
                            )
                        } else {
                            NanitLoginResult.Error(response.code, "Empty access_token on refresh")
                        }
                    } else {
                        NanitLoginResult.Error(response.code, "Token refresh failed (HTTP ${response.code})")
                    }
                }
            } catch (e: Exception) {
                NanitLoginResult.Error(-1, e.message ?: "Network error refreshing token")
            }
        }

    suspend fun fetchBabiesAndDevices(accessToken: String): Result<List<NanitBabyDevice>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$baseUrl/babies")
                    .header("nanit-api-version", "1")
                    .header("Authorization", accessToken)
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Failed to fetch babies: HTTP ${response.code}")
                    }
                    val root = JSONObject(bodyText)
                    val babiesArr = root.optJSONArray("babies") ?: JSONArray()
                    val list = mutableListOf<NanitBabyDevice>()
                    for (i in 0 until babiesArr.length()) {
                        val b = babiesArr.getJSONObject(i)
                        val babyUid = b.optString("uid")
                        val babyName = b.optString("name", "Baby ${i + 1}")
                        val cameraUid = b.optString("camera_uid").ifBlank {
                            b.optJSONObject("camera")?.optString("uid").orEmpty()
                        }
                        val speakerUid = b.optString("speaker_uid").takeIf { it.isNotBlank() }
                            ?: b.optJSONObject("speaker")
                                ?.optJSONObject("speaker")
                                ?.optString("uid")
                                ?.takeIf { it.isNotBlank() }
                            ?: b.optJSONObject("speaker")?.optString("uid")?.takeIf { it.isNotBlank() }
                            ?: b.optJSONObject("sound_and_light")?.optString("uid")?.takeIf { it.isNotBlank() }

                        list.add(
                            NanitBabyDevice(
                                babyUid = babyUid,
                                babyName = babyName,
                                cameraUid = cameraUid,
                                speakerUid = speakerUid
                            )
                        )
                    }
                    list
                }
            }
        }

    suspend fun fetchSpeakerLocalToken(accessToken: String, speakerUid: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$baseUrl/speakers/$speakerUid/udtokens")
                    .header("nanit-api-version", "1")
                    .header("Authorization", accessToken)
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Failed to fetch speaker udtoken: HTTP ${response.code}")
                    }
                    val json = JSONObject(bodyText)
                    json.optString("token").ifBlank {
                        json.optString("udtoken").ifBlank { accessToken }
                    }
                }
            }
        }
}
