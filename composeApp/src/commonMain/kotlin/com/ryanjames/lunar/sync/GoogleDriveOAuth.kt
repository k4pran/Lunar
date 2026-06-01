package com.ryanjames.lunar.sync

import com.ryanjames.lunar.library.data.GoogleDriveStorageSettings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class GoogleDriveOAuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMillis: Long? = null,
)

data class GoogleDriveAccessToken(
    val accessToken: String,
    val expiresAtEpochMillis: Long? = null,
)

interface GoogleDriveOAuthCoordinator {
    val isSupported: Boolean

    suspend fun getOrFetchRefreshToken(settings: GoogleDriveStorageSettings): GoogleDriveOAuthSession

    suspend fun refreshAccessToken(
        refreshToken: String,
        clientId: String,
        clientSecret: String = "",
    ): GoogleDriveAccessToken
}

object UnsupportedGoogleDriveOAuthCoordinator : GoogleDriveOAuthCoordinator {
    override val isSupported: Boolean = false

    override suspend fun getOrFetchRefreshToken(settings: GoogleDriveStorageSettings): GoogleDriveOAuthSession {
        throw UnsupportedOperationException("Google Drive sign-in is unavailable on this target.")
    }

    override suspend fun refreshAccessToken(
        refreshToken: String,
        clientId: String,
        clientSecret: String,
    ): GoogleDriveAccessToken {
        throw UnsupportedOperationException("Google Drive token refresh is unavailable on this target.")
    }
}

@Serializable
data class GoogleOAuthTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresInSeconds: Int = 3600,
    @SerialName("token_type") val tokenType: String = "Bearer",
    val scope: String? = null,
)

internal fun buildFormBody(fields: Map<String, String>): String =
    fields.entries.joinToString("&") { (key, value) ->
        "${urlEncode(key)}=${urlEncode(value)}"
    }

internal fun urlEncode(value: String): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val asInt = byte.toInt() and 0xFF
        val char = asInt.toChar()
        if (
            char in 'a'..'z' ||
            char in 'A'..'Z' ||
            char in '0'..'9' ||
            char == '-' ||
            char == '_' ||
            char == '.' ||
            char == '~'
        ) {
            append(char)
        } else {
            append('%')
            append(asInt.toString(16).uppercase().padStart(2, '0'))
        }
    }
}

internal fun urlEncodePathSegment(value: String): String =
    urlEncode(value).replace("%2F", "/")

internal fun tokenExpiryEpochMillis(
    currentEpochMillis: Long,
    expiresInSeconds: Int,
): Long = currentEpochMillis + expiresInSeconds.toLong() * 1000L

internal const val GOOGLE_DRIVE_READONLY_SCOPE = "https://www.googleapis.com/auth/drive.readonly"
internal const val GOOGLE_DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
internal const val GOOGLE_DRIVE_SYNC_SCOPE = "$GOOGLE_DRIVE_READONLY_SCOPE $GOOGLE_DRIVE_FILE_SCOPE"
internal const val GOOGLE_OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token"
