package com.example.trimvideopro.settings

/** Copy/paste hygiene only. Never log or interpolate a credential in validation errors. */
object GroqApiKeyInput {
    private val keyPattern = Regex("gsk_[A-Za-z0-9_-]+")
    const val INVALID_MESSAGE = "Invalid Groq API key. Paste only your Groq key in Settings, without labels or other text."
    const val MISSING_MESSAGE = "Add your Groq API key in Settings first."

    fun normalize(input: String): String {
        // Mobile keyboards and copied web pages may insert NBSP, CR/LF, tabs or invisible formatting.
        var key = input.filterNot { character ->
            character.isWhitespace() || Character.isSpaceChar(character) || character in
                "\u200B\u200C\u200D\u2060\uFEFF"
        }
        key = unwrap(key)
        if (key.startsWith("Bearer", ignoreCase = true)) key = unwrap(key.substring(6))
        return key
    }

    private fun unwrap(value: String): String {
        var result = value
        for (quote in listOf('"', '\'', '`')) {
            if (result.length >= 2 && result.first() == quote && result.last() == quote) {
                result = result.substring(1, result.lastIndex)
            }
        }
        return result
    }

    fun isValid(input: String): Boolean = keyPattern.matches(normalize(input))

    /** Validate before media decoding or constructing any Authorization header. */
    fun requireValid(input: String): String {
        val key = normalize(input)
        require(key.isNotEmpty()) { MISSING_MESSAGE }
        require(keyPattern.matches(key)) { INVALID_MESSAGE }
        return key
    }
}
