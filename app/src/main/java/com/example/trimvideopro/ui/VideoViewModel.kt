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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class VideoUiState(
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

    private val _uiState = MutableStateFlow(VideoUiState())
    val uiState: StateFlow<VideoUiState> = _uiState.asStateFlow()

    init {
        val context = getApplication<Application>()
        val savedLanguage = AppPreferences.getLanguage(context) ?: AppLanguage.fromLocale()
        _uiState.update {
            it.copy(
                language = savedLanguage,
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
                    selectedTranscriptChunk = null,
                    statusMessage = null
                )
            }
            val meta = VideoProcessor.extractMetadata(context, uri)
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
        val validStart = startMs.coerceIn(0L, meta.durationMs - 500L)
        val validEnd = endMs.coerceIn(validStart + 500L, meta.durationMs)
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
        viewModelScope.launch {
            val context = getApplication<Application>()
            val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "TrimmedVideos")
            val list = mutableListOf<ProcessedVideo>()

            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && file.extension == "mp4") {
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

    // ========== Transcript Functions ==========

    fun generateTranscript(context: Context, language: String = "auto") {
        val uri = _uiState.value.selectedVideoUri ?: return
        val strings = if (_uiState.value.language == AppLanguage.KHMER) KhmerStrings else EnglishStrings

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    transcriptStatus = TranscriptStatus.ExtractingAudio,
                    transcriptProgress = 0f,
                    transcriptProgressMessage = strings.extractingAudio
                )
            }

            val requestedLanguage = when (language.lowercase()) {
                "en", "english" -> "en"
                "km", "khmer" -> "km"
                else -> "auto"
            }
            val config = TranscriptConfig(language = requestedLanguage)
            val result = TranscriptProcessor.generateTranscript(
                context = context,
                videoUri = uri,
                config = config
            ) { progress, message ->
                _uiState.update {
                    it.copy(
                        transcriptProgress = progress,
                        transcriptProgressMessage = message
                    )
                }
            }

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
                        transcriptStatus = TranscriptStatus.Error(strings.transcriptError),
                        transcriptProgress = 0f,
                        transcriptProgressMessage = strings.transcriptError
                    )
                }
                Toast.makeText(context, strings.transcriptError, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun showTranscriptDialog(show: Boolean) {
        _uiState.update { it.copy(showTranscriptDialog = show) }
    }

    fun selectTranscriptChunk(chunk: TranscriptChunk?) {
        _uiState.update { it.copy(selectedTranscriptChunk = chunk) }
    }

    fun exportTranscriptToSrt(context: Context) {
        val transcript = _uiState.value.transcript ?: return
        val strings = if (_uiState.value.language == AppLanguage.KHMER) KhmerStrings else EnglishStrings

        viewModelScope.launch {
            try {
                val outputDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "Transcripts")
                if (!outputDir.exists()) outputDir.mkdirs()

                val fileName = "transcript_${System.currentTimeMillis() % 10000}.srt"
                val outputFile = File(outputDir, fileName)

                val success = TranscriptProcessor.exportToSrt(transcript, outputFile.absolutePath)
                if (success) {
                    // Also save to Downloads folder
                    saveToDownloads(context, outputFile, "srt")
                    Toast.makeText(context, strings.srtExportedSuccess, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, strings.errorSaving, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, strings.errorSaving, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun exportTranscriptToVtt(context: Context) {
        val transcript = _uiState.value.transcript ?: return
        val strings = if (_uiState.value.language == AppLanguage.KHMER) KhmerStrings else EnglishStrings

        viewModelScope.launch {
            try {
                val outputDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "Transcripts")
                if (!outputDir.exists()) outputDir.mkdirs()

                val fileName = "transcript_${System.currentTimeMillis() % 10000}.vtt"
                val outputFile = File(outputDir, fileName)

                val success = TranscriptProcessor.exportToVtt(transcript, outputFile.absolutePath)
                if (success) {
                    saveToDownloads(context, outputFile, "vtt")
                    Toast.makeText(context, strings.vttExportedSuccess, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, strings.errorSaving, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, strings.errorSaving, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun exportTranscriptToText(context: Context) {
        val transcript = _uiState.value.transcript ?: return
        val strings = if (_uiState.value.language == AppLanguage.KHMER) KhmerStrings else EnglishStrings

        viewModelScope.launch {
            try {
                val outputDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "Transcripts")
                if (!outputDir.exists()) outputDir.mkdirs()

                val fileName = "transcript_${System.currentTimeMillis() % 10000}.txt"
                val outputFile = File(outputDir, fileName)

                val success = TranscriptProcessor.exportToTextWithTimestamps(transcript, outputFile.absolutePath)
                if (success) {
                    saveToDownloads(context, outputFile, "txt")
                    Toast.makeText(context, strings.txtExportedSuccess, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, strings.errorSaving, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, strings.errorSaving, Toast.LENGTH_SHORT).show()
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

    private fun saveToDownloads(context: Context, sourceFile: File, extension: String) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, sourceFile.name)
                    put(MediaStore.Downloads.MIME_TYPE, when (extension) {
                        "srt" -> "application/x-subrip"
                        "vtt" -> "text/vtt"
                        else -> "text/plain"
                    })
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TrimVideoPro/Transcripts")
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        sourceFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val trimDir = File(downloadsDir, "TrimVideoPro/Transcripts")
                if (!trimDir.exists()) trimDir.mkdirs()
                val destFile = File(trimDir, sourceFile.name)
                sourceFile.copyTo(destFile, overwrite = true)
            }
        } catch (e: Exception) {
            // Silently fail, file is already saved to app directory
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
