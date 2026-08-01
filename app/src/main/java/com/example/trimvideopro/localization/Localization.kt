package com.example.trimvideopro.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

enum class AppLanguage(val code: String, val displayName: String, val flag: String) {
    ENGLISH("en", "English", "🇬🇧"),
    KHMER("km", "ភាសាខ្មែរ", "🇰🇭")
}

data class AppStrings(
    val appTitle: String,
    val offlineBadge: String,
    val selectVideo: String,
    val changeVideo: String,
    val refresh: String,
    val refreshedMsg: String,
    val tabTrim: String,
    val tabSplit: String,
    val tabHistory: String,
    val videoInfo: String,
    val duration: String,
    val fileSize: String,
    val resolution: String,
    
    // Trim
    val startTime: String,
    val endTime: String,
    val selectedDuration: String,
    val trimVideo: String,
    val adjustStart: String,
    val adjustEnd: String,
    val previewTrim: String,
    
    // Split
    val splitByTime: String,
    val splitByCount: String,
    val intervalSeconds: String,
    val intervalMinutes: String,
    val numberOfClips: String,
    val calculatedClips: String,
    val splitVideo: String,
    val clipPrefix: String,
    val totalClipsToGenerate: String,
    
    // History & Actions
    val noVideosExported: String,
    val exportedClips: String,
    val play: String,
    val share: String,
    val delete: String,
    val clearAll: String,
    val savedToStorage: String,
    val saveToGallery: String,
    val saveToStorage: String,
    val saveAllToGallery: String,
    val saveAllToStorage: String,
    val savedToGallerySuccess: String,
    val savedToStorageSuccess: String,
    val trimCompleteTitle: String,
    val splitCompleteTitle: String,
    val clipsGeneratedCount: String,
    val errorSaving: String,
    
    // Processing
    val processingVideo: String,
    val processingClip: String,
    val cancel: String,
    val successTrim: String,
    val successSplit: String,
    val errorProcessing: String,
    
    // Instructions / Apk Build
    val buildApkGuide: String,
    val buildApkSteps: String,
    val close: String,

    // Settings
    val settings: String,
    val clearCache: String,
    val cacheCleared: String,
    val language: String,
    val shareWithFriends: String,
    val shareAppText: String,
    val feedback: String,
    val feedbackTitle: String,
    val feedbackHint: String,
    val send: String,
    val feedbackThanks: String,
    val aboutUs: String,
    val aboutUsText: String
)

val EnglishStrings = AppStrings(
    appTitle = "Trim Video Pro",
    offlineBadge = "100% Offline • No Watermark",
    selectVideo = "Select Video to Edit",
    changeVideo = "Choose Different Video",
    refresh = "Refresh",
    refreshedMsg = "Video settings and state refreshed",
    tabTrim = "Trim Video",
    tabSplit = "Split Video",
    tabHistory = "Exported Clips",
    videoInfo = "Video Information",
    duration = "Duration",
    fileSize = "File Size",
    resolution = "Resolution",
    
    startTime = "Start Time",
    endTime = "End Time",
    selectedDuration = "Selected Clip Length",
    trimVideo = "Trim Video Now",
    adjustStart = "Start Point",
    adjustEnd = "End Point",
    previewTrim = "Preview Selected Segment",
    
    splitByTime = "Split by Time (Interval)",
    splitByCount = "Split by Number of Clips",
    intervalSeconds = "Interval (Seconds)",
    intervalMinutes = "Interval (Minutes)",
    numberOfClips = "Number of Equal Clips",
    calculatedClips = "Calculated Clips Preview",
    splitVideo = "Split Video Now",
    clipPrefix = "Clip",
    totalClipsToGenerate = "Clips will be generated",
    
    noVideosExported = "No exported clips yet. Trim or split a video to get started!",
    exportedClips = "Exported Video Clips",
    play = "Play",
    share = "Share",
    delete = "Delete",
    clearAll = "Clear History",
    savedToStorage = "File saved locally",
    saveToGallery = "Save to Gallery",
    saveToStorage = "Save to Storage",
    saveAllToGallery = "Save All to Gallery",
    saveAllToStorage = "Save All to Storage",
    savedToGallerySuccess = "Saved to Gallery successfully!",
    savedToStorageSuccess = "Saved to Storage (Downloads) successfully!",
    trimCompleteTitle = "Trim Completed!",
    splitCompleteTitle = "Split Completed!",
    clipsGeneratedCount = "clip(s) ready to save",
    errorSaving = "Error saving file. Please try again.",
    
    processingVideo = "Processing Video...",
    processingClip = "Generating Clip",
    cancel = "Cancel",
    successTrim = "Video trimmed successfully!",
    successSplit = "Video split successfully!",
    errorProcessing = "Error processing video. Please try another file.",
    
    buildApkGuide = "How to Build APK",
    buildApkSteps = "1. Open Android Studio or terminal in project root.\n" +
            "2. Run: ./gradlew assembleRelease\n" +
            "3. Find generated APK at: app/build/outputs/apk/release/app-release.apk\n" +
            "4. Install on any Android device!",
    close = "Close",

    settings = "Settings",
    clearCache = "Clear Cache",
    cacheCleared = "Cache cleared successfully!",
    language = "Language / ភាសា",
    shareWithFriends = "Share with your friend 🥰",
    shareAppText = "Check out Trim Video Pro - Free offline video trimming & splitting app! Created by Moeun💞",
    feedback = "Feedback",
    feedbackTitle = "Send Feedback",
    feedbackHint = "Type your feedback or suggestions here...",
    send = "Send",
    feedbackThanks = "Thank you for your feedback! 🥰",
    aboutUs = "About Us",
    aboutUsText = "Trim Video Pro v1.0\nCreated by Moeun💞\n\n• 100% Offline & Private\n• Free with No Watermarks\n• Fast Trim & Split by Time or Clips"
)

val KhmerStrings = AppStrings(
    appTitle = "កាត់វីដេអូ ប្រូ (Trim Video Pro)",
    offlineBadge = "អូហ្វឡាញ ១០០% • គ្មានរូបសញ្ញាទឹក (Watermark)",
    selectVideo = "ជ្រើសរើសវីដេអូដើម",
    changeVideo = "ប្តូរវីដេអូផ្សេង",
    refresh = "ផ្ទុកឡើងវិញ (Refresh)",
    refreshedMsg = "បានផ្ទុកឡើងវិញនិងកំណត់វីដេអូឡើងវិញ",
    tabTrim = "កាត់យកផ្នែក",
    tabSplit = "ពុះចែកវីដេអូ",
    tabHistory = "វីដេអូដែលបានរក្សាទុក",
    videoInfo = "ព័ត៌មានវីដេអូ",
    duration = "រយៈពេលសរុប",
    fileSize = "ទំហំឯកសារ",
    resolution = "កម្រិតរូបភាព",
    
    startTime = "ម៉ោងចាប់ផ្តើម",
    endTime = "ម៉ោងបញ្ចប់",
    selectedDuration = "ប្រវែងវីដេអូដែលជ្រើសរើស",
    trimVideo = "កាត់វីដេអូឥឡូវនេះ",
    adjustStart = "ចំណុចចាប់ផ្តើម",
    adjustEnd = "ចំណុចបញ្ចប់",
    previewTrim = "មើលវីដេអូដែលបានជ្រើស",
    
    splitByTime = "ពុះតាមរយៈពេល (នាទី/វិនាទី)",
    splitByCount = "ពុះតាមចំនួនភាគ (Clips)",
    intervalSeconds = "ចន្លោះពេល (វិនាទី)",
    intervalMinutes = "ចន្លោះពេល (នាទី)",
    numberOfClips = "ចំនួនភាគស្មើៗគ្នា",
    calculatedClips = "បញ្ជីភាគដែលនឹងត្រូវពុះចែក",
    splitVideo = "ពុះចែកវីដេអូឥឡូវនេះ",
    clipPrefix = "ភាគទី",
    totalClipsToGenerate = "ភាគនឹងត្រូវបង្កើតឡើង",
    
    noVideosExported = "មិនទាន់មានវីដេអូដែលបានកាត់នៅឡើយទេ។ សូមជ្រើសរើសវីដេអូដើម្បីកាត់!",
    exportedClips = "បញ្ជីវីដេអូដែលបានរក្សាទុក",
    play = "ទស្សនា",
    share = "ចែករំលែក",
    delete = "លុប",
    clearAll = "លុបប្រវត្តិទាំងអស់",
    savedToStorage = "បានរក្សាទុកក្នុងម៉ាស៊ីន",
    saveToGallery = "រក្សាទុកក្នុង Gallery",
    saveToStorage = "រក្សាទុកក្នុង Storage",
    saveAllToGallery = "រក្សាទុកទាំងអស់ក្នុង Gallery",
    saveAllToStorage = "រក្សាទុកទាំងអស់ក្នុង Storage",
    savedToGallerySuccess = "បានរក្សាទុកក្នុង Gallery ដោយជោគជ័យ!",
    savedToStorageSuccess = "បានរក្សាទុកក្នុង Storage (Downloads) ដោយជោគជ័យ!",
    trimCompleteTitle = "បានកាត់វីដេអូរួចរាល់!",
    splitCompleteTitle = "បានពុះចែកវីដេអូរួចរាល់!",
    clipsGeneratedCount = "ភាគត្រូវបានបង្កើតរួចរាល់",
    errorSaving = "មានបញ្ហាក្នុងការរក្សាទុកឯកសារ។ សូមសាកល្បងម្តងទៀត។",
    
    processingVideo = "កំពុងដំណើការកាត់វីដេអូ...",
    processingClip = "កំពុងបង្កើតភាគទី",
    cancel = "បោះបង់",
    successTrim = "បានកាត់វីដេអូដោយជោគជ័យ!",
    successSplit = "បានពុះចែកវីដេអូដោយជោគជ័យ!",
    errorProcessing = "មានបញ្ហាក្នុងការកាត់វីដេអូ។ សូមសាកល្បងឯកសារផ្សេង។",
    
    buildApkGuide = "របៀបបង្កើតឯកសារ APK",
    buildApkSteps = "១. បើក Android Studio ឬ Terminal ក្នុង Folder គម្រោង\n" +
            "២. វាយបញ្ជា៖ ./gradlew assembleRelease\n" +
            "៣. ឯកសារ APK ស្ថិតនៅ៖ app/build/outputs/apk/release/app-release.apk\n" +
            "៤. ដំឡើងលើទូរស័ព្ទ Android បានភ្លាមៗ!",
    close = "បិទ",

    settings = "ការកំណត់",
    clearCache = "សម្អាត Cache",
    cacheCleared = "បានសម្អាត Cache រួចរាល់!",
    language = "ភាសា / Language",
    shareWithFriends = "ចែករំលែកទៅកាន់មិត្តភក្តិ 🥰",
    shareAppText = "សាកល្បង កាត់វីដេអូ ប្រូ (Trim Video Pro) - កម្មវិធីកាត់វីដេអូឥតគិតថ្លៃ អូហ្វឡាញ! បង្កើតដោយ Moeun💞",
    feedback = "មតិរិះគន់ និងសំណូមពរ",
    feedbackTitle = "ផ្ញើមតិរិះគន់",
    feedbackHint = "វាយមតិរិះគន់ ឬសំណូមពររបស់អ្នកនៅទីនេះ...",
    send = "ផ្ញើ",
    feedbackThanks = "សូមអរគុណសម្រាប់ការផ្ញើមតិរិះគន់! 🥰",
    aboutUs = "អំពីកម្មវិធី",
    aboutUsText = "កាត់វីដេអូ ប្រូ (Trim Video Pro) v1.0\nបង្កើតដោយ Moeun💞\n\n• ដំណើរការអូហ្វឡាញ ១០០% និងសុវត្ថិភាព\n• ឥតគិតថ្លៃ និងគ្មាន Watermark\n• កាត់និងពុះចែកវីដេអូរហ័ស"
)

val LocalAppStrings = staticCompositionLocalOf { EnglishStrings }
