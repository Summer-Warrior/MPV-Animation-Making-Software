package com.example.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
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
    private val repository: ShowRepository
) : ViewModel() {

    // Structured JSON schema for converting documentation into a multi-scene animated online show
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

    // 1. Lazy initialization of Structured JSON Gemini 3.8 Flash model
    private val structuredShowModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = showGenerationSchema
            }
        )
    }

    // 2. Lazy initialization of Streaming Gemini 3.8 Flash model for Director Notes
    private val directorStreamModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    // 3. Lazy initialization of Gemini Nano Banana 2.1 model for runtime AI Texture / Sprite generation
    private val nanoBananaImageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    val savedShows: StateFlow<List<ShowProject>> = repository.allShows
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StarterShowPresets.defaultShowProjects
        )

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
            val savedId = repository.saveShow(_activeShow.value)
            _activeShow.value = _activeShow.value.copy(id = savedId)
        }
    }

    fun deleteSavedShow(id: Long) {
        viewModelScope.launch {
            repository.deleteShow(id)
        }
    }

    /**
     * AI Documentation-to-Animation Show Generator (Structured JSON via Gemini 3.8 Flash)
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
        } ?: "Use a rich variety of classic and modern home consoles and handhelds across the scenes (e.g. gameboy_dmg, snes_superfx, ps1_saturn, nintendo_64, gba_advance, nintendo_ds, psp_vita_3ds, switch_deck_hd2d)."

        val prompt = """
            You are the AI Showrunner and Technical Animation Director for ChronoStage AI.
            Convert the following text documentation into an exciting 4-scene animated online show that mixes 3D models, 2D pixel sprites, surface textures, and authentic video game console & handheld rendering pipelines.
            $consoleConstraint

            Documentation Input:
            $docText
        """.trimIndent()

        viewModelScope.launch {
            _docToShowState.value = DocToShowUiState.Generating
            try {
                val response = withTimeout(60_000L) {
                    structuredShowModel.generateContent(prompt)
                }
                val jsonString = response.text
                    ?: throw IllegalStateException("Model returned an empty JSON response.")

                val json = JSONObject(jsonString)
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
                _selectedSceneIndex.value = 0
                _sceneProgressFloat.value = 0f
                _consoleOverrideId.value = null
                _isPlaying.value = true
                _docToShowState.value = DocToShowUiState.Success(
                    "Generated '$showTitle' with ${parsedScenes.size} hybrid 2D/3D scenes!"
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
     * Streaming AI Director Notes & Show Choreography Analysis (generateContentStream)
     */
    fun streamAiDirectorNotes() {
        val docText = _documentationInput.value.trim()
        if (docText.isBlank()) {
            _directorStreamState.value = DirectorStreamState.Error("Enter documentation first to stream AI Director notes.")
            return
        }

        val prompt = """
            You are an expert Online Show Animation Director specializing in hybrid 2D sprite + 3D model choreography across retro and modern game consoles and handhelds (NES, Game Boy DMG, SNES Super FX, PS1, N64, GBA, Nintendo DS, Dreamcast, PSP/Vita, Switch HD-2D).
            Provide a concise, high-impact 4-bullet visual direction breakdown for turning this documentation into an unforgettable animated online episode (covering camera transitions, 2D sprite vs 3D mesh contrast, and hardware shader transitions):

            $docText
        """.trimIndent()

        viewModelScope.launch {
            _directorStreamState.value = DirectorStreamState.Loading
            val accumulated = StringBuilder()
            try {
                withTimeout(60_000L) {
                    directorStreamModel.generateContentStream(prompt).collect { chunk ->
                        val text = chunk.text.orEmpty()
                        accumulated.append(text)
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
     * Runtime AI Texture & Sprite Sheet Synthesis using Gemini Nano Banana 2.1
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
                val bitmap = withTimeout(60_000L) {
                    val response = nanoBananaImageModel.generateContent(prompt)
                    response.candidates.firstOrNull()
                        ?.content?.parts?.filterIsInstance<ImagePart>()
                        ?.firstOrNull()?.image
                }
                if (bitmap != null) {
                    _aiCustomBitmap.value = bitmap
                    _textureGenState.value = TextureGenUiState.Success(bitmap, prompt)
                    // Automatically apply the AI_CUSTOM_BITMAP texture preset to the active scene
                    updateCurrentSceneConfig { scene ->
                        scene.copy(texturePreset = TexturePreset.AI_CUSTOM_BITMAP)
                    }
                } else {
                    _textureGenState.value = TextureGenUiState.Error("No image was returned by the model.")
                }
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
        fun provideFactory(repository: ShowRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChronoStageViewModel(repository) }
        }
    }
}
