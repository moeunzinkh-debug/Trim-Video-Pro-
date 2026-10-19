package com.example.trimvideopro.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.trimvideopro.localization.LocalAppStrings
import com.example.trimvideopro.localization.tool
import com.example.trimvideopro.localization.toolTitle
import com.example.trimvideopro.model.AudioEffects
import com.example.trimvideopro.model.AudioPresets
import com.example.trimvideopro.model.TranscriptProvider
import com.example.trimvideopro.model.TranscriptStatus
import com.example.trimvideopro.processor.VideoProcessor
import com.example.trimvideopro.ui.components.MediaSourceCard
import com.example.trimvideopro.ui.components.SplitConfigView

@Composable
fun MergeScreen(state: VideoUiState, viewModel: VideoViewModel) {
    val context = LocalContext.current
    val s = LocalAppStrings.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.addMergeSources(context, uris)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(s.toolTitle(AppScreen.MERGE), style = MaterialTheme.typography.headlineSmall)
        Text(s.tool("Join 2–20 clips in the order below, without quality loss. Clips must have matching codecs, resolution, orientation and audio tracks.",
            "បញ្ចូលវីដេអូ 2–20 តាមលំដាប់ខាងក្រោម ដោយមិនបាត់បង់គុណភាព។ កូឌិក ទំហំ ទិស និងបទសំឡេងត្រូវតែដូចគ្នា។"))
        OutlinedButton(onClick = { picker.launch(arrayOf("video/*")) }, enabled = !state.isProcessing && !state.isLoadingMerge,
            modifier = Modifier.fillMaxWidth().testTag("merge_add_videos")) {
            Text(s.tool("Add videos", "បន្ថែមវីដេអូ"))
        }
        if (state.isLoadingMerge) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.mergeSources.forEachIndexed { index, source ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${index + 1}. ${source.fileName}", style = MaterialTheme.typography.titleSmall)
                    Text(VideoProcessor.formatDuration(source.durationMs))
                    Row {
                        IconButton(onClick = { viewModel.moveMergeSource(index, -1) }, enabled = index > 0 && !state.isProcessing) {
                            Icon(Icons.Default.ArrowUpward, s.tool("Move up", "រំកិលឡើង"))
                        }
                        IconButton(onClick = { viewModel.moveMergeSource(index, 1) }, enabled = index < state.mergeSources.lastIndex && !state.isProcessing) {
                            Icon(Icons.Default.ArrowDownward, s.tool("Move down", "រំកិលចុះ"))
                        }
                        IconButton(onClick = { viewModel.removeMergeSource(index) }, enabled = !state.isProcessing) {
                            Icon(Icons.Default.Delete, s.delete)
                        }
                    }
                }
            }
        }
        state.toolError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("media_tool_error")) }
        Button(onClick = { viewModel.executeMerge(context) },
            enabled = state.mergeSources.size >= 2 && !state.isProcessing && !state.isLoadingMerge,
            modifier = Modifier.fillMaxWidth().testTag("merge_export")) { Text(s.tool("Merge and export MP4", "បញ្ចូល និងនាំចេញ MP4")) }
    }
}

@Composable
fun AudioToolScreen(screen: AppScreen, state: VideoUiState, viewModel: VideoViewModel) {
    val context = LocalContext.current
    val s = LocalAppStrings.current
    val busy = state.isProcessing || state.isRecommending
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.selectVideo(context, uri)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(s.toolTitle(screen), style = MaterialTheme.typography.headlineSmall)
        Text(s.tool("Export 16-bit WAV audio. Originals are never changed. Video sources use their first audio track.",
            "នាំចេញសំឡេង WAV 16-bit។ ឯកសារដើមមិនត្រូវបានផ្លាស់ប្តូរ។ ប្រើបទសំឡេងដំបូងរបស់វីដេអូ។"))
        MediaSourceCard(state.selectedVideoUri, state.metadata, state.isExtractingMetadata,
            onPickMedia = { if (!busy) picker.launch(if (screen == AppScreen.EXTRACT_AUDIO) arrayOf("video/*") else arrayOf("audio/*", "video/*")) },
            onRefresh = { if (!busy) viewModel.refreshState(context) })
        if (screen == AppScreen.SPLIT_AUDIO) {
            SplitConfigView(state.splitMode, state.splitIntervalSec, state.splitPartsCount, state.calculatedSegments,
                onModeChange = { if (!busy) viewModel.setSplitMode(it) },
                onIntervalChange = { if (!busy) viewModel.setSplitIntervalSec(it) },
                onPartsChange = { if (!busy) viewModel.setSplitPartsCount(it) })
            Text(s.tool("Up to 50 parts. Each boundary is decoded to audio samples, not rounded to video keyframes.",
                "អាចបំបែករហូតដល់ 50 ផ្នែក។ បំបែកតាមសំណាកសំឡេង មិនមែនតាមស៊ុមវីដេអូ។"), style = MaterialTheme.typography.bodySmall)
        }
        if (screen == AppScreen.AUDIO_EDITOR) AudioEffectsPanel(state, viewModel)
        state.toolError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("media_tool_error")) }
        Button(onClick = { viewModel.executeAudio(context, split = screen == AppScreen.SPLIT_AUDIO, applyEffects = screen == AppScreen.AUDIO_EDITOR) },
            enabled = state.metadata != null && !state.isExtractingMetadata && !busy,
            modifier = Modifier.fillMaxWidth().testTag("audio_export")) {
            Text(s.tool("Export WAV", "នាំចេញ WAV"))
        }
        Text(s.tool("Exports appear in History. Save to Downloads or share from there. Audio studio exports audio only; it does not replace a video's soundtrack.",
            "ឯកសារនាំចេញនៅក្នុងប្រវត្តិ។ អាចរក្សាទុក ឬចែករំលែកពីទីនោះ។ ការកែសំឡេងនាំចេញតែសំឡេង មិនជំនួសសំឡេងក្នុងវីដេអូទេ។"),
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AudioEffectsPanel(state: VideoUiState, viewModel: VideoViewModel) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    val fx = state.effects
    val enabled = !state.isProcessing && !state.isRecommending
    var goal by rememberSaveable { mutableStateOf("") }
    Text(s.tool("Presets & AI assistant", "គំរូ និងជំនួយការ AI"), style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(s.tool("Speech", "ការនិយាយ") to AudioPresets.speech,
            s.tool("Music", "តន្ត្រី") to AudioPresets.music,
            s.tool("Room", "បន្ទប់") to AudioPresets.room).forEach { (label, preset) ->
            OutlinedButton(onClick = { viewModel.updateEffects(preset) }, enabled = enabled, contentPadding = PaddingValues(8.dp)) { Text(label) }
        }
    }
    OutlinedTextField(value = goal, onValueChange = { goal = it.take(500) }, enabled = enabled,
        label = { Text(s.tool("Desired sound", "សំឡេងដែលចង់បាន")) },
        placeholder = { Text(s.tool("Clear podcast voice, less bass, no echo…", "សំឡេងនិយាយច្បាស់ កាត់បាស គ្មានអេកូ…")) },
        modifier = Modifier.fillMaxWidth().testTag("audio_ai_goal"))
    Text(s.tool("Groq AI suggests settings from your description, not by listening. Only this description is sent. Requires a Groq key in Settings. Review the controls before export.",
        "Groq AI ណែនាំតាមការពិពណ៌នា មិនស្តាប់ឯកសារសំឡេងទេ។ ផ្ញើតែការពិពណ៌នា។ ត្រូវការសោ Groq ក្នុងការកំណត់។ ពិនិត្យមុននាំចេញ។"),
        style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { viewModel.recommendEffects(context, goal) }, enabled = enabled && goal.isNotBlank(),
        modifier = Modifier.testTag("audio_ai_recommend")) {
        Text(if (state.isRecommending) s.tool("Recommending…", "កំពុងណែនាំ…") else s.tool("Recommend settings", "ណែនាំការកំណត់"))
    }
    if (state.isRecommending) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.recommendation?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    EffectSlider(s.tool("Gain", "កម្រិតសំឡេង"), fx.gainDb, -18f..18f, "dB", enabled) { viewModel.updateEffects(fx.copy(gainDb = it)) }
    EffectSlider(s.tool("Bass EQ", "EQ បាស"), fx.bassDb, -12f..12f, "dB", enabled) { viewModel.updateEffects(fx.copy(bassDb = it)) }
    EffectSlider(s.tool("Mid EQ", "EQ មធ្យម"), fx.midDb, -12f..12f, "dB", enabled) { viewModel.updateEffects(fx.copy(midDb = it)) }
    EffectSlider(s.tool("Treble EQ", "EQ ខ្ពស់"), fx.trebleDb, -12f..12f, "dB", enabled) { viewModel.updateEffects(fx.copy(trebleDb = it)) }
    EffectSlider(s.tool("Echo mix", "អេកូ"), fx.echoMix * 100, 0f..60f, "%", enabled) { viewModel.updateEffects(fx.copy(echoMix = it / 100)) }
    EffectSlider(s.tool("Echo delay", "ពេលពន្យារអេកូ"), fx.echoDelayMs.toFloat(), 50f..800f, "ms", enabled) { viewModel.updateEffects(fx.copy(echoDelayMs = it.toInt())) }
    EffectSlider(s.tool("Fade in / out", "បន្ថយដើម / ចុង"), fx.fadeMs.toFloat(), 0f..3000f, "ms", enabled) { viewModel.updateEffects(fx.copy(fadeMs = it.toInt())) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(s.tool("Compressor", "បង្រួមកម្រិតសំឡេង"), Modifier.weight(1f))
        Switch(fx.compressor, onCheckedChange = { viewModel.updateEffects(fx.copy(compressor = it)) }, enabled = enabled)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(s.tool("Noise gate", "កាត់សំឡេងរំខានស្រាល"), Modifier.weight(1f))
        Switch(fx.noiseGate, onCheckedChange = { viewModel.updateEffects(fx.copy(noiseGate = it)) }, enabled = enabled)
    }
    Text(s.tool("Noise gate reduces very quiet sounds; it is not AI noise removal. Effects are heard in the exported file; the source player above is unprocessed.",
        "Noise gate កាត់សំឡេងស្រាល មិនមែនលុបសំឡេងរំខានដោយ AI ទេ។ បែបផែនមានក្នុងឯកសារនាំចេញ។ កម្មវិធីចាក់ខាងលើជាសំឡេងដើម។"), style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { viewModel.updateEffects(AudioEffects()) }, enabled = enabled) { Text(s.tool("Reset effects", "កំណត់បែបផែនឡើងវិញ")) }
}

@Composable
private fun EffectSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String,
    enabled: Boolean, onChange: (Float) -> Unit) {
    Column {
        Text("$label: ${"%.1f".format(value)} $unit")
        Slider(value, onValueChange = onChange, valueRange = range, enabled = enabled)
    }
}

@Composable
fun TranscriptProviderSelector(provider: TranscriptProvider, status: TranscriptStatus, onChange: (TranscriptProvider) -> Unit) {
    val s = LocalAppStrings.current
    val enabled = status is TranscriptStatus.Idle || status is TranscriptStatus.Success || status is TranscriptStatus.Error
    Text(s.tool("Transcription model", "ម៉ូដែលបម្លែងសំឡេង"), style = MaterialTheme.typography.titleMedium)
    Column {
        TranscriptProvider.entries.forEach { option ->
            FilterChip(selected = provider == option, onClick = { onChange(option) }, enabled = enabled,
                label = { Text(option.label) }, modifier = Modifier.testTag("transcript_provider_${option.name}"))
        }
    }
    Text(if (provider == TranscriptProvider.GROQ_WHISPER)
        s.tool("Whisper Large v3 · maximum 25 MB per file · auto-detect source language (90+ languages). Media is uploaded to Groq only when you generate a transcript.",
            "Whisper Large v3 · អតិបរមា 25 MB · ស្គាល់ភាសាដើមស្វ័យប្រវត្តិ (ជាង 90 ភាសា)។ ឯកសារត្រូវបានផ្ញើទៅ Groq ពេលបង្កើតអត្ថបទ។")
    else s.tool("Media is uploaded to Gemini only when you generate a transcript.", "ឯកសារត្រូវបានផ្ញើទៅ Gemini ពេលបង្កើតអត្ថបទ។"),
        style = MaterialTheme.typography.bodySmall)
}
