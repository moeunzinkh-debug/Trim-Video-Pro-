package com.example.trimvideopro

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.trimvideopro.model.AudioEffects
import com.example.trimvideopro.processor.AudioProcessor
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sin

/** Exercises the real Android extractor and WAV writer on a device/emulator. */
@RunWith(AndroidJUnit4::class)
class AudioProcessorInstrumentedTest {
    @Test fun extractsExactRangeAndRendersEffects() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "test-source.wav")
        val rate = 16000
        val pcm = ByteBuffer.allocate(rate * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(rate) { frame -> pcm.putShort((sin(2 * Math.PI * 440 * frame / rate) * 10000).toInt().toShort()) }
        // Header built here independently of the production writer.
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(pcm.capacity() + 36).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1.toShort()).putShort(1.toShort()).putInt(rate).putInt(rate * 2)
            .putShort(2.toShort()).putShort(16.toShort()).put("data".toByteArray()).putInt(pcm.capacity()).array()
        source.outputStream().use { it.write(header); it.write(pcm.array()) }
        val outputs = mutableListOf<File>()
        try {
            val clean = AudioProcessor.render(context, Uri.fromFile(source), 250, 750, onProgress = {})
            outputs.add(File(clean.uri.path!!))
            val quiet = AudioProcessor.render(context, Uri.fromFile(source), 250, 750,
                AudioEffects(gainDb = -6f), onProgress = {})
            outputs.add(File(quiet.uri.path!!))
            check(abs(clean.durationMs - 500) <= 1)
            check(abs(clean.fileSize - (44 + rate)) <= 32)
            val data = outputs[0].readBytes()
            check(String(data.copyOfRange(0, 4)) == "RIFF")
            check(String(data.copyOfRange(8, 12)) == "WAVE")
            fun peak(bytes: ByteArray): Int {
                val buffer = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN)
                var peak = 0
                while (buffer.remaining() >= 2) peak = maxOf(peak, abs(buffer.short.toInt()))
                return peak
            }
            check(peak(outputs[1].readBytes()) < peak(data) * 0.6)
        } finally {
            source.delete()
            outputs.forEach { it.delete() }
        }
    }
}
