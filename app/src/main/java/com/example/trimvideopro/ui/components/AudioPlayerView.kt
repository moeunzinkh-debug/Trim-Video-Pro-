package com.example.trimvideopro.ui.components

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale

/** A small audio-only preview used when the selected source has no video track. */
@Composable
fun AudioPlayerView(
    audioUri: Uri,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var player by remember(audioUri) { mutableStateOf<MediaPlayer?>(null) }
    var isPrepared by remember(audioUri) { mutableStateOf(false) }
    var isPlaying by remember(audioUri) { mutableStateOf(false) }
    var durationMs by remember(audioUri) { mutableStateOf(0) }

    DisposableEffect(audioUri) {
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        try {
            mediaPlayer.setDataSource(context, audioUri)
            mediaPlayer.setOnPreparedListener {
                durationMs = it.duration
                isPrepared = true
            }
            mediaPlayer.setOnCompletionListener {
                isPlaying = false
            }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                isPrepared = false
                isPlaying = false
                true
            }
            mediaPlayer.prepareAsync()
        } catch (_: Exception) {
            isPrepared = false
        }

        onDispose {
            isPlaying = false
            isPrepared = false
            player = null
            mediaPlayer.release()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AudioFile,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Audio preview",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (isPrepared) formatDuration(durationMs) else "Preparing audio…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = {
                    player?.let { mediaPlayer ->
                        if (isPrepared) {
                            if (mediaPlayer.isPlaying) {
                                mediaPlayer.pause()
                                isPlaying = false
                            } else {
                                mediaPlayer.start()
                                isPlaying = true
                            }
                        }
                    }
                },
                enabled = isPrepared
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play"
                )
            }
        }
    }
}

private fun formatDuration(durationMs: Int): String {
    val totalSeconds = durationMs / 1000
    return String.format(
        Locale.getDefault(),
        "%02d:%02d",
        totalSeconds / 60,
        totalSeconds % 60
    )
}
