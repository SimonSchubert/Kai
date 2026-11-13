import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("KaiDatabase") {
            packageName.set("com.kainext.duo.db")
            // The schema lives on user devices — structural changes need a
            // numbered .sqm migration file (see conversation.sq header).
            // This makes the build verify that migrations reproduce the schema.
            verifyMigrations.set(true)
        }
    }
}

compose.resources {
    // Pinned so the generated `Res` class stays at `kai.composeapp.generated.resources`
    // regardless of rootProject.name or the Gradle module path. The whole codebase imports
    // that package; deriving it from the project name would silently break ~700 imports
    // the moment the project is renamed.
    packageOfResClass = "kai.composeapp.generated.resources"
}

composeCompiler {
    stabilityConfigurationFiles.add(project.layout.projectDirectory.file("compose_stability.conf"))
}

kotlin {
    android {
        namespace = "com.kainext.duo.shared"
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
        withHostTest {}
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/src/commonMain/kotlin"))
            dependencies {
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

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

                implementation(libs.tts)
                implementation(libs.tts.compose)

                implementation(libs.koin.compose)
                implementation(libs.koin.compose.viewmodel)
                implementation(libs.koin.core)

                implementation(libs.multiplatform.settings)
                implementation(libs.multiplatform.settings.no.arg)

                implementation(libs.filekit.core)
                implementation(libs.filekit.compose)

                implementation(libs.coil.compose)
                implementation(libs.coil.svg)
                implementation(libs.coil.network.ktor3)

                implementation(libs.reorderable)

                implementation(libs.sqldelight.runtime)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.turbine)
                implementation(libs.multiplatform.settings.test)
            }
        }
        androidMain {
            kotlin.srcDir("src/jvmShared/kotlin")
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.lifecycle.process)
                implementation(libs.spght.encryptedprefs)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.koin.android)
                implementation(libs.material)
                implementation(libs.bouncycastle.provider)
                implementation(libs.shizuku.api)
                implementation(libs.shizuku.provider)
                implementation(libs.shizuku.aidl)
                implementation(libs.litert.lm)
                implementation(libs.sqldelight.android.driver)
                implementation(libs.xz)
            }
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
                    .file("generated/src/commonMain/kotlin/com/kainext/duo/Version.kt")
                    .get()
                    .asFile
            versionFile.parentFile?.mkdirs()
            versionFile.writeText(
                """
                package com.kainext.duo

                object Version {
                    const val appVersion = "$appVersion"
                }
                """.trimIndent(),
            )
        }
    }
}

apply<VersionGeneratorPlugin>()
