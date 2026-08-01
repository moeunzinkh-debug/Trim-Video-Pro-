package com.example.trimvideopro.model

import android.net.Uri

data class VideoMetadata(
    val uri: Uri,
    val fileName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fileSize: Long,
    val mimeType: String = "video/mp4",
    val frameRate: Float = 30f
)

data class ClipSegment(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val title: String
) {
    val durationMs: Long get() = endMs - startMs
}

data class ProcessedVideo(
    val id: String,
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val fileSize: Long,
    val timestampMs: Long = System.currentTimeMillis()
)

enum class SplitMode {
    BY_DURATION,
    BY_PARTS
}
