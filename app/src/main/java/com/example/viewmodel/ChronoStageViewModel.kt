package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.AiImageRequest
import com.example.ai.AiProviderId
import com.example.ai.AiTextRequest
import com.example.ai.ContextSourceItem
import com.example.ai.MultiSourceContextRegistry
import com.example.ai.OmniAiConfig
import com.example.ai.OmniAiRouter
import com.example.ai.OpenRouterPkceHelper
import com.example.ai.SecureAiKeyVault
import com.example.data.ShowMarkdownExporter
import com.example.data.ShowRepository
import com.example.data.StarterShowPresets
import com.example.model.AnimationMotion
import com.example.model.CameraMove
import com.example.model.ConsoleHardwareCatalog
import com.example.model.ConsoleSpec
import com.example.model.Model3DType
import com.example.model.ParticleFxType
import com.example.model.ShowProject
import com.example.model.ShowScene
import com.example.model.SpriteActorType
import com.example.model.TexturePreset
import com.google.firebase.ai.type.Schema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "ChronoStageViewModel"

sealed interface DocToShowUiState {
    data object Idle : DocToShowUiState
    data object Generating : DocToShowUiState
    data class Success(val summary: String) : DocToShowUiState
    data class Error(val message: String) : DocToShowUiState
}

sealed interface DirectorStreamState {
    data object Idle : DirectorStreamState
    data object Loading : DirectorStreamState
    data class Streaming(val text: String) : DirectorStreamState
    data class Success(val text: String) : DirectorStreamState
    data class Error(val message: String) : DirectorStreamState
}

sealed interface TextureGenUiState {
    data object Idle : TextureGenUiState
    data object Loading : TextureGenUiState
    data class Success(val bitmap: Bitmap, val promptUsed: String) : TextureGenUiState
    data class Error(val message: String) : TextureGenUiState
}

class ChronoStageViewModel(
    private val repository: ShowRepository,
    private val keyVault: SecureAiKeyVault,
    private val contextRegistry: MultiSourceContextRegistry,
    private val omniAiRouter: OmniAiRouter
) : ViewModel() {

    // Structured JSON schema for Firebase AI Logic Gemini 3.8 Flash show generation
    private val showGenerationSchema by lazy {
        val sceneSchema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Short punchy scene title"),
                "documentationExcerpt" to Schema.string(description = "Key technical or lore fact from the documentation"),
                "narratorScript" to Schema.string(description = "Engaging online show host voiceover line for this scene"),
                "consoleId" to Schema.enumeration(
                    values = ConsoleHardwareCatalog.allConsoles.map { it.id },
                    description = "Target video game console or handheld hardware profile ID"
                ),
                "model3D" to Schema.enumeration(
                    values = Model3DType.entries.map { it.id },
                    description = "Primary 3D model mesh on stage"
                ),
                "modelMotion" to Schema.enumeration(
                    values = AnimationMotion.entries.map { it.id },
                    description = "3D keyframe animation cycle"
                ),
                "texturePreset" to Schema.enumeration(
                    values = TexturePreset.entries.map { it.id },
                    description = "Surface UV texture shader preset"
                ),
                "primarySprite" to Schema.enumeration(
                    values = SpriteActorType.entries.map { it.id },
                    description = "Primary 2D pixel sprite actor on stage"
                ),
                "secondarySprite" to Schema.enumeration(
                    values = SpriteActorType.entries.map { it.id },
                    description = "Secondary 2D pixel sprite actor on stage"
                ),
                "cameraMove" to Schema.enumeration(
                    values = CameraMove.entries.map { it.id },
                    description = "3D camera movement choreography"
                ),
                "particleFx" to Schema.enumeration(
                    values = ParticleFxType.entries.map { it.id },
                    description = "Stage particle effects"
                ),
                "durationSeconds" to Schema.integer(description = "Scene duration in seconds between 5 and 10")
            )
        )

        Schema.obj(
            properties = mapOf(
                "showTitle" to Schema.string(description = "Title of the animated online show"),
                "showSubtitle" to Schema.string(description = "Tagline summarizing console eras and topic"),
                "scenes" to Schema.array(
                    items = sceneSchema,
                    description = "3 to 5 sequential animated show scenes"
                )
            )
        )
    }

    val savedShows: StateFlow<List<ShowProject>> = repository.allShows
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StarterShowPresets.defaultShowProjects
        )

    val contextSources: StateFlow<List<ContextSourceItem>> = contextRegistry.sources

    private val _omniAiConfig = MutableStateFlow(keyVault.loadConfig())
    val omniAiConfig: StateFlow<OmniAiConfig> = _omniAiConfig.asStateFlow()

    private val _hubStatusMessage = MutableStateFlow<String?>(null)
    val hubStatusMessage: StateFlow<String?> = _hubStatusMessage.asStateFlow()

    private val _isHubBusy = MutableStateFlow(false)
    val isHubBusy: StateFlow<Boolean> = _isHubBusy.asStateFlow()

    private val _activeShow = MutableStateFlow(StarterShowPresets.defaultShowProjects.first())
    val activeShow: StateFlow<ShowProject> = _activeShow.asStateFlow()

    private val _selectedSceneIndex = MutableStateFlow(0)
    val selectedSceneIndex: StateFlow<Int> = _selectedSceneIndex.asStateFlow()

    private val _consoleOverrideId = MutableStateFlow<String?>(null)
    val consoleOverrideId: StateFlow<String?> = _consoleOverrideId.asStateFlow()

    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _autoAdvanceScenes = MutableStateFlow(true)
    val autoAdvanceScenes: StateFlow<Boolean> = _autoAdvanceScenes.asStateFlow()

    private val _sceneProgressFloat = MutableStateFlow(0f)
    val sceneProgressFloat: StateFlow<Float> = _sceneProgressFloat.asStateFlow()

    private val _wireframeOverlay = MutableStateFlow(false)
    val wireframeOverlay: StateFlow<Boolean> = _wireframeOverlay.asStateFlow()

    private val _crtAndLcdFilter = MutableStateFlow(true)
    val crtAndLcdFilter: StateFlow<Boolean> = _crtAndLcdFilter.asStateFlow()

    private val _isBroadcastFullscreen = MutableStateFlow(false)
    val isBroadcastFullscreen: StateFlow<Boolean> = _isBroadcastFullscreen.asStateFlow()

    private val _userYawOffset = MutableStateFlow(0f)
    val userYawOffset: StateFlow<Float> = _userYawOffset.asStateFlow()

    private val _userPitchOffset = MutableStateFlow(0f)
    val userPitchOffset: StateFlow<Float> = _userPitchOffset.asStateFlow()

    private val _documentationInput = MutableStateFlow(
        StarterShowPresets.documentationTemplates.first().documentationText
    )
    val documentationInput: StateFlow<String> = _documentationInput.asStateFlow()

    private val _preferredTargetConsoleId = MutableStateFlow<String?>(null)
    val preferredTargetConsoleId: StateFlow<String?> = _preferredTargetConsoleId.asStateFlow()

    private val _docToShowState = MutableStateFlow<DocToShowUiState>(DocToShowUiState.Idle)
    val docToShowState: StateFlow<DocToShowUiState> = _docToShowState.asStateFlow()

    private val _directorStreamState = MutableStateFlow<DirectorStreamState>(DirectorStreamState.Idle)
    val directorStreamState: StateFlow<DirectorStreamState> = _directorStreamState.asStateFlow()

    private val _texturePromptInput = MutableStateFlow(
        "Seamless glowing 16-bit cybernetic circuit board texture with neon cyan and magenta energy conduits, game asset UV map"
    )
    val texturePromptInput: StateFlow<String> = _texturePromptInput.asStateFlow()

    private val _textureGenState = MutableStateFlow<TextureGenUiState>(TextureGenUiState.Idle)
    val textureGenState: StateFlow<TextureGenUiState> = _textureGenState.asStateFlow()

    private val _aiCustomBitmap = MutableStateFlow<Bitmap?>(null)
    val aiCustomBitmap: StateFlow<Bitmap?> = _aiCustomBitmap.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            // Reload persisted AI custom texture for the initial show if present on disk
            loadPersistedTextureForShow(_activeShow.value.id)
        }
        // Automatic multi-scene show sequencer loop
        viewModelScope.launch {
            while (true) {
                delay(100L)
                if (_isPlaying.value && _autoAdvanceScenes.value) {
                    val scenes = _activeShow.value.scenes
                    if (scenes.isNotEmpty()) {
                        val idx = _selectedSceneIndex.value.coerceIn(0, scenes.lastIndex)
                        val durationSec = scenes[idx].durationSeconds.coerceAtLeast(4)
                        val step = 0.1f / durationSec.toFloat()
                        val nextProgress = _sceneProgressFloat.value + step
                        if (nextProgress >= 1f) {
                            _sceneProgressFloat.value = 0f
                            _selectedSceneIndex.value = (idx + 1) % scenes.size
                        } else {
                            _sceneProgressFloat.value = nextProgress
                        }
                    }
                }
            }
        }
    }

    private fun loadPersistedTextureForShow(showId: Long) {
        viewModelScope.launch {
            val loaded = repository.loadShowTexture(showId)
            _aiCustomBitmap.value = loaded
            if (loaded != null) {
                _textureGenState.value = TextureGenUiState.Success(loaded, "Loaded from saved show archive")
            } else {
                _textureGenState.value = TextureGenUiState.Idle
            }
        }
    }

    fun getCurrentScene(): ShowScene {
        val scenes = _activeShow.value.scenes
        if (scenes.isEmpty()) return StarterShowPresets.defaultShowProjects.first().scenes.first()
        val idx = _selectedSceneIndex.value.coerceIn(0, scenes.lastIndex)
        return scenes[idx]
    }

    fun getEffectiveConsoleSpec(scene: ShowScene): ConsoleSpec {
        val targetId = _consoleOverrideId.value ?: scene.consoleId
        return ConsoleHardwareCatalog.findById(targetId)
    }

    fun selectShowProject(project: ShowProject) {
        _activeShow.value = project
        _selectedSceneIndex.value = 0
        _sceneProgressFloat.value = 0f
        _documentationInput.value = project.sourceDocumentation
        loadPersistedTextureForShow(project.id)
    }

    fun selectScene(index: Int) {
        val scenes = _activeShow.value.scenes
        if (index in scenes.indices) {
            _selectedSceneIndex.value = index
            _sceneProgressFloat.value = 0f
        }
    }

    fun nextScene() {
        val scenes = _activeShow.value.scenes
        if (scenes.isNotEmpty()) {
            _selectedSceneIndex.value = (_selectedSceneIndex.value + 1) % scenes.size
            _sceneProgressFloat.value = 0f
        }
    }

    fun previousScene() {
        val scenes = _activeShow.value.scenes
        if (scenes.isNotEmpty()) {
            _selectedSceneIndex.value =
                (_selectedSceneIndex.value - 1 + scenes.size) % scenes.size
            _sceneProgressFloat.value = 0f
        }
    }

    fun togglePlayPause() {
        _isPlaying.value = !_isPlaying.value
    }

    fun toggleAutoAdvance() {
        _autoAdvanceScenes.value = !_autoAdvanceScenes.value
    }

    fun toggleWireframeOverlay() {
        _wireframeOverlay.value = !_wireframeOverlay.value
    }

    fun toggleCrtAndLcdFilter() {
        _crtAndLcdFilter.value = !_crtAndLcdFilter.value
    }

    fun setBroadcastFullscreen(fullscreen: Boolean) {
        _isBroadcastFullscreen.value = fullscreen
    }

    fun onCameraDrag(deltaYaw: Float, deltaPitch: Float) {
        _userYawOffset.value += deltaYaw
        _userPitchOffset.value = (_userPitchOffset.value + deltaPitch).coerceIn(-0.6f, 0.6f)
    }

    fun resetCameraOrbit() {
        _userYawOffset.value = 0f
        _userPitchOffset.value = 0f
    }

    fun setConsoleHardwareOverride(consoleId: String?) {
        _consoleOverrideId.value = consoleId
    }

    fun updateDocumentationInput(text: String) {
        _documentationInput.value = text
    }

    fun setPreferredTargetConsoleId(consoleId: String?) {
        _preferredTargetConsoleId.value = consoleId
    }

    fun updateTexturePrompt(prompt: String) {
        _texturePromptInput.value = prompt
    }

    fun updateCurrentSceneConfig(transform: (ShowScene) -> ShowScene) {
        val currentProject = _activeShow.value
        val idx = _selectedSceneIndex.value.coerceIn(0, currentProject.scenes.lastIndex)
        val updatedScenes = currentProject.scenes.toMutableList()
        updatedScenes[idx] = transform(updatedScenes[idx])
        _activeShow.value = currentProject.copy(scenes = updatedScenes)
    }

    fun saveActiveShowToLibrary() {
        viewModelScope.launch {
            val savedId = repository.saveShow(_activeShow.value, _aiCustomBitmap.value)
            _activeShow.value = _activeShow.value.copy(id = savedId)
        }
    }

    fun deleteSavedShow(id: Long) {
        viewModelScope.launch {
            repository.deleteShow(id)
            if (_activeShow.value.id == id) {
                _aiCustomBitmap.value = null
            }
        }
    }

    fun exportShowMarkdown(project: ShowProject = _activeShow.value): String {
        return ShowMarkdownExporter.exportShowToMarkdown(project)
    }

    // --- Multi-AI + Multi-Source Hub Controls ---

    fun selectActiveTextProvider(provider: AiProviderId) {
        val updated = _omniAiConfig.value.copy(activeTextProvider = provider)
        keyVault.saveConfig(updated)
        _omniAiConfig.value = keyVault.loadConfig()
        _hubStatusMessage.value = "Text & Show Scripting routed to ${provider.displayName}"
    }

    fun selectActiveImageProvider(provider: AiProviderId) {
        if (!provider.supportsImage) return
        val updated = _omniAiConfig.value.copy(activeImageProvider = provider)
        keyVault.saveConfig(updated)
        _omniAiConfig.value = keyVault.loadConfig()
        _hubStatusMessage.value = "Image & Texture Synthesis routed to ${provider.displayName}"
    }

    fun toggleFirebaseFallback(enabled: Boolean) {
        val updated = _omniAiConfig.value.copy(enableFirebaseFallback = enabled)
        keyVault.saveConfig(updated)
        _omniAiConfig.value = keyVault.loadConfig()
    }

    fun saveProviderKey(provider: AiProviderId, rawKey: String) {
        keyVault.saveApiKey(provider, rawKey)
        _omniAiConfig.value = keyVault.loadConfig()
        _hubStatusMessage.value = "Saved encrypted API key for ${provider.shortBadge}."
    }

    fun clearProviderKey(provider: AiProviderId) {
        keyVault.clearApiKey(provider)
        _omniAiConfig.value = keyVault.loadConfig()
        _hubStatusMessage.value = "Cleared API key for ${provider.shortBadge}."
    }

    fun updateProviderModelAndUrl(
        provider: AiProviderId,
        textModel: String,
        imageModel: String,
        baseUrl: String
    ) {
        val current = _omniAiConfig.value
        val existing = current.settingsFor(provider)
        val updatedSettings = existing.copy(
            textModel = textModel.trim().ifBlank { provider.defaultTextModel },
            imageModel = imageModel.trim().ifBlank { provider.defaultImageModel.orEmpty() },
            baseUrl = baseUrl.trim().ifBlank { provider.defaultBaseUrl }
        )
        val updated = current.copy(
            providers = current.providers + (provider to updatedSettings)
        )
        keyVault.saveConfig(updated)
        _omniAiConfig.value = keyVault.loadConfig()
        _hubStatusMessage.value = "Updated ${provider.shortBadge} model and endpoint settings."
    }

    fun startOpenRouterPkce(onLaunchUrl: (String) -> Unit) {
        viewModelScope.launch {
            _isHubBusy.value = true
            _hubStatusMessage.value = "Waiting for OpenRouter OAuth callback on 127.0.0.1..."
            try {
                val apiKey = OpenRouterPkceHelper.runLoopbackPkceFlow(
                    httpClient = OmniAiRouter.defaultRouterHttpClient(),
                    onLaunchBrowser = { session -> onLaunchUrl(session.authorizationUrl) }
                )
                keyVault.saveApiKey(AiProviderId.OPENROUTER, apiKey)
                val updated = _omniAiConfig.value.copy(activeTextProvider = AiProviderId.OPENROUTER)
                keyVault.saveConfig(updated)
                _omniAiConfig.value = keyVault.loadConfig()
                _hubStatusMessage.value = "Connected OpenRouter via PKCE OAuth!"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "OpenRouter PKCE flow failed", e)
                _hubStatusMessage.value = "OpenRouter PKCE error: ${e.localizedMessage ?: "Failed"}"
            } finally {
                _isHubBusy.value = false
            }
        }
    }

    fun importSafDocument(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isHubBusy.value = true
            try {
                val item = contextRegistry.importSafDocument(context, uri)
                _hubStatusMessage.value = "Imported '${item.title}' (${item.content.length} chars)."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "SAF import failed", e)
                _hubStatusMessage.value = "Import error: ${e.localizedMessage ?: "Unsupported file"}"
            } finally {
                _isHubBusy.value = false
            }
        }
    }

    fun addHttpsContextSource(rawUrl: String, titleOverride: String) {
        viewModelScope.launch {
            _isHubBusy.value = true
            _hubStatusMessage.value = "Validating and fetching HTTPS source..."
            try {
                val item = contextRegistry.addAndFetchHttpsSource(rawUrl, titleOverride)
                _hubStatusMessage.value = "Attached HTTPS source '${item.title}'."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "HTTPS source fetch failed", e)
                _hubStatusMessage.value = "URL error: ${e.localizedMessage ?: "Blocked or unreachable"}"
            } finally {
                _isHubBusy.value = false
            }
        }
    }

    fun addNoteContextSource(title: String, body: String) {
        runCatching {
            val item = contextRegistry.addNoteSource(title, body)
            _hubStatusMessage.value = "Added note '${item.title}' to Context Hub."
        }.onFailure { e ->
            _hubStatusMessage.value = e.localizedMessage ?: "Unable to add note."
        }
    }

    fun toggleContextSource(id: String) {
        contextRegistry.toggleSourceEnabled(id)
    }

    fun removeContextSource(id: String) {
        contextRegistry.removeSource(id)
    }

    private fun buildLiveShowContextSummary(): String {
        val show = _activeShow.value
        val activeScene = getCurrentScene()
        val activeConsole = getEffectiveConsoleSpec(activeScene)
        return buildString {
            appendLine("Current Show: ${show.title} (${show.scenes.size} scenes)")
            appendLine("Active Stage Scene: ${activeScene.title} | Console: ${activeConsole.name} (${activeConsole.shortCode})")
            appendLine("Active Assets: 3D=${activeScene.model3D.displayName}, Sprites=${activeScene.primarySprite.displayName} & ${activeScene.secondarySprite.displayName}, Texture=${activeScene.texturePreset.displayName}")
        }
    }

    /**
     * AI Documentation-to-Animation Show Generator routed via OmniAiRouter + MultiSourceContextRegistry.
     */
    fun generateAnimatedShowFromDocumentation() {
        val docText = _documentationInput.value.trim()
        if (docText.isBlank()) {
            _docToShowState.value = DocToShowUiState.Error("Please enter or select text documentation first.")
            return
        }

        val consoleConstraint = _preferredTargetConsoleId.value?.let { id ->
            val spec = ConsoleHardwareCatalog.findById(id)
            "Prioritize using consoleId '${spec.id}' (${spec.name}) for the primary scenes, or blend it with complementary consoles/handhelds."
        } ?: "Use a rich variety of classic and modern home consoles and handhelds across the scenes."

        val validConsoles = ConsoleHardwareCatalog.allConsoles.joinToString(", ") { it.id }
        val validModels = Model3DType.entries.joinToString(", ") { it.id }
        val validMotions = AnimationMotion.entries.joinToString(", ") { it.id }
        val validTextures = TexturePreset.entries.joinToString(", ") { it.id }
        val validSprites = SpriteActorType.entries.joinToString(", ") { it.id }
        val validCameras = CameraMove.entries.joinToString(", ") { it.id }
        val validFx = ParticleFxType.entries.joinToString(", ") { it.id }

        val contextBlock = contextRegistry.assembleContextBlock(
            liveShowContext = buildLiveShowContextSummary()
        )

        val systemPrompt = """
            You are the AI Showrunner and Technical Animation Director for ChronoStage AI.
            Return ONLY valid raw JSON matching this exact shape:
            {"showTitle":"...","showSubtitle":"...","scenes":[{"title":"...","documentationExcerpt":"...","narratorScript":"...","consoleId":"...","model3D":"...","modelMotion":"...","texturePreset":"...","primarySprite":"...","secondarySprite":"...","cameraMove":"...","particleFx":"...","durationSeconds":7}]}
            Allowed enum values:
            - consoleId: $validConsoles
            - model3D: $validModels
            - modelMotion: $validMotions
            - texturePreset: $validTextures
            - primarySprite / secondarySprite: $validSprites
            - cameraMove: $validCameras
            - particleFx: $validFx
        """.trimIndent()

        val userPrompt = buildString {
            appendLine("Convert the following documentation into a 4-scene animated online show mixing 3D models, 2D pixel sprites, textures, and console/handheld pipelines.")
            appendLine(consoleConstraint)
            if (contextBlock.isNotBlank()) {
                appendLine()
                appendLine(contextBlock)
            }
            appendLine()
            appendLine("Documentation Input:")
            appendLine(docText)
        }

        viewModelScope.launch {
            _docToShowState.value = DocToShowUiState.Generating
            try {
                val aiResponse = withTimeout(60_000L) {
                    omniAiRouter.generateText(
                        request = AiTextRequest(
                            prompt = userPrompt,
                            systemInstruction = systemPrompt,
                            requireJson = true,
                            maxTokens = 2400
                        ),
                        firebaseResponseSchema = showGenerationSchema
                    )
                }

                val cleanJson = OmniAiRouter.extractRawJsonObject(aiResponse.text)
                val json = JSONObject(cleanJson)
                val showTitle = json.getString("showTitle")
                val showSubtitle = json.optString("showSubtitle", "AI-Directed Hybrid 2D/3D Online Show")
                val scenesArray = json.getJSONArray("scenes")

                val parsedScenes = mutableListOf<ShowScene>()
                for (i in 0 until scenesArray.length()) {
                    val item = scenesArray.getJSONObject(i)
                    parsedScenes += ShowScene(
                        id = "ai_scene_${System.currentTimeMillis()}_$i",
                        index = i,
                        title = item.optString("title", "Scene ${i + 1}"),
                        documentationExcerpt = item.optString("documentationExcerpt", ""),
                        narratorScript = item.optString("narratorScript", ""),
                        consoleId = item.optString("consoleId", "switch_deck_hd2d"),
                        model3D = Model3DType.fromId(item.optString("model3D", "MECHA_VANGUARD")),
                        modelMotion = AnimationMotion.fromId(item.optString("modelMotion", "ORBIT_SHOWCASE")),
                        texturePreset = TexturePreset.fromId(item.optString("texturePreset", "NEON_GRID")),
                        primarySprite = SpriteActorType.fromId(item.optString("primarySprite", "CYBER_KNIGHT")),
                        secondarySprite = SpriteActorType.fromId(item.optString("secondarySprite", "DOCU_BOT")),
                        cameraMove = CameraMove.fromId(item.optString("cameraMove", "ORBIT_360")),
                        particleFx = ParticleFxType.fromId(item.optString("particleFx", "STARFIELD_WARP")),
                        durationSeconds = item.optInt("durationSeconds", 7).coerceIn(5, 12)
                    )
                }

                if (parsedScenes.isEmpty()) {
                    throw IllegalStateException("No scenes were generated in the structured output.")
                }

                val newProject = ShowProject(
                    id = 0L,
                    title = showTitle,
                    subtitle = showSubtitle,
                    sourceDocumentation = docText,
                    scenes = parsedScenes
                )
                val newId = repository.saveShow(newProject)
                _activeShow.value = newProject.copy(id = newId)
                _aiCustomBitmap.value = null
                _selectedSceneIndex.value = 0
                _sceneProgressFloat.value = 0f
                _consoleOverrideId.value = null
                _isPlaying.value = true

                val fallbackNote = if (aiResponse.fallbackUsed) " (Fallback)" else ""
                _docToShowState.value = DocToShowUiState.Success(
                    "Generated '$showTitle' (${parsedScenes.size} scenes) via ${aiResponse.providerUsed.shortBadge}$fallbackNote!"
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Show generation timed out", e)
                _docToShowState.value = DocToShowUiState.Error(
                    "Show generation timed out after 60 seconds. Please try again."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate animated show from documentation", e)
                _docToShowState.value = DocToShowUiState.Error(
                    e.localizedMessage ?: "Failed to generate animated show."
                )
            }
        }
    }

    /**
     * Streaming AI Director Notes routed through OmniAiRouter + MultiSourceContextRegistry.
     */
    fun streamAiDirectorNotes() {
        val docText = _documentationInput.value.trim()
        if (docText.isBlank()) {
            _directorStreamState.value = DirectorStreamState.Error("Enter documentation first to stream AI Director notes.")
            return
        }

        val contextBlock = contextRegistry.assembleContextBlock(
            liveShowContext = buildLiveShowContextSummary()
        )
        val prompt = buildString {
            appendLine("You are an expert Online Show Animation Director specializing in hybrid 2D sprite + 3D model choreography across retro and modern game consoles and handhelds.")
            appendLine("Provide a concise, high-impact 4-bullet visual direction breakdown for turning this documentation into an unforgettable animated online episode:")
            if (contextBlock.isNotBlank()) {
                appendLine()
                appendLine(contextBlock)
            }
            appendLine()
            appendLine(docText)
        }

        viewModelScope.launch {
            _directorStreamState.value = DirectorStreamState.Loading
            val accumulated = StringBuilder()
            try {
                withTimeout(60_000L) {
                    omniAiRouter.streamText(AiTextRequest(prompt = prompt)).collect { (chunkText, _) ->
                        accumulated.append(chunkText)
                        _directorStreamState.value = DirectorStreamState.Streaming(accumulated.toString())
                    }
                }
                if (accumulated.isNotBlank()) {
                    _directorStreamState.value = DirectorStreamState.Success(accumulated.toString())
                } else {
                    _directorStreamState.value = DirectorStreamState.Error("Model returned an empty response.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Director notes streaming timed out", e)
                _directorStreamState.value = DirectorStreamState.Error("Director stream timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Director notes streaming failed", e)
                _directorStreamState.value = DirectorStreamState.Error(
                    e.localizedMessage ?: "Streaming failed."
                )
            }
        }
    }

    /**
     * Runtime AI Texture & Sprite Sheet Synthesis routed via OmniAiRouter and persisted to disk.
     */
    fun generateRuntimeTextureOrSprite() {
        val prompt = _texturePromptInput.value.trim()
        if (prompt.isBlank()) {
            _textureGenState.value = TextureGenUiState.Error("Please enter a texture or sprite prompt.")
            return
        }

        viewModelScope.launch {
            _textureGenState.value = TextureGenUiState.Loading
            try {
                val imgResp = withTimeout(60_000L) {
                    omniAiRouter.generateImage(AiImageRequest(prompt = prompt))
                }
                val bitmap = imgResp.bitmap
                _aiCustomBitmap.value = bitmap
                updateCurrentSceneConfig { scene ->
                    scene.copy(texturePreset = TexturePreset.AI_CUSTOM_BITMAP)
                }

                // Persist show + custom texture immediately to filesDir/show_textures/<showId>.png
                val currentShow = _activeShow.value
                val persistedId = if (currentShow.id > 0L) {
                    repository.saveShow(currentShow, bitmap)
                } else {
                    repository.saveShow(currentShow, bitmap).also { newId ->
                        _activeShow.value = currentShow.copy(id = newId)
                    }
                }
                if (persistedId > 0L && _activeShow.value.id != persistedId) {
                    _activeShow.value = _activeShow.value.copy(id = persistedId)
                }

                val fallbackSuffix = if (imgResp.fallbackUsed) " (Fallback)" else ""
                _textureGenState.value = TextureGenUiState.Success(
                    bitmap = bitmap,
                    promptUsed = "$prompt — via ${imgResp.providerUsed.shortBadge}$fallbackSuffix (Saved to Disk)"
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI texture generation timed out", e)
                _textureGenState.value = TextureGenUiState.Error("Image generation timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI texture generation failed", e)
                _textureGenState.value = TextureGenUiState.Error(
                    e.localizedMessage ?: "Image generation failed."
                )
            }
        }
    }

    companion object {
        fun provideFactory(
            repository: ShowRepository,
            keyVault: SecureAiKeyVault,
            contextRegistry: MultiSourceContextRegistry,
            omniAiRouter: OmniAiRouter
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChronoStageViewModel(repository, keyVault, contextRegistry, omniAiRouter)
            }
        }
    }
}
