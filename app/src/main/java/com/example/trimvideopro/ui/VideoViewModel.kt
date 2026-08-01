package com.example.trimvideopro.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.trimvideopro.localization.AppLanguage
import com.example.trimvideopro.localization.EnglishStrings
import com.example.trimvideopro.localization.KhmerStrings
import com.example.trimvideopro.model.ClipSegment
import com.example.trimvideopro.model.ProcessedVideo
import com.example.trimvideopro.model.SplitMode
import com.example.trimvideopro.model.VideoMetadata
import com.example.trimvideopro.processor.VideoProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class VideoUiState(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val selectedVideoUri: Uri? = null,
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
        loadExportedVideos()
    }

    fun toggleLanguage() {
        val nextLang = if (_uiState.value.language == AppLanguage.ENGLISH) AppLanguage.KHMER else AppLanguage.ENGLISH
        _uiState.update { it.copy(language = nextLang) }
    }

    fun setLanguage(language: AppLanguage) {
        _uiState.update { it.copy(language = language) }
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
            _uiState.update { it.copy(isExtractingMetadata = true, selectedVideoUri = uri, statusMessage = null) }
            val meta = VideoProcessor.extractMetadata(context, uri)
            if (meta != null) {
                val thumb = VideoProcessor.extractThumbnail(context, uri, positionMs = meta.durationMs / 4)
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
}
