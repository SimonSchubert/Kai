plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.paparazzi)
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
            assets.directories.add(
                project(":composeApp").file("build/generated/assets/copyAndroidMainComposeResourcesToAndroidAssets").path,
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
    testImplementation(libs.kotlinx.collections.immutable)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.compose.material3)
    testImplementation(libs.compose.components.resources)
}

// Ensure composeApp resources are generated before screenshot tests
tasks.matching { it.name.contains("preparePaparazzi") }.configureEach {
    dependsOn(":composeApp:copyAndroidMainComposeResourcesToAndroidAssets")
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

// Record golden images for kai-ui component tests only (faster than full suite)
tasks.register("recordKaiUiScreenshots") {
    dependsOn("recordPaparazziDebug")
    doFirst {
        tasks.named("testDebugUnitTest").configure {
            (this as Test).filter.includeTestsMatching("*.KaiUiScreenshotTest")
        }
    }
}

// Task to copy screenshots to fastlane and README locations
tasks.register("updateScreenshots") {
    dependsOn("recordPaparazziDebug")

    doLast {
        val snapshotsDir = file("src/test/snapshots/images")
        val readmeDir = rootProject.file("screenshots").also { it.mkdirs() }
        val siteImgDir = rootProject.file("site/img")

        // (source-key contains → destination file). Source-key is a substring of the
        // Paparazzi filename; destination is the resolved path on disk.
        val copies =
            buildList<Pair<String, java.io.File>> {
                // Fastlane-style phone screenshots from ScreenshotTest → screenshots/
                mapOf(
                    "ScreenshotTest_chatEmptyState_light" to "mobile-1.png",
                    "ScreenshotTest_chatWithMessages_dark" to "mobile-2.png",
                    "ScreenshotTest_chatWithDynamicUi_light" to "mobile-3.png",
                    "ScreenshotTest_settingsFree_dark" to "mobile-4.png",
                    "ScreenshotTest_settingsTools_light" to "mobile-5.png",
                    "ScreenshotTest_settingsGeneral_dark" to "mobile-6.png",
                    "ScreenshotTest_settingsSandbox_dark" to "mobile-7.png",
                ).forEach { (k, v) -> add(k to readmeDir.resolve(v)) }

                // Hero carousel screenshots from KaiUiScreenshotTest → site/img/
                mapOf(
                    "KaiUiScreenshotTest_scenario_survivalGame_dark" to "survival-dark.png",
                    "KaiUiScreenshotTest_scenario_recipeCard_light" to "recipe-light.png",
                    "KaiUiScreenshotTest_scenario_sustainableTech_light" to "ecopulse-light.png",
                    "KaiUiScreenshotTest_scenario_memories_dark" to "memories-dark.png",
                ).forEach { (k, v) -> add(k to siteImgDir.resolve(v)) }

                // /run-gemma-locally/ landing-page screenshots → site/img/
                mapOf(
                    "GemmaLocalScreenshotTest_gemmaLocal_settings_dark" to "gemma-local-settings.png",
                    "GemmaLocalScreenshotTest_gemmaLocal_modelCard_dark" to "gemma-local-model-card.png",
                    "GemmaLocalScreenshotTest_gemmaLocal_contextSlider_dark" to "gemma-local-context-slider.png",
                    "GemmaLocalScreenshotTest_gemmaLocal_download_dark" to "gemma-local-download.png",
                    "GemmaLocalScreenshotTest_gemmaLocal_select_dark" to "gemma-local-select.png",
                    "GemmaLocalScreenshotTest_gemmaLocal_chat_dark" to "gemma-local-chat.png",
                ).forEach { (k, v) -> add(k to siteImgDir.resolve(v)) }
            }

        val files = snapshotsDir.listFiles() ?: emptyArray()
        copies.forEach { (key, dest) ->
            val match = files.firstOrNull { it.name.contains(key) } ?: return@forEach
            match.copyTo(dest, overwrite = true)
            println("Copied ${match.name} -> ${dest.relativeTo(rootProject.projectDir)}")
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
