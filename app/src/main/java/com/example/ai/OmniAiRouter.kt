package com.example.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

private const val ROUTER_TAG = "OmniAiRouter"

/**
 * Multi-AI Router supporting 6 providers with independent Text and Image provider selection
 * plus automatic fallback when Firebase Studio App Check is unavailable on sideloaded builds.
 */
class OmniAiRouter(
    private val keyVault: SecureAiKeyVault,
    private val httpClient: OkHttpClient = defaultRouterHttpClient()
) {
    // Lazy Firebase AI Logic handles (only initialized when FIREBASE_STUDIO is invoked)
    private val firebaseTextModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    private val firebaseImageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    fun getConfig(): OmniAiConfig = keyVault.loadConfig()

    suspend fun generateText(
        request: AiTextRequest,
        firebaseResponseSchema: Schema? = null
    ): AiTextResponse {
        val config = keyVault.loadConfig()
        val primary = config.activeTextProvider

        return try {
            executeTextProvider(primary, config, request, firebaseResponseSchema, fallbackUsed = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (primary == AiProviderId.FIREBASE_STUDIO && config.enableFirebaseFallback) {
                val fallbackProvider = findFallbackTextProvider(config)
                if (fallbackProvider != null) {
                    Log.w(
                        ROUTER_TAG,
                        "Firebase Studio text call failed; falling back to ${fallbackProvider.id}",
                        e
                    )
                    return executeTextProvider(
                        provider = fallbackProvider,
                        config = config,
                        request = request,
                        firebaseResponseSchema = null,
                        fallbackUsed = true
                    )
                }
            }
            throw e
        }
    }

    fun streamText(request: AiTextRequest): Flow<Pair<String, AiProviderId>> = flow {
        val config = keyVault.loadConfig()
        val primary = config.activeTextProvider
        if (primary == AiProviderId.FIREBASE_STUDIO) {
            try {
                val fullPrompt = if (request.systemInstruction.isNotBlank()) {
                    "${request.systemInstruction}\n\n${request.prompt}"
                } else {
                    request.prompt
                }
                firebaseTextModel.generateContentStream(fullPrompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        emit(piece to AiProviderId.FIREBASE_STUDIO)
                    }
                }
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (config.enableFirebaseFallback) {
                    val fallback = findFallbackTextProvider(config)
                    if (fallback != null) {
                        val resp = executeTextProvider(fallback, config, request, null, fallbackUsed = true)
                        emit(resp.text to resp.providerUsed)
                        return@flow
                    }
                }
                throw e
            }
        } else {
            val resp = executeTextProvider(primary, config, request, null, fallbackUsed = false)
            emit(resp.text to resp.providerUsed)
        }
    }

    suspend fun generateImage(request: AiImageRequest): AiImageResponse {
        val config = keyVault.loadConfig()
        val primary = config.activeImageProvider

        return try {
            executeImageProvider(primary, config, request, fallbackUsed = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (primary == AiProviderId.FIREBASE_STUDIO && config.enableFirebaseFallback) {
                val fallbackProvider = findFallbackImageProvider(config)
                if (fallbackProvider != null) {
                    Log.w(
                        ROUTER_TAG,
                        "Firebase Studio image call failed; falling back to ${fallbackProvider.id}",
                        e
                    )
                    return executeImageProvider(
                        provider = fallbackProvider,
                        config = config,
                        request = request,
                        fallbackUsed = true
                    )
                }
            }
            throw e
        }
    }

    fun findFallbackTextProvider(config: OmniAiConfig): AiProviderId? {
        val preferenceOrder = listOf(
            AiProviderId.OPENROUTER,
            AiProviderId.DIRECT_GEMINI,
            AiProviderId.ANTHROPIC_CLAUDE,
            AiProviderId.OPENAI,
            AiProviderId.LOCAL_OPENAI_COMPAT
        )
        return preferenceOrder.firstOrNull { provider ->
            if (!provider.supportsText) return@firstOrNull false
            if (provider == AiProviderId.LOCAL_OPENAI_COMPAT) {
                config.settingsFor(provider).baseUrl.isNotBlank() &&
                    config.settingsFor(provider).baseUrl != AiProviderId.LOCAL_OPENAI_COMPAT.defaultBaseUrl
            } else {
                keyVault.hasApiKey(provider)
            }
        }
    }

    fun findFallbackImageProvider(config: OmniAiConfig): AiProviderId? {
        val preferenceOrder = listOf(
            AiProviderId.DIRECT_GEMINI,
            AiProviderId.OPENAI,
            AiProviderId.OPENROUTER
        )
        return preferenceOrder.firstOrNull { provider ->
            provider.supportsImage && keyVault.hasApiKey(provider)
        }
    }

    private suspend fun executeTextProvider(
        provider: AiProviderId,
        config: OmniAiConfig,
        request: AiTextRequest,
        firebaseResponseSchema: Schema?,
        fallbackUsed: Boolean
    ): AiTextResponse {
        val settings = config.settingsFor(provider)
        val modelId = settings.textModel.ifBlank { provider.defaultTextModel }

        return when (provider) {
            AiProviderId.FIREBASE_STUDIO -> {
                val model = if (request.requireJson && firebaseResponseSchema != null) {
                    Firebase.ai.generativeModel(
                        modelName = modelId,
                        generationConfig = generationConfig {
                            responseMimeType = "application/json"
                            responseSchema = firebaseResponseSchema
                        }
                    )
                } else {
                    firebaseTextModel
                }
                val combinedPrompt = if (request.systemInstruction.isNotBlank()) {
                    "${request.systemInstruction}\n\n${request.prompt}"
                } else {
                    request.prompt
                }
                val resp = model.generateContent(combinedPrompt)
                val text = resp.text?.trim()
                    ?: throw IllegalStateException("Firebase AI Logic returned an empty response.")
                AiTextResponse(text, provider, modelId, fallbackUsed)
            }

            AiProviderId.DIRECT_GEMINI -> {
                val apiKey = requireApiKey(provider)
                val baseUrl = settings.baseUrl.ifBlank { provider.defaultBaseUrl }.trimEnd('/')
                val url = "$baseUrl/models/$modelId:generateContent"
                val payload = buildDirectGeminiTextPayload(request)
                val raw = executeJsonPost(
                    url = url,
                    jsonBody = payload,
                    headers = mapOf("x-goog-api-key" to apiKey)
                )
                val text = parseDirectGeminiText(raw)
                AiTextResponse(text, provider, modelId, fallbackUsed)
            }

            AiProviderId.OPENROUTER -> {
                val apiKey = requireApiKey(provider)
                val baseUrl = settings.baseUrl.ifBlank { provider.defaultBaseUrl }.trimEnd('/')
                require(baseUrl.startsWith("https://", ignoreCase = true)) {
                    "OpenRouter endpoint must use https://"
                }
                val url = "$baseUrl/chat/completions"
                val payload = buildOpenRouterTextPayload(modelId, request)
                val raw = executeJsonPost(
                    url = url,
                    jsonBody = payload,
                    headers = mapOf(
                        "Authorization" to "Bearer $apiKey",
                        "HTTP-Referer" to "https://chronostage.aistudio.local",
                        "X-Title" to "ChronoStage AI"
                    )
                )
                val text = parseOpenAiStyleText(raw)
                AiTextResponse(text, provider, modelId, fallbackUsed)
            }

            AiProviderId.ANTHROPIC_CLAUDE -> {
                val apiKey = requireApiKey(provider)
                val baseUrl = settings.baseUrl.ifBlank { provider.defaultBaseUrl }.trimEnd('/')
                val url = "$baseUrl/messages"
                val payload = buildAnthropicClaudePayload(modelId, request)
                val raw = executeJsonPost(
                    url = url,
                    jsonBody = payload,
                    headers = mapOf(
                        "x-api-key" to apiKey,
                        "anthropic-version" to "2023-06-01"
                    )
                )
                val text = parseAnthropicText(raw)
                AiTextResponse(text, provider, modelId, fallbackUsed)
            }

            AiProviderId.OPENAI -> {
                val apiKey = requireApiKey(provider)
                val baseUrl = settings.baseUrl.ifBlank { provider.defaultBaseUrl }.trimEnd('/')
                val url = "$baseUrl/chat/completions"
                val payload = buildOpenAiCompatiblePayload(modelId, request)
                val raw = executeJsonPost(
                    url = url,
                    jsonBody = payload,
                    headers = mapOf("Authorization" to "Bearer $apiKey")
                )
                val text = parseOpenAiStyleText(raw)
                AiTextResponse(text, provider, modelId, fallbackUsed)
            }

            AiProviderId.LOCAL_OPENAI_COMPAT -> {
                val baseUrl = validateLocalOrHttpsEndpoint(
                    settings.baseUrl.ifBlank { provider.defaultBaseUrl }
                ).trimEnd('/')
                val url = "$baseUrl/chat/completions"
                val payload = buildLocalOpenAiCompatiblePayload(modelId, request)
                val headers = buildMap {
                    val optionalKey = keyVault.getApiKey(provider)
                    if (!optionalKey.isNullOrBlank()) {
                        put("Authorization", "Bearer $optionalKey")
                    }
                }
                val raw = executeJsonPost(url = url, jsonBody = payload, headers = headers)
                val text = parseOpenAiStyleText(raw)
                AiTextResponse(text, provider, modelId, fallbackUsed)
            }
        }
    }

    private suspend fun executeImageProvider(
        provider: AiProviderId,
        config: OmniAiConfig,
        request: AiImageRequest,
        fallbackUsed: Boolean
    ): AiImageResponse {
        if (!provider.supportsImage) {
            throw IllegalStateException("${provider.displayName} does not support image generation. Select Firebase Studio, Direct Gemini, OpenAI, or OpenRouter for images.")
        }
        val settings = config.settingsFor(provider)
        val modelId = settings.imageModel.ifBlank { provider.defaultImageModel.orEmpty() }

        return when (provider) {
            AiProviderId.FIREBASE_STUDIO -> {
                val resp = firebaseImageModel.generateContent(request.prompt)
                val bitmap = resp.candidates.firstOrNull()
                    ?.content?.parts?.filterIsInstance<ImagePart>()
                    ?.firstOrNull()?.image
                    ?: throw IllegalStateException("Firebase AI Logic returned no image.")
                AiImageResponse(bitmap, provider, modelId, fallbackUsed)
            }

            AiProviderId.DIRECT_GEMINI -> {
                val apiKey = requireApiKey(provider)
                val baseUrl = settings.baseUrl.ifBlank { provider.defaultBaseUrl }.trimEnd('/')
                val url = "$baseUrl/models/$modelId:generateContent"
                val payload = buildDirectGeminiImagePayload(request)
                val raw = executeJsonPost(
                    url = url,
                    jsonBody = payload,
                    headers = mapOf("x-goog-api-key" to apiKey)
                )
                val bitmap = parseDirectGeminiImage(raw)
                AiImageResponse(bitmap, provider, modelId, fallbackUsed)
            }

            AiProviderId.OPENAI -> {
                val apiKey = requireApiKey(provider)
                val baseUrl = settings.baseUrl.ifBlank { provider.defaultBaseUrl }.trimEnd('/')
                val url = "$baseUrl/images/generations"
                val payload = buildOpenAiImagePayload(modelId, request)
                val raw = executeJsonPost(
                    url = url,
                    jsonBody = payload,
                    headers = mapOf("Authorization" to "Bearer $apiKey")
                )
                val bitmap = parseOpenAiImage(raw)
                AiImageResponse(bitmap, provider, modelId, fallbackUsed)
            }

            AiProviderId.OPENROUTER -> {
                val apiKey = requireApiKey(provider)
                val baseUrl = settings.baseUrl.ifBlank { provider.defaultBaseUrl }.trimEnd('/')
                val url = "$baseUrl/chat/completions"
                val payload = buildOpenRouterImagePayload(modelId, request)
                val raw = executeJsonPost(
                    url = url,
                    jsonBody = payload,
                    headers = mapOf(
                        "Authorization" to "Bearer $apiKey",
                        "HTTP-Referer" to "https://chronostage.aistudio.local",
                        "X-Title" to "ChronoStage AI"
                    )
                )
                val bitmap = parseOpenRouterImage(raw)
                AiImageResponse(bitmap, provider, modelId, fallbackUsed)
            }

            else -> throw IllegalStateException("${provider.displayName} does not support image generation.")
        }
    }

    private fun requireApiKey(provider: AiProviderId): String {
        return keyVault.getApiKey(provider)?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("API key required for ${provider.displayName}. Open AI Hub to add your key.")
    }

    private suspend fun executeJsonPost(
        url: String,
        jsonBody: JSONObject,
        headers: Map<String, String>
    ): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
        headers.forEach { (k, v) -> builder.header(k, v) }

        httpClient.newCall(builder.build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val snippet = body.take(240).replace("\n", " ")
                throw IllegalStateException("HTTP ${resp.code} from AI provider: $snippet")
            }
            body
        }
    }

    private fun parseDirectGeminiText(rawJson: String): String {
        val root = JSONObject(rawJson)
        val candidates = root.optJSONArray("candidates")
            ?: throw IllegalStateException("Direct Gemini returned no candidates.")
        val first = candidates.optJSONObject(0)
        val parts = first?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw IllegalStateException("Direct Gemini returned no content parts.")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val partText = parts.optJSONObject(i)?.optString("text").orEmpty()
            sb.append(partText)
        }
        return sb.toString().trim().ifEmpty {
            throw IllegalStateException("Direct Gemini returned empty text.")
        }
    }

    private fun parseOpenAiStyleText(rawJson: String): String {
        val root = JSONObject(rawJson)
        val choices = root.optJSONArray("choices")
            ?: throw IllegalStateException("Provider returned no choices.")
        val message = choices.optJSONObject(0)?.optJSONObject("message")
        val content = message?.optString("content").orEmpty().trim()
        if (content.isEmpty()) {
            throw IllegalStateException("Provider returned empty message content.")
        }
        return content
    }

    private fun parseAnthropicText(rawJson: String): String {
        val root = JSONObject(rawJson)
        val contentArr = root.optJSONArray("content")
            ?: throw IllegalStateException("Anthropic returned no content blocks.")
        val sb = StringBuilder()
        for (i in 0 until contentArr.length()) {
            val block = contentArr.optJSONObject(i)
            if (block?.optString("type") == "text") {
                sb.append(block.optString("text"))
            }
        }
        return sb.toString().trim().ifEmpty {
            throw IllegalStateException("Anthropic returned empty text.")
        }
    }

    private fun parseDirectGeminiImage(rawJson: String): Bitmap {
        val root = JSONObject(rawJson)
        val parts = root.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?: throw IllegalStateException("Direct Gemini returned no image parts.")
        for (i in 0 until parts.length()) {
            val inlineData = parts.optJSONObject(i)?.optJSONObject("inlineData")
                ?: parts.optJSONObject(i)?.optJSONObject("inline_data")
            val b64 = inlineData?.optString("data").orEmpty()
            if (b64.isNotBlank()) {
                return decodeBitmapFromBase64(b64)
            }
        }
        throw IllegalStateException("Direct Gemini response contained no inline image data.")
    }

    private fun parseOpenAiImage(rawJson: String): Bitmap {
        val root = JSONObject(rawJson)
        val data0 = root.optJSONArray("data")?.optJSONObject(0)
            ?: throw IllegalStateException("OpenAI image response had no data array.")
        val b64 = data0.optString("b64_json").orEmpty()
        if (b64.isNotBlank()) {
            return decodeBitmapFromBase64(b64)
        }
        throw IllegalStateException("OpenAI response did not include b64_json bitmap payload.")
    }

    private fun parseOpenRouterImage(rawJson: String): Bitmap {
        val root = JSONObject(rawJson)
        val msg = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
        val images = msg?.optJSONArray("images")
        val dataUrl = images?.optJSONObject(0)
            ?.optJSONObject("image_url")
            ?.optString("url")
            .orEmpty()
        if (dataUrl.startsWith("data:image/")) {
            val b64 = dataUrl.substringAfter("base64,", "")
            if (b64.isNotBlank()) {
                return decodeBitmapFromBase64(b64)
            }
        }
        throw IllegalStateException("OpenRouter response contained no inline image data URL.")
    }

    private fun decodeBitmapFromBase64(b64: String): Bitmap {
        val bytes = PureBase64.decodeStandard(b64)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("Failed to decode bitmap from AI image bytes.")
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultRouterHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        fun validateLocalOrHttpsEndpoint(rawUrl: String): String {
            val trimmed = rawUrl.trim()
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase()
            require(scheme == "https" || scheme == "http") {
                "Local/Custom endpoint must start with http:// or https://"
            }
            require(!uri.host.isNullOrBlank()) { "Endpoint URL must specify a host." }
            return trimmed
        }

        /**
         * Strips optional Markdown JSON fences (` ```json ... ``` `) when external providers
         * wrap structured JSON output.
         */
        fun extractRawJsonObject(responseText: String): String {
            val trimmed = responseText.trim()
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
            val start = trimmed.indexOf('{')
            val end = trimmed.lastIndexOf('}')
            return if (start >= 0 && end > start) {
                trimmed.substring(start, end + 1)
            } else {
                trimmed
            }
        }

        fun buildDirectGeminiTextPayload(request: AiTextRequest): JSONObject {
            val root = JSONObject()
            if (request.systemInstruction.isNotBlank()) {
                root.put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", request.systemInstruction))
                    )
                )
            }
            root.put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", request.prompt)))
                )
            )
            val genConfig = JSONObject()
                .put("temperature", request.temperature)
                .put("maxOutputTokens", request.maxTokens)
            if (request.requireJson) {
                genConfig.put("responseMimeType", "application/json")
            }
            root.put("generationConfig", genConfig)
            return root
        }

        fun buildOpenRouterTextPayload(model: String, request: AiTextRequest): JSONObject {
            return buildOpenAiCompatiblePayload(model, request).apply {
                put("route", "fallback")
            }
        }

        fun buildAnthropicClaudePayload(model: String, request: AiTextRequest): JSONObject {
            val root = JSONObject()
            root.put("model", model)
            root.put("max_tokens", request.maxTokens)
            root.put("temperature", request.temperature)
            if (request.systemInstruction.isNotBlank()) {
                root.put("system", request.systemInstruction)
            }
            root.put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", request.prompt)
                )
            )
            return root
        }

        fun buildOpenAiCompatiblePayload(model: String, request: AiTextRequest): JSONObject {
            val messages = JSONArray()
            if (request.systemInstruction.isNotBlank()) {
                messages.put(
                    JSONObject()
                        .put("role", "system")
                        .put("content", request.systemInstruction)
                )
            }
            messages.put(
                JSONObject()
                    .put("role", "user")
                    .put("content", request.prompt)
            )
            return JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("temperature", request.temperature)
                put("max_tokens", request.maxTokens)
                if (request.requireJson) {
                    put("response_format", JSONObject().put("type", "json_object"))
                }
            }
        }

        fun buildLocalOpenAiCompatiblePayload(model: String, request: AiTextRequest): JSONObject {
            // Many local Ollama/LM Studio servers reject response_format when strict schema isn't set,
            // so we pass standard OpenAI chat messages + stream=false for reliable local JSON output.
            val messages = JSONArray()
            if (request.systemInstruction.isNotBlank()) {
                messages.put(
                    JSONObject()
                        .put("role", "system")
                        .put("content", request.systemInstruction)
                )
            }
            messages.put(
                JSONObject()
                    .put("role", "user")
                    .put("content", request.prompt)
            )
            return JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("temperature", request.temperature)
                put("max_tokens", request.maxTokens)
                put("stream", false)
            }
        }

        fun buildDirectGeminiImagePayload(request: AiImageRequest): JSONObject {
            return JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("parts", JSONArray().put(JSONObject().put("text", request.prompt)))
                    )
                )
                put(
                    "generationConfig",
                    JSONObject().put("responseModalities", JSONArray().put("IMAGE"))
                )
            }
        }

        fun buildOpenAiImagePayload(model: String, request: AiImageRequest): JSONObject {
            return JSONObject().apply {
                put("model", model)
                put("prompt", request.prompt)
                put("size", "1024x1024")
                put("response_format", "b64_json")
            }
        }

        fun buildOpenRouterImagePayload(model: String, request: AiImageRequest): JSONObject {
            return JSONObject().apply {
                put("model", model)
                put(
                    "messages",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", request.prompt)
                    )
                )
                put("modalities", JSONArray().put("image").put("text"))
            }
        }
    }
}
