package com.ryanjames.lunar.platform

import com.ryanjames.lunar.library.data.GoogleDriveStorageSettings
import com.ryanjames.lunar.sync.GOOGLE_DRIVE_SYNC_SCOPE
import com.ryanjames.lunar.sync.GOOGLE_OAUTH_TOKEN_URL
import com.ryanjames.lunar.sync.GoogleDriveAccessToken
import com.ryanjames.lunar.sync.GoogleDriveOAuthCoordinator
import com.ryanjames.lunar.sync.GoogleDriveOAuthSession
import com.ryanjames.lunar.sync.GoogleOAuthTokenResponse
import com.ryanjames.lunar.sync.SyncHttpClient
import com.ryanjames.lunar.sync.buildFormBody
import com.ryanjames.lunar.sync.tokenExpiryEpochMillis
import com.ryanjames.lunar.sync.urlEncode
import java.awt.Desktop
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.Clock

class DesktopGoogleDriveOAuthCoordinator(
    private val httpClient: SyncHttpClient,
    private val clock: Clock = Clock.System,
) : GoogleDriveOAuthCoordinator {
    private val json = Json { ignoreUnknownKeys = true }

    override val isSupported: Boolean = true

    override suspend fun getOrFetchRefreshToken(settings: GoogleDriveStorageSettings): GoogleDriveOAuthSession {
        val clientId = settings.clientId.trim()
        val clientSecret = settings.clientSecret.trim()
        require(clientId.isNotBlank()) { "Google Drive requires an OAuth client ID." }

        val existingRefreshToken = settings.refreshToken.trim()
        if (existingRefreshToken.isNotBlank()) {
            val refreshedToken = refreshAccessToken(
                refreshToken = existingRefreshToken,
                clientId = clientId,
                clientSecret = clientSecret,
            )
            return GoogleDriveOAuthSession(
                accessToken = refreshedToken.accessToken,
                refreshToken = existingRefreshToken,
                expiresAtEpochMillis = refreshedToken.expiresAtEpochMillis,
            )
        }

        return requestNewRefreshToken(
            clientId = clientId,
            clientSecret = clientSecret,
        )
    }

    override suspend fun refreshAccessToken(
        refreshToken: String,
        clientId: String,
        clientSecret: String,
    ): GoogleDriveAccessToken = withContext(Dispatchers.IO) {
        val trimmedRefreshToken = refreshToken.trim()
        val trimmedClientId = clientId.trim()
        require(trimmedRefreshToken.isNotBlank()) { "Google Drive refresh token is missing." }
        require(trimmedClientId.isNotBlank()) { "Google Drive requires an OAuth client ID." }

        val fields = linkedMapOf(
            "client_id" to trimmedClientId,
            "refresh_token" to trimmedRefreshToken,
            "grant_type" to "refresh_token",
        )
        if (clientSecret.isNotBlank()) {
            fields["client_secret"] = clientSecret.trim()
        }

        val now = clock.now().toEpochMilliseconds()
        val response = httpClient.postForm(
            url = GOOGLE_OAUTH_TOKEN_URL,
            formBody = buildFormBody(fields),
            headers = mapOf("Accept" to "application/json"),
        )
        val token = json.decodeFromString(GoogleOAuthTokenResponse.serializer(), response)
        GoogleDriveAccessToken(
            accessToken = token.accessToken,
            expiresAtEpochMillis = tokenExpiryEpochMillis(now, token.expiresInSeconds),
        )
    }

    private suspend fun requestNewRefreshToken(
        clientId: String,
        clientSecret: String,
    ): GoogleDriveOAuthSession = withContext(Dispatchers.IO) {
        val callbackServer = openLoopbackServer()
        callbackServer.use { server ->
            server.soTimeout = GOOGLE_OAUTH_CALLBACK_TIMEOUT_MILLIS

            val redirectUri = "http://localhost:${server.localPort}$GOOGLE_OAUTH_CALLBACK_PATH"
            val state = randomToken(24)
            val codeVerifier = randomCodeVerifier()
            val codeChallenge = codeChallenge(codeVerifier)
            val authorizationUrl = buildAuthorizationUrl(
                clientId = clientId,
                redirectUri = redirectUri,
                state = state,
                codeChallenge = codeChallenge,
            )

            openBrowser(authorizationUrl)

            val callback = waitForAuthorizationCode(
                server = server,
                expectedState = state,
            )

            val fields = linkedMapOf(
                "code" to callback.code,
                "client_id" to clientId,
                "redirect_uri" to redirectUri,
                "grant_type" to "authorization_code",
                "code_verifier" to codeVerifier,
            )
            if (clientSecret.isNotBlank()) {
                fields["client_secret"] = clientSecret
            }

            val now = clock.now().toEpochMilliseconds()
            val response = httpClient.postForm(
                url = GOOGLE_OAUTH_TOKEN_URL,
                formBody = buildFormBody(fields),
                headers = mapOf("Accept" to "application/json"),
            )
            val token = json.decodeFromString(GoogleOAuthTokenResponse.serializer(), response)
            val refreshToken = token.refreshToken?.trim().orEmpty()
            if (refreshToken.isBlank()) {
                throw IllegalStateException(
                    "Google did not return a refresh token. Revoke Lunar in your Google account permissions and try again.",
                )
            }

            GoogleDriveOAuthSession(
                accessToken = token.accessToken,
                refreshToken = refreshToken,
                expiresAtEpochMillis = tokenExpiryEpochMillis(now, token.expiresInSeconds),
            )
        }
    }

    private fun waitForAuthorizationCode(
        server: ServerSocket,
        expectedState: String,
    ): GoogleOAuthCallback {
        val socket = try {
            server.accept()
        } catch (_: java.net.SocketTimeoutException) {
            throw IllegalStateException("Google sign-in timed out before a response was returned.")
        }

        socket.use { client ->
            val request = readCallbackRequest(client)
            return when {
                request.error != null -> {
                    writeBrowserResponse(
                        socket = client,
                        success = false,
                        message = "Google sign-in did not complete: ${request.error}.",
                    )
                    throw IllegalStateException("Google sign-in was cancelled or denied: ${request.error}.")
                }

                request.code.isNullOrBlank() -> {
                    writeBrowserResponse(
                        socket = client,
                        success = false,
                        message = "Google sign-in did not return an authorization code.",
                    )
                    throw IllegalStateException("Google sign-in did not return an authorization code.")
                }

                request.state != expectedState -> {
                    writeBrowserResponse(
                        socket = client,
                        success = false,
                        message = "Google sign-in state did not match the original request.",
                    )
                    throw IllegalStateException("Google sign-in state did not match the original request.")
                }

                else -> {
                    writeBrowserResponse(
                        socket = client,
                        success = true,
                        message = "Google Drive is connected. You can close this window and return to Lunar.",
                    )
                    GoogleOAuthCallback(code = request.code)
                }
            }
        }
    }

    private fun readCallbackRequest(socket: Socket): GoogleOAuthCallbackRequest {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        val requestLine = reader.readLine()
            ?: throw IllegalStateException("Google sign-in was interrupted before the callback was received.")

        while (true) {
            val headerLine = reader.readLine() ?: break
            if (headerLine.isBlank()) break
        }

        val target = requestLine.substringAfter(' ').substringBefore(" HTTP/")
        val callbackUri = URI("http://localhost$target")
        val params = parseUrlEncodedQuery(callbackUri.rawQuery)
        return GoogleOAuthCallbackRequest(
            code = params["code"],
            state = params["state"],
            error = params["error"],
        )
    }

    private fun writeBrowserResponse(
        socket: Socket,
        success: Boolean,
        message: String,
    ) {
        val body = """
            <html>
            <head><title>Lunar Google Drive</title></head>
            <body style="font-family:Segoe UI, sans-serif; padding:24px; background:#f5f8fb; color:#123;">
                <h2 style="margin-top:0;">${if (success) "Google Drive connected" else "Google Drive sign-in failed"}</h2>
                <p>$message</p>
            </body>
            </html>
        """.trimIndent()
        val bytes = body.toByteArray(Charsets.UTF_8)
        val statusLine = if (success) "HTTP/1.1 200 OK" else "HTTP/1.1 400 Bad Request"

        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
        writer.write(statusLine)
        writer.write("\r\n")
        writer.write("Content-Type: text/html; charset=utf-8\r\n")
        writer.write("Cache-Control: no-store\r\n")
        writer.write("Content-Length: ${bytes.size}\r\n")
        writer.write("\r\n")
        writer.flush()
        socket.getOutputStream().write(bytes)
        socket.getOutputStream().flush()
    }

    private fun openBrowser(url: String) {
        if (!Desktop.isDesktopSupported()) {
            throw IllegalStateException("Desktop browser launch is unavailable on this machine.")
        }

        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            throw IllegalStateException("Desktop browser launch is unavailable on this machine.")
        }

        desktop.browse(URI(url))
    }

    private fun buildAuthorizationUrl(
        clientId: String,
        redirectUri: String,
        state: String,
        codeChallenge: String,
    ): String {
        val query = buildList {
            add("client_id=${urlEncode(clientId)}")
            add("redirect_uri=${urlEncode(redirectUri)}")
            add("response_type=code")
            add("scope=${urlEncode(GOOGLE_DRIVE_SYNC_SCOPE)}")
            add("access_type=offline")
            add("prompt=consent")
            add("state=${urlEncode(state)}")
            add("code_challenge=${urlEncode(codeChallenge)}")
            add("code_challenge_method=S256")
        }.joinToString("&")

        return "$GOOGLE_OAUTH_AUTHORIZATION_URL?$query"
    }

    private fun openLoopbackServer(): ServerSocket {
        val address = InetAddress.getByName("127.0.0.1")
        return runCatching {
            ServerSocket(DEFAULT_GOOGLE_REDIRECT_PORT, 0, address)
        }.getOrElse {
            ServerSocket(0, 0, address)
        }
    }

    private fun randomCodeVerifier(): String = randomToken(64)

    private fun randomToken(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun codeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(codeVerifier.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }

    private fun parseUrlEncodedQuery(query: String?): Map<String, String> {
        if (query.isNullOrBlank()) return emptyMap()
        return query.split('&')
            .filter(String::isNotBlank)
            .associate { entry ->
                val key = entry.substringBefore('=')
                val value = entry.substringAfter('=', "")
                URLDecoder.decode(key, Charsets.UTF_8.name()) to URLDecoder.decode(value, Charsets.UTF_8.name())
            }
    }
}

private data class GoogleOAuthCallbackRequest(
    val code: String? = null,
    val state: String? = null,
    val error: String? = null,
)

private data class GoogleOAuthCallback(
    val code: String,
)

private val secureRandom = SecureRandom()

private const val DEFAULT_GOOGLE_REDIRECT_PORT = 43827
private const val GOOGLE_OAUTH_CALLBACK_TIMEOUT_MILLIS = 180_000
private const val GOOGLE_OAUTH_CALLBACK_PATH = "/"
private const val GOOGLE_OAUTH_AUTHORIZATION_URL = "https://accounts.google.com/o/oauth2/v2/auth"
