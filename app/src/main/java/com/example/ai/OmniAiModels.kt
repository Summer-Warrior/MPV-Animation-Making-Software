package com.example.ai

import android.graphics.Bitmap

enum class AiProviderId(
    val id: String,
    val displayName: String,
    val shortBadge: String,
    val defaultTextModel: String,
    val defaultImageModel: String?,
    val supportsText: Boolean,
    val supportsImage: Boolean,
    val requiresApiKey: Boolean,
    val defaultBaseUrl: String,
    val description: String
) {
    FIREBASE_STUDIO(
        id = "FIREBASE_STUDIO",
        displayName = "Firebase AI Logic (Studio)",
        shortBadge = "Firebase",
        defaultTextModel = "gemini-3.8-flash",
        defaultImageModel = "gemini-nano-banana-2.1",
        supportsText = true,
        supportsImage = true,
        requiresApiKey = false,
        defaultBaseUrl = "",
        description = "Zero-key Gemini 3.8 Flash & Nano Banana 2.1 via Firebase App Check."
    ),
    OPENROUTER(
        id = "OPENROUTER",
        displayName = "OpenRouter (Multi-Model)",
        shortBadge = "OpenRouter",
        defaultTextModel = "google/gemini-2.5-flash",
        defaultImageModel = "google/gemini-2.5-flash-image-preview",
        supportsText = true,
        supportsImage = true,
        requiresApiKey = true,
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        description = "Unified gateway for 200+ models with 1-tap PKCE OAuth or API key."
    ),
    DIRECT_GEMINI(
        id = "DIRECT_GEMINI",
        displayName = "Direct Gemini API",
        shortBadge = "Gemini API",
        defaultTextModel = "gemini-2.5-flash",
        defaultImageModel = "gemini-2.5-flash-image-preview",
        supportsText = true,
        supportsImage = true,
        requiresApiKey = true,
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        description = "Direct Google AI Studio REST endpoint using your personal API key."
    ),
    ANTHROPIC_CLAUDE(
        id = "ANTHROPIC_CLAUDE",
        displayName = "Anthropic Claude",
        shortBadge = "Claude",
        defaultTextModel = "claude-sonnet-4-20250514",
        defaultImageModel = null,
        supportsText = true,
        supportsImage = false,
        requiresApiKey = true,
        defaultBaseUrl = "https://api.anthropic.com/v1",
        description = "High-precision show scripting and technical documentation synthesis."
    ),
    OPENAI(
        id = "OPENAI",
        displayName = "OpenAI API",
        shortBadge = "OpenAI",
        defaultTextModel = "gpt-4.1-mini",
        defaultImageModel = "gpt-image-1",
        supportsText = true,
        supportsImage = true,
        requiresApiKey = true,
        defaultBaseUrl = "https://api.openai.com/v1",
        description = "GPT show scripting and GPT Image texture/sprite synthesis."
    ),
    LOCAL_OPENAI_COMPAT(
        id = "LOCAL_OPENAI_COMPAT",
        displayName = "Local OpenAI-Compat (Ollama / LM Studio)",
        shortBadge = "Local LLM",
        defaultTextModel = "llama3.2",
        defaultImageModel = null,
        supportsText = true,
        supportsImage = false,
        requiresApiKey = false,
        defaultBaseUrl = "http://10.0.2.2:11434/v1",
        description = "Self-hosted Ollama, LM Studio, or vLLM endpoint on LAN or localhost."
    );

    companion object {
        fun fromId(id: String?): AiProviderId {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: FIREBASE_STUDIO
        }
    }
}

data class ProviderSettings(
    val provider: AiProviderId,
    val textModel: String = provider.defaultTextModel,
    val imageModel: String = provider.defaultImageModel.orEmpty(),
    val baseUrl: String = provider.defaultBaseUrl,
    val hasApiKey: Boolean = !provider.requiresApiKey,
    val maskedKeyPreview: String = ""
)

data class OmniAiConfig(
    val activeTextProvider: AiProviderId = AiProviderId.FIREBASE_STUDIO,
    val activeImageProvider: AiProviderId = AiProviderId.FIREBASE_STUDIO,
    val enableFirebaseFallback: Boolean = true,
    val providers: Map<AiProviderId, ProviderSettings> = AiProviderId.entries.associateWith {
        ProviderSettings(provider = it)
    }
) {
    fun settingsFor(provider: AiProviderId): ProviderSettings {
        return providers[provider] ?: ProviderSettings(provider = provider)
    }
}

enum class ContextSourceType(val label: String) {
    DOCUMENT_FILE("Document File"),
    HTTPS_URL("HTTPS Docs URL"),
    NOTEBOOK_NOTE("Notebook / Lore Note"),
    LIVE_SHOW_CONTEXT("Live Show Context");

    companion object {
        fun fromName(name: String?): ContextSourceType {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: NOTEBOOK_NOTE
        }
    }
}

data class ContextSourceItem(
    val id: String,
    val title: String,
    val type: ContextSourceType,
    val origin: String,
    val content: String,
    val enabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

data class AiTextRequest(
    val prompt: String,
    val systemInstruction: String = "",
    val requireJson: Boolean = false,
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048
)

data class AiTextResponse(
    val text: String,
    val providerUsed: AiProviderId,
    val modelUsed: String,
    val fallbackUsed: Boolean = false
)

data class AiImageRequest(
    val prompt: String
)

data class AiImageResponse(
    val bitmap: Bitmap,
    val providerUsed: AiProviderId,
    val modelUsed: String,
    val fallbackUsed: Boolean = false
)

/**
 * Pure Kotlin RFC 4648 Base64 & Base64URL codec that works identically across
 * Android API 24+, Robolectric, and plain JVM unit tests without platform stubs.
 */
internal object PureBase64 {
    private const val STD_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun encodeUrlNoPad(bytes: ByteArray): String = encode(bytes, URL_ALPHABET, pad = false)

    fun encodeStandard(bytes: ByteArray): String = encode(bytes, STD_ALPHABET, pad = true)

    fun decodeStandard(input: String): ByteArray {
        val clean = input.trim().replace("\n", "").replace("\r", "")
        if (clean.isEmpty()) return ByteArray(0)
        val lookup = IntArray(128) { -1 }
        STD_ALPHABET.forEachIndexed { idx, ch -> lookup[ch.code] = idx }
        val out = ArrayList<Byte>(clean.length * 3 / 4)
        var buffer = 0
        var bitsCollected = 0
        for (ch in clean) {
            if (ch == '=') break
            val code = ch.code
            if (code >= 128 || lookup[code] < 0) continue
            buffer = (buffer shl 6) or lookup[code]
            bitsCollected += 6
            if (bitsCollected >= 8) {
                bitsCollected -= 8
                out.add(((buffer shr bitsCollected) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }

    private fun encode(bytes: ByteArray, alphabet: String, pad: Boolean): String {
        if (bytes.isEmpty()) return ""
        val sb = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val hasB1 = i + 1 < bytes.size
            val hasB2 = i + 2 < bytes.size
            val b1 = if (hasB1) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (hasB2) bytes[i + 2].toInt() and 0xFF else 0

            val triple = (b0 shl 16) or (b1 shl 8) or b2
            sb.append(alphabet[(triple shr 18) and 0x3F])
            sb.append(alphabet[(triple shr 12) and 0x3F])
            if (hasB1) {
                sb.append(alphabet[(triple shr 6) and 0x3F])
            } else if (pad) {
                sb.append('=')
            }
            if (hasB2) {
                sb.append(alphabet[triple and 0x3F])
            } else if (pad) {
                sb.append('=')
            }
            i += 3
        }
        return sb.toString()
    }
}
