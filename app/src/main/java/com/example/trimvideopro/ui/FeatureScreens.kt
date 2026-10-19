package com.example.trimvideopro.ui

import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.trimvideopro.localization.LocalAppStrings
import com.example.trimvideopro.model.ClipSegment
import com.example.trimvideopro.model.ProcessedVideo
import com.example.trimvideopro.model.SplitMode
import com.example.trimvideopro.model.Transcript
import com.example.trimvideopro.model.TranscriptChunk
import com.example.trimvideopro.model.TranscriptStatus
import com.example.trimvideopro.model.VideoMetadata
import com.example.trimvideopro.ui.components.ExportHistoryView
import com.example.trimvideopro.ui.components.MediaSourceCard
import com.example.trimvideopro.ui.components.SplitConfigView
import com.example.trimvideopro.ui.components.TimelineTrimSlider
import com.example.trimvideopro.ui.components.TranscriptView
import com.example.ui.theme.AmberAccent


@Composable
fun TrimScreen(
    selectedVideoUri: Uri?,
    metadata: VideoMetadata?,
    isExtractingMetadata: Boolean,
    trimStartMs: Long,
    trimEndMs: Long,
    isProcessing: Boolean,
    onPickMedia: () -> Unit,
    onRefresh: () -> Unit,
    onRangeChange: (Long, Long) -> Unit,
    onAdjustStart: (Long) -> Unit,
    onAdjustEnd: (Long) -> Unit,
    onTrim: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    ScreenColumn(modifier = modifier) {
        FeaturePageHeader(
            icon = Icons.Default.ContentCut,
            title = strings.tabTrim,
            description = strings.featureTrimDescription,
            accent = MaterialTheme.colorScheme.primary
        )

        MediaSourceCard(
            selectedVideoUri = selectedVideoUri,
            metadata = metadata,
            isExtractingMetadata = isExtractingMetadata,
            onPickMedia = onPickMedia,
            onRefresh = onRefresh,
            showPlayer = true
        )

        if (selectedVideoUri != null && metadata != null) {
            TimelineTrimSlider(
                totalDurationMs = metadata.durationMs,
                startMs = trimStartMs,
                endMs = trimEndMs,
                onRangeChange = onRangeChange,
                onAdjustStart = onAdjustStart,
                onAdjustEnd = onAdjustEnd
            )

            Button(
                onClick = onTrim,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isProcessing
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
        } else {
            ToolHintCard(text = strings.selectMediaHint)
        }
    }
}

@Composable
fun SplitScreen(
    selectedVideoUri: Uri?,
    metadata: VideoMetadata?,
    isExtractingMetadata: Boolean,
    splitMode: SplitMode,
    splitIntervalSec: Int,
    splitPartsCount: Int,
    calculatedSegments: List<ClipSegment>,
    isProcessing: Boolean,
    onPickMedia: () -> Unit,
    onRefresh: () -> Unit,
    onModeChange: (SplitMode) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onPartsChange: (Int) -> Unit,
    onSplit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    ScreenColumn(modifier = modifier) {
        FeaturePageHeader(
            icon = Icons.Default.ViewStream,
            title = strings.tabSplit,
            description = strings.featureSplitDescription,
            accent = MaterialTheme.colorScheme.secondary
        )

        MediaSourceCard(
            selectedVideoUri = selectedVideoUri,
            metadata = metadata,
            isExtractingMetadata = isExtractingMetadata,
            onPickMedia = onPickMedia,
            onRefresh = onRefresh,
            showPlayer = false
        )

        if (selectedVideoUri != null && metadata != null) {
            SplitConfigView(
                splitMode = splitMode,
                intervalSec = splitIntervalSec,
                partsCount = splitPartsCount,
                calculatedSegments = calculatedSegments,
                onModeChange = onModeChange,
                onIntervalChange = onIntervalChange,
                onPartsChange = onPartsChange
            )

            Button(
                onClick = onSplit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isProcessing && calculatedSegments.isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Default.ViewStream,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "${strings.splitVideo} (${calculatedSegments.size} ${strings.clipPrefix})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        } else {
            ToolHintCard(text = strings.selectMediaHint)
        }
    }
}

@Composable
fun TranscriptScreen(
    exportNotice: String? = null,
    provider: com.example.trimvideopro.model.TranscriptProvider = com.example.trimvideopro.model.TranscriptProvider.GEMINI,
    onProviderChange: (com.example.trimvideopro.model.TranscriptProvider) -> Unit = {},
    selectedVideoUri: Uri?,
    metadata: VideoMetadata?,
    isExtractingMetadata: Boolean,
    transcript: Transcript?,
    transcriptStatus: TranscriptStatus,
    transcriptProgress: Float,
    transcriptProgressMessage: String,
    selectedChunk: TranscriptChunk?,
    onPickMedia: () -> Unit,
    onRefresh: () -> Unit,
    onGenerateTranscript: (String) -> Unit,
    onChunkSelected: (TranscriptChunk) -> Unit,
    onExportSrt: () -> Unit,
    onExportVtt: () -> Unit,
    onExportText: () -> Unit,
    onShareTranscript: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    ScreenColumn(modifier = modifier) {
        FeaturePageHeader(
            icon = Icons.Default.ClosedCaption,
            title = strings.tabTranscript,
            description = strings.featureTranscriptDescription,
            accent = MaterialTheme.colorScheme.tertiary
        )

        MediaSourceCard(
            selectedVideoUri = selectedVideoUri,
            metadata = metadata,
            isExtractingMetadata = isExtractingMetadata,
            onPickMedia = onPickMedia,
            onRefresh = onRefresh,
            showPlayer = false
        )

        TranscriptProviderSelector(provider, transcriptStatus, onProviderChange)
        exportNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        if (selectedVideoUri != null) {
            TranscriptView(
                transcript = transcript,
                transcriptStatus = transcriptStatus,
                transcriptProgress = transcriptProgress,
                transcriptProgressMessage = transcriptProgressMessage,
                selectedChunk = selectedChunk,
                onGenerateTranscript = onGenerateTranscript,
                onChunkSelected = onChunkSelected,
                onExportSrt = onExportSrt,
                onExportVtt = onExportVtt,
                onExportText = onExportText,
                onShare = onShareTranscript
            )
        } else {
            ToolHintCard(text = strings.selectMediaHint)
        }
    }
}

@Composable
fun HistoryScreen(
    videos: List<ProcessedVideo>,
    onDeleteVideo: (ProcessedVideo) -> Unit,
    onClearAll: () -> Unit,
    onSaveToGallery: (ProcessedVideo) -> Unit,
    onSaveToStorage: (ProcessedVideo) -> Unit,
    onSaveAllToGallery: () -> Unit,
    onSaveAllToStorage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    ScreenColumn(modifier = modifier) {
        FeaturePageHeader(
            icon = Icons.Default.Movie,
            title = strings.tabHistory,
            description = strings.featureHistoryDescription,
            accent = AmberAccent
        )

        ExportHistoryView(
            videos = videos,
            onDeleteVideo = onDeleteVideo,
            onClearAll = onClearAll,
            onSaveToGallery = onSaveToGallery,
            onSaveToStorage = onSaveToStorage,
            onSaveAllToGallery = onSaveAllToGallery,
            onSaveAllToStorage = onSaveAllToStorage
        )
    }
}

/** Shared scrollable page container for every tool screen. */
@Composable
private fun ScreenColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        content()
    }
}

@Composable
private fun FeaturePageHeader(
    icon: ImageVector,
    title: String,
    description: String,
    accent: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(26.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ToolHintCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(20.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
