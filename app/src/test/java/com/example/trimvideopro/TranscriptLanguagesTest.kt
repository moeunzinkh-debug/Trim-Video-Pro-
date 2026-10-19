package com.example.trimvideopro

import com.example.trimvideopro.model.TranscriptConfig
import com.example.trimvideopro.model.TranscriptLanguages
import org.junit.Test
import java.util.Locale

class TranscriptLanguagesTest {
    @Test fun detectionIsTheDefaultAndSendsNoLanguageHint() {
        check(TranscriptConfig().language == TranscriptLanguages.AUTO)
        check(TranscriptLanguages.apiHint("auto") == null)
        check(TranscriptLanguages.apiHint("") == null)
    }

    @Test fun catalogContains99UniqueSourceLanguages() {
        val languages = TranscriptLanguages.supported
        check(languages.size == 99)
        check(languages.map { it.code }.distinct().size == languages.size)
        check(languages.map { it.name }.distinct().size == languages.size)
        check(languages.all { TranscriptLanguages.apiHint(it.code) == it.code })
    }

    @Test fun supportsSourceLanguagesBeyondEnglishAndKhmer() {
        for (code in listOf("th", "zh", "ja", "vi", "fr", "es", "ar", "hi", "km", "en")) {
            check(TranscriptLanguages.normalize(code) == code)
            check(TranscriptLanguages.apiHint(code) == code)
        }
    }

    @Test fun providerLanguageNamesResolveToCodes() {
        check(TranscriptLanguages.normalize(" Thai ") == "th")
        check(TranscriptLanguages.normalize("chinese") == "zh")
        check(TranscriptLanguages.normalize("KHMER") == "km")
        check(TranscriptLanguages.normalize("javanese") == "jw")
        check(TranscriptLanguages.displayName("vi") == "Vietnamese")
    }

    @Test fun unknownValuesFallBackToDetectionInsteadOfEnglish() {
        check(TranscriptLanguages.normalize("unsupported-language") == "auto")
        check(TranscriptLanguages.apiHint("unsupported-language") == null)
    }

    @Test fun normalizationIsIndependentOfDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            check(TranscriptLanguages.normalize("HINDI") == "hi")
            check(TranscriptLanguages.normalize("VI") == "vi")
        } finally { Locale.setDefault(previous) }
    }
}
