package com.example.trimvideopro

import com.example.trimvideopro.model.Transcript
import com.example.trimvideopro.model.TranscriptChunk
import com.example.trimvideopro.processor.TranscriptExportFormat
import com.example.trimvideopro.processor.TranscriptExportProcessor
import org.junit.Test
import java.util.Locale

class TranscriptExportTest {
    private val transcript = Transcript("source", listOf(TranscriptChunk(0, 1250, 3750, "សួស្តី")), "km")

    @Test fun srtKeepsKhmerAndCommaMilliseconds() {
        val text = TranscriptExportProcessor.content(transcript, TranscriptExportFormat.SRT)
        check(text.startsWith("1\n00:00:01,250 --> 00:00:03,750\nសួស្តី"))
    }

    @Test fun vttHasHeaderAndDotMillisecondsWithoutChangingSpokenText() {
        val text = TranscriptExportProcessor.content(transcript, TranscriptExportFormat.VTT)
        check(text.startsWith("WEBVTT\n\n1\n00:00:01.250 --> 00:00:03.750"))
        check(text.contains("សួស្តី"))
        val literal = transcript.copy(chunks = listOf(transcript.chunks[0].copy(text = "00:00:01,250")))
        check(TranscriptExportProcessor.content(literal, TranscriptExportFormat.VTT).contains("\n00:00:01,250\n"))
    }

    @Test fun textExportAndDirectoryAreExplicit() {
        check(TranscriptExportProcessor.content(transcript, TranscriptExportFormat.TEXT).contains("សួស្តី"))
        check(TranscriptExportFormat.TEXT.extension == "txt")
        check(TranscriptExportFormat.VTT.mimeType == "text/vtt")
        check(TranscriptExportProcessor.DIRECTORY == "TrimVideoPro/Transcripts")
    }

    @Test fun subtitleDigitsDoNotDependOnPhoneLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar"))
            check(transcript.toSrtFormat().contains("00:00:01,250"))
        } finally { Locale.setDefault(previous) }
    }
}
