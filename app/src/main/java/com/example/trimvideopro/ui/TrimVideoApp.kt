package com.example.trimvideopro.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.trimvideopro.localization.tool
import com.example.trimvideopro.localization.toolTitle
import com.example.trimvideopro.localization.AppLanguage
import com.example.trimvideopro.localization.AppStrings
import com.example.trimvideopro.localization.EnglishStrings
import com.example.trimvideopro.localization.KhmerStrings
import com.example.trimvideopro.localization.LocalAppStrings
import com.example.trimvideopro.model.ProcessedVideo
import com.example.trimvideopro.settings.AppPreferences
import com.example.trimvideopro.ui.components.ApkBuildGuideDialog

/** Pages reachable from the home grid. */
enum class AppScreen {
    HOME,
    TRIM,
    SPLIT,
    TRANSCRIPT,
    MERGE,
    EXTRACT_AUDIO,
    SPLIT_AUDIO,
    AUDIO_EDITOR,
    HISTORY
}

private val MediaPickerMimeTypes = arrayOf("video/*", "audio/*")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimVideoApp(
    viewModel: VideoViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val strings = if (state.language == AppLanguage.KHMER) KhmerStrings else EnglishStrings

    // Lightweight in-app navigation: the home page is the hub and every tool
    // opens on its own page. System back returns to the home page.
    var currentScreenName by rememberSaveable { mutableStateOf(AppScreen.HOME.name) }
    val currentScreen = remember(currentScreenName) {
        runCatching { AppScreen.valueOf(currentScreenName) }.getOrDefault(AppScreen.HOME)
    }
    val navigateTo: (AppScreen) -> Unit = { currentScreenName = it.name }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectVideo(context, uri)
        }
    }

    var pendingStorageAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingStorageAction
        pendingStorageAction = null
        if (granted) action?.invoke() else android.widget.Toast.makeText(context,
            strings.tool("Storage permission is needed to save on Android 7–9.", "ត្រូវការសិទ្ធិផ្ទុក ដើម្បីរក្សាទុកលើ Android 7–9។"),
            android.widget.Toast.LENGTH_LONG).show()
    }
    val withStoragePermission: (() -> Unit) -> Unit = { action ->
        if (android.os.Build.VERSION.SDK_INT >= 29 || androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED) action()
        else {
            pendingStorageAction = action
            storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    CompositionLocalProvider(LocalAppStrings provides strings) {
        BackHandler(enabled = currentScreen != AppScreen.HOME) {
            navigateTo(AppScreen.HOME)
        }

        Scaffold(
            topBar = {
                AppTopBar(
                    screen = currentScreen,
                    strings = strings,
                    language = state.language,
                    onToggleLanguage = { viewModel.toggleLanguage() },
                    onRefresh = { viewModel.refreshState(context) },
                    onOpenSettings = { viewModel.toggleSettingsDialog(true) },
                    onBack = { navigateTo(AppScreen.HOME) }
                )
            }
        ) { paddingValues ->
            AnimatedContent(
                targetState = currentScreen,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                transitionSpec = {
                    if (targetState.ordinal > initialState.ordinal) {
                        (slideInHorizontally { it / 8 } + fadeIn()).togetherWith(
                            slideOutHorizontally { -it / 8 } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally { -it / 8 } + fadeIn()).togetherWith(
                            slideOutHorizontally { it / 8 } + fadeOut()
                        )
                    }
                },
                label = "app_screen"
            ) { targetScreen ->
                when (targetScreen) {
                    AppScreen.HOME -> HomeScreen(
                        exportedClipCount = state.exportedVideos.size,
                        onOpenTool = navigateTo
                    )

                    AppScreen.TRIM -> TrimScreen(
                        selectedVideoUri = state.selectedVideoUri,
                        metadata = state.metadata,
                        isExtractingMetadata = state.isExtractingMetadata,
                        trimStartMs = state.trimStartMs,
                        trimEndMs = state.trimEndMs,
                        isProcessing = state.isProcessing,
                        onPickMedia = { mediaPickerLauncher.launch(MediaPickerMimeTypes) },
                        onRefresh = { viewModel.refreshState(context) },
                        onRangeChange = { start, end -> viewModel.updateTrimRange(start, end) },
                        onAdjustStart = { delta -> viewModel.adjustTrimStart(delta) },
                        onAdjustEnd = { delta -> viewModel.adjustTrimEnd(delta) },
                        onTrim = { viewModel.executeTrim(context) }
                    )

                    AppScreen.SPLIT -> SplitScreen(
                        selectedVideoUri = state.selectedVideoUri,
                        metadata = state.metadata,
                        isExtractingMetadata = state.isExtractingMetadata,
                        splitMode = state.splitMode,
                        splitIntervalSec = state.splitIntervalSec,
                        splitPartsCount = state.splitPartsCount,
                        calculatedSegments = state.calculatedSegments,
                        isProcessing = state.isProcessing,
                        onPickMedia = { mediaPickerLauncher.launch(MediaPickerMimeTypes) },
                        onRefresh = { viewModel.refreshState(context) },
                        onModeChange = { viewModel.setSplitMode(it) },
                        onIntervalChange = { viewModel.setSplitIntervalSec(it) },
                        onPartsChange = { viewModel.setSplitPartsCount(it) },
                        onSplit = { viewModel.executeSplit(context) }
                    )

                    AppScreen.MERGE -> MergeScreen(state, viewModel)
                    AppScreen.EXTRACT_AUDIO, AppScreen.SPLIT_AUDIO, AppScreen.AUDIO_EDITOR ->
                        AudioToolScreen(targetScreen, state, viewModel)

                    AppScreen.TRANSCRIPT -> TranscriptScreen(
                        exportNotice = state.transcriptExportNotice,
                        provider = state.transcriptProvider,
                        onProviderChange = viewModel::setTranscriptProvider,
                        selectedVideoUri = state.selectedVideoUri,
                        metadata = state.metadata,
                        isExtractingMetadata = state.isExtractingMetadata,
                        transcript = state.transcript,
                        transcriptStatus = state.transcriptStatus,
                        transcriptProgress = state.transcriptProgress,
                        transcriptProgressMessage = state.transcriptProgressMessage,
                        selectedChunk = state.selectedTranscriptChunk,
                        onPickMedia = { mediaPickerLauncher.launch(MediaPickerMimeTypes) },
                        onRefresh = { viewModel.refreshState(context) },
                        onGenerateTranscript = { language ->
                            viewModel.generateTranscript(context, language)
                        },
                        onChunkSelected = { chunk ->
                            viewModel.selectTranscriptChunk(chunk)
                            // Jump the trim range to the selected chunk's timestamps
                            viewModel.updateTrimRange(chunk.startMs, chunk.endMs)
                        },
                        onExportSrt = { withStoragePermission { viewModel.exportTranscriptToSrt(context) } },
                        onExportVtt = { withStoragePermission { viewModel.exportTranscriptToVtt(context) } },
                        onExportText = { withStoragePermission { viewModel.exportTranscriptToText(context) } },
                        onShareTranscript = { viewModel.shareTranscript(context) }
                    )

                    AppScreen.HISTORY -> HistoryScreen(
                        videos = state.exportedVideos,
                        onDeleteVideo = { viewModel.deleteExportedVideo(it) },
                        onClearAll = { viewModel.clearAllExportedVideos(context) },
                        onSaveToGallery = { video -> withStoragePermission { viewModel.saveVideoToGallery(context, video) } },
                        onSaveToStorage = { video -> withStoragePermission { viewModel.saveVideoToStorage(context, video) } },
                        onSaveAllToGallery = { withStoragePermission { viewModel.saveAllToGallery(context, state.exportedVideos) } },
                        onSaveAllToStorage = { withStoragePermission { viewModel.saveAllToStorage(context, state.exportedVideos) } }
                    )
                }
            }
        }

        // Processing Progress Dialog
        if (state.isProcessing) {
            Dialog(onDismissRequest = {}) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { state.processingProgress },
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = if (state.totalClipsToProcess > 1) {
                                "${strings.processingClip} ${state.processingClipIndex}/${state.totalClipsToProcess}"
                            } else {
                                strings.processingVideo
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        LinearProgressIndicator(
                            progress = { state.processingProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "${(state.processingProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace)
                        )
                    }
                }
            }
        }

        // Completion Dialog with Save to Gallery / Storage Buttons
        if (state.showCompletionDialog && !state.completedClips.isNullOrEmpty()) {
            TrimCompletionDialog(
                videos = state.completedClips!!,
                onSaveToGallery = { video -> withStoragePermission { viewModel.saveVideoToGallery(context, video) } },
                onSaveToStorage = { video -> withStoragePermission { viewModel.saveVideoToStorage(context, video) } },
                onSaveAllToGallery = { withStoragePermission { viewModel.saveAllToGallery(context, state.completedClips!!) } },
                onSaveAllToStorage = { withStoragePermission { viewModel.saveAllToStorage(context, state.completedClips!!) } },
                onDismiss = { viewModel.dismissCompletionDialog() }
            )
        }

        // APK Build Guide Dialog
        if (state.showApkBuildDialog) {
            ApkBuildGuideDialog(
                onDismiss = { viewModel.toggleApkBuildDialog(false) }
            )
        }

        // Settings Bottom Sheet
        if (state.showSettingsDialog) {
            SettingsBottomSheet(
                currentLanguage = state.language,
                currentApiKey = AppPreferences.getSavedApiKey(context),
                currentGroqKey = AppPreferences.getGroqApiKey(context),
                onSaveGroqKey = { AppPreferences.saveGroqApiKey(context, it) },
                onLanguageSelected = { viewModel.setLanguage(it) },
                onSaveApiKey = { viewModel.saveGeminiApiKey(context, it) },
                onClearCache = { viewModel.clearCache(context) },
                onShareWithFriends = { viewModel.shareApp(context) },
                onOpenFeedback = { viewModel.toggleFeedbackDialog(true) },
                onOpenAboutUs = { viewModel.toggleAboutUsDialog(true) },
                onDismiss = { viewModel.toggleSettingsDialog(false) }
            )
        }

        // Feedback Dialog
        if (state.showFeedbackDialog) {
            FeedbackDialog(
                onSend = { feedback -> viewModel.sendFeedback(context, feedback) },
                onDismiss = { viewModel.toggleFeedbackDialog(false) }
            )
        }

        // About Us Dialog
        if (state.showAboutUsDialog) {
            AboutUsDialog(
                onDismiss = { viewModel.toggleAboutUsDialog(false) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    screen: AppScreen,
    strings: AppStrings,
    language: AppLanguage,
    onToggleLanguage: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            if (screen != AppScreen.HOME) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.back
                    )
                }
            }
        },
        title = {
            Column {
                Text(
                    text = screenTitle(screen, strings),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                if (screen == AppScreen.HOME) {
                    Text(
                        text = "Created by Moeun💞",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    text = strings.offlineBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        },
        actions = {
            // Language switch toggle button (EN / KM)
            IconButton(onClick = onToggleLanguage) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = language.flag,
                        modifier = Modifier.padding(6.dp),
                        fontSize = 16.sp
                    )
                }
            }

            // 🔄 REFRESH BUTTON
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = strings.refresh,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // ⚙️ SETTINGS BUTTON
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("open_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = strings.settings,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

private fun screenTitle(screen: AppScreen, strings: AppStrings): String = when (screen) {
    AppScreen.HOME -> strings.appTitle
    AppScreen.TRIM -> strings.tabTrim
    AppScreen.SPLIT -> strings.tabSplit
    AppScreen.TRANSCRIPT -> strings.tabTranscript
    AppScreen.HISTORY -> strings.tabHistory
    AppScreen.MERGE, AppScreen.EXTRACT_AUDIO, AppScreen.SPLIT_AUDIO, AppScreen.AUDIO_EDITOR -> strings.toolTitle(screen)
}

@Composable
fun TrimCompletionDialog(
    videos: List<ProcessedVideo>,
    onSaveToGallery: (ProcessedVideo) -> Unit,
    onSaveToStorage: (ProcessedVideo) -> Unit,
    onSaveAllToGallery: (() -> Unit)?,
    onSaveAllToStorage: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Success Icon & Header
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = if (videos.size > 1) strings.splitCompleteTitle else strings.trimCompleteTitle,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                Text(
                    text = "${videos.size} ${strings.clipsGeneratedCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Main Action Buttons
                if (videos.size > 1 && onSaveAllToGallery != null && onSaveAllToStorage != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onSaveAllToGallery,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Collections,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = strings.tool("Save all to media library", "រក្សាទុកទាំងអស់ក្នុងបណ្ណាល័យ"),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        OutlinedButton(
                            onClick = onSaveAllToStorage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = strings.saveAllToStorage,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                } else if (videos.isNotEmpty()) {
                    val firstVideo = videos.first()
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onSaveToGallery(firstVideo) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Collections,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = strings.tool("Save to media library", "រក្សាទុកក្នុងបណ្ណាល័យ"),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        OutlinedButton(
                            onClick = { onSaveToStorage(firstVideo) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = strings.saveToStorage,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = strings.close,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
