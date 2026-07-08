# New app: Rubber Ring (de.singular.looper)

Adds `metadata/de.singular.looper.yml` for **Rubber Ring**, a tool for practicing
song parts: pick an audio file, mark a start/stop region on its waveform, and play it
back in a seamless, gapless loop, with an auto-detected (and hand-editable) beat grid.

- **Source:** https://github.com/mkay/RubberRing
- **License:** GPL-3.0-only
- **Package:** `de.singular.looper`
- **Build:** versionName `0.2`, versionCode `2`, tag `v0.2`

## FOSS compliance

- [x] GPL-3.0-only, `LICENSE` file in repo root.
- [x] No proprietary dependencies — only AndroidX / Jetpack Compose (Apache-2.0),
      resolved from Google's Maven and Maven Central.
- [x] No trackers, no ads, no analytics.
- [x] No non-free network services; the app requests **zero** permissions and has no
      network access.
- [x] No signing config committed; no prebuilt binaries in the repo.

## Build

Current-toolchain app: AGP 9.2.1 / Gradle 9.5.1 / compileSdk 36, Java 17. The release
build is a plain `assembleRelease` — no minification, no signing config — and resolves
only AndroidX / Jetpack Compose artifacts from Google's Maven and Maven Central.

An earlier tag built cleanly from a clean checkout in F-Droid's buildserver image
(`registry.gitlab.com/fdroid/fdroidserver:buildserver`): `gradlew-fdroid` accepted
Gradle 9.5.1 (transparency-log verified) and AGP 9.2.1 auto-provisioned `android-36` +
`build-tools 36.0.0`, producing the unsigned APK. `v0.2` adds only Kotlin/Compose UI
changes and metadata over that tag — no new runtime dependencies (the one addition,
JUnit, is test-only and not in the release APK). Please flag if the production
buildserver's SDK/AGP provisioning differs for `v0.2`.

## Metadata

Fastlane metadata (title, short/full descriptions, icon, phone screenshots) is provided
in-repo under `fastlane/metadata/android/en-US/` and will be picked up automatically.

## Checklist

- [x] `fdroid lint de.singular.looper` passes (or: please advise on any lint output).
- [x] `fdroid readmeta` / `fdroid rewritemeta` clean.
- [x] AutoUpdateMode / UpdateCheckMode set to Tags for future releases.
