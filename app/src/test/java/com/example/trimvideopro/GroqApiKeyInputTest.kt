package com.example.trimvideopro

import com.example.trimvideopro.settings.GroqApiKeyInput
import okhttp3.Request
import org.junit.Test

class GroqApiKeyInputTest {
    // Synthetic test values only, not working provider credentials.
    private val fakeKey = "gsk_notARealKeyForTesting1234567890"

    @Test fun fixesEmbeddedNewlinesLikeTheReportedAuthorizationError() {
        val fakeLongKey = "gsk_" + "FakeKey123".repeat(8)
        val pasted = fakeLongKey.take(56) + "\n" + fakeLongKey.drop(56)
        check(GroqApiKeyInput.requireValid(pasted) == fakeLongKey)
    }

    @Test fun removesPastedSpacesCrLfAndTabs() {
        val pasted = "  gsk_notAReal\r\nKeyFor\tTesting1234 567890\n"
        check(GroqApiKeyInput.requireValid(pasted) == fakeKey)
    }

    @Test fun removesNbspAndCommonInvisibleFormatting() {
        val pasted = "\uFEFFgsk_notAReal\u00A0KeyFor\u200BTesting\u20601234567890\u200D"
        check(GroqApiKeyInput.requireValid(pasted) == fakeKey)
    }

    @Test fun acceptsCopiedBearerOrQuotedKeyWithoutDuplicatingBearer() {
        for (pasted in listOf("Bearer $fakeKey", "bearer\n$fakeKey", "\"$fakeKey\"", "'$fakeKey'", "`$fakeKey`", "Bearer \"$fakeKey\"")) {
            check(GroqApiKeyInput.requireValid(pasted) == fakeKey)
        }
    }

    @Test fun leavesCleanKeysUnchangedAndNormalizationIsIdempotent() {
        check(GroqApiKeyInput.normalize(fakeKey) == fakeKey)
        val normalized = GroqApiKeyInput.normalize(" Bearer\n$fakeKey ")
        check(GroqApiKeyInput.normalize(normalized) == normalized)
    }

    @Test fun rejectsLabelsControlsUnicodeAndHeaderInjectionCharacters() {
        for (invalid in listOf("Groq API key=$fakeKey", "not-a-key", "gsk_bad:token", "gsk_bad\u0000token", "gsk_bad\u007Ftoken", "gsk_ខ្មែរ", "gsk_key\r\nX-Injected:evil", "gsk_")) {
            check(!GroqApiKeyInput.isValid(invalid))
            check(runCatching { GroqApiKeyInput.requireValid(invalid) }.isFailure)
        }
    }

    @Test fun emptyInputProducesActionableMissingKeyError() {
        for (empty in listOf("", " \r\n\t", "\u200B\uFEFF")) {
            val exception = runCatching { GroqApiKeyInput.requireValid(empty) }.exceptionOrNull()
            check(exception is IllegalArgumentException)
            check(exception.message == GroqApiKeyInput.MISSING_MESSAGE)
        }
    }

    @Test fun invalidKeyErrorNeverContainsThePastedCredential() {
        val invalid = "gsk_fakeSensitiveValue:doNotEcho"
        val exception = runCatching { GroqApiKeyInput.requireValid(invalid) }.exceptionOrNull()!!
        check(exception.message == GroqApiKeyInput.INVALID_MESSAGE)
        check(!exception.message.orEmpty().contains(invalid))
        check(!exception.message.orEmpty().contains("fakeSensitiveValue"))
    }

    @Test fun normalizedKeyBuildsSafeAuthorizationHeaderWithoutNetworkCall() {
        val key = GroqApiKeyInput.requireValid("Bearer\n\"gsk_notAReal\r\nKeyForTesting1234567890\"")
        val request = Request.Builder().url("https://api.groq.com/openai/v1/audio/transcriptions")
            .header("Authorization", "Bearer $key").build()
        check(request.header("Authorization") == "Bearer $fakeKey")
        check(request.header("Authorization")!!.all { it.code in 32..126 })
    }
}
