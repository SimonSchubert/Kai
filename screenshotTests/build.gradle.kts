plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.paparazzi)
    kotlin("android")
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "com.inspiredandroid.kai.screenshots"
    // Use SDK 34 for Paparazzi compatibility (1.3.4 doesn't support SDK 36 yet)
    compileSdk = 34

    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    // Include composeApp's assets (which contain compose resources)
    sourceSets {
        getByName("main") {
            assets.srcDirs(
                project(":composeApp").file("build/generated/assets/copyDebugComposeResourcesToAndroidAssets"),
            )
        }
    }
}

dependencies {
    implementation(project(":composeApp"))
    testImplementation(project(":composeApp"))
    // Required for types used directly in test code (KMP doesn't expose transitively)
    testImplementation(libs.filekit.core)
    implementation(libs.tts)
    implementation(libs.tts.compose)
    testImplementation("androidx.compose.material3:material3")
    testImplementation("org.jetbrains.compose.components:components-resources:${libs.versions.compose.multiplatform.get()}")
}

// Ensure composeApp resources are generated before screenshot tests
tasks.matching { it.name.contains("preparePaparazzi") }.configureEach {
    dependsOn(":composeApp:copyDebugComposeResourcesToAndroidAssets")
}

// Task to copy screenshots to fastlane and README locations
tasks.register("updateScreenshots") {
    dependsOn("recordPaparazziDebug")

    doLast {
        val snapshotsDir = file("src/test/snapshots/images")
        val fastlaneDir = rootProject.file("fastlane/metadata/android/en-US/images/phoneScreenshots")
        val readmeDir = rootProject.file("screenshots")

        // Ensure destination directories exist
        fastlaneDir.mkdirs()
        readmeDir.mkdirs()

        // Mapping for fastlane (light theme screenshots for Play Store)
        val fastlaneMapping =
            mapOf(
                "chatEmptyState_light" to "1.png",
                "chatWithMessages_dark" to "2.png",
                "chatWithCodeExample_light" to "3.png",
                "settingsFree_dark" to "4.png",
            )

        // Copy to fastlane
        snapshotsDir.listFiles()?.forEach { file ->
            fastlaneMapping.entries.find { file.name.contains(it.key) }?.let { (_, destName) ->
                file.copyTo(fastlaneDir.resolve("0$destName"), overwrite = true)
                file.copyTo(readmeDir.resolve("mobile-$destName"), overwrite = true)
                println("Copied ${file.name} -> fastlane/$destName")
            }
        }
    }
}
