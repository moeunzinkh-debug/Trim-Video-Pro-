package com.example.trimvideopro

import com.example.trimvideopro.processor.GroqProcessor
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GroqTranscriptTest {
    @Test fun preservesWhisperTimestampsAndKhmerText() {
        val transcript = GroqProcessor.parseTranscript("""{
            "language":"khmer", "segments":[
                {"start":0.25,"end":1.75,"text":" សួស្តី "},
                {"start":2.5,"end":3.125,"text":"លោកអ្នក"}
            ]
        }""", "content://media/1", "auto", 4000)
        check(transcript.language == "km")
        check(transcript.chunks[0].startMs == 250L)
        check(transcript.chunks[1].endMs == 3125L)
        check(transcript.chunks[0].text == "សួស្តី")
        check(transcript.toSrtFormat().contains("00:00:00,250 --> 00:00:01,750"))
    }

    @Test fun autoDetectionRetainsOtherSourceLanguages() {
        val transcript = GroqProcessor.parseTranscript("""{
            "language":"thai", "text":"สวัสดี", "duration":1.25
        }""", "source", "auto", 2000)
        check(transcript.language == "th")
        check(transcript.getFullText() == "สวัสดี")
    }

    @Test fun emptyAndInvalidSegmentsAreIgnored() {
        val transcript = GroqProcessor.parseTranscript("""{"language":"english","segments":[
            {"start":0,"end":1,"text":" "},
            {"start":-1,"end":2,"text":"Invalid"},
            {"start":2,"end":3,"text":"Hello"}
        ]}""", "source", "auto", 3000)
        check(transcript.chunks.size == 1)
        check(transcript.chunks.single().index == 0)
        check(transcript.language == "en")
    }

    @Test fun fallsBackToFullTextWhenSegmentsAreAbsent() {
        val transcript = GroqProcessor.parseTranscript("""{"text":"Hello world","duration":1.5}""", "source", "en", 2000)
        check(transcript.chunks.single().endMs == 1500L)
        check(transcript.getFullText() == "Hello world")
    }

    @Test fun missingSpeechIsAnActionableError() {
        check(runCatching { GroqProcessor.parseTranscript("""{"text":"","segments":[]}""", "source", "auto", 0) }.isFailure)
    }
}
