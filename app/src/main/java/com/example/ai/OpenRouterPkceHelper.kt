package com.example.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

data class PkceAuthSession(
    val codeVerifier: String,
    val codeChallenge: String,
    val state: String,
    val callbackPort: Int,
    val authorizationUrl: String
)

/**
 * Implements OpenRouter OAuth PKCE (RFC 7636 S256) with a loopback-only `127.0.0.1`
 * callback listener and safe authorization code exchange.
 */
object OpenRouterPkceHelper {

    const val LOOPBACK_HOST = "127.0.0.1"
    const val CALLBACK_PATH = "/openrouter/callback"
    const val OPENROUTER_AUTH_ENDPOINT = "https://openrouter.ai/auth"
    const val OPENROUTER_KEYS_EXCHANGE_URL = "https://openrouter.ai/api/v1/auth/keys"

    private val secureRandom = SecureRandom()

    fun generateCodeVerifier(numRandomBytes: Int = 32): String {
        val bytes = ByteArray(numRandomBytes.coerceIn(32, 96))
        secureRandom.nextBytes(bytes)
        return PureBase64.encodeUrlNoPad(bytes)
    }

    fun generateStateNonce(): String {
        val bytes = ByteArray(18)
        secureRandom.nextBytes(bytes)
        return PureBase64.encodeUrlNoPad(bytes)
    }

    /**
     * Computes RFC 7636 S256 challenge: BASE64URL-ENCODE(SHA256(ASCII(code_verifier))).
     */
    fun computeCodeChallengeS256(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(codeVerifier.toByteArray(StandardCharsets.US_ASCII))
        return PureBase64.encodeUrlNoPad(digest)
    }

    fun buildCallbackUrl(port: Int): String {
        require(port in 1..65535) { "Loopback callback port must be between 1 and 65535." }
        return "http://$LOOPBACK_HOST:$port$CALLBACK_PATH"
    }

    fun buildAuthorizationUrl(
        callbackPort: Int,
        codeChallenge: String,
        state: String
    ): String {
        val callbackUrl = buildCallbackUrl(callbackPort)
        val encodedCallback = URLEncoder.encode(callbackUrl, "UTF-8")
        val encodedChallenge = URLEncoder.encode(codeChallenge, "UTF-8")
        val encodedState = URLEncoder.encode(state, "UTF-8")
        return "$OPENROUTER_AUTH_ENDPOINT?callback_url=$encodedCallback&code_challenge=$encodedChallenge&code_challenge_method=S256&state=$encodedState"
    }

    /**
     * Parses `code` and `state` from an HTTP request-line such as:
     * `GET /openrouter/callback?code=abc123&state=xyz HTTP/1.1`
     */
    fun parseCallbackRequestLine(requestLine: String): Pair<String?, String?> {
        val parts = requestLine.trim().split(' ')
        if (parts.size < 2) return null to null
        val pathAndQuery = parts[1]
        val queryIdx = pathAndQuery.indexOf('?')
        if (queryIdx < 0) return null to null
        val query = pathAndQuery.substring(queryIdx + 1)
        var code: String? = null
        var state: String? = null
        query.split('&').forEach { pair ->
            val kv = pair.split('=', limit = 2)
            if (kv.size == 2) {
                val key = URLDecoder.decode(kv[0], "UTF-8")
                val value = URLDecoder.decode(kv[1], "UTF-8")
                when (key) {
                    "code" -> code = value
                    "state" -> state = value
                }
            }
        }
        return code to state
    }

    /**
     * Opens a temporary loopback-only `ServerSocket` on `127.0.0.1`, launches the browser callback
     * via [onLaunchBrowser], waits for OpenRouter's redirect, verifies `state`, and exchanges the
     * authorization code for a user-scoped OpenRouter API key.
     */
    suspend fun runLoopbackPkceFlow(
        httpClient: OkHttpClient,
        timeoutMs: Int = 120_000,
        onLaunchBrowser: (PkceAuthSession) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val verifier = generateCodeVerifier()
        val challenge = computeCodeChallengeS256(verifier)
        val expectedState = generateStateNonce()

        val loopbackAddr = InetAddress.getByName(LOOPBACK_HOST)
        ServerSocket(0, 4, loopbackAddr).use { serverSocket ->
            serverSocket.soTimeout = timeoutMs
            val session = PkceAuthSession(
                codeVerifier = verifier,
                codeChallenge = challenge,
                state = expectedState,
                callbackPort = serverSocket.localPort,
                authorizationUrl = buildAuthorizationUrl(
                    callbackPort = serverSocket.localPort,
                    codeChallenge = challenge,
                    state = expectedState
                )
            )
            onLaunchBrowser(session)

            val authCode = serverSocket.accept().use { client ->
                client.soTimeout = 10_000
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
                val requestLine = reader.readLine().orEmpty()
                val (code, returnedState) = parseCallbackRequestLine(requestLine)

                val validState = returnedState == null || returnedState == expectedState
                val ok = !code.isNullOrBlank() && validState
                val body = if (ok) {
                    "<html><body style='font-family:sans-serif;background:#070A12;color:#F2F6FF;padding:32px;'>" +
                        "<h2>ChronoStage AI Connected</h2>" +
                        "<p>OpenRouter authorization succeeded. You can return to ChronoStage AI now.</p>" +
                        "</body></html>"
                } else {
                    "<html><body style='font-family:sans-serif;background:#070A12;color:#FF5252;padding:32px;'>" +
                        "<h2>Authorization Incomplete</h2>" +
                        "<p>No valid authorization code was received. Please retry from ChronoStage AI.</p>" +
                        "</body></html>"
                }
                val response = buildString {
                    append(if (ok) "HTTP/1.1 200 OK\r\n" else "HTTP/1.1 400 Bad Request\r\n")
                    append("Content-Type: text/html; charset=UTF-8\r\n")
                    append("Content-Length: ${body.toByteArray(StandardCharsets.UTF_8).size}\r\n")
                    append("Connection: close\r\n\r\n")
                    append(body)
                }
                client.getOutputStream().write(response.toByteArray(StandardCharsets.UTF_8))
                client.getOutputStream().flush()

                if (!ok) {
                    throw IllegalStateException("OpenRouter OAuth callback failed state or code validation.")
                }
                code!!
            }

            exchangeCodeForApiKey(
                httpClient = httpClient,
                code = authCode,
                codeVerifier = verifier
            )
        }
    }

    suspend fun exchangeCodeForApiKey(
        httpClient: OkHttpClient,
        code: String,
        codeVerifier: String
    ): String = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("code", code)
            put("code_verifier", codeVerifier)
            put("code_challenge_method", "S256")
        }.toString()

        val request = Request.Builder()
            .url(OPENROUTER_KEYS_EXCHANGE_URL)
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val client = httpClient.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        client.newCall(request).execute().use { resp ->
            val respBody = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("OpenRouter key exchange failed (HTTP ${resp.code}).")
            }
            val json = JSONObject(respBody)
            val key = json.optString("key").trim()
            if (key.isEmpty()) {
                throw IllegalStateException("OpenRouter key exchange returned an empty key.")
            }
            key
        }
    }
}
