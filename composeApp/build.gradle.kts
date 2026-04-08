import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidLibrary {
        namespace = "com.inspiredandroid.kai.shared"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        androidResources {
            enable = true
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "com.inspiredandroid.kai")
        }
    }

    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "composeApp"
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "composeApp.js"
                devServer =
                    (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                        // Serve sources to debug inside browser
                        static(rootDirPath)
                        static(projectDirPath)
                    }
            }
        }
        binaries.executable()
    }

    sourceSets {
        val desktopMain by getting
        val commonMain by getting {
            kotlin.srcDir(layout.buildDirectory.dir("generated/src/commonMain/kotlin"))
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.turbine)
                implementation(libs.multiplatform.settings.test)
            }
        }

        val androidMain by getting {
            kotlin.srcDir("src/jvmShared/kotlin")
        }
        desktopMain.kotlin.srcDir("src/jvmShared/kotlin")
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.spght.encryptedprefs)
            implementation(libs.ktor.client.android)
            implementation(libs.koin.android)
            implementation(libs.material)
            implementation(libs.bouncycastle.provider)
            implementation(libs.litert.lm)
        }
        commonMain.dependencies {
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.core)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.components.uiToolingPreview)

            implementation(libs.androidx.navigation.compose)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)

            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)

            implementation(libs.multiplatform.markdown.renderer)
            implementation(libs.multiplatform.markdown.renderer.m3)
            implementation(libs.multiplatform.markdown.renderer.coil3)
            implementation(libs.multiplatform.markdown.renderer.code)

            implementation(libs.tts)
            implementation(libs.tts.compose)

            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.core)

            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)

            implementation(libs.filekit.core)
            implementation(libs.filekit.compose)

            implementation(libs.coil.svg)
            implementation(libs.coil.network.ktor3)

            implementation(libs.reorderable)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.cio)
            implementation(libs.bouncycastle.provider)
            implementation(libs.slf4j.nop)
            implementation(libs.litert.lm.jvm)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.ktor.network)
            implementation(libs.ktor.network.tls)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.inspiredandroid.kai.MainKt"

        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.AppImage)
            packageName = "Kai"
            packageVersion = libs.versions.appVersion.get()

            macOS {
                iconFile.set(project.file("icon.icns"))
            }
            windows {
                iconFile.set(project.file("icon.ico"))
            }
            linux {
                iconFile.set(project.file("icon.png"))
                modules("jdk.security.auth")
            }
        }
    }
}

// BouncyCastle is a cryptographically signed JCE provider jar. ProGuard rewrites
// it and strips the META-INF signatures, causing "SHA-256 digest error" at
// runtime. After ProGuard finishes, replace the processed jar with the original.
afterEvaluate {
    tasks.matching { it.name == "proguardReleaseJars" }.configureEach {
        doLast {
            val proguardDir =
                layout.buildDirectory
                    .dir("compose/tmp/main-release/proguard")
                    .get()
                    .asFile
            val processedJar = proguardDir.listFiles()?.find { it.name.startsWith("bcprov") } ?: return@doLast
            val originalJar =
                configurations["desktopRuntimeClasspath"]
                    .resolve()
                    .find { it.name.startsWith("bcprov") } ?: return@doLast
            originalJar.copyTo(processedJar, overwrite = true)
            logger.lifecycle("Restored original signed BouncyCastle jar: ${processedJar.name}")
        }
    }
}

class VersionGeneratorPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.afterEvaluate {
            val appVersion = libs.versions.appVersion.get()

            // Generate Kotlin version file
            val versionFile =
                layout.buildDirectory
                    .file("generated/src/commonMain/kotlin/com/inspiredandroid/kai/Version.kt")
                    .get()
                    .asFile
            versionFile.parentFile?.mkdirs()
            versionFile.writeText(
                """
                package com.inspiredandroid.kai

                object Version {
                    const val appVersion = "$appVersion"
                }
                """.trimIndent(),
            )

            // Update iOS Config.xcconfig with version
            val xcConfigFile = rootProject.file("iosApp/Configuration/Config.xcconfig")
            if (xcConfigFile.exists()) {
                val content = xcConfigFile.readText()
                val updatedContent =
                    if (content.contains("APP_VERSION=")) {
                        content.replace(Regex("APP_VERSION=.*"), "APP_VERSION=$appVersion")
                    } else {
                        content.trimEnd() + "\nAPP_VERSION=$appVersion\n"
                    }
                xcConfigFile.writeText(updatedContent)
            }
        }
    }
}

apply<VersionGeneratorPlugin>()
