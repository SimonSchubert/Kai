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
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

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

// Only run store screenshot tests when generating store screenshots
tasks.matching { it.name == "testDebugUnitTest" }.configureEach {
    val task = this as Test
    if (gradle.startParameter.taskNames.any { it.contains("generateStoreScreenshots") }) {
        task.filter.includeTestsMatching("*.StoreScreenshotTest")
        task.filter.includeTestsMatching("*.TabletStoreScreenshotTest")
    } else {
        task.filter.excludeTestsMatching("*.StoreScreenshotTest")
        task.filter.excludeTestsMatching("*.TabletStoreScreenshotTest")
    }
}

// Task to copy screenshots to fastlane and README locations
tasks.register("updateScreenshots") {
    dependsOn("recordPaparazziDebug")

    doLast {
        val snapshotsDir = file("src/test/snapshots/images")
        val readmeDir = rootProject.file("screenshots")

        // Ensure destination directories exist
        readmeDir.mkdirs()

        // Mapping for fastlane screenshots
        val screenshotMapping =
            mapOf(
                "chatEmptyState_light" to "1.png",
                "chatWithMessages_dark" to "2.png",
                "chatWithCodeExample_light" to "3.png",
                "settingsFree_dark" to "4.png",
                "settingsTools_light" to "5.png",
                "settingsGeneral_light" to "7.png",
            )

        // Copy phone screenshots (from ScreenshotTest)
        snapshotsDir.listFiles()?.filter { it.name.startsWith("com.inspiredandroid.kai.screenshots_ScreenshotTest_") }?.forEach { file ->
            screenshotMapping.entries.find { file.name.contains(it.key) }?.let { (_, destName) ->
                file.copyTo(readmeDir.resolve("mobile-$destName"), overwrite = true)
                println("Copied ${file.name} -> phoneScreenshots/0$destName")
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

        // Clear existing screenshots first
        fastlaneDir.listFiles()?.forEach { localeDir ->
            localeDir
                .resolve("images/phoneScreenshots")
                .listFiles()
                ?.filter { it.extension == "png" }
                ?.forEach { it.delete() }
            localeDir
                .resolve("images/tenInchScreenshots")
                .listFiles()
                ?.filter { it.extension == "png" }
                ?.forEach { it.delete() }
        }

        // Phone screenshots
        val phoneRegex = Regex("""StoreScreenshotTest_\w+\[([^\]]+)\]_store_[a-zA-Z0-9-]+_(\d+(?:_\w+)?)\.png""")
        val phoneSnapshots =
            snapshotsDir.listFiles()?.filter {
                it.name.contains("StoreScreenshotTest_") && !it.name.contains("Tablet") && it.name.contains("_store_") &&
                    it.extension == "png"
            } ?: emptyList()

        phoneSnapshots.forEach { file ->
            val match = phoneRegex.find(file.name)
            if (match != null) {
                val (locale, name) = match.destructured
                val targetDir = File(fastlaneDir, "$locale/images/phoneScreenshots")
                targetDir.mkdirs()
                val index = name.trimStart('0')
                val targetFile = File(targetDir, "${index}_$locale.png")
                file.copyTo(targetFile, overwrite = true)
                println("Copied -> $locale/phoneScreenshots/${index}_$locale.png")
            }
        }

        // Tablet screenshots - locale comes from [paramName] in test class name
        val tabletRegex = Regex("""TabletStoreScreenshotTest_\w+\[([^\]]+)\]_tablet_[a-zA-Z0-9-]+_(\d+(?:_\w+)?)\.png""")
        val tabletSnapshots =
            snapshotsDir.listFiles()?.filter {
                it.name.contains("TabletStoreScreenshotTest_") && it.name.contains("_tablet_") && it.extension == "png"
            } ?: emptyList()

        tabletSnapshots.forEach { file ->
            val match = tabletRegex.find(file.name)
            if (match != null) {
                val (locale, name) = match.destructured
                val targetDir = File(fastlaneDir, "$locale/images/tenInchScreenshots")
                targetDir.mkdirs()
                val index = name.trimStart('0')
                val targetFile = File(targetDir, "${index}_$locale.png")
                file.copyTo(targetFile, overwrite = true)
                println("Copied -> $locale/tenInchScreenshots/${index}_$locale.png")
            }
        }

        if (phoneSnapshots.isEmpty() && tabletSnapshots.isEmpty()) {
            println("No store screenshots found.")
        }
    }
}
