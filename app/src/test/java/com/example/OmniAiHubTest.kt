package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiImageRequest
import com.example.ai.AiProviderId
import com.example.ai.AiTextRequest
import com.example.ai.MultiSourceContextRegistry
import com.example.ai.OmniAiRouter
import com.example.ai.OpenRouterPkceHelper
import com.example.ai.SecureAiKeyVault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OmniAiHubTest {

    @Test
    fun `OpenRouterPkceHelper computes RFC 7636 S256 challenge and loopback auth URL`() {
        // RFC 7636 Appendix B official test vector
        val rfcVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val expectedChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        assertEquals(expectedChallenge, OpenRouterPkceHelper.computeCodeChallengeS256(rfcVerifier))

        val verifier = OpenRouterPkceHelper.generateCodeVerifier()
        assertTrue(verifier.length >= 43)
        assertFalse(verifier.contains('='))
        assertFalse(verifier.contains('+'))
        assertFalse(verifier.contains('/'))

        val authUrl = OpenRouterPkceHelper.buildAuthorizationUrl(
            callbackPort = 41234,
            codeChallenge = expectedChallenge,
            state = "nonce_state_123"
        )
        assertTrue(authUrl.startsWith("https://openrouter.ai/auth?"))
        assertTrue(authUrl.contains("code_challenge_method=S256"))
        assertTrue(authUrl.contains("code_challenge=$expectedChallenge"))
        assertTrue(authUrl.contains("127.0.0.1%3A41234%2Fopenrouter%2Fcallback"))
        assertTrue(authUrl.contains("state=nonce_state_123"))

        val (code, state) = OpenRouterPkceHelper.parseCallbackRequestLine(
            "GET /openrouter/callback?code=or_code_999&state=nonce_state_123 HTTP/1.1"
        )
        assertEquals("or_code_999", code)
        assertEquals("nonce_state_123", state)
    }

    @Test
    fun `MultiSourceContextRegistry blocks SSRF URLs and allows safe public HTTPS URLs`() {
        val blockedUrls = listOf(
            "http://example.com/docs",
            "file:///etc/passwd",
            "javascript:alert(1)",
            "https://localhost/admin",
            "https://127.0.0.1/secret",
            "https://169.254.169.254/latest/meta-data/",
            "https://metadata.google.internal/computeMetadata/v1/",
            "https://10.0.0.4/internal",
            "https://172.16.5.2/private",
            "https://192.168.1.10/router",
            "https://[::1]/loopback",
            "https://printer.local/status",
            "https://user:pass@example.com/docs"
        )
        blockedUrls.forEach { url ->
            val res = MultiSourceContextRegistry.validateSafeHttpsUrl(url, resolveDns = false)
            assertFalse("Expected SSRF URL to be blocked: $url", res.isSafe)
        }

        val allowed = MultiSourceContextRegistry.validateSafeHttpsUrl(
            "https://developer.android.com/jetpack/compose",
            resolveDns = false
        )
        assertTrue(allowed.isSafe)
        assertEquals("https://developer.android.com/jetpack/compose", allowed.normalizedUrl)
    }

    @Test
    fun `MultiSourceContextRegistry toggles persists and trims assembled context to budget`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDir = File(context.filesDir, "test_ctx_${System.nanoTime()}").apply { mkdirs() }
        try {
            val registry = MultiSourceContextRegistry(tempDir)
            val noteA = registry.addNoteSource("Lore Alpha", "A".repeat(300))
            val noteB = registry.addNoteSource("Lore Beta", "B".repeat(300))

            // Toggle noteA off and confirm it disappears from context block
            registry.toggleSourceEnabled(noteA.id)
            val block = registry.assembleContextBlock(
                liveShowContext = "Live Stage: SNES Super FX",
                maxChars = 1000
            )
            assertTrue(block.contains("Live Stage: SNES Super FX"))
            assertTrue(block.contains("Lore Beta"))
            assertFalse(block.contains("Lore Alpha"))

            // Verify budget trimming works cleanly
            val trimmed = registry.assembleContextBlock(
                liveShowContext = "Live Stage: Game Boy DMG",
                maxChars = 180
            )
            assertTrue(trimmed.length <= 180)
            assertTrue(trimmed.endsWith("...[context trimmed to budget]"))

            // Verify persistence to filesDir/omni_ai_sources.json survives reload
            val reloaded = MultiSourceContextRegistry(tempDir)
            assertEquals(2, reloaded.sources.value.size)
            assertFalse(reloaded.sources.value.first { it.id == noteA.id }.enabled)
            assertTrue(reloaded.sources.value.first { it.id == noteB.id }.enabled)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `OmniAiRouter builds accurate payloads for all 5 external providers and images`() {
        val req = AiTextRequest(
            prompt = "Create a 4-scene retro show.",
            systemInstruction = "Return valid JSON only.",
            requireJson = true,
            temperature = 0.5,
            maxTokens = 1500
        )

        // 1. Direct Gemini
        val geminiPayload = OmniAiRouter.buildDirectGeminiTextPayload(req)
        assertEquals(
            "application/json",
            geminiPayload.getJSONObject("generationConfig").getString("responseMimeType")
        )
        assertEquals(
            "Return valid JSON only.",
            geminiPayload.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text")
        )

        // 2. OpenRouter
        val openRouterPayload = OmniAiRouter.buildOpenRouterTextPayload("google/gemini-2.5-flash", req)
        assertEquals("google/gemini-2.5-flash", openRouterPayload.getString("model"))
        assertEquals("fallback", openRouterPayload.getString("route"))
        assertEquals("json_object", openRouterPayload.getJSONObject("response_format").getString("type"))

        // 3. Anthropic Claude
        val claudePayload = OmniAiRouter.buildAnthropicClaudePayload("claude-sonnet-4-20250514", req)
        assertEquals("claude-sonnet-4-20250514", claudePayload.getString("model"))
        assertEquals("Return valid JSON only.", claudePayload.getString("system"))
        assertEquals(1500, claudePayload.getInt("max_tokens"))
        assertEquals("user", claudePayload.getJSONArray("messages").getJSONObject(0).getString("role"))

        // 4. OpenAI
        val openAiPayload = OmniAiRouter.buildOpenAiCompatiblePayload("gpt-4.1-mini", req)
        assertEquals("gpt-4.1-mini", openAiPayload.getString("model"))
        assertEquals(2, openAiPayload.getJSONArray("messages").length())
        assertEquals("json_object", openAiPayload.getJSONObject("response_format").getString("type"))

        // 5. Local OpenAI-Compatible (Ollama / LM Studio)
        val localPayload = OmniAiRouter.buildLocalOpenAiCompatiblePayload("llama3.2", req)
        assertEquals("llama3.2", localPayload.getString("model"))
        assertFalse(localPayload.getBoolean("stream"))
        assertFalse(localPayload.has("response_format"))

        // Image payload builders
        val imgReq = AiImageRequest("16-bit SNES neon tile texture")
        val geminiImg = OmniAiRouter.buildDirectGeminiImagePayload(imgReq)
        assertEquals(
            "IMAGE",
            geminiImg.getJSONObject("generationConfig").getJSONArray("responseModalities").getString(0)
        )
        val openAiImg = OmniAiRouter.buildOpenAiImagePayload("gpt-image-1", imgReq)
        assertEquals("b64_json", openAiImg.getString("response_format"))
        val openRouterImg = OmniAiRouter.buildOpenRouterImagePayload("google/gemini-2.5-flash-image-preview", imgReq)
        assertEquals("image", openRouterImg.getJSONArray("modalities").getString(0))

        // Markdown JSON fence extraction
        val extracted = OmniAiRouter.extractRawJsonObject("```json\n{\"showTitle\":\"Chrono\"}\n```")
        assertEquals("{\"showTitle\":\"Chrono\"}", extracted)
    }

    @Test
    fun `SecureAiKeyVault stores masks clears keys and selects configured fallback provider`() {
        val vault = SecureAiKeyVault(context = null)
        val router = OmniAiRouter(vault)

        assertNull(router.findFallbackTextProvider(vault.loadConfig()))

        vault.saveApiKey(AiProviderId.OPENROUTER, "sk-or-v1-secret12345678")
        assertEquals("sk-or-v1-secret12345678", vault.getApiKey(AiProviderId.OPENROUTER))
        assertTrue(vault.hasApiKey(AiProviderId.OPENROUTER))
        assertEquals("sk-o••••5678", vault.maskApiKey(vault.getApiKey(AiProviderId.OPENROUTER)))
        assertEquals(AiProviderId.OPENROUTER, router.findFallbackTextProvider(vault.loadConfig()))

        vault.clearApiKey(AiProviderId.OPENROUTER)
        assertNull(vault.getApiKey(AiProviderId.OPENROUTER))
        assertFalse(vault.hasApiKey(AiProviderId.OPENROUTER))
    }
}
