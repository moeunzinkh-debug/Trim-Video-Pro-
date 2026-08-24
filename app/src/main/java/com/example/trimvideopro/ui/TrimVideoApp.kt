package com.example.trimvideopro.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.trimvideopro.localization.AppLanguage
import com.example.trimvideopro.localization.EnglishStrings
import com.example.trimvideopro.localization.KhmerStrings
import com.example.trimvideopro.localization.LocalAppStrings
import com.example.trimvideopro.model.ProcessedVideo
import com.example.trimvideopro.processor.VideoProcessor
import com.example.trimvideopro.ui.components.ApkBuildGuideDialog
import com.example.trimvideopro.ui.components.ExportHistoryView
import com.example.trimvideopro.ui.components.SplitConfigView
import com.example.trimvideopro.ui.components.TimelineTrimSlider
import com.example.trimvideopro.ui.components.TranscriptView
import com.example.trimvideopro.ui.components.VideoPlayerView

enum class AppTab {
    TRIM,
    SPLIT,
    TRANSCRIPT,
    HISTORY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimVideoApp(
    viewModel: VideoViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val strings = if (state.language == AppLanguage.KHMER) KhmerStrings else EnglishStrings

    var selectedTab by remember { mutableStateOf(AppTab.TRIM) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectVideo(context, uri)
        }
    }

    CompositionLocalProvider(LocalAppStrings provides strings) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = strings.appTitle,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Created by Moeun💞",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = strings.offlineBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        // Language switch toggle button (EN / KM)
                        IconButton(onClick = { viewModel.toggleLanguage() }) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = state.language.flag,
                                    modifier = Modifier.padding(6.dp),
                                    fontSize = 16.sp
                                )
                            }
                        }

                        // 🔄 REFRESH BUTTON
                        IconButton(onClick = { viewModel.refreshState(context) }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = strings.refresh,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // ⚙️ SETTINGS BUTTON
                        IconButton(
                            onClick = { viewModel.toggleSettingsDialog(true) },
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
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == AppTab.TRIM,
                        onClick = { selectedTab = AppTab.TRIM },
                        icon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                        label = { Text(strings.tabTrim, fontSize = 12.sp) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == AppTab.SPLIT,
                        onClick = { selectedTab = AppTab.SPLIT },
                        icon = { Icon(Icons.Default.ViewStream, contentDescription = null) },
                        label = { Text(strings.tabSplit, fontSize = 12.sp) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == AppTab.TRANSCRIPT,
                        onClick = { selectedTab = AppTab.TRANSCRIPT },
                        icon = { Icon(Icons.Default.ClosedCaption, contentDescription = null) },
                        label = { Text(strings.tabTranscript, fontSize = 12.sp) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == AppTab.HISTORY,
                        onClick = { selectedTab = AppTab.HISTORY },
                        icon = { Icon(Icons.Default.Movie, contentDescription = null) },
                        label = { Text("${strings.tabHistory} (${state.exportedVideos.size})", fontSize = 12.sp) }
                    )
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Video Pick / Overview Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (state.selectedVideoUri == null) {
                            // Pick video CTA
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = strings.selectVideo,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Button(
                                    onClick = { videoPickerLauncher.launch("video/*") },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(strings.selectVideo)
                                }
                            }
                        } else {
                            // Video Details & Player
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = state.metadata?.fileName ?: "Selected Video",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1
                                    )
                                    state.metadata?.let { meta ->
                                        Text(
                                            text = "${VideoProcessor.formatDuration(meta.durationMs)} • ${meta.width}x${meta.height} • ${VideoProcessor.formatFileSize(meta.fileSize)}",
                                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row {
                                    IconButton(onClick = { viewModel.refreshState(context) }) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = strings.refresh,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { videoPickerLauncher.launch("video/*") },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(strings.changeVideo, fontSize = 12.sp)
                                    }
                                }
                            }

                            if (state.isExtractingMetadata) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                VideoPlayerView(
                                    videoUri = state.selectedVideoUri!!,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // Main Tab Content
                when (selectedTab) {
                    AppTab.TRIM -> {
                        if (state.selectedVideoUri != null && state.metadata != null) {
                            TimelineTrimSlider(
                                totalDurationMs = state.metadata!!.durationMs,
                                startMs = state.trimStartMs,
                                endMs = state.trimEndMs,
                                onRangeChange = { start, end ->
                                    viewModel.updateTrimRange(start, end)
                                },
                                onAdjustStart = { delta -> viewModel.adjustTrimStart(delta) },
                                onAdjustEnd = { delta -> viewModel.adjustTrimEnd(delta) }
                            )

                            Button(
                                onClick = { viewModel.executeTrim(context) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !state.isProcessing
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCut,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = strings.trimVideo,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    AppTab.SPLIT -> {
                        if (state.selectedVideoUri != null && state.metadata != null) {
                            SplitConfigView(
                                splitMode = state.splitMode,
                                intervalSec = state.splitIntervalSec,
                                partsCount = state.splitPartsCount,
                                calculatedSegments = state.calculatedSegments,
                                onModeChange = { viewModel.setSplitMode(it) },
                                onIntervalChange = { viewModel.setSplitIntervalSec(it) },
                                onPartsChange = { viewModel.setSplitPartsCount(it) }
                            )

                            Button(
                                onClick = { viewModel.executeSplit(context) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !state.isProcessing && state.calculatedSegments.isNotEmpty()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ViewStream,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = "${strings.splitVideo} (${state.calculatedSegments.size} ${strings.clipPrefix})",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    AppTab.TRANSCRIPT -> {
                        if (state.selectedVideoUri != null) {
                            TranscriptView(
                                transcript = state.transcript,
                                transcriptStatus = state.transcriptStatus,
                                transcriptProgress = state.transcriptProgress,
                                transcriptProgressMessage = state.transcriptProgressMessage,
                                selectedChunk = state.selectedTranscriptChunk,
                                onGenerateTranscript = { language ->
                                    viewModel.generateTranscript(context, language)
                                },
                                onChunkSelected = { chunk ->
                                    viewModel.selectTranscriptChunk(chunk)
                                    // Update trim range to the selected chunk's timestamps
                                    viewModel.updateTrimRange(chunk.startMs, chunk.endMs)
                                },
                                onExportSrt = { viewModel.exportTranscriptToSrt(context) },
                                onExportVtt = { viewModel.exportTranscriptToVtt(context) },
                                onExportText = { viewModel.exportTranscriptToText(context) },
                                onShare = { viewModel.shareTranscript(context) }
                            )
                        }
                    }

                    AppTab.HISTORY -> {
                        ExportHistoryView(
                            videos = state.exportedVideos,
                            onDeleteVideo = { viewModel.deleteExportedVideo(it) },
                            onClearAll = { viewModel.clearAllExportedVideos(context) },
                            onSaveToGallery = { video -> viewModel.saveVideoToGallery(context, video) },
                            onSaveToStorage = { video -> viewModel.saveVideoToStorage(context, video) },
                            onSaveAllToGallery = { viewModel.saveAllToGallery(context, state.exportedVideos) },
                            onSaveAllToStorage = { viewModel.saveAllToStorage(context, state.exportedVideos) }
                        )
                    }
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
                onSaveToGallery = { video -> viewModel.saveVideoToGallery(context, video) },
                onSaveToStorage = { video -> viewModel.saveVideoToStorage(context, video) },
                onSaveAllToGallery = { viewModel.saveAllToGallery(context, state.completedClips!!) },
                onSaveAllToStorage = { viewModel.saveAllToStorage(context, state.completedClips!!) },
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
                onLanguageSelected = { viewModel.setLanguage(it) },
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

@Composable
fun TrimCompletionDialog(
    videos: List<ProcessedVideo>,
    onSaveToGallery: (ProcessedVideo) -> Unit,
    onSaveToStorage: (ProcessedVideo) -> Unit,
    onSaveAllToGallery: (() -> Unit)?,
    onSaveAllToStorage: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    var playingUri by remember { mutableStateOf<Uri?>(null) }

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
                                text = strings.saveAllToGallery,
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
                                text = strings.saveToGallery,
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
