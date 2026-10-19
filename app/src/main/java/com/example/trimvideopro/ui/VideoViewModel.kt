package com.example.trimvideopro.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.trimvideopro.localization.tool
import com.example.trimvideopro.processor.TranscriptExportProcessor
import com.example.trimvideopro.processor.TranscriptExportFormat
import com.example.trimvideopro.localization.AppLanguage
import com.example.trimvideopro.localization.EnglishStrings
import com.example.trimvideopro.localization.KhmerStrings
import com.example.trimvideopro.model.ClipSegment
import com.example.trimvideopro.model.ProcessedVideo
import com.example.trimvideopro.model.SplitMode
import com.example.trimvideopro.model.Transcript
import com.example.trimvideopro.model.TranscriptChunk
import com.example.trimvideopro.model.TranscriptConfig
import com.example.trimvideopro.model.TranscriptStatus
import com.example.trimvideopro.model.VideoMetadata
import com.example.trimvideopro.processor.TranscriptProcessor
import com.example.trimvideopro.processor.VideoProcessor
import com.example.trimvideopro.settings.AppPreferences
import com.example.trimvideopro.model.AudioEffects
import com.example.trimvideopro.model.TranscriptLanguages
import com.example.trimvideopro.model.TranscriptProvider
import com.example.trimvideopro.processor.AudioProcessor
import com.example.trimvideopro.processor.AudioSegments
import com.example.trimvideopro.processor.MergeProcessor
import com.example.trimvideopro.processor.GroqProcessor
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class VideoUiState(
    val mergeSources: List<VideoMetadata> = emptyList(),
    val isLoadingMerge: Boolean = false,
    val effects: AudioEffects = AudioEffects(),
    val isRecommending: Boolean = false,
    val recommendation: String? = null,
    val toolError: String? = null,
    val transcriptProvider: TranscriptProvider = TranscriptProvider.GEMINI,
    val language: AppLanguage = AppLanguage.fromLocale(),
    val selectedVideoUri: Uri? = null,
    /** True when a user-provided Gemini key is saved in private app preferences. */
    val apiKeyConfigured: Boolean = false,
    val metadata: VideoMetadata? = null,
    val isExtractingMetadata: Boolean = false,
    val previewThumbnail: Bitmap? = null,
    
    // Trim parameters
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 10000L,
    
    // Split parameters
    val splitMode: SplitMode = SplitMode.BY_DURATION,
    val splitIntervalSec: Int = 30,
    val splitPartsCount: Int = 3,
    val calculatedSegments: List<ClipSegment> = emptyList(),
    
    // Processing state
    val isProcessing: Boolean = false,
    val processingClipIndex: Int = 0,
    val totalClipsToProcess: Int = 1,
    val processingProgress: Float = 0f,
    val statusMessage: String? = null,
    
    // Transcript state
    val transcriptExportNotice: String? = null,
    val transcriptStatus: TranscriptStatus = TranscriptStatus.Idle,
    val transcript: Transcript? = null,
    val transcriptProgress: Float = 0f,
    val transcriptProgressMessage: String = "",
    val showTranscriptDialog: Boolean = false,
    val selectedTranscriptChunk: TranscriptChunk? = null,
    
    // Exported videos
    val exportedVideos: List<ProcessedVideo> = emptyList(),
    val completedClips: List<ProcessedVideo>? = null,
    val showCompletionDialog: Boolean = false,
    val showApkBuildDialog: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val showFeedbackDialog: Boolean = false,
    val showAboutUsDialog: Boolean = false
)

class VideoViewModel(application: Application) : AndroidViewModel(application) {

    private var transcriptJob: Job? = null
    private val _uiState = MutableStateFlow(VideoUiState())
    val uiState: StateFlow<VideoUiState> = _uiState.asStateFlow()

    init {
        val context = getApplication<Application>()
        val savedLanguage = AppPreferences.getLanguage(context) ?: AppLanguage.fromLocale()
        _uiState.update {
            it.copy(
                language = savedLanguage,
                transcriptProvider = AppPreferences.getTranscriptProvider(context),
                apiKeyConfigured = AppPreferences.getSavedApiKey(context).isNotBlank()
            )
        }
        loadExportedVideos()
    }

    fun toggleLanguage() {
        val nextLang = if (_uiState.value.language == AppLanguage.ENGLISH) {
            AppLanguage.KHMER
        } else {
            AppLanguage.ENGLISH
        }
        setLanguage(nextLang)
    }

    fun setLanguage(language: AppLanguage) {
        AppPreferences.saveLanguage(getApplication(), language)
        _uiState.update { it.copy(language = language) }
        recalculateSegments()
    }

    fun saveGeminiApiKey(context: Context, apiKey: String) {
        val cleanedKey = apiKey.trim()
        AppPreferences.saveApiKey(context, cleanedKey)
        _uiState.update { it.copy(apiKeyConfigured = cleanedKey.isNotBlank()) }
        val strings = if (_uiState.value.language == AppLanguage.KHMER) KhmerStrings else EnglishStrings
        Toast.makeText(
            context,
            if (cleanedKey.isBlank()) strings.apiKeyCleared else strings.apiKeySaved,
            Toast.LENGTH_SHORT
        ).show()
    }

    fun selectVideo(context: Context, uri: Uri) {
        if (_uiState.value.isProcessing) return
        transcriptJob?.cancel()
        viewModelScope.launch {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (ignored: Exception) {
            }
            _uiState.update {
                it.copy(
                    isExtractingMetadata = true,
                    selectedVideoUri = uri,
                    metadata = null,
                    previewThumbnail = null,
                    transcriptStatus = TranscriptStatus.Idle,
                    transcript = null,
                    transcriptExportNotice = null,
                    selectedTranscriptChunk = null,
                    statusMessage = null
                )
            }
            val meta = VideoProcessor.extractMetadata(context, uri)
            if (_uiState.value.selectedVideoUri != uri) return@launch
            if (meta != null) {
                val thumb = if (meta.mimeType.startsWith("audio/")) {
                    null
                } else {
                    VideoProcessor.extractThumbnail(context, uri, positionMs = meta.durationMs / 4)
                }
                _uiState.update {
                    it.copy(
                        metadata = meta,
                        isExtractingMetadata = false,
                        previewThumbnail = thumb,
                        trimStartMs = 0L,
                        trimEndMs = meta.durationMs
                    )
                }
                recalculateSegments()
            } else {
                val errorMsg = if (_uiState.value.language == AppLanguage.KHMER) KhmerStrings.errorProcessing else EnglishStrings.errorProcessing
                _uiState.update { it.copy(isExtractingMetadata = false, statusMessage = errorMsg) }
            }
        }
    }

    fun updateTrimRange(startMs: Long, endMs: Long) {
        val meta = _uiState.value.metadata ?: return
        if (meta.durationMs <= 0) return
        val minimum = minOf(500L, meta.durationMs)
        val validStart = startMs.coerceIn(0L, meta.durationMs - minimum)
        val validEnd = endMs.coerceIn(validStart + minimum, meta.durationMs)
        _uiState.update {
            it.copy(trimStartMs = validStart, trimEndMs = validEnd)
        }
    }

    fun adjustTrimStart(deltaMs: Long) {
        val meta = _uiState.value.metadata ?: return
        val newStart = (_uiState.value.trimStartMs + deltaMs).coerceIn(0L, _uiState.value.trimEndMs - 500L)
        _uiState.update { it.copy(trimStartMs = newStart) }
    }

    fun adjustTrimEnd(deltaMs: Long) {
        val meta = _uiState.value.metadata ?: return
        val newEnd = (_uiState.value.trimEndMs + deltaMs).coerceIn(_uiState.value.trimStartMs + 500L, meta.durationMs)
        _uiState.update { it.copy(trimEndMs = newEnd) }
    }

    fun setSplitMode(mode: SplitMode) {
        _uiState.update { it.copy(splitMode = mode) }
        recalculateSegments()
    }

    fun setSplitIntervalSec(sec: Int) {
        val valid = sec.coerceIn(1, 3600)
        _uiState.update { it.copy(splitIntervalSec = valid) }
        recalculateSegments()
    }

    fun setSplitPartsCount(parts: Int) {
        val valid = parts.coerceIn(2, 50)
        _uiState.update { it.copy(splitPartsCount = valid) }
        recalculateSegments()
    }

    private fun recalculateSegments() {
        val meta = _uiState.value.metadata ?: return
        val durationMs = meta.durationMs
        if (durationMs <= 0) return

        val segments = mutableListOf<ClipSegment>()
        val strings = if (_uiState.value.language == AppLanguage.KHMER) KhmerStrings else EnglishStrings

        if (_uiState.value.splitMode == SplitMode.BY_DURATION) {
            val intervalMs = _uiState.value.splitIntervalSec * 1000L
            var currentStart = 0L
            var clipIndex = 1

            while (currentStart < durationMs) {
                val currentEnd = (currentStart + intervalMs).coerceAtMost(durationMs)
                if (currentEnd - currentStart >= 300L) { // Min 300ms clip
                    segments.add(
                        ClipSegment(
                            index = clipIndex,
                            startMs = currentStart,
                            endMs = currentEnd,
                            title = "${strings.clipPrefix} $clipIndex"
                        )
                    )
                    clipIndex++
                }
                currentStart = currentEnd
            }
        } else {
            val parts = _uiState.value.splitPartsCount.coerceAtLeast(2)
            val partLengthMs = durationMs / parts

            for (i in 0 until parts) {
                val start = i * partLengthMs
                val end = if (i == parts - 1) durationMs else (i + 1) * partLengthMs
                if (end - start >= 300L) {
                    segments.add(
                        ClipSegment(
                            index = i + 1,
                            startMs = start,
                            endMs = end,
                            title = "${strings.clipPrefix} ${i + 1}"
                        )
                    )
                }
            }
        }

        _uiState.update { it.copy(calculatedSegments = segments) }
    }

    fun executeTrim(context: Context) {
        val meta = _uiState.value.metadata ?: return
        val uri = _uiState.value.selectedVideoUri ?: return
        val startMs = _uiState.value.trimStartMs
        val endMs = _uiState.value.trimEndMs
        val strings = if (_uiState.value.language == AppLanguage.KHMER) KhmerStrings else EnglishStrings

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingClipIndex = 1,
                    totalClipsToProcess = 1,
                    processingProgress = 0f,
                    statusMessage = null
                )
            }

            val fileName = "Trim_${System.currentTimeMillis() % 10000}.mp4"
            val result = VideoProcessor.trimVideo(context, uri, startMs, endMs, fileName) { progress ->
                _uiState.update { it.copy(processingProgress = progress) }
            }

            if (result != null) {
                Toast.makeText(context, strings.successTrim, Toast.LENGTH_SHORT).show()
                loadExportedVideos()
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = strings.successTrim,
                        completedClips = listOf(result),
                        showCompletionDialog = true
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = strings.errorProcessing
                    )
                }
            }
        }
    }

    fun executeSplit(context: Context) {
        val uri = _uiState.value.selectedVideoUri ?: return
        val segments = _uiState.value.calculatedSegments
        if (segments.isEmpty()) return

        val strings = if (_uiState.value.language == AppLanguage.KHMER) KhmerStrings else EnglishStrings

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingClipIndex = 1,
                    totalClipsToProcess = segments.size,
                    processingProgress = 0f,
                    statusMessage = null
                )
            }

            val results = VideoProcessor.splitVideo(context, uri, segments) { clipIdx, progress ->
                _uiState.update {
                    it.copy(
                        processingClipIndex = clipIdx,
                        processingProgress = progress
                    )
                }
            }

            if (results.isNotEmpty()) {
                Toast.makeText(context, strings.successSplit, Toast.LENGTH_SHORT).show()
                loadExportedVideos()
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = strings.successSplit,
                        completedClips = results,
                        showCompletionDialog = true
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = strings.errorProcessing
                    )
                }
            }
        }
    }

    fun saveVideoToGallery(context: Context, video: ProcessedVideo) {
        viewModelScope.launch {
            val success = VideoProcessor.saveToGallery(context, video.uri, video.title)
            val strings = if (_uiState.value.language == AppLanguage.KHMER) KhmerStrings else EnglishStrings
            if (success) {
                Toast.makeText(context, strings.savedToGallerySuccess, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, strings.errorSaving, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun saveVideoToStorage(context: Context, video: ProcessedVideo) {
        viewModelScope.launch {
            val success = VideoProcessor.saveToPublicStorage(context, video.uri, video.title)
            val strings = if (_uiState.value.language == AppLanguage.KHMER) KhmerStrings else EnglishStrings
            if (success) {
                Toast.makeText(context, strings.savedToStorageSuccess, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, strings.errorSaving, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun saveAllToGallery(context: Context, videos: List<ProcessedVideo>) {
        viewModelScope.launch {
            var count = 0
            videos.forEach { video ->
                if (VideoProcessor.saveToGallery(context, video.uri, video.title)) {
                    count++
                }
            }
            val strings = if (_uiState.value.language == AppLanguage.KHMER) KhmerStrings else EnglishStrings
            if (count > 0) {
                Toast.makeText(context, "${strings.savedToGallerySuccess} ($count)", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, strings.errorSaving, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun saveAllToStorage(context: Context, videos: List<ProcessedVideo>) {
        viewModelScope.launch {
            var count = 0
            videos.forEach { video ->
                if (VideoProcessor.saveToPublicStorage(context, video.uri, video.title)) {
                    count++
                }
            }
            val strings = if (_uiState.value.language == AppLanguage.KHMER) KhmerStrings else EnglishStrings
            if (count > 0) {
                Toast.makeText(context, "${strings.savedToStorageSuccess} ($count)", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, strings.errorSaving, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun dismissCompletionDialog() {
        _uiState.update { it.copy(showCompletionDialog = false, completedClips = null) }
    }

    fun refreshState(context: Context) {
        viewModelScope.launch {
            val meta = _uiState.value.metadata
            val strings = if (_uiState.value.language == AppLanguage.KHMER) KhmerStrings else EnglishStrings

            if (meta != null && _uiState.value.selectedVideoUri != null) {
                _uiState.update {
                    it.copy(
                        trimStartMs = 0L,
                        trimEndMs = meta.durationMs,
                        splitIntervalSec = 30,
                        splitPartsCount = 3,
                        statusMessage = strings.refreshedMsg
                    )
                }
                recalculateSegments()
            } else {
                _uiState.update {
                    it.copy(
                        selectedVideoUri = null,
                        metadata = null,
                        statusMessage = strings.refreshedMsg
                    )
                }
            }
            loadExportedVideos()
            Toast.makeText(context, strings.refreshedMsg, Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteExportedVideo(video: ProcessedVideo) {
        viewModelScope.launch {
            try {
                val file = File(video.uri.path ?: "")
                if (file.exists()) file.delete()
            } catch (ignored: Exception) {}
            loadExportedVideos()
        }
    }

    fun clearAllExportedVideos(context: Context) {
        viewModelScope.launch {
            val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "TrimmedVideos")
            if (dir.exists()) {
                dir.listFiles()?.forEach { file ->
                    file.delete()
                }
            }
            loadExportedVideos()
        }
    }

    fun toggleApkBuildDialog(show: Boolean) {
        _uiState.update { it.copy(showApkBuildDialog = show) }
    }

    fun toggleSettingsDialog(show: Boolean) {
        _uiState.update { it.copy(showSettingsDialog = show) }
    }

    fun toggleFeedbackDialog(show: Boolean) {
        _uiState.update { it.copy(showFeedbackDialog = show) }
    }

    fun toggleAboutUsDialog(show: Boolean) {
        _uiState.update { it.copy(showAboutUsDialog = show) }
    }

    fun clearCache(context: Context) {
        if (_uiState.value.isProcessing || _uiState.value.isRecommending ||
            (_uiState.value.transcriptStatus !is TranscriptStatus.Idle &&
             _uiState.value.transcriptStatus !is TranscriptStatus.Success &&
             _uiState.value.transcriptStatus !is TranscriptStatus.Error)) return
        viewModelScope.launch {
            try {
                context.cacheDir.deleteRecursively()
            } catch (ignored: Exception) {}
            val strings = if (_uiState.value.language == AppLanguage.KHMER) KhmerStrings else EnglishStrings
            Toast.makeText(context, strings.cacheCleared, Toast.LENGTH_SHORT).show()
        }
    }

    fun shareApp(context: Context) {
        try {
            val strings = if (_uiState.value.language == AppLanguage.KHMER) KhmerStrings else EnglishStrings
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, strings.appTitle)
                putExtra(android.content.Intent.EXTRA_TEXT, strings.shareAppText)
            }
            context.startActivity(android.content.Intent.createChooser(intent, strings.shareWithFriends))
        } catch (ignored: Exception) {}
    }

    fun sendFeedback(context: Context, feedbackText: String) {
        val strings = if (_uiState.value.language == AppLanguage.KHMER) KhmerStrings else EnglishStrings
        if (feedbackText.isNotBlank()) {
            Toast.makeText(context, strings.feedbackThanks, Toast.LENGTH_LONG).show()
            toggleFeedbackDialog(false)
        }
    }

    private fun loadExportedVideos() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "TrimmedVideos")
            val list = mutableListOf<ProcessedVideo>()

            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && file.extension.lowercase() in setOf("mp4", "wav", "m4a")) {
                        val retriever = MediaMetadataRetriever()
                        var durationMs = 0L
                        try {
                            retriever.setDataSource(file.absolutePath)
                            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            durationMs = dur?.toLongOrNull() ?: 0L
                        } catch (ignored: Exception) {
                        } finally {
                            try { retriever.release() } catch (ignored: Exception) {}
                        }

                        list.add(
                            ProcessedVideo(
                                id = file.name,
                                uri = Uri.fromFile(file),
                                title = file.name,
                                durationMs = durationMs,
                                fileSize = file.length(),
                                timestampMs = file.lastModified()
                            )
                        )
                    }
                }
            }

            list.sortByDescending { it.timestampMs }
            _uiState.update { it.copy(exportedVideos = list) }
        }
    }


    fun setTranscriptProvider(provider: TranscriptProvider) {
        AppPreferences.saveTranscriptProvider(getApplication(), provider)
        _uiState.update { it.copy(transcriptProvider = provider) }
    }

    fun updateEffects(effects: AudioEffects) {
        if (!_uiState.value.isProcessing && !_uiState.value.isRecommending) {
            _uiState.update { it.copy(effects = effects.bounded(), recommendation = null) }
        }
    }

    fun recommendEffects(context: Context, goal: String) {
        if (_uiState.value.isRecommending || _uiState.value.isProcessing) return
        _uiState.update { it.copy(isRecommending = true, toolError = null) }
        viewModelScope.launch {
            try {
                val (effects, reason) = GroqProcessor.recommend(context, goal)
                _uiState.update { it.copy(effects = effects, recommendation = reason) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { _uiState.update { it.copy(toolError = e.message ?: "Recommendation failed.") } } finally { _uiState.update { it.copy(isRecommending = false) } }
        }
    }

    fun addMergeSources(context: Context, uris: List<Uri>) {
        if (_uiState.value.isLoadingMerge || _uiState.value.isProcessing) return
        _uiState.update { it.copy(isLoadingMerge = true, toolError = null) }
        viewModelScope.launch {
            try {
                require(_uiState.value.mergeSources.size + uris.size <= 20) { "Maximum 20 videos." }
                val added = uris.map { uri ->
                    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    val meta = VideoProcessor.extractMetadata(context, uri) ?: error("Cannot read a selected video.")
                    require(!meta.mimeType.startsWith("audio/") && meta.durationMs > 0) { "Choose video files with a valid duration." }
                    meta
                }
                _uiState.update { it.copy(mergeSources = it.mergeSources + added) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { _uiState.update { it.copy(toolError = e.message) } } finally { _uiState.update { it.copy(isLoadingMerge = false) } }
        }
    }

    fun moveMergeSource(index: Int, delta: Int) {
        if (_uiState.value.isProcessing) return
        _uiState.update {
            val list = it.mergeSources.toMutableList()
            val target = index + delta
            if (index in list.indices && target in list.indices) {
                val item = list.removeAt(index)
                list.add(target, item)
            }
            it.copy(mergeSources = list)
        }
    }

    fun removeMergeSource(index: Int) {
        if (!_uiState.value.isProcessing) _uiState.update {
            it.copy(mergeSources = it.mergeSources.filterIndexed { i, _ -> i != index })
        }
    }

    fun executeMerge(context: Context) {
        val uris = _uiState.value.mergeSources.map { it.uri }
        runMediaTool {
            listOf(MergeProcessor.merge(context, uris) { progress ->
                _uiState.update { it.copy(processingProgress = progress) }
            })
        }
    }

    fun executeAudio(context: Context, split: Boolean = false, applyEffects: Boolean = false) {
        val state = _uiState.value
        val uri = state.selectedVideoUri ?: return
        val duration = state.metadata?.durationMs ?: return
        runMediaTool {
            val ranges = if (!split) listOf(0L to duration) else if (state.splitMode == SplitMode.BY_PARTS)
                AudioSegments.byParts(duration, state.splitPartsCount)
            else AudioSegments.byDuration(duration, state.splitIntervalSec)
            val results = mutableListOf<ProcessedVideo>()
            try {
                ranges.forEachIndexed { index, (start, end) ->
                    _uiState.update { it.copy(processingClipIndex = index + 1, totalClipsToProcess = ranges.size) }
                    results.add(AudioProcessor.render(context, uri, start, end,
                        if (applyEffects) state.effects else AudioEffects(),
                        if (split) "Split_Audio_${index + 1}" else if (applyEffects) "Edited_Audio" else "Extracted_Audio"
                    ) { progress -> _uiState.update { it.copy(processingProgress = progress) } })
                }
                results
            } catch (e: Throwable) {
                results.forEach { File(it.uri.path.orEmpty()).delete() }
                throw e
            }
        }
    }

    private fun runMediaTool(work: suspend () -> List<ProcessedVideo>) {
        if (_uiState.value.isProcessing || _uiState.value.isRecommending) return
        _uiState.update { it.copy(isProcessing = true, processingProgress = 0f, processingClipIndex = 1,
            totalClipsToProcess = 1, toolError = null) }
        viewModelScope.launch {
            try {
                val results = work()
                _uiState.update { it.copy(completedClips = results, showCompletionDialog = true) }
                loadExportedVideos()
            } catch (e: CancellationException) { throw e } catch (e: Exception) { _uiState.update { it.copy(toolError = e.message ?: "Media processing failed.") } } finally { _uiState.update { it.copy(isProcessing = false) } }
        }
    }

    // ========== Transcript Functions ==========

    fun generateTranscript(context: Context, language: String = "auto") {
        if (transcriptJob?.isActive == true) return
        if (_uiState.value.transcriptStatus !is TranscriptStatus.Idle &&
            _uiState.value.transcriptStatus !is TranscriptStatus.Success &&
            _uiState.value.transcriptStatus !is TranscriptStatus.Error) return
        val uri = _uiState.value.selectedVideoUri ?: return
        val strings = if (_uiState.value.language == AppLanguage.KHMER) KhmerStrings else EnglishStrings

        val provider = _uiState.value.transcriptProvider
        transcriptJob = viewModelScope.launch {
            val generationContext = coroutineContext
            _uiState.update {
                it.copy(
                    transcriptStatus = TranscriptStatus.ExtractingAudio,
                    transcriptProgress = 0f,
                    transcriptProgressMessage = strings.extractingAudio
                )
            }

            val requestedLanguage = TranscriptLanguages.normalize(language)
            val config = TranscriptConfig(language = requestedLanguage)
            var failure: String? = null
            val result = try {
                if (provider == TranscriptProvider.GROQ_WHISPER) {
                    GroqProcessor.transcribe(context, uri, requestedLanguage, _uiState.value.metadata?.durationMs ?: 0,
                        onPartialTranscript = { partial ->
                            generationContext.ensureActive()
                            _uiState.update { it.copy(transcript = partial) }
                        }
                    ) { progress, message ->
                        generationContext.ensureActive()
                        _uiState.update { it.copy(transcriptProgress = progress, transcriptProgressMessage = message) }
                    }
                } else TranscriptProcessor.generateTranscript(
                context = context,
                videoUri = uri,
                config = config
            ) { progress, message ->
                generationContext.ensureActive()
                _uiState.update {
                    it.copy(
                        transcriptProgress = progress,
                        transcriptProgressMessage = message
                    )
                }
            }

            } catch (e: CancellationException) { throw e } catch (e: Exception) { failure = e.message; null }
            if (_uiState.value.selectedVideoUri != uri) return@launch

            if (result != null) {
                _uiState.update {
                    it.copy(
                        transcriptStatus = TranscriptStatus.Success(result),
                        transcript = result,
                        transcriptProgress = 1f,
                        transcriptProgressMessage = strings.transcriptComplete
                    )
                }
                Toast.makeText(context, strings.transcriptComplete, Toast.LENGTH_SHORT).show()
            } else {
                _uiState.update {
                    it.copy(
                        transcriptStatus = TranscriptStatus.Error(failure ?: strings.transcriptError),
                        transcriptProgress = 0f,
                        transcriptProgressMessage = failure ?: strings.transcriptError
                    )
                }
                Toast.makeText(context, failure ?: strings.transcriptError, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun showTranscriptDialog(show: Boolean) {
        _uiState.update { it.copy(showTranscriptDialog = show) }
    }

    fun selectTranscriptChunk(chunk: TranscriptChunk?) {
        _uiState.update { it.copy(selectedTranscriptChunk = chunk) }
    }

    fun exportTranscriptToSrt(context: Context) = exportTranscript(context, TranscriptExportFormat.SRT)
    fun exportTranscriptToVtt(context: Context) = exportTranscript(context, TranscriptExportFormat.VTT)
    fun exportTranscriptToText(context: Context) = exportTranscript(context, TranscriptExportFormat.TEXT)

    private fun exportTranscript(context: Context, format: TranscriptExportFormat) {
        val transcript = _uiState.value.transcript ?: return
        val strings = if (_uiState.value.language == AppLanguage.KHMER) KhmerStrings else EnglishStrings
        viewModelScope.launch {
            try {
                val path = TranscriptExportProcessor.save(context, transcript, format)
                val message = strings.tool("Saved to $path", "បានរក្សាទុកនៅ $path")
                _uiState.update { it.copy(transcriptExportNotice = message) }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                val message = strings.tool("Download failed. Check storage space/permission. ${e.message}",
                    "ការរក្សាទុកទៅ Downloads បរាជ័យ។ សូមពិនិត្យទំហំផ្ទុក និងសិទ្ធិ។ ${e.message}")
                _uiState.update { it.copy(transcriptExportNotice = message) }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun shareTranscript(context: Context) {
        val transcript = _uiState.value.transcript ?: return
        val strings = if (_uiState.value.language == AppLanguage.KHMER) KhmerStrings else EnglishStrings

        try {
            val shareText = buildString {
                appendLine("📝 Media Transcript")
                appendLine("==================")
                appendLine()
                transcript.chunks.forEach { chunk ->
                    appendLine("[${formatTimestamp(chunk.startMs)}] ${chunk.text}")
                }
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, strings.shareTranscript)
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(intent, strings.shareTranscript))
        } catch (e: Exception) {
            Toast.makeText(context, strings.errorSharing, Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatTimestamp(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun clearTranscript() {
        transcriptJob?.cancel()
        _uiState.update {
            it.copy(
                transcriptStatus = TranscriptStatus.Idle,
                transcript = null,
                transcriptProgress = 0f,
                transcriptProgressMessage = "",
                selectedTranscriptChunk = null
            )
        }
    }
}
