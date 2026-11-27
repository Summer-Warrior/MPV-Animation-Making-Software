package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.MultiSourceContextRegistry
import com.example.ai.OmniAiRouter
import com.example.ai.SecureAiKeyVault
import com.example.appcheck.configureAppCheck
import com.example.data.ShowDatabase
import com.example.data.ShowRepository
import com.example.data.ShowTextureStore
import com.example.engine.ChronoStageViewport
import com.example.model.ConsoleHardwareCatalog
import com.example.model.ShowProject
import com.example.ui.components.OmniAiConnectionsSheet
import com.example.ui.screens.AiDocDirectorTab
import com.example.ui.screens.AssetAndTextureLabTab
import com.example.ui.screens.ConsolesAndHandheldsTab
import com.example.ui.screens.SavedShowsLibraryTab
import com.example.ui.screens.StageAndTimelineTab
import com.example.ui.theme.ChronoStageTheme
import com.example.viewmodel.ChronoStageViewModel
import kotlinx.coroutines.launch

enum class StudioDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    STAGE("stage", R.string.tab_stage, Icons.Default.Movie),
    AI_DIRECTOR("ai_director", R.string.tab_ai_director, Icons.Default.AutoAwesome),
    CONSOLES("consoles", R.string.tab_consoles, Icons.Default.SportsEsports),
    ASSET_LAB("asset_lab", R.string.tab_assets, Icons.Default.Layers),
    SAVED_SHOWS("saved_shows", R.string.tab_shows, Icons.Default.VideoLibrary)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        // STEP 2: Initialize Compose UI
        setContent {
            ChronoStageTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChronoStageApp()
                }
            }
        }
    }
}

@Composable
fun ChronoStageApp() {
    val context = LocalContext.current
    val dependencies = remember {
        val db = ShowDatabase.getInstance(context)
        val textureStore = ShowTextureStore(context)
        val repository = ShowRepository(db.showDao(), textureStore)
        val keyVault = SecureAiKeyVault(context)
        val contextRegistry = MultiSourceContextRegistry(context)
        val omniAiRouter = OmniAiRouter(keyVault)
        ChronoStageViewModel.provideFactory(
            repository = repository,
            keyVault = keyVault,
            contextRegistry = contextRegistry,
            omniAiRouter = omniAiRouter
        )
    }
    val viewModel: ChronoStageViewModel = viewModel(factory = dependencies)

    val activeShow by viewModel.activeShow.collectAsStateWithLifecycle()
    val selectedSceneIndex by viewModel.selectedSceneIndex.collectAsStateWithLifecycle()
    val consoleOverrideId by viewModel.consoleOverrideId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val autoAdvanceScenes by viewModel.autoAdvanceScenes.collectAsStateWithLifecycle()
    val sceneProgress by viewModel.sceneProgressFloat.collectAsStateWithLifecycle()
    val wireframeOverlay by viewModel.wireframeOverlay.collectAsStateWithLifecycle()
    val crtAndLcdFilter by viewModel.crtAndLcdFilter.collectAsStateWithLifecycle()
    val isBroadcastFullscreen by viewModel.isBroadcastFullscreen.collectAsStateWithLifecycle()
    val userYawOffset by viewModel.userYawOffset.collectAsStateWithLifecycle()
    val userPitchOffset by viewModel.userPitchOffset.collectAsStateWithLifecycle()
    val documentationInput by viewModel.documentationInput.collectAsStateWithLifecycle()
    val preferredTargetConsoleId by viewModel.preferredTargetConsoleId.collectAsStateWithLifecycle()
    val docToShowState by viewModel.docToShowState.collectAsStateWithLifecycle()
    val directorStreamState by viewModel.directorStreamState.collectAsStateWithLifecycle()
    val texturePromptInput by viewModel.texturePromptInput.collectAsStateWithLifecycle()
    val textureGenState by viewModel.textureGenState.collectAsStateWithLifecycle()
    val aiCustomBitmap by viewModel.aiCustomBitmap.collectAsStateWithLifecycle()
    val savedShows by viewModel.savedShows.collectAsStateWithLifecycle()
    val omniAiConfig by viewModel.omniAiConfig.collectAsStateWithLifecycle()
    val contextSources by viewModel.contextSources.collectAsStateWithLifecycle()
    val hubStatusMessage by viewModel.hubStatusMessage.collectAsStateWithLifecycle()
    val isHubBusy by viewModel.isHubBusy.collectAsStateWithLifecycle()

    val currentScene = remember(activeShow, selectedSceneIndex) {
        val list = activeShow.scenes
        val safeIdx = selectedSceneIndex.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        list.getOrElse(safeIdx) { activeShow.scenes.first() }
    }
    val effectiveConsole = remember(currentScene, consoleOverrideId) {
        ConsoleHardwareCatalog.findById(consoleOverrideId ?: currentScene.consoleId)
    }

    var currentTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showAiHubSheet by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun copyShowMarkdownToClipboard(project: ShowProject) {
        val markdown = viewModel.exportShowMarkdown(project)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("${project.title} Storyboard", markdown))
        scope.launch {
            snackbarHostState.showSnackbar("Copied '${project.title}' Markdown for Google Docs / Notebook!")
        }
    }

    // Fullscreen Broadcast Cinema Mode
    if (isBroadcastFullscreen) {
        BackHandler {
            viewModel.setBroadcastFullscreen(false)
        }
        BroadcastCinemaScreen(
            showTitle = activeShow.title,
            currentScene = currentScene,
            consoleSpec = effectiveConsole,
            isPlaying = isPlaying,
            sceneProgress = sceneProgress,
            wireframeOverlay = wireframeOverlay,
            crtAndLcdFilter = crtAndLcdFilter,
            aiCustomBitmap = aiCustomBitmap,
            userYawOffset = userYawOffset,
            userPitchOffset = userPitchOffset,
            onCameraDrag = viewModel::onCameraDrag,
            onTogglePlayPause = viewModel::togglePlayPause,
            onPreviousScene = viewModel::previousScene,
            onNextScene = viewModel::nextScene,
            onExitFullscreen = { viewModel.setBroadcastFullscreen(false) }
        )
        return
    }

    // BackHandler for secondary tabs returning to Stage & Timeline home tab
    if (currentTabIndex != 0) {
        BackHandler {
            currentTabIndex = 0
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            ChronoStageTopBar(
                activeConsoleName = effectiveConsole.name,
                activeConsoleShort = effectiveConsole.shortCode,
                accentColor = effectiveConsole.accentColor,
                onOpenAiDirector = { currentTabIndex = StudioDestination.AI_DIRECTOR.ordinal }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("bottom_navigation_bar")
            ) {
                StudioDestination.entries.forEachIndexed { idx, dest ->
                    NavigationBarItem(
                        selected = currentTabIndex == idx,
                        onClick = { currentTabIndex = idx },
                        icon = {
                            Icon(
                                imageVector = dest.icon,
                                contentDescription = stringResource(dest.labelRes)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(dest.labelRes),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        },
                        modifier = Modifier.testTag("nav_tab_${dest.route}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(modifier = Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                when (StudioDestination.entries[currentTabIndex]) {
                    StudioDestination.STAGE -> {
                        StageAndTimelineTab(
                            activeShow = activeShow,
                            selectedSceneIndex = selectedSceneIndex,
                            currentScene = currentScene,
                            effectiveConsole = effectiveConsole,
                            consoleOverrideId = consoleOverrideId,
                            isPlaying = isPlaying,
                            autoAdvanceScenes = autoAdvanceScenes,
                            sceneProgress = sceneProgress,
                            wireframeOverlay = wireframeOverlay,
                            crtAndLcdFilter = crtAndLcdFilter,
                            aiCustomBitmap = aiCustomBitmap,
                            userYawOffset = userYawOffset,
                            userPitchOffset = userPitchOffset,
                            onCameraDrag = viewModel::onCameraDrag,
                            onResetCamera = viewModel::resetCameraOrbit,
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onPreviousScene = viewModel::previousScene,
                            onNextScene = viewModel::nextScene,
                            onSelectScene = viewModel::selectScene,
                            onToggleAutoAdvance = viewModel::toggleAutoAdvance,
                            onToggleWireframe = viewModel::toggleWireframeOverlay,
                            onToggleCrtFilter = viewModel::toggleCrtAndLcdFilter,
                            onSelectConsoleOverride = viewModel::setConsoleHardwareOverride,
                            onEnterBroadcastFullscreen = { viewModel.setBroadcastFullscreen(true) },
                            onSaveShowProject = {
                                viewModel.saveActiveShowToLibrary()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Saved '${activeShow.title}' to Show Archive!")
                                }
                            },
                            onExportMarkdown = { copyShowMarkdownToClipboard(activeShow) }
                        )
                    }
                    StudioDestination.AI_DIRECTOR -> {
                        AiDocDirectorTab(
                            documentationInput = documentationInput,
                            preferredTargetConsoleId = preferredTargetConsoleId,
                            omniAiConfig = omniAiConfig,
                            enabledContextSourcesCount = contextSources.count { it.enabled },
                            docToShowState = docToShowState,
                            directorStreamState = directorStreamState,
                            onUpdateDocumentation = viewModel::updateDocumentationInput,
                            onSelectPreferredConsole = viewModel::setPreferredTargetConsoleId,
                            onGenerateShow = viewModel::generateAnimatedShowFromDocumentation,
                            onStreamDirectorNotes = viewModel::streamAiDirectorNotes,
                            onOpenStageTab = { currentTabIndex = StudioDestination.STAGE.ordinal },
                            onOpenAiHub = { showAiHubSheet = true }
                        )
                    }
                    StudioDestination.CONSOLES -> {
                        ConsolesAndHandheldsTab(
                            activeConsoleId = effectiveConsole.id,
                            consoleOverrideId = consoleOverrideId,
                            onApplyConsoleToStage = viewModel::setConsoleHardwareOverride,
                            onApplyConsoleToCurrentScene = { newConsoleId ->
                                viewModel.updateCurrentSceneConfig { it.copy(consoleId = newConsoleId) }
                            },
                            onJumpToStage = { currentTabIndex = StudioDestination.STAGE.ordinal }
                        )
                    }
                    StudioDestination.ASSET_LAB -> {
                        AssetAndTextureLabTab(
                            currentScene = currentScene,
                            texturePromptInput = texturePromptInput,
                            textureGenState = textureGenState,
                            onUpdateScene = viewModel::updateCurrentSceneConfig,
                            onUpdateTexturePrompt = viewModel::updateTexturePrompt,
                            onGenerateAiTexture = viewModel::generateRuntimeTextureOrSprite,
                            onJumpToStage = { currentTabIndex = StudioDestination.STAGE.ordinal }
                        )
                    }
                    StudioDestination.SAVED_SHOWS -> {
                        SavedShowsLibraryTab(
                            savedShows = savedShows,
                            activeShowId = activeShow.id,
                            onLoadShow = { show ->
                                viewModel.selectShowProject(show)
                                currentTabIndex = StudioDestination.STAGE.ordinal
                            },
                            onDeleteShow = viewModel::deleteSavedShow,
                            onExportShowMarkdown = ::copyShowMarkdownToClipboard
                        )
                    }
                }
            }
        }
    }

    if (showAiHubSheet) {
        OmniAiConnectionsSheet(
            config = omniAiConfig,
            contextSources = contextSources,
            hubStatusMessage = hubStatusMessage,
            isHubBusy = isHubBusy,
            onDismiss = { showAiHubSheet = false },
            onSelectTextProvider = viewModel::selectActiveTextProvider,
            onSelectImageProvider = viewModel::selectActiveImageProvider,
            onToggleFirebaseFallback = viewModel::toggleFirebaseFallback,
            onSaveProviderKey = viewModel::saveProviderKey,
            onClearProviderKey = viewModel::clearProviderKey,
            onUpdateProviderModelAndUrl = viewModel::updateProviderModelAndUrl,
            onStartOpenRouterPkce = viewModel::startOpenRouterPkce,
            onImportSafDocument = { uri -> viewModel.importSafDocument(context, uri) },
            onAddHttpsSource = viewModel::addHttpsContextSource,
            onAddNoteSource = viewModel::addNoteContextSource,
            onToggleSourceEnabled = viewModel::toggleContextSource,
            onRemoveSource = viewModel::removeContextSource
        )
    }
}

@Composable
private fun ChronoStageTopBar(
    activeConsoleName: String,
    activeConsoleShort: String,
    accentColor: Color,
    onOpenAiDirector: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = accentColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = activeConsoleShort,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "Engine: $activeConsoleName",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onOpenAiDirector,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("topbar_doc_to_show_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Doc to Show",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Doc → Show",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun BroadcastCinemaScreen(
    showTitle: String,
    currentScene: com.example.model.ShowScene,
    consoleSpec: com.example.model.ConsoleSpec,
    isPlaying: Boolean,
    sceneProgress: Float,
    wireframeOverlay: Boolean,
    crtAndLcdFilter: Boolean,
    aiCustomBitmap: android.graphics.Bitmap?,
    userYawOffset: Float,
    userPitchOffset: Float,
    onCameraDrag: (Float, Float) -> Unit,
    onTogglePlayPause: () -> Unit,
    onPreviousScene: () -> Unit,
    onNextScene: () -> Unit,
    onExitFullscreen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05070E))
            .padding(12.dp)
            .testTag("broadcast_cinema_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LIVE ONLINE SHOW BROADCAST • ${consoleSpec.name.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = consoleSpec.accentColor
                )
                Text(
                    text = showTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = onExitFullscreen,
                modifier = Modifier.testTag("exit_broadcast_fullscreen_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FullscreenExit,
                    contentDescription = "Exit Fullscreen",
                    tint = Color.White
                )
            }
        }

        ChronoStageViewport(
            scene = currentScene,
            consoleSpec = consoleSpec,
            isPlaying = isPlaying,
            wireframeOverlay = wireframeOverlay,
            crtAndLcdFilterEnabled = crtAndLcdFilter,
            aiCustomBitmap = aiCustomBitmap,
            userYawOffset = userYawOffset,
            userPitchOffset = userPitchOffset,
            onCameraDrag = onCameraDrag,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { sceneProgress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = consoleSpec.accentColor
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousScene) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = onTogglePlayPause) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause"
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isPlaying) "Pause Show" else "Resume Show")
            }
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(onClick = onNextScene) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
            }
        }
    }
}
