plugins {
    // AGP 9 ships built-in Kotlin support, so no separate Kotlin plugin is needed.
    id("com.android.application") version "9.2.1" apply false
    // Compose compiler plugin; version pinned to AGP 9.2.1's bundled Kotlin (2.3.10).
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false
}
