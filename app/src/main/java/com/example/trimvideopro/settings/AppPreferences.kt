package com.example.trimvideopro.settings

import android.content.Context
import com.example.trimvideopro.localization.AppLanguage

/**
 * Small, local settings store for values the user chooses in the app.
 *
 * The Gemini key is never written to the project or the build configuration. It is
 * kept only in the app's private preferences and can be removed from Settings at
 * any time. The key is intentionally not included in [VideoUiState] until the
 * settings sheet is opened, so it is not exposed by the public UI state flow.
 */
object AppPreferences {
    private const val PREFERENCES_NAME = "trim_video_pro_preferences"
    private const val GEMINI_API_KEY = "gemini_api_key"
    private const val APP_LANGUAGE = "app_language"

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getSavedApiKey(context: Context): String =
        preferences(context).getString(GEMINI_API_KEY, "").orEmpty()

    fun saveApiKey(context: Context, apiKey: String) {
        val cleanedKey = apiKey.trim()
        preferences(context).edit().apply {
            if (cleanedKey.isEmpty()) {
                remove(GEMINI_API_KEY)
            } else {
                putString(GEMINI_API_KEY, cleanedKey)
            }
        }.apply()
    }

    fun getLanguage(context: Context): AppLanguage? {
        val code = preferences(context).getString(APP_LANGUAGE, null) ?: return null
        return AppLanguage.entries.firstOrNull { it.code == code }
    }

    fun saveLanguage(context: Context, language: AppLanguage) {
        preferences(context).edit().putString(APP_LANGUAGE, language.code).apply()
    }
}
