# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.3] - 2026-07-10

### Added
- Pitch-preserving speed control — slow down or speed up playback from 0.5× to
  1.5× without changing pitch (hand-rolled WSOLA time-stretch, no dependencies).
- Practice arrangements — chain saved loops into an ordered sequence, each
  repeated a set number of times, optionally looping the whole set, for drilling
  A–B–A transitions.
- A screen-kept-on indicator in the app bar.

### Changed
- The speed control is a compact link in the beat-control row.
- Refined the Arrange popup: the arm toggle sits beside the title as an "enabled"
  switch, "Repeat whole sequence" is a switch, and steps have an alternating row
  background.
- Single-loop and grid controls (loop chips, Save, Snap/Auto/Tap/½×/2×, BPM,
  Speed) dim while an arrangement is enabled, since they don't affect arrangement
  playback; tapping one explains why.
- The Arrange button uses the Material "steppers" icon and fills brand-red while
  the arrangement is armed.

## [0.2] - 2026-07-08

### Added
- System / Light / Dark theme option, with a theme-aware waveform palette.
- A Settings screen consolidating playback options, appearance, and library
  backup/restore.
- Back up and restore the whole library — tracks and saved loops — to a file.
- Navigation drawer available everywhere, plus a Recent tracks list.
- One-tap ½× / 2× tempo buttons to fix octave errors in beat detection.

### Changed
- The library is now the home screen; the back gesture returns there instead of
  quitting the app.
- The library list is sorted by last opened.
- Reworked beat detection (spectral-flux onset detection with fractional-period
  phase); replaced the "Downbeat = start" control with the octave fix.
- Standalone ring logo on the library screen; the app is locked to portrait.

### Fixed
- Restoring a backup didn't refresh the library until relaunch.

## [0.1] - 2026-07-06

### Added
- Initial release: pick an audio file, mark a start/stop region on its waveform,
  and play it back in a seamless, gapless loop.
- Automatic beat grid (dependency-free tempo + downbeat estimation) with snapping
  and full manual override.
- Import-and-own library: picked files are copied into app-private storage.
- Multiple named saved loops per track; custom, renameable track titles.
- Follow-playhead auto-scroll, keep-screen-on, and an in-app quick-help sheet.

[Unreleased]: https://github.com/mkay/RubberRing/compare/v0.3...HEAD
[0.3]: https://github.com/mkay/RubberRing/compare/v0.2...v0.3
[0.2]: https://github.com/mkay/RubberRing/compare/v0.1...v0.2
[0.1]: https://github.com/mkay/RubberRing/releases/tag/v0.1
