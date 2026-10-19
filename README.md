# Trim Video Pro

Native Android / Jetpack Compose media tools with English and Khmer UI.

## New tools

- **Merge videos:** add 2–20 videos, move clips up/down, remove clips, and export a lossless MP4 in that order. This is native remuxing, not transcoding: codecs and codec configuration, resolution, orientation, frame rate (when reported), and audio track presence/settings must match. Incompatible, protected, or unsupported timestamp streams fail with an explanation rather than producing a corrupt merge. Re-encode incompatible clips externally first.
- **Extract audio:** decode the first audio track of a video to 16-bit PCM WAV.
- **Split audio:** choose audio or video; split by seconds or 2–50 parts. Remainders are retained and ranges are rendered at audio-sample boundaries. The duration mode also permits at most 50 parts.
- **Audio studio:** export audio with gain, three-band EQ (250 Hz / 4 kHz crossovers), echo mix/delay, a simple 4:1 compressor above −12 dBFS, soft noise gate, and fade-in/out. Speech, Music, and Room presets work offline. Effects are baked into WAV exports. The source player is an unprocessed preview, not a live effects preview. This does **not** replace the soundtrack inside a video. The noise gate is not AI source separation or AI noise removal.
- **AI sound suggestions:** describe your desired sound and choose **Recommend settings**. Groq `llama-3.3-70b-versatile` returns bounded settings and an explanation; review the sliders before exporting. Only the description (up to 500 characters) is sent, not the recording. Recommendations are goal-based, not an analysis of the audio.
- **Groq transcription:** choose **Groq · Whisper Large v3** on the Transcript screen. Uses `whisper-large-v3` at `POST https://api.groq.com/openai/v1/audio/transcriptions`, multipart media upload, `verbose_json`, and segment timestamps. Gemini remains available. Automatic source-language detection is the default (90+ languages). The source-language selector offers 99 explicit Whisper languages, including English, Khmer, Thai, Chinese, Japanese, and Vietnamese. Auto mode omits the API language hint so the provider can detect the source; it is independent of the app UI language. Transcripts preserve the original language and script, without translation. Gemini auto-detection is also no longer restricted to English/Khmer. Groq's original segment timing is retained for SRT, VTT, and text export.

## API keys and privacy

Open **Settings → Groq API**, enter your own Groq API key, and save. Clear the field and save to remove it. The key is shared by Groq transcription and sound-setting suggestions. Gemini has a separate key setting and continues to support the existing optional build-time `.env` configuration.

User-entered keys live in app-private preferences, are not included in the public ViewModel state, and are excluded from cloud backup and device transfer. No Groq key is committed or embedded in the APK. Do not ship an APK with a shared production API key; a production multi-user service should use an authenticated backend instead of distributing a secret.

Editing, merging, extraction, splitting, and built-in sound presets run on-device. **Transcription uploads the selected media to the selected provider.** AI recommendations upload only the typed description. Requests can incur provider charges and rate limits. API calls require internet and a valid user-supplied key; no live API calls were made during development.

Groq uploads are limited to **25 MB per file** in this implementation. Supported upload extensions: FLAC, MP3, MP4, MPEG, MPGA, M4A, OGG, WAV, WebM. Larger media must first be extracted or split, saved from History, and selected again. WAV is uncompressed and can be large. No automatic chunked transcription is implemented.

## Exports

All new tools write app-private exports into `TrimmedVideos`, alongside existing video exports. New outputs are finalized from `.partial` files before appearing in History; failed renders are removed. A failed multi-part audio export removes the parts created by that operation. Originals are never overwritten.

History supports WAV/M4A audio playback, MP4 video playback, correct MIME types, and content-URI sharing through FileProvider. Save audio to Music, video to Movies, or either to Downloads. Android 7–9 request storage permission; Android 10+ uses scoped MediaStore storage. Unsuccessful public copies are cleaned up. PCM WAV exports have a 4 GB format limit; use smaller parts for longer recordings.

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
- `GroqTranscriptTest`: Robolectric checks for real segment timing, Khmer text, invalid/empty segments, full-text fallback, and missing speech.
- `AudioProcessorInstrumentedTest`: real Android WAV extraction of a half-second range and a rendered gain comparison.

Validation performed in the coding workspace:

- All six source-language checks passed using a standalone Kotlin harness.
- All 11 audio logic checks passed using a standalone Kotlin harness invoking the test methods (not the Gradle/JUnit runner).
- Native media processors compiled with Kotlin 2.2.10 against Android API 35 and coroutines.
- The processors, preferences, localization data, and ViewModel also passed a standalone compile with OkHttp/Okio and minimal lifecycle/Compose-runtime stubs. This is **not** a Compose/Gradle APK build.
- Kotlin syntax parsing and XML parsing passed; `git diff --check` passed.

A complete APK build, Robolectric suite, instrumentation tests, and live Groq/Gemini requests still require an Android SDK/build environment, a device/emulator, and API credentials. They were not run in this workspace.

### Device smoke-test checklist

1. Merge two matching phone clips; verify ordering, orientation, audio continuity, duration, playback, and save/share. Try differing resolutions and audio presence and verify a clear rejection.
2. Extract a video with audio and one without; confirm valid WAV output or a no-audio error.
3. Split a recording into three unequal parts and by a duration with a remainder; verify all content is retained and the final short part plays.
4. Export Speech/Room presets and manual EQ/echo; compare the **exported** sound, not the unprocessed source player.
5. Set a Groq key, generate English and Khmer transcripts, and inspect SRT/VTT timing. Try an invalid key, an oversized file, and airplane mode.
6. Request AI settings from a typed goal; verify the reason and sliders, then export. Clear the key and verify an actionable error.
7. Save and share audio/video on Android 9 and Android 10+; deny legacy storage permission and verify no success is reported.
