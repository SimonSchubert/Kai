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
    compileSdk = 36

    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        missingDimensionStrategy("distribution", "foss")
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
                project(":composeApp").file("build/generated/assets/copyFossDebugComposeResourcesToAndroidAssets"),
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
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation("androidx.compose.material3:material3")
    testImplementation("org.jetbrains.compose.components:components-resources:${libs.versions.compose.multiplatform.get()}")
}

// Ensure composeApp resources are generated before screenshot tests
tasks.matching { it.name.contains("preparePaparazzi") }.configureEach {
    dependsOn(":composeApp:copyFossDebugComposeResourcesToAndroidAssets")
}

// Only run StoreScreenshotTest when generating store screenshots
tasks.matching { it.name == "testDebugUnitTest" }.configureEach {
    val task = this as Test
    if (gradle.startParameter.taskNames.any { it.contains("generateStoreScreenshots") }) {
        task.filter.includeTestsMatching("*.StoreScreenshotTest")
    }
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
                "settingsTools_light" to "5.png",
                "exploreSpace_dark" to "6.png",
                "settingsGeneral_light" to "7.png",
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

// Task to generate localized store screenshots and copy to fastlane structure
tasks.register("generateStoreScreenshots") {
    dependsOn("recordPaparazziDebug")

    doLast {
        val snapshotsDir = file("src/test/snapshots/images")
        val fastlaneDir = rootProject.file("fastlane/metadata/android")

        val regex = Regex("""StoreScreenshotTest_\w+\[([^\]]+)\]_store_[a-zA-Z-]+_(\d+(?:_\w+)?)\.png""")
        val snapshots =
            snapshotsDir.listFiles()?.filter {
                it.name.contains("StoreScreenshotTest_") && it.name.contains("_store_") && it.extension == "png"
            } ?: emptyList()

        if (snapshots.isEmpty()) {
            println("No store screenshots found.")
            return@doLast
        }

        snapshots.forEach { file ->
            val match = regex.find(file.name)
            if (match != null) {
                val (locale, name) = match.destructured
                val targetDir = File(fastlaneDir, "$locale/images/phoneScreenshots")
                targetDir.mkdirs()
                val targetFile = File(targetDir, "$name.png")
                file.copyTo(targetFile, overwrite = true)
                println("Copied -> $locale/$name.png")
            }
        }
    }
}
