package com.example.ai

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed AndroidKeyStore (AES-256-GCM) vault for end-user AI API keys and
 * routing preferences, with an automatic in-memory fallback for JVM/Robolectric tests
 * where AndroidKeyStore is unavailable.
 */
class SecureAiKeyVault(
    context: Context? = null
) {
    private val prefs: SharedPreferences? = context?.applicationContext?.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val memoryKeyFallback = ConcurrentHashMap<AiProviderId, String>()
    private var memoryConfigJson: String? = null

    private val useAndroidKeyStore: Boolean by lazy {
        if (prefs == null) return@lazy false
        runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
            ks.load(null)
            getOrCreateSecretKey(ks) != null
        }.getOrDefault(false)
    }

    fun saveApiKey(provider: AiProviderId, rawKey: String) {
        val trimmed = rawKey.trim()
        if (trimmed.isEmpty()) {
            clearApiKey(provider)
            return
        }
        memoryKeyFallback[provider] = trimmed
        val localPrefs = prefs
        if (useAndroidKeyStore && localPrefs != null) {
            runCatching {
                val encrypted = encryptValue(trimmed)
                localPrefs.edit().putString(keyPrefName(provider), encrypted).apply()
            }
        }
    }

    fun getApiKey(provider: AiProviderId): String? {
        memoryKeyFallback[provider]?.let { if (it.isNotBlank()) return it }
        if (useAndroidKeyStore && prefs != null) {
            val stored = prefs.getString(keyPrefName(provider), null) ?: return null
            return runCatching {
                decryptValue(stored).also { plain ->
                    if (plain.isNotBlank()) {
                        memoryKeyFallback[provider] = plain
                    }
                }
            }.getOrNull()
        }
        return null
    }

    fun hasApiKey(provider: AiProviderId): Boolean {
        if (!provider.requiresApiKey) return true
        return !getApiKey(provider).isNullOrBlank()
    }

    fun clearApiKey(provider: AiProviderId) {
        memoryKeyFallback.remove(provider)
        prefs?.edit()?.remove(keyPrefName(provider))?.apply()
    }

    fun maskApiKey(rawKey: String?): String {
        if (rawKey.isNullOrBlank()) return ""
        val trimmed = rawKey.trim()
        return if (trimmed.length <= 8) {
            "••••${trimmed.takeLast(2)}"
        } else {
            "${trimmed.take(4)}••••${trimmed.takeLast(4)}"
        }
    }

    fun loadConfig(): OmniAiConfig {
        val rawJson = prefs?.getString(CONFIG_PREF_KEY, null) ?: memoryConfigJson
        val baseConfig = if (rawJson.isNullOrBlank()) {
            OmniAiConfig()
        } else {
            parseConfigJson(rawJson)
        }
        val hydratedProviders = AiProviderId.entries.associateWith { provider ->
            val current = baseConfig.settingsFor(provider)
            val key = getApiKey(provider)
            current.copy(
                hasApiKey = !provider.requiresApiKey || !key.isNullOrBlank(),
                maskedKeyPreview = maskApiKey(key)
            )
        }
        return baseConfig.copy(providers = hydratedProviders)
    }

    fun saveConfig(config: OmniAiConfig) {
        val json = serializeConfigJson(config)
        memoryConfigJson = json
        prefs?.edit()?.putString(CONFIG_PREF_KEY, json)?.apply()
    }

    private fun serializeConfigJson(config: OmniAiConfig): String {
        val root = JSONObject()
        root.put("activeTextProvider", config.activeTextProvider.id)
        root.put("activeImageProvider", config.activeImageProvider.id)
        root.put("enableFirebaseFallback", config.enableFirebaseFallback)
        val providersObj = JSONObject()
        config.providers.forEach { (id, settings) ->
            val pObj = JSONObject()
            pObj.put("textModel", settings.textModel)
            pObj.put("imageModel", settings.imageModel)
            pObj.put("baseUrl", settings.baseUrl)
            providersObj.put(id.id, pObj)
        }
        root.put("providers", providersObj)
        return root.toString()
    }

    private fun parseConfigJson(raw: String): OmniAiConfig {
        return runCatching {
            val root = JSONObject(raw)
            val activeText = AiProviderId.fromId(root.optString("activeTextProvider"))
            val activeImage = AiProviderId.fromId(root.optString("activeImageProvider"))
            val fallback = root.optBoolean("enableFirebaseFallback", true)
            val providersObj = root.optJSONObject("providers")
            val map = AiProviderId.entries.associateWith { provider ->
                val pObj = providersObj?.optJSONObject(provider.id)
                ProviderSettings(
                    provider = provider,
                    textModel = pObj?.optString("textModel", provider.defaultTextModel)
                        ?.ifBlank { provider.defaultTextModel } ?: provider.defaultTextModel,
                    imageModel = pObj?.optString("imageModel", provider.defaultImageModel.orEmpty())
                        ?: provider.defaultImageModel.orEmpty(),
                    baseUrl = pObj?.optString("baseUrl", provider.defaultBaseUrl)
                        ?.ifBlank { provider.defaultBaseUrl } ?: provider.defaultBaseUrl
                )
            }
            OmniAiConfig(
                activeTextProvider = activeText,
                activeImageProvider = activeImage,
                enableFirebaseFallback = fallback,
                providers = map
            )
        }.getOrElse { OmniAiConfig() }
    }

    private fun getOrCreateSecretKey(keyStore: KeyStore): SecretKey? {
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encryptValue(plainText: String): String {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val secretKey = getOrCreateSecretKey(ks)
            ?: throw IllegalStateException("AndroidKeyStore secret key unavailable")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return "${PureBase64.encodeStandard(iv)}:${PureBase64.encodeStandard(cipherText)}"
    }

    private fun decryptValue(payload: String): String {
        val parts = payload.split(':')
        require(parts.size == 2) { "Malformed encrypted vault entry." }
        val iv = PureBase64.decodeStandard(parts[0])
        val cipherBytes = PureBase64.decodeStandard(parts[1])
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val secretKey = getOrCreateSecretKey(ks)
            ?: throw IllegalStateException("AndroidKeyStore secret key unavailable")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, StandardCharsets.UTF_8)
    }

    private fun keyPrefName(provider: AiProviderId): String = "enc_key_${provider.id}"

    companion object {
        private const val PREFS_NAME = "omni_ai_secure_vault"
        private const val CONFIG_PREF_KEY = "omni_ai_routing_config"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "chronostage_omni_ai_aes_gcm_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}
