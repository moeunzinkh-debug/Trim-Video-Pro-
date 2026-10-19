package com.example.trimvideopro

import com.example.trimvideopro.processor.EnergyVad
import com.example.trimvideopro.processor.SpeechChunkWriter
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

class SpeechChunkWriterTest {
    @Test fun hardLimitBoundsEveryUploadAndPreservesSourceOffsets() {
        val directory = Files.createTempDirectory("speech-test").toFile()
        val writer = SpeechChunkWriter(directory, softLimitSamples = 1000, hardLimitSamples = 1600)
        try {
            repeat(4000) { writer.append(0.1f) }
            val chunks = writer.finish()
            check(chunks.size == 3)
            check(chunks.map { it.sourceStartMs } == listOf(0L, 100L, 200L))
            check(chunks.map { it.durationMs } == listOf(100L, 100L, 50L))
            check(chunks.sumOf { (it.file.length() - 44) / 2 } == 4000L)
            chunks.forEach {
                check(it.file.length() <= 44 + 3200)
                val header = ByteBuffer.wrap(it.file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
                check(header.getInt(24) == 16000)
                check(header.getShort(22).toInt() == 1)
                check(header.getInt(40) == it.file.length().toInt() - 44)
            }
        } finally { writer.close(); directory.deleteRecursively() }
    }

    @Test fun quietBoundariesArePreferredAfterSoftLimit() {
        val directory = Files.createTempDirectory("speech-vad-test").toFile()
        val writer = SpeechChunkWriter(directory, softLimitSamples = 16000, hardLimitSamples = 32000)
        try {
            repeat(16000) { writer.append(0.1f) }
            repeat(8000) { writer.append(0f) }
            repeat(16000) { writer.append(0.1f) }
            val chunks = writer.finish()
            check(chunks.first().durationMs == 1500L)
            check(chunks[1].sourceStartMs == 1500L)
            check(chunks.sumOf { it.durationMs } == 2500L)
        } finally { writer.close(); directory.deleteRecursively() }
    }

    @Test fun digitalSilenceIsSkippedButQuietSpeechIsRetained() {
        val directory = Files.createTempDirectory("speech-quiet-test").toFile()
        val writer = SpeechChunkWriter(directory, 1600, 1600)
        try {
            repeat(1600) { writer.append(0f) }
            repeat(1600) { writer.append(0.0005f) }
            val chunks = writer.finish()
            check(chunks.size == 1)
            check(chunks.single().sourceStartMs == 100L)
        } finally { writer.close(); check(directory.listFiles()!!.isEmpty()); directory.deleteRecursively() }
    }

    @Test fun cleanupRemovesCompletedAndUnfinishedFiles() {
        val directory = Files.createTempDirectory("speech-cleanup-test").toFile()
        val writer = SpeechChunkWriter(directory, 1600, 1600)
        repeat(1800) { writer.append(0.1f) }
        writer.close()
        check(directory.listFiles()!!.isEmpty())
        directory.deleteRecursively()
    }

    @Test fun vadDistinguishesSilenceFromEnergy() {
        val vad = EnergyVad()
        repeat(319) { check(vad.accept(0f) == null) }
        check(vad.accept(0f) == false)
        repeat(319) { check(vad.accept(0.1f) == null) }
        check(vad.accept(0.1f) == true)
    }
}
