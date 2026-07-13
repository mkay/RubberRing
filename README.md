<h1>
  <img src="docs/icon.png" alt="RubberRing icon" height="52" align="middle" />
  RubberRing
</h1>

An Android tool for practicing song parts. Pick an audio file, mark a start/stop region on its waveform, and play it back in a seamless, gapless loop — slowed down without changing pitch, and snapped to an editable beat grid. Chain loops into practice arrangements to drill transitions.

Early-stage release — expect rough edges. Beat detection and count-in still need improvement. Feedback and bug reports welcome via Issues.

## Screenshots

<img src="docs/screenshot.png" alt="RubberRing loop editor" width="320" />

## Features

- **Import-and-own library** — picked files are copied into app-private storage, so loops
  survive the original being moved or deleted. The library doubles as the home screen, with
  recents and custom, renameable track titles.
- **Waveform loop editing** — drag start/end handles to mark a section; it decodes to PCM and
  loops via `AudioTrack` loop points, so the boundary wraps with zero audible gap or click.
- **Editable beat grid** — a dependency-free estimator finds BPM and a downbeat, and handles
  snap to the grid. Refine it by hand: tap the tempo, nudge ±1 BPM, or halve/double to fix
  the octave errors auto-detect is prone to.
- **Pitch-preserving speed** — slow down or speed up from 0.5× to 1.5× with a WSOLA
  time-stretch that keeps the original pitch.
- **Saved loops** — capture a region + grid per track and recall it later.
- **Practice arrangements** — chain saved loops into an ordered sequence, each repeated a set
  number of times and optionally looping the whole set — for drilling A–B–A transitions.
- **Session comforts** — System/Light/Dark themes with a theme-aware waveform, follow-playhead auto-scroll, keep-screen-on, library backup & restore, and an in-app quick-help sheet.
  Preferences persist across launches.

## Tech stack

- **Language:** Kotlin (Java 17 target)
- **UI:** Jetpack Compose (Canvas for the waveform, grid, and handles)
- **Build:** Android Gradle Plugin 9.2.1 + Gradle 9.5.1 (via wrapper)
- **SDK:** `minSdk` 26 · `compile`/`targetSdk` 36
- **Decode:** `MediaExtractor` + `MediaCodec` → PCM
- **Playback:** low-level `AudioTrack` with sample-accurate loop points
- **Beat detection & time-stretch:** custom (WSOLA), no native/NDK or GPL dependencies
- **Async:** Kotlin Coroutines + Flow

## Building

Requires a JDK (17+) and the Android SDK. Point the build at your SDK by creating a
`local.properties` file in the project root (this file is git-ignored):

```properties
sdk.dir=/path/to/Android/Sdk
```

Then build a debug APK:

```sh
./gradlew assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`; sideload it to a device
to run it.

## Project layout

```
app/src/main/java/de/singular/looper/
  MainActivity.kt        Compose entry point
  LooperViewModel.kt     app state, orchestrates decode / detect / play
  audio/
    AudioDecoder.kt      file URI -> PCM + metadata
    LoopPlayer.kt        AudioTrack wrapper: loop points, play/pause/seek
    BeatDetector.kt      tempo + downbeat estimation
    TimeStretch.kt       pitch-preserving WSOLA speed change
    DecodedAudio.kt / WaveformData.kt   PCM + envelope models
  library/
    LibraryRepository.kt import-and-own storage + metadata index
    LibraryTrack.kt / SavedLoop.kt / ArrangementStep.kt
  ui/
    LoopWaveform.kt      waveform + grid + handle rendering
```

## Support

If Rubber Ring is useful to you, you can support its development on
[Ko-fi](https://ko-fi.com/s1ngular). Thank you!

## Disclaimer

This project was developed with the assistance of Claude under my direction and functional review. The code has been analysed with Codacy (detekt).

[![Codacy Badge](https://app.codacy.com/project/badge/Grade/ddcda70adc4c442dbb9f03d9aa2f0e31)](https://app.codacy.com/gh/mkay/RubberRing/dashboard)
