import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.singular.looper"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.singular.looper"
        minSdk = 26
        targetSdk = 36
        versionCode = 13
        versionName = "0.12"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Keep the git sha out of the APK. F-Droid rebuilds the *tagged* commit and compares
            // byte-for-byte with the published binary, so an embedded sha turns any slip between
            // "what was built" and "what was tagged" into an unreproducible release (it cost us
            // 0.3). Without it, the two can't disagree.
            vcsInfo { include = false }
        }
    }

    buildFeatures {
        compose = true
        // For the version shown on the About screen. Generated from the constants above only —
        // unlike vcsInfo it embeds nothing build-specific, so it stays reproducible.
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

/**
 * Fails the build on a user-facing string typed straight into a composable.
 *
 * This exists because Android's own `HardcodedText` lint only reads XML layouts — it has nothing
 * to say about `Text("Cancel")`, which is the only way this app writes UI. Without a check of our
 * own, the next screen written would silently be English-only and nothing would complain.
 *
 * Deliberately narrow: it looks at the two constructs that actually put words in front of someone,
 * and leaves alone the places a bare string is legitimate (Compose animation labels, log messages,
 * JSON keys). Something that slips past this is still caught by reading the diff.
 */
val checkNoHardcodedUiStrings by tasks.registering {
    group = "verification"
    description = "Fails if a composable passes a literal where a string resource belongs."
    val sources = fileTree("src/main/java") { include("**/*.kt") }
    inputs.files(sources)
    // No real output; the up-to-date marker keeps repeat runs cheap.
    val stamp = layout.buildDirectory.file("tmp/hardcoded-ui-strings.ok")
    outputs.file(stamp)
    doLast {
        val patterns = listOf(
            Regex("\\bText\\(\\s*\""),
            Regex("\\bcontentDescription\\s*=\\s*\""),
        )
        val offenders = sources.files.flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> patterns.any { it.containsMatchIn(line) } }
                .map { (i, line) -> "${file.relativeTo(projectDir)}:${i + 1}: ${line.trim()}" }
        }
        if (offenders.isNotEmpty()) {
            error(
                "Hardcoded UI strings — move these to res/values/strings.xml and read them " +
                    "with stringResource():\n" + offenders.joinToString("\n"),
            )
        }
        stamp.get().asFile.apply { parentFile.mkdirs() }.writeText("ok\n")
    }
}

tasks.named("check") { dependsOn(checkNoHardcodedUiStrings) }

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    // Only for AppCompatDelegate.setApplicationLocales. On Android 13+ that call just forwards to
    // the framework's per-app language, but below it AppCompat is what stores the choice and
    // re-applies it on launch — and that machinery hangs off AppCompatActivity, which is why
    // MainActivity extends it despite the UI being entirely Compose.
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-compose:1.12.4")
    // Custom Tabs, for the Support dialog's links. The browser fetches in its own process, so
    // this buys an in-app-feeling tab *without* the INTERNET permission — the manifest stays
    // permission-free, which is the whole basis of the app's no-tracking claim.
    implementation("androidx.browser:browser:1.8.0")

    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // 2.9.x is compiled against API 36; 2.10+/2.11 require compileSdk 37.
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Local JVM unit tests (BeatDetector is pure Kotlin — no Android framework needed).
    testImplementation("junit:junit:4.13.2")
}
