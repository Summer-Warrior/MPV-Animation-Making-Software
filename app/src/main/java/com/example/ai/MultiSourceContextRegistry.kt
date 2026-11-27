package com.example.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit

data class UrlValidationResult(
    val isSafe: Boolean,
    val normalizedUrl: String = "",
    val reason: String = ""
)

/**
 * Manages persisted Multi-Source Context items (`filesDir/omni_ai_sources.json`):
 * - SAF `.md`, `.txt`, `.json` documents
 * - SSRF-protected `https://` documentation URLs
 * - Pasted Notebook / lore / script notes
 * - Dynamic live show context injection
 */
class MultiSourceContextRegistry(
    private val baseFilesDir: File,
    private val httpClient: OkHttpClient = defaultSafeHttpClient()
) {
    constructor(context: Context, httpClient: OkHttpClient = defaultSafeHttpClient()) : this(
        baseFilesDir = context.filesDir,
        httpClient = httpClient
    )

    private val storageFile: File
        get() = File(baseFilesDir, SOURCES_FILENAME)

    private val _sources = MutableStateFlow<List<ContextSourceItem>>(loadFromDisk())
    val sources: StateFlow<List<ContextSourceItem>> = _sources.asStateFlow()

    fun addNoteSource(title: String, content: String): ContextSourceItem {
        val cleanTitle = title.trim().ifBlank { "Notebook Note" }
        val cleanContent = content.trim().take(MAX_SOURCE_CHARS)
        require(cleanContent.isNotBlank()) { "Note content cannot be blank." }
        val item = ContextSourceItem(
            id = UUID.randomUUID().toString(),
            title = cleanTitle,
            type = ContextSourceType.NOTEBOOK_NOTE,
            origin = "Pasted Notebook / Lore Note",
            content = cleanContent,
            enabled = true
        )
        updateAndPersist { listOf(item) + it }
        return item
    }

    suspend fun importSafDocument(context: Context, uri: Uri): ContextSourceItem =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val displayName = queryDisplayName(context, uri) ?: "document.txt"
            val ext = displayName.substringAfterLast('.', "").lowercase()
            require(ext in ALLOWED_DOC_EXTENSIONS) {
                "Only .md, .txt, and .json documents are supported."
            }
            val rawBytes = resolver.openInputStream(uri)?.use { stream ->
                stream.readNBytesCompat(MAX_FETCH_BYTES)
            } ?: throw IllegalStateException("Unable to read selected document.")

            val text = String(rawBytes, StandardCharsets.UTF_8).trim().take(MAX_SOURCE_CHARS)
            require(text.isNotBlank()) { "Selected document is empty." }

            val item = ContextSourceItem(
                id = UUID.randomUUID().toString(),
                title = displayName,
                type = ContextSourceType.DOCUMENT_FILE,
                origin = "SAF: $displayName",
                content = text,
                enabled = true
            )
            updateAndPersist { listOf(item) + it }
            item
        }

    suspend fun addAndFetchHttpsSource(
        rawUrl: String,
        titleOverride: String? = null
    ): ContextSourceItem = withContext(Dispatchers.IO) {
        val validation = validateSafeHttpsUrl(rawUrl, resolveDns = true)
        if (!validation.isSafe) {
            throw SecurityException(validation.reason)
        }

        val request = Request.Builder()
            .url(validation.normalizedUrl)
            .header("Accept", "text/plain, text/markdown, application/json, text/html;q=0.8")
            .header("User-Agent", "ChronoStageAI/1.0")
            .get()
            .build()

        val fetchedText = httpClient.newCall(request).execute().use { response ->
            if (response.isRedirect) {
                throw SecurityException("Blocked redirect from external URL source for safety.")
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to fetch URL (HTTP ${response.code}).")
            }
            val bodyStream = response.body?.byteStream()
                ?: throw IllegalStateException("Empty response body from URL.")
            val bytes = bodyStream.readNBytesCompat(MAX_FETCH_BYTES)
            stripHtmlTagsIfNeeded(String(bytes, StandardCharsets.UTF_8)).trim().take(MAX_SOURCE_CHARS)
        }

        require(fetchedText.isNotBlank()) { "No readable text found at URL." }

        val host = runCatching { URI(validation.normalizedUrl).host }.getOrNull() ?: "HTTPS Source"
        val item = ContextSourceItem(
            id = UUID.randomUUID().toString(),
            title = titleOverride?.trim()?.ifBlank { host } ?: host,
            type = ContextSourceType.HTTPS_URL,
            origin = validation.normalizedUrl,
            content = fetchedText,
            enabled = true
        )
        updateAndPersist { listOf(item) + it }
        item
    }

    fun toggleSourceEnabled(id: String) {
        updateAndPersist { list ->
            list.map { item ->
                if (item.id == id) item.copy(enabled = !item.enabled) else item
            }
        }
    }

    fun setSourceEnabled(id: String, enabled: Boolean) {
        updateAndPersist { list ->
            list.map { item ->
                if (item.id == id) item.copy(enabled = enabled) else item
            }
        }
    }

    fun removeSource(id: String) {
        updateAndPersist { list -> list.filterNot { it.id == id } }
    }

    fun reloadFromDisk() {
        _sources.value = loadFromDisk()
    }

    /**
     * Assembles all enabled sources plus optional live show context into a clean,
     * token-budgeted context block bounded strictly to [maxChars].
     */
    fun assembleContextBlock(
        liveShowContext: String? = null,
        maxChars: Int = DEFAULT_CONTEXT_BUDGET_CHARS
    ): String {
        val budget = maxChars.coerceAtLeast(120)
        val enabledItems = _sources.value.filter { it.enabled && it.content.isNotBlank() }
        val hasLive = !liveShowContext.isNullOrBlank()
        if (enabledItems.isEmpty() && !hasLive) return ""

        val sb = StringBuilder()
        sb.appendLine("### MULTI-SOURCE CONTEXT HUB")

        if (hasLive) {
            sb.appendLine("[LIVE SHOW CONTEXT]")
            sb.appendLine(liveShowContext!!.trim())
            sb.appendLine()
        }

        for (item in enabledItems) {
            if (sb.length >= budget) break
            sb.appendLine("[${item.type.label}: ${item.title}]")
            sb.appendLine(item.content.trim())
            sb.appendLine()
        }

        val raw = sb.toString().trim()
        return if (raw.length <= budget) {
            raw
        } else {
            val suffix = "\n...[context trimmed to budget]"
            val keep = (budget - suffix.length).coerceAtLeast(0)
            raw.take(keep).trimEnd() + suffix
        }
    }

    private fun updateAndPersist(transform: (List<ContextSourceItem>) -> List<ContextSourceItem>) {
        val updated = transform(_sources.value)
        _sources.value = updated
        persistToDisk(updated)
    }

    private fun persistToDisk(items: List<ContextSourceItem>) {
        runCatching {
            if (!baseFilesDir.exists()) {
                baseFilesDir.mkdirs()
            }
            val arr = JSONArray()
            items.forEach { item ->
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("type", item.type.name)
                    put("origin", item.origin)
                    put("content", item.content)
                    put("enabled", item.enabled)
                    put("updatedAt", item.updatedAt)
                }
                arr.put(obj)
            }
            storageFile.writeText(arr.toString(), StandardCharsets.UTF_8)
        }
    }

    private fun loadFromDisk(): List<ContextSourceItem> {
        val file = storageFile
        if (!file.exists() || !file.isFile) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText(StandardCharsets.UTF_8))
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        ContextSourceItem(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            title = obj.optString("title", "Context Source"),
                            type = ContextSourceType.fromName(obj.optString("type")),
                            origin = obj.optString("origin", ""),
                            content = obj.optString("content", ""),
                            enabled = obj.optBoolean("enabled", true),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun stripHtmlTagsIfNeeded(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("<")) return trimmed
        return trimmed
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun java.io.InputStream.readNBytesCompat(maxBytes: Int): ByteArray {
        val buffer = ByteArray(4096)
        val out = java.io.ByteArrayOutputStream()
        var total = 0
        while (total < maxBytes) {
            val toRead = minOf(buffer.size, maxBytes - total)
            val count = read(buffer, 0, toRead)
            if (count <= 0) break
            out.write(buffer, 0, count)
            total += count
        }
        return out.toByteArray()
    }

    companion object {
        const val SOURCES_FILENAME = "omni_ai_sources.json"
        const val DEFAULT_CONTEXT_BUDGET_CHARS = 6_000
        private const val MAX_SOURCE_CHARS = 24_000
        private const val MAX_FETCH_BYTES = 256 * 1024
        private val ALLOWED_DOC_EXTENSIONS = setOf("md", "txt", "json")

        fun defaultSafeHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        /**
         * Strict SSRF guard: requires `https://` scheme, rejects embedded userinfo,
         * blocks localhost, `.local`, `.internal`, cloud metadata hosts, private RFC1918,
         * CGNAT, loopback, link-local, and IPv6 ULA/loopback/link-local addresses.
         */
        fun validateSafeHttpsUrl(
            rawUrl: String,
            resolveDns: Boolean = false
        ): UrlValidationResult {
            val trimmed = rawUrl.trim()
            if (trimmed.isBlank()) {
                return UrlValidationResult(false, reason = "URL cannot be empty.")
            }
            val uri = runCatching { URI(trimmed) }.getOrNull()
                ?: return UrlValidationResult(false, reason = "Malformed URL syntax.")

            val scheme = uri.scheme?.lowercase()
            if (scheme != "https") {
                return UrlValidationResult(false, reason = "Only https:// URLs are permitted.")
            }
            if (!uri.rawUserInfo.isNullOrBlank()) {
                return UrlValidationResult(false, reason = "Embedded credentials in URL are not allowed.")
            }

            val host = uri.host?.lowercase()?.trimEnd('.')
                ?: return UrlValidationResult(false, reason = "URL must contain a valid hostname.")

            val bareHost = host.removePrefix("[").removeSuffix("]")
            if (isBlockedHostname(bareHost)) {
                return UrlValidationResult(false, reason = "Host '$bareHost' is blocked for network safety.")
            }

            // Check literal IP addresses directly (IPv4 or IPv6)
            if (isIpLiteral(bareHost)) {
                val parsed = runCatching { InetAddress.getByName(bareHost) }.getOrNull()
                    ?: return UrlValidationResult(false, reason = "Invalid IP literal.")
                if (isDisallowedIpAddress(parsed)) {
                    return UrlValidationResult(false, reason = "Private, loopback, or link-local IP addresses are blocked.")
                }
            } else if (resolveDns) {
                val resolved = runCatching { InetAddress.getAllByName(bareHost) }.getOrNull()
                    ?: return UrlValidationResult(false, reason = "Unable to resolve hostname '$bareHost'.")
                if (resolved.any { isDisallowedIpAddress(it) }) {
                    return UrlValidationResult(false, reason = "Hostname resolves to a blocked internal IP address.")
                }
            }

            return UrlValidationResult(isSafe = true, normalizedUrl = uri.toASCIIString())
        }

        private fun isBlockedHostname(host: String): Boolean {
            if (host.isBlank()) return true
            if (host == "localhost" || host.endsWith(".localhost")) return true
            if (host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".arpa")) return true
            if (host == "metadata.google.internal" || host == "metadata" || host == "169.254.169.254") return true
            return false
        }

        private fun isIpLiteral(host: String): Boolean {
            if (host.contains(':')) return true // IPv6 literal
            val parts = host.split('.')
            return parts.size == 4 && parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }
        }

        fun isDisallowedIpAddress(address: InetAddress): Boolean {
            if (address.isAnyLocalAddress ||
                address.isLoopbackAddress ||
                address.isLinkLocalAddress ||
                address.isSiteLocalAddress ||
                address.isMulticastAddress
            ) {
                return true
            }
            val bytes = address.address
            if (address is Inet4Address && bytes.size == 4) {
                val b0 = bytes[0].toInt() and 0xFF
                val b1 = bytes[1].toInt() and 0xFF
                // 0.0.0.0/8, 10.0.0.0/8, 127.0.0.0/8
                if (b0 == 0 || b0 == 10 || b0 == 127) return true
                // 100.64.0.0/10 (CGNAT)
                if (b0 == 100 && (b1 in 64..127)) return true
                // 169.254.0.0/16 (Link-local / Cloud metadata)
                if (b0 == 169 && b1 == 254) return true
                // 172.16.0.0/12 (Private)
                if (b0 == 172 && (b1 in 16..31)) return true
                // 192.168.0.0/16 (Private)
                if (b0 == 192 && b1 == 168) return true
            }
            if (address is Inet6Address && bytes.size == 16) {
                val firstByte = bytes[0].toInt() and 0xFF
                // fc00::/7 Unique Local Address (ULA)
                if ((firstByte and 0xFE) == 0xFC) return true
                // fe80::/10 Link-Local
                if (firstByte == 0xFE && ((bytes[1].toInt() and 0xC0) == 0x80)) return true
            }
            return false
        }
    }
}
