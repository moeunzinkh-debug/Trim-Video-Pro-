package com.example.trimvideopro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.trimvideopro.settings.AppPreferences
import com.example.trimvideopro.settings.GroqApiKeyInput
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GroqApiKeyPreferencesTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val fakeKey = "gsk_fakeKeyForPreferenceTests123"
    private fun store() = context.getSharedPreferences("trim_video_pro_preferences", Context.MODE_PRIVATE)

    @Before fun clearPreferences() { store().edit().clear().commit() }

    @Test fun repairsKeysSavedByOlderBuildsAutomatically() {
        store().edit().putString("groq_api_key", " gsk_fakeKeyFor\nPreferenceTests123\r ").commit()
        check(AppPreferences.getGroqApiKey(context) == fakeKey)
        check(store().getString("groq_api_key", null) == fakeKey)
    }

    @Test fun savesOnlyNormalizedKeys() {
        AppPreferences.saveGroqApiKey(context, "Bearer \"gsk_fakeKeyFor\nPreferenceTests123\"")
        check(store().getString("groq_api_key", null) == fakeKey)
    }

    @Test fun invalidSaveDoesNotReplaceWorkingKey() {
        AppPreferences.saveGroqApiKey(context, fakeKey)
        check(runCatching { AppPreferences.saveGroqApiKey(context, "gsk_invalid:header") }.isFailure)
        check(AppPreferences.getGroqApiKey(context) == fakeKey)
        store().edit().putString("groq_api_key", "gsk_invalid:header").commit()
        val read = AppPreferences.getGroqApiKey(context)
        check(runCatching { GroqApiKeyInput.requireValid(read) }.isFailure)
    }

    @Test fun whitespaceOnlyInputRemovesKey() {
        AppPreferences.saveGroqApiKey(context, fakeKey)
        AppPreferences.saveGroqApiKey(context, "\n\t\u200B ")
        check(AppPreferences.getGroqApiKey(context).isEmpty())
        check(!store().contains("groq_api_key"))
    }
}
