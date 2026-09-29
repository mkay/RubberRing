# Changelog

All notable changes to this project are documented here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.12] - 2026-09-29

### Added
- A saved loop can be exported as a WAV file: long-press its chip and choose Export as WAV. The file is the loop as it sounds in the app, at the current speed and with the track's normalization but without the click, cut exactly at the loop's edges so it lands on a DAW's grid. WAV only, since Android has no built-in Vorbis encoder and every DAW reads WAV.

### Fixed
- Loops played slower or faster than 1× are now exactly as long as they should be. The time-stretch dropped up to one grain at the end, so a loop at 0.8× came round about 30 ms early on every pass and stumbled at the loop point.

## [0.11] - 2026-08-27

### Added
- The chord lane can name chord roots: it detects the harmony as usual, then names only the root of each chord, one note per chord. Over a C chord with an E underneath it says C, where Bass notes says E. It reads the whole mix rather than the bottom of it, so unlike Bass notes it does not need an audible bass line to follow. Options → Chords, then Re-detect to apply.

### Changed
- The Click tab in Options is split under two headings, Click and Count-in, so it is clear at a glance which settings apply to the count-in alone and which apply to the click throughout the loop.
- Bass notes now says in the options and in the help that it needs a track with an audible bass, which is the condition under which it gives useful names at all.

## [0.10] - 2026-08-09

### Added
- The click can carry on through the whole loop, not only during the count-in, with its own volume and a choice of low, medium or high pitch (Options → Click). At zero volume it is silent, and the count-in does not click either.
- The chord lane can name bass notes instead of chords: the lowest note in the mix rather than the harmony over it. Over a C chord with an E underneath it says E where chord detection says C, which is the note a bassist is playing when the chord sits on an inversion. Options → Chords, then Re-detect to apply.
- The chord under the playhead can be shown in large type across the waveform while playing, readable at arm's length with both hands on the instrument (Options → Chords).

### Changed
- The Count-in tab in Options is now called Click, since it holds every click setting rather than only the count-in.
- The click is loud enough to be heard over a mastered track.

### Fixed
- The count-in now lands exactly on the beat the audio comes in on. It is mixed into the same output stream as the track rather than played on a stream of its own, so the two can no longer drift apart by a device-dependent amount.

## [0.9] - 2026-08-04

### Added
- Up to ten saved loops per track, instead of four, and the Save button keeps its place beside Arrange rather than disappearing at the cap.
- Loop chips can be dragged into the order you practise them.
- A short "What's new" note on the first launch after an update, and never on a fresh install. It covers the current release only and links out to this changelog; Settings → About reopens it.

### Changed
- Recalling a loop scrolls it into view, and loop names have to be unique within a track.
- Snap, Auto and Tap now share one outline as a group, since all three act on the beat grid; track options move out of the row to their own button.

## [0.8] - 2026-07-31

### Added
- A German translation, and a language picker in Settings → System → General. The choice is stored as Android's own per-app language rather than as a private setting, so on Android 13 and up it and the picker in Android's Settings are one value instead of two that can disagree. Leave it on "System default" to follow the device.
- Optional German note names for chords (Settings → Playback): B natural reads H, and B flat reads B, as written in German-speaking countries. Off by default, since international spelling is what chord charts and tab sites use. It is a display choice only — a chord is stored as a pitch class either way, so switching re-spells existing hand edits rather than altering them.
- Send a recording straight to Rubber Ring from another app — it now appears in the share sheet for audio, and under "Open with" in a file manager. The track is imported and opened in one step, exactly as if you had picked it yourself. As with any import, the file is copied into the app; the original is left where it is.
- A Support entry in the menu, with three ways to help the app along: a like on its web page, a star on GitHub, or Ko-fi. Each is a link out through the browser, so the app still asks for no network permission of its own and nothing here is measured.
- The licence is now stated in the app itself, under About: the copyright line, GPL-3.0-only for the code, and CC BY 4.0 for the wordmark and icon. The repository carries matching SPDX headers and a COPYRIGHT file.

### Changed
- Settings are split into Playback, System and About tabs instead of one long column. Playback is what the waveform does mid-practice; System is the app's own set-up. About stops being a separate screen and becomes the third tab.
- Recent tracks in the menu are marked with a waveform icon, so they read as tracks rather than as more destinations.
- Every piece of text in the app now comes from a resource file instead of being written into the code, which is what makes translating it possible at all. No visible change on its own.
- The beat-grid chips shrink their labels a step rather than wrapping when a translation runs longer than the English, so a longer word can't break the row.

## [0.7.1] - 2026-07-20

Maintenance release: identical features to 0.7, rebuilt with a reproducibility fix (disabled AGP's non-deterministic ART baseline-profile dex rewriting) so F-Droid's buildserver can reproduce and publish it. No functional changes.

## [0.7] - 2026-07-19

### Added
- An About page (Settings → About this app) — the version, where the source lives, where to report a bug, and how to support development. Long-press the version to copy it, so a bug report can name the exact build.

### Changed
- The library home is headed by the "Rubber Ring" wordmark and its "a play-along looper" tagline above the ring; the app name drops out of the corner there, since the banner already says it, and stays as the filename on an open track.
- Decoding a track now spins the app's own ring mark instead of a generic progress circle.

### Known issues
- The count-in's handoff to playback can still sit slightly off the beat, most noticeably at fast tempos. BPM, beat grid and markers can all be corrected by hand in the meantime.

## [0.6] - 2026-07-15

### Added
- Chord detection with a hand-editable chord lane — a dependency-free estimator labels the chords over the waveform (Options → Chords). Tap a chord to relabel, split or merge it, and drag a boundary to nudge it (it snaps to the beat); your edits are saved with the track. Turn the lane off entirely if you don't want it. Detection is tuned to the author's own material and won't be right on every recording — hence the hand-editing.
- Per-track audio normalization (Options → Audio) — even out quiet tracks. Choose Peak (a classic normalize, provably without distortion) or Loudness (targets a set level with a gentle limiter); off by default. Your imported file is never rewritten, and the waveform is drawn through the same gain so the boost is visible.
- Manage the library in bulk — long-press a track to start a selection, tap to add more, then delete them all in one action (the confirmation names the count).

### Fixed
- Beat detection on compound meters (6/8 and similar): the grid now lands on the felt, dotted-note beat instead of falling between the beats.

### Known issues
- The count-in's handoff to playback can still sit slightly off the beat, most noticeably at fast tempos. It's next on the agenda; BPM, beat grid and markers can all be corrected by hand in the meantime.

## [0.5] - 2026-07-13

### Added
- Count-in — long-press Play to start after a metronome counts you in at the track's tempo. Its meter (2/3/4/6 beats per bar) and length (1 or 2 bars) are set per track under Options, and travel with the track.
- A hint over the waveform on every track you open, pointing out that markers are moved with a long-press — the one gesture nothing on screen could show.
- "Add padding to waveform" (Settings → Playback, on by default) — turn the new edge padding off to get the full waveform width back.
- Quick help now mentions that a long-press on Play starts with a count-in.

### Changed
- Options and Arrange open as bottom sheets instead of popups, so they get the room they need and dismiss with a swipe.
- The BPM octave menu lists Double above Halve.
- The "Keep screen on" icon is now a brightness-alert symbol instead of a lightbulb, which read as a light/dark-mode toggle.

### Fixed
- Loop markers parked at the very start or end of the waveform sat on top of Android's back-gesture strip, so trying to drag one left the app instead of moving the marker. The waveform now holds back from the gesture strips.

### Known issues
- Beat detection still misreads the tempo on many real recordings, and the count-in's handoff to playback can sit slightly off the beat, most noticeably at fast tempos. Both are next on the agenda; BPM, beat grid and markers can all be corrected by hand in the meantime.

## [0.4] - 2026-07-12

Maintenance release: identical features to 0.3, rebuilt with clean release hygiene (the tagged commit now matches the published binary) so F-Droid can reproduce and publish it. No functional changes.

## [0.3] - 2026-07-10

### Added
- Pitch-preserving speed control — slow down or speed up playback from 0.5× to 1.5× without changing pitch (hand-rolled WSOLA time-stretch, no dependencies).
- Practice arrangements — chain saved loops into an ordered sequence, each repeated a set number of times, optionally looping the whole set, for drilling A–B–A transitions.
- A screen-kept-on indicator in the app bar.

### Changed
- The speed control is a compact link in the beat-control row.
- Refined the Arrange popup: the arm toggle sits beside the title as an "enabled" switch, "Repeat whole sequence" is a switch, and steps have an alternating row background.
- Single-loop and grid controls (loop chips, Save, Snap/Auto/Tap/½×/2×, BPM, Speed) dim while an arrangement is enabled, since they don't affect arrangement playback; tapping one explains why.
- The Arrange button uses the Material "steppers" icon and fills brand-red while the arrangement is armed.

## [0.2] - 2026-07-08

### Added
- System / Light / Dark theme option, with a theme-aware waveform palette.
- A Settings screen consolidating playback options, appearance, and library backup/restore.
- Back up and restore the whole library — tracks and saved loops — to a file.
- Navigation drawer available everywhere, plus a Recent tracks list.
- One-tap ½× / 2× tempo buttons to fix octave errors in beat detection.

### Changed
- The library is now the home screen; the back gesture returns there instead of quitting the app.
- The library list is sorted by last opened.
- Reworked beat detection (spectral-flux onset detection with fractional-period phase); replaced the "Downbeat = start" control with the octave fix.
- Standalone ring logo on the library screen; the app is locked to portrait.

### Fixed
- Restoring a backup didn't refresh the library until relaunch.

## [0.1] - 2026-07-06

### Added
- Initial release: pick an audio file, mark a start/stop region on its waveform, and play it back in a seamless, gapless loop.
- Automatic beat grid (dependency-free tempo + downbeat estimation) with snapping and full manual override.
- Import-and-own library: picked files are copied into app-private storage.
- Multiple named saved loops per track; custom, renameable track titles.
- Follow-playhead auto-scroll, keep-screen-on, and an in-app quick-help sheet.

[Unreleased]: https://github.com/mkay/RubberRing/compare/v0.12...HEAD
[0.12]: https://github.com/mkay/RubberRing/compare/v0.11...v0.12
[0.11]: https://github.com/mkay/RubberRing/compare/v0.10...v0.11
[0.10]: https://github.com/mkay/RubberRing/compare/v0.9...v0.10
[0.9]: https://github.com/mkay/RubberRing/compare/v0.8...v0.9
[0.8]: https://github.com/mkay/RubberRing/compare/v0.7.1...v0.8
[0.7.1]: https://github.com/mkay/RubberRing/compare/v0.7...v0.7.1
[0.7]: https://github.com/mkay/RubberRing/compare/v0.6...v0.7
[0.6]: https://github.com/mkay/RubberRing/compare/v0.5...v0.6
[0.5]: https://github.com/mkay/RubberRing/compare/v0.4...v0.5
[0.4]: https://github.com/mkay/RubberRing/compare/v0.3...v0.4
[0.3]: https://github.com/mkay/RubberRing/compare/v0.2...v0.3
[0.2]: https://github.com/mkay/RubberRing/compare/v0.1...v0.2
[0.1]: https://github.com/mkay/RubberRing/releases/tag/v0.1
