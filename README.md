# Trim Video Pro

Native Android / Jetpack Compose media tools with English and Khmer UI.

## New tools

- **Merge videos:** add 2–20 videos and reorder/remove them. Matching clips use lossless remuxing. If codecs, sizes, orientation, audio tracks or timestamps differ, Media3 Transformer automatically normalizes to H.264/AAC (48 kHz stereo), fits clips to the first clip's aspect ratio up to 720p, adds silence for missing audio, and letterboxes instead of stretching. Conversion uses device codecs and can take longer or change quality; unsupported/DRM/corrupt sources can still fail.
- **Extract audio:** decode the first audio track of a video to 16-bit PCM WAV.
- **Split audio:** choose audio or video; split by seconds or 2–50 parts. Remainders are retained and ranges are rendered at audio-sample boundaries. The duration mode also permits at most 50 parts.
- **Audio studio:** export audio with gain, three-band EQ (250 Hz / 4 kHz crossovers), echo mix/delay, a simple 4:1 compressor above −12 dBFS, soft noise gate, and fade-in/out. Speech, Music, and Room presets work offline. Effects are baked into WAV exports. The source player is an unprocessed preview, not a live effects preview. This does **not** replace the soundtrack inside a video. The noise gate is not AI source separation or AI noise removal.
- **AI sound suggestions:** describe your desired sound and choose **Recommend settings**. Groq `llama-3.3-70b-versatile` returns bounded settings and an explanation; review the sliders before exporting. Only the description (up to 500 characters) is sent, not the recording. Recommendations are goal-based, not an analysis of the audio.
- **Groq transcription:** choose **Groq · Whisper Large v3** on the Transcript screen. Uses `whisper-large-v3` at `POST https://api.groq.com/openai/v1/audio/transcriptions`, multipart media upload, `verbose_json`, and segment timestamps. Gemini remains available. Automatic source-language detection is the default (90+ languages). The source-language selector offers 99 explicit Whisper languages, including English, Khmer, Thai, Chinese, Japanese, and Vietnamese. Auto mode omits the API language hint so the provider can detect the source; it is independent of the app UI language. Transcripts preserve the original language and script, without translation. Gemini auto-detection is also no longer restricted to English/Khmer. Groq's original segment timing is retained for SRT, VTT, and text export.

## API keys and privacy

Open **Settings → Groq API**, enter your own Groq API key, and save. Clear the field and save to remove it. The key is shared by Groq transcription and sound-setting suggestions. Gemini has a separate key setting and continues to support the existing optional build-time `.env` configuration.

User-entered keys live in app-private preferences, are not included in the public ViewModel state, and are excluded from cloud backup and device transfer. No Groq key is committed or embedded in the APK. Do not ship an APK with a shared production API key; a production multi-user service should use an authenticated backend instead of distributing a secret.

Editing, merging, extraction, splitting, and built-in sound presets run on-device. **Transcription uploads the selected media to the selected provider.** AI recommendations upload only the typed description. Requests can incur provider charges and rate limits. API calls require internet and a valid user-supplied key; no live API calls were made during development.

Groq transcription now **extracts the first audio track automatically** using a streaming native decoder, downmixes/resamples to 16 kHz mono PCM16, and uses lightweight energy-based VAD to choose quiet boundaries at 7–8 minutes (hard limit 8 minutes). Each WAV upload is at most about 15.4 MB, below the 25 MB limit. Parts are sent sequentially; subtitle timestamps are shifted back into the source timeline. Only digital/near-digital silent parts are discarded, not quiet speech. Transient network/429/5xx errors are retried up to three attempts with bounded backoff. Successfully completed text remains exportable if a later part fails. Resume across app restarts/restarting the job is not implemented. Temporary audio is deleted after completion/failure/cancellation. Sufficient cache storage and API quota are still required.

This is **energy-based VAD**, not a neural speech model or AI denoising. It is used for quiet split points, not to delete silent intervals inside an uploaded part.

## Installable test release

CI explicitly sets `ALLOW_TEST_SIGNING=true` to sign the release variant with the Android debug key when no production keystore is configured. It is named **Trim Video Pro (Test)** and uses package `com.aistudio.trimvideopro.moenuo.test` so it can coexist with the original app. Both APK signatures are verified with `apksigner`. The test key is cached between builds; if the cache is lost, update compatibility is not guaranteed. This is not a Play Store/production release identity. Normal local builds do not silently opt into test signing; configure the existing `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` environment variables for real release signing. No signing secrets are committed.

An unsigned APK cannot be installed. A correctly signed APK can still trigger a Play Protect warning; signing is not a promise to remove reputation/security warnings. Do not disable device protections to work around an alert.

## Exports

All new tools write app-private exports into `TrimmedVideos`, alongside existing video exports. New outputs are finalized from `.partial` files before appearing in History; failed renders are removed. A failed multi-part audio export removes the parts created by that operation. Originals are never overwritten.

History supports WAV/M4A audio playback, MP4 video playback, correct MIME types, and content-URI sharing through FileProvider. Save audio to Music, video to Movies, or either to Downloads. Android 7–9 request storage permission; Android 10+ uses scoped MediaStore storage. Unsuccessful public copies are cleaned up. PCM WAV exports have a 4 GB format limit; use smaller parts for longer recordings.

Transcript SRT/TXT/VTT exports are written as UTF-8 directly to public **Downloads/TrimVideoPro/Transcripts**, with unique filenames, pending-file cleanup, actual failure reporting, and a visible saved path. Android 7–9 request storage permission before exporting. No success is reported merely because a private copy exists.

**មគ្គុទ្ទេសក៍ជាភាសាខ្មែរ៖ [របៀបប្រើ និងការកែបញ្ហា](docs/USER_GUIDE_KM.md)**

## Build and verification

This repository is an Android Gradle project (not a web app). Use an Android development environment with a Gradle version compatible with the repository's Android Gradle plugin, JDK 17+, and the Android SDK requested by `app/build.gradle.kts`. The repository does not currently include a Gradle wrapper.

```sh
gradle testDebugUnitTest assembleDebug
# With a connected emulator/device:
gradle connectedDebugAndroidTest
```

Tests added:

- `AudioEngineTest`: 11 pure JVM checks for neutral processing, stereo-isolated echo, clipping, compression, gate, fades, finite/bounded values, and split boundaries/validation.
- `TranscriptLanguagesTest`: six pure JVM checks for default detection, omitted auto hints, the 99-language catalog, language-name normalization, unknown-language fallback, and locale independence.
- `SpeechChunkWriterTest`: five checks for VAD boundaries, upload bounds, original offsets, quiet speech retention, and cache cleanup.
- `TranscriptExportTest`: four checks for SRT/VTT/TXT formatting, Khmer text, and locale-independent subtitle digits.
- `MergeSizingTest`: two checks for portrait/landscape normalization and encoder-safe dimensions.
- `GroqTranscriptTest`: Robolectric checks for real segment timing, Khmer text, invalid/empty segments, full-text fallback, and missing speech.
- `AudioProcessorInstrumentedTest`: real Android WAV extraction of a half-second range and a rendered gain comparison.

Validation performed in the coding workspace:

- All five speech-chunk checks passed using a standalone Kotlin harness.
- All six source-language checks passed using a standalone Kotlin harness.
- All 11 audio logic checks passed using a standalone Kotlin harness invoking the test methods (not the Gradle/JUnit runner).
- Native media processors compiled with Kotlin 2.2.10 against Android API 35 and coroutines.
- The processors, preferences, localization data, and ViewModel also passed a standalone compile with OkHttp/Okio and minimal lifecycle/Compose-runtime stubs. This is **not** a Compose/Gradle APK build.
- Kotlin syntax parsing and XML parsing passed; `git diff --check` passed.

A complete APK build, Robolectric suite, instrumentation tests, and live Groq/Gemini requests still require an Android SDK/build environment, a device/emulator, and API credentials. They were not run in this workspace.

### Device smoke-test checklist

1. Merge two matching phone clips; verify ordering, orientation, audio continuity, duration, playback, and save/share. Try different resolutions/orientations/codecs and missing audio; verify automatic conversion, letterboxing, and silence.
2. Extract a video with audio and one without; confirm valid WAV output or a no-audio error.
3. Split a recording into three unequal parts and by a duration with a remainder; verify all content is retained and the final short part plays.
4. Export Speech/Room presets and manual EQ/echo; compare the **exported** sound, not the unprocessed source player.
5. Set a Groq key, generate English and Khmer transcripts, and inspect SRT/VTT timing. Try an invalid key, a video above 25 MB, a recording above 8 minutes, silent audio, and airplane mode. Verify chunk offsets and preserved partial text on a later failure.
6. Request AI settings from a typed goal; verify the reason and sliders, then export. Clear the key and verify an actionable error.
7. Save and share audio/video on Android 9 and Android 10+; deny legacy storage permission and verify no success is reported.

### Groq key copy/paste fix (1.1.1)

Groq keys are normalized on input, save, and read: pasted spaces/CR/LF/tabs, Unicode spacing, and common invisible formatting are removed; surrounding quotes and an optional Bearer prefix are unwrapped. Previously saved wrapped keys are repaired automatically. Only an ASCII `gsk_` token is accepted before decoding audio or constructing HTTP headers. Invalid-key errors never include the supplied credential. Both transcription and AI sound recommendations use this validation. Missing/invalid credentials show an actionable Settings error, and invalid saves do not overwrite a valid saved key. This validates input shape, not provider validity or quota.

`GroqApiKeyInputTest` adds nine normalization/header-safety checks without making a network request. `GroqApiKeyPreferencesTest` adds four Robolectric save/migration/clear/rejection checks.
