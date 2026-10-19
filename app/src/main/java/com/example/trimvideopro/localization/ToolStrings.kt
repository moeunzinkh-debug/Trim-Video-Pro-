package com.example.trimvideopro.localization

fun AppStrings.tool(english: String, khmer: String): String = if (this === KhmerStrings) khmer else english

fun AppStrings.toolTitle(screen: com.example.trimvideopro.ui.AppScreen): String = when (screen) {
    com.example.trimvideopro.ui.AppScreen.MERGE -> tool("Merge videos", "បញ្ចូលវីដេអូ")
    com.example.trimvideopro.ui.AppScreen.EXTRACT_AUDIO -> tool("Extract audio", "ទាញយកសំឡេង")
    com.example.trimvideopro.ui.AppScreen.SPLIT_AUDIO -> tool("Split audio", "បំបែកសំឡេង")
    com.example.trimvideopro.ui.AppScreen.AUDIO_EDITOR -> tool("Audio studio", "កែសម្រួលសំឡេង")
    else -> ""
}
