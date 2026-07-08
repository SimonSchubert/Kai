# Desktop Dev-Tools Sandbox Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the desktop/Flatpak build of Kai a working "Dev Tools" sandbox (currently a no-op), backed by micromamba/conda-forge, matching Android's install → install-packages → use-shell UX.

**Architecture:** A new `desktopMain`-only `DesktopLinuxSandboxManager` downloads the micromamba binary to `~/.kai/linux-sandbox/micromamba/` and drives package installs via `micromamba install`. A new `DesktopPersistentShell` runs a plain (non-chroot) `bash` with `PATH`/`MAMBA_ROOT_PREFIX` pointed at that install, reusing the same sentinel-based command-boundary protocol Android's shell already uses. `DesktopSandboxController` (replacing `NoOpSandboxController`) wires these into the existing `SandboxController` interface untouched. The sandbox tab, currently gated to Android only, is extended to also show on `Platform.Desktop.Linux`.

**Tech Stack:** Kotlin Multiplatform, Ktor client (`ktor-client-cio`, already a desktop dependency), Koin DI, `kotlin.test` for desktopTest, Ktor's `ktor-client-mock` (new test-only dependency) for testable HTTP.

## Global Constraints

- Reference spec: `docs/superpowers/specs/2026-06-30-desktop-sandbox-design.md` — every task below implements a specific piece of it.
- Storage layout (from spec): `~/.kai/linux-sandbox/micromamba/bin/micromamba` (binary) and `~/.kai/linux-sandbox/micromamba/root/` (`MAMBA_ROOT_PREFIX`).
- Default package set (from spec, conda-forge names): `git`, `curl`, `wget`, `jq`, `python`, `nodejs`, `openssh`, `lftp`, `rsync`.
- Zero changes to any file under `composeApp/src/androidMain/` — this plan is additive-only on the desktop side, except for one dependency-free file relocation (Task 1) that is verified not to break the Android build.
- Do not implement the Roadmap section (non-Flatpak bwrap+Alpine) — explicitly out of scope per the spec.
- `getAppFilesDirectory()` (`composeApp/src/desktopMain/kotlin/com/inspiredandroid/kai/Platform.jvm.kt:131`) already resolves to `~/.kai` and must be reused, not reimplemented.

---

### Task 1: Move `SandboxState` to `jvmShared` so desktop can use it

**Files:**
- Move: `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/sandbox/SandboxState.kt` → `composeApp/src/jvmShared/kotlin/com/inspiredandroid/kai/sandbox/SandboxState.kt`
- Test: no new test — this is a pure relocation of a dependency-free file, verified by the existing Android build/tests still passing.

**Interfaces:**
- Produces: `sealed interface SandboxState` with `NotInstalled`, `Downloading(progress: Float)`, `Extracting`, `Installing(detail: String = "")`, `Ready`, `Error(message: String)` — package `com.inspiredandroid.kai.sandbox`. Both `androidMain` and `desktopMain` can now import this (recall `composeApp/build.gradle.kts` already wires `desktopMain.kotlin.srcDir("src/jvmShared/kotlin")` and `androidMain.kotlin.srcDir("src/jvmShared/kotlin")`).

- [ ] **Step 1: Read the current file to confirm exact contents before moving**

Run: `cat composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/sandbox/SandboxState.kt`
Expected output:
```kotlin
package com.inspiredandroid.kai.sandbox

sealed interface SandboxState {
    data object NotInstalled : SandboxState
    data class Downloading(val progress: Float) : SandboxState
    data object Extracting : SandboxState
    data class Installing(val detail: String = "") : SandboxState
    data object Ready : SandboxState
    data class Error(val message: String) : SandboxState
}
```

- [ ] **Step 2: Create the file at the new jvmShared location with identical content**

Create `composeApp/src/jvmShared/kotlin/com/inspiredandroid/kai/sandbox/SandboxState.kt`:
```kotlin
package com.inspiredandroid.kai.sandbox

sealed interface SandboxState {
    data object NotInstalled : SandboxState
    data class Downloading(val progress: Float) : SandboxState
    data object Extracting : SandboxState
    data class Installing(val detail: String = "") : SandboxState
    data object Ready : SandboxState
    data class Error(val message: String) : SandboxState
}
```

- [ ] **Step 3: Delete the old androidMain copy**

Run: `trash composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/sandbox/SandboxState.kt`
Expected: file removed, no error (this path is on the real disk volume, not tmpfs, so `trash` — not `rip` — is correct here).

- [ ] **Step 4: Verify the Android build still compiles (no other file needs to change — same package, same file name, compiler just finds it via the new source root)**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL`. If it fails referencing `SandboxState`, check for a stray duplicate file left behind by Step 3.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/jvmShared/kotlin/com/inspiredandroid/kai/sandbox/SandboxState.kt composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/sandbox/SandboxState.kt
git commit -m "Move SandboxState to jvmShared so desktop can reuse it"
```

---

### Task 2: Extend the sandbox tab visibility gate to desktop Linux

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/settings/SandboxViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/com/inspiredandroid/kai/ui/settings/SandboxViewModelTest.kt` (new)

**Interfaces:**
- Consumes: `Platform` sealed class from `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/Platform.kt` (`Platform.Mobile.Android`, `Platform.Desktop.Linux`, etc. — already defined, no changes needed there).
- Produces: `fun shouldShowSandboxTab(platform: Platform): Boolean` — a new top-level function in `SandboxViewModel.kt`, used by later tasks' UI work (Task 8) if needed, and directly by this task's own `SandboxUiState` seeding.

- [ ] **Step 1: Write the failing test**

Create `composeApp/src/commonTest/kotlin/com/inspiredandroid/kai/ui/settings/SandboxViewModelTest.kt`:
```kotlin
package com.inspiredandroid.kai.ui.settings

import com.inspiredandroid.kai.Platform
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SandboxViewModelTest {

    @Test
    fun showsOnAndroid() {
        assertTrue(shouldShowSandboxTab(Platform.Mobile.Android))
    }

    @Test
    fun showsOnDesktopLinux() {
        assertTrue(shouldShowSandboxTab(Platform.Desktop.Linux))
    }

    @Test
    fun hiddenOnIos() {
        assertFalse(shouldShowSandboxTab(Platform.Mobile.Ios))
    }

    @Test
    fun hiddenOnDesktopMac() {
        assertFalse(shouldShowSandboxTab(Platform.Desktop.Mac))
    }

    @Test
    fun hiddenOnDesktopWindows() {
        assertFalse(shouldShowSandboxTab(Platform.Desktop.Windows))
    }

    @Test
    fun hiddenOnWeb() {
        assertFalse(shouldShowSandboxTab(Platform.Web))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :composeApp:desktopTest --tests "com.inspiredandroid.kai.ui.settings.SandboxViewModelTest"`
Expected: FAIL with "Unresolved reference: shouldShowSandboxTab" (compile error, since the function doesn't exist yet).

- [ ] **Step 3: Add the function and use it in `SandboxViewModel`**

In `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/settings/SandboxViewModel.kt`, replace:
```kotlin
    private val _state = MutableStateFlow(
        applyStatus(
            sandboxController.status.value,
            SandboxUiState(
                showSandbox = currentPlatform is Platform.Mobile.Android,
                isSandboxEnabled = dataRepository.isSandboxEnabled(),
            ),
        ),
    )
```
with:
```kotlin
    private val _state = MutableStateFlow(
        applyStatus(
            sandboxController.status.value,
            SandboxUiState(
                showSandbox = shouldShowSandboxTab(currentPlatform),
                isSandboxEnabled = dataRepository.isSandboxEnabled(),
            ),
        ),
    )
```

Then add this top-level function to the same file, above the `SandboxUiState` data class:
```kotlin
fun shouldShowSandboxTab(platform: Platform): Boolean =
    platform is Platform.Mobile.Android || platform is Platform.Desktop.Linux
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :composeApp:desktopTest --tests "com.inspiredandroid.kai.ui.settings.SandboxViewModelTest"`
Expected: `BUILD SUCCESSFUL`, 6 tests passed.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/settings/SandboxViewModel.kt composeApp/src/commonTest/kotlin/com/inspiredandroid/kai/ui/settings/SandboxViewModelTest.kt
git commit -m "Show the sandbox tab on desktop Linux, not just Android"
```

---

### Task 3: Micromamba path/arch resolution (pure functions)

**Files:**
- Create: `composeApp/src/desktopMain/kotlin/com/inspiredandroid/kai/sandbox/MicromambaPaths.kt`
- Test: `composeApp/src/desktopTest/kotlin/com/inspiredandroid/kai/sandbox/MicromambaPathsTest.kt`

**Interfaces:**
- Produces:
  - `fun micromambaDownloadUrl(osArch: String = System.getProperty("os.arch")): String` — conda-forge/micromamba release URL for the running architecture.
  - `class MicromambaLayout(baseDir: File)` with `val binaryFile: File`, `val rootPrefix: File` — used by Task 4/5/6.
- Consumes: nothing new (plain `java.io.File`, `java.lang.System`).

- [ ] **Step 1: Write the failing tests**

Create `composeApp/src/desktopTest/kotlin/com/inspiredandroid/kai/sandbox/MicromambaPathsTest.kt`:
```kotlin
package com.inspiredandroid.kai.sandbox

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MicromambaPathsTest {

    private lateinit var baseDir: File

    @BeforeTest
    fun setUp() {
        baseDir = Files.createTempDirectory("micromamba-paths-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        baseDir.deleteRecursively()
    }

    @Test
    fun downloadUrlForX86_64() {
        val url = micromambaDownloadUrl(osArch = "amd64")
        assertEquals(
            "https://micro.mamba.pm/api/micromamba/linux-64/latest",
            url,
        )
    }

    @Test
    fun downloadUrlForAarch64() {
        val url = micromambaDownloadUrl(osArch = "aarch64")
        assertEquals(
            "https://micro.mamba.pm/api/micromamba/linux-aarch64/latest",
            url,
        )
    }

    @Test
    fun layoutPointsBinaryUnderBinDir() {
        val layout = MicromambaLayout(baseDir)
        assertEquals(File(baseDir, "bin/micromamba"), layout.binaryFile)
    }

    @Test
    fun layoutPointsRootPrefixUnderRootDir() {
        val layout = MicromambaLayout(baseDir)
        assertEquals(File(baseDir, "root"), layout.rootPrefix)
    }

    @Test
    fun layoutDoesNotCreateDirsOnConstruction() {
        MicromambaLayout(baseDir)
        assertTrue(baseDir.listFiles()?.isEmpty() != false)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:desktopTest --tests "com.inspiredandroid.kai.sandbox.MicromambaPathsTest"`
Expected: FAIL with "Unresolved reference: micromambaDownloadUrl" / "Unresolved reference: MicromambaLayout".

- [ ] **Step 3: Implement**

Create `composeApp/src/desktopMain/kotlin/com/inspiredandroid/kai/sandbox/MicromambaPaths.kt`:
```kotlin
package com.inspiredandroid.kai.sandbox

import java.io.File

/**
 * Maps JVM `os.arch` values to the conda-forge/micromamba platform tag used
 * in their release download URLs. See https://mamba.readthedocs.io/en/latest/installation/micromamba-installation.html
 */
fun micromambaDownloadUrl(osArch: String = System.getProperty("os.arch")): String {
    val tag = when {
        osArch == "amd64" || osArch == "x86_64" -> "linux-64"
        osArch == "aarch64" || osArch == "arm64" -> "linux-aarch64"
        else -> "linux-64"
    }
    return "https://micro.mamba.pm/api/micromamba/$tag/latest"
}

/**
 * Layout of the desktop micromamba install under `~/.kai/linux-sandbox/micromamba/`.
 * Construction does no I/O — callers create directories explicitly when needed.
 */
class MicromambaLayout(baseDir: File) {
    val binaryFile: File = File(baseDir, "bin/micromamba")
    val rootPrefix: File = File(baseDir, "root")
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:desktopTest --tests "com.inspiredandroid.kai.sandbox.MicromambaPathsTest"`
Expected: `BUILD SUCCESSFUL`, 5 tests passed.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/desktopMain/kotlin/com/inspiredandroid/kai/sandbox/MicromambaPaths.kt composeApp/src/desktopTest/kotlin/com/inspiredandroid/kai/sandbox/MicromambaPathsTest.kt
git commit -m "Add micromamba path/arch resolution for desktop sandbox"
```

---

### Task 4: Micromamba binary downloader (testable via Ktor mock)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `composeApp/build.gradle.kts`
- Create: `composeApp/src/desktopMain/kotlin/com/inspiredandroid/kai/sandbox/MicromambaDownloader.kt`
- Test: `composeApp/src/desktopTest/kotlin/com/inspiredandroid/kai/sandbox/MicromambaDownloaderTest.kt`

**Interfaces:**
- Consumes: `micromambaDownloadUrl()` from Task 3.
- Produces: `class MicromambaDownloader(private val httpClient: HttpClient)` with `suspend fun download(targetFile: File, onProgress: (Float) -> Unit)` — used by Task 5's `DesktopLinuxSandboxManager`.

- [ ] **Step 1: Add the `ktor-client-mock` test dependency**

In `gradle/libs.versions.toml`, find the `ktor-client-cio` line (around line 59) and add directly below it:
```toml
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
```

In `composeApp/build.gradle.kts`, find the `desktopTest` source set (create it if it doesn't already have a `dependencies` block — check first with `grep -n "desktopTest" composeApp/build.gradle.kts`). Add:
```kotlin
        val desktopTest by getting {
            dependencies {
                implementation(libs.ktor.client.mock)
            }
        }
```
If `desktopTest by getting` already exists with a `dependencies` block, add `implementation(libs.ktor.client.mock)` as a new line inside it instead of creating a duplicate block.

- [ ] **Step 2: Write the failing test**

Create `composeApp/src/desktopTest/kotlin/com/inspiredandroid/kai/sandbox/MicromambaDownloaderTest.kt`:
```kotlin
package com.inspiredandroid.kai.sandbox

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MicromambaDownloaderTest {

    private lateinit var targetDir: File

    @BeforeTest
    fun setUp() {
        targetDir = Files.createTempDirectory("micromamba-downloader-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        targetDir.deleteRecursively()
    }

    @Test
    fun downloadsBytesToTargetFile() = runTest {
        val fakeBytes = "fake-micromamba-binary-contents".toByteArray()
        val engine = MockEngine { request ->
            respond(
                content = fakeBytes,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentLength, fakeBytes.size.toString()),
            )
        }
        val downloader = MicromambaDownloader(HttpClient(engine))
        val target = File(targetDir, "micromamba")

        var lastProgress = 0f
        downloader.download(target) { progress -> lastProgress = progress }

        assertTrue(target.exists())
        assertEquals(fakeBytes.toList(), target.readBytes().toList())
        assertEquals(1f, lastProgress)
    }

    @Test
    fun throwsOnNonSuccessStatus() = runTest {
        val engine = MockEngine { respond(content = "", status = HttpStatusCode.NotFound) }
        val downloader = MicromambaDownloader(HttpClient(engine))
        val target = File(targetDir, "micromamba")

        assertFailsWith<java.io.IOException> {
            downloader.download(target) { }
        }
        assertTrue(!target.exists())
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :composeApp:desktopTest --tests "com.inspiredandroid.kai.sandbox.MicromambaDownloaderTest"`
Expected: FAIL with "Unresolved reference: MicromambaDownloader".

- [ ] **Step 4: Implement**

Create `composeApp/src/desktopMain/kotlin/com/inspiredandroid/kai/sandbox/MicromambaDownloader.kt`:
```kotlin
package com.inspiredandroid.kai.sandbox

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private const val BUFFER_SIZE = 8192

class MicromambaDownloader(private val httpClient: HttpClient) {

    suspend fun download(targetFile: File, onProgress: (Float) -> Unit) {
        val url = micromambaDownloadUrl()
        httpClient.prepareGet(url).execute { response ->
            if (!response.status.isSuccess()) {
                throw IOException("HTTP ${response.status.value} from $url")
            }
            targetFile.parentFile?.mkdirs()
            val totalBytes = response.contentLength() ?: -1L
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(BUFFER_SIZE)
            var downloadedBytes = 0L

            try {
                FileOutputStream(targetFile).use { output ->
                    while (!channel.isClosedForRead) {
                        val bytesRead = channel.readAvailable(buffer)
                        if (bytesRead <= 0) break
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            onProgress(downloadedBytes.toFloat() / totalBytes)
                        }
                    }
                }
            } catch (e: Exception) {
                targetFile.delete()
                throw e
            }
        }
        targetFile.setExecutable(true, false)
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :composeApp:desktopTest --tests "com.inspiredandroid.kai.sandbox.MicromambaDownloaderTest"`
Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml composeApp/build.gradle.kts composeApp/src/desktopMain/kotlin/com/inspiredandroid/kai/sandbox/MicromambaDownloader.kt composeApp/src/desktopTest/kotlin/com/inspiredandroid/kai/sandbox/MicromambaDownloaderTest.kt
git commit -m "Add testable micromamba binary downloader for desktop sandbox"
```

---

### Task 5: `DesktopLinuxSandboxManager` — install + package management state machine

**Files:**
- Create: `composeApp/src/desktopMain/kotlin/com/inspiredandroid/kai/sandbox/DesktopLinuxSandboxManager.kt`
- Test: `composeApp/src/desktopTest/kotlin/com/inspiredandroid/kai/sandbox/DesktopLinuxSandboxManagerTest.kt`

**Interfaces:**
- Consumes: `MicromambaLayout`, `MicromambaDownloader` (Tasks 3–4), `SandboxState` (Task 1), `getAppFilesDirectory()` (`Platform.jvm.kt`, unchanged).
- Produces: `class DesktopLinuxSandboxManager(private val baseDir: File, private val downloader: MicromambaDownloader)` with:
  - `val state: StateFlow<SandboxState>`
  - `fun setup()`
  - `fun cancel()`
  - `fun reset()`
  - `fun installPackages()`
  - `fun arePackagesInstalled(): Boolean`
  - `fun getDiskUsageMB(): Long`
  - `val layout: MicromambaLayout` (exposed so Task 6/7 can build shell env vars from it)
  - Used directly by Task 7's `DesktopSandboxController`.

- [ ] **Step 1: Write the failing tests**

Create `composeApp/src/desktopTest/kotlin/com/inspiredandroid/kai/sandbox/DesktopLinuxSandboxManagerTest.kt`:
```kotlin
package com.inspiredandroid.kai.sandbox

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.delay
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopLinuxSandboxManagerTest {

    private lateinit var baseDir: File

    @BeforeTest
    fun setUp() {
        baseDir = Files.createTempDirectory("desktop-sandbox-manager-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        baseDir.deleteRecursively()
    }

    private fun managerWithFakeDownload(bytes: ByteArray = byteArrayOf(1, 2, 3, 4)): DesktopLinuxSandboxManager {
        val engine = MockEngine {
            respond(
                content = bytes,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentLength, bytes.size.toString()),
            )
        }
        return DesktopLinuxSandboxManager(baseDir, MicromambaDownloader(HttpClient(engine)))
    }

    @Test
    fun startsNotInstalled() {
        val manager = managerWithFakeDownload()
        assertIs<SandboxState.NotInstalled>(manager.state.value)
    }

    @Test
    fun setupDownloadsBinaryAndReachesReady() = kotlinx.coroutines.test.runTest {
        val manager = managerWithFakeDownload()
        manager.setup()
        // setup() launches on its own scope; poll briefly for completion.
        var attempts = 0
        while (manager.state.value !is SandboxState.Ready && attempts < 50) {
            delay(20)
            attempts++
        }
        assertIs<SandboxState.Ready>(manager.state.value)
        assertTrue(manager.layout.binaryFile.exists())
        assertTrue(manager.layout.binaryFile.canExecute())
    }

    @Test
    fun resetDeletesEverythingAndReturnsToNotInstalled() = kotlinx.coroutines.test.runTest {
        val manager = managerWithFakeDownload()
        manager.setup()
        var attempts = 0
        while (manager.state.value !is SandboxState.Ready && attempts < 50) {
            delay(20)
            attempts++
        }
        manager.reset()
        attempts = 0
        while (manager.state.value !is SandboxState.NotInstalled && attempts < 50) {
            delay(20)
            attempts++
        }
        assertIs<SandboxState.NotInstalled>(manager.state.value)
        assertFalse(manager.layout.binaryFile.exists())
    }

    @Test
    fun arePackagesInstalledFalseWhenNotReady() {
        val manager = managerWithFakeDownload()
        assertFalse(manager.arePackagesInstalled())
    }

    @Test
    fun diskUsageZeroWhenNotInstalled() {
        val manager = managerWithFakeDownload()
        assertEquals(0L, manager.getDiskUsageMB())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:desktopTest --tests "com.inspiredandroid.kai.sandbox.DesktopLinuxSandboxManagerTest"`
Expected: FAIL with "Unresolved reference: DesktopLinuxSandboxManager".

- [ ] **Step 3: Implement**

Create `composeApp/src/desktopMain/kotlin/com/inspiredandroid/kai/sandbox/DesktopLinuxSandboxManager.kt`:
```kotlin
package com.inspiredandroid.kai.sandbox

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val DEFAULT_PACKAGES = listOf(
    "git", "curl", "wget", "jq", "python", "nodejs", "openssh", "lftp", "rsync",
)

class DesktopLinuxSandboxManager(
    private val baseDir: File,
    private val downloader: MicromambaDownloader,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null

    private val _state = MutableStateFlow<SandboxState>(SandboxState.NotInstalled)
    val state: StateFlow<SandboxState> = _state

    val layout: MicromambaLayout = MicromambaLayout(baseDir)

    init {
        if (layout.binaryFile.exists() && layout.binaryFile.canExecute()) {
            _state.value = SandboxState.Ready
        }
    }

    fun setup() {
        if (currentJob?.isActive == true) return
        currentJob = scope.launch {
            try {
                _state.value = SandboxState.Downloading(0f)
                downloader.download(layout.binaryFile) { progress ->
                    _state.value = SandboxState.Downloading(progress)
                }
                layout.rootPrefix.mkdirs()
                _state.value = SandboxState.Ready
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = if (layout.binaryFile.exists()) SandboxState.Ready else SandboxState.NotInstalled
            } catch (e: Exception) {
                _state.value = SandboxState.Error(e.message ?: "Setup failed")
            }
        }
    }

    fun cancel() {
        currentJob?.cancel()
        currentJob = null
        _state.value = if (layout.binaryFile.exists() && layout.binaryFile.canExecute()) {
            SandboxState.Ready
        } else {
            SandboxState.NotInstalled
        }
    }

    fun reset() {
        scope.launch {
            currentJob?.cancel()
            baseDir.deleteRecursively()
            _state.value = SandboxState.NotInstalled
        }
    }

    fun installPackages() {
        if (currentJob?.isActive == true) return
        currentJob = scope.launch {
            try {
                for (pkg in DEFAULT_PACKAGES) {
                    ensureActive()
                    _state.value = SandboxState.Installing("Installing $pkg...")
                    val process = ProcessBuilder(
                        layout.binaryFile.absolutePath,
                        "install", "-y", "-p", layout.rootPrefix.absolutePath,
                        "-c", "conda-forge", pkg,
                    ).redirectErrorStream(true).start()
                    val finished = process.waitFor(120, TimeUnit.SECONDS)
                    ensureActive()
                    if (!finished) {
                        process.destroyForcibly()
                        _state.value = SandboxState.Error("Timed out installing $pkg")
                        return@launch
                    }
                    if (process.exitValue() != 0) {
                        val output = process.inputStream.bufferedReader().readText()
                        _state.value = SandboxState.Error("Failed to install $pkg: ${output.take(200)}")
                        return@launch
                    }
                }
                _state.value = SandboxState.Ready
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = SandboxState.Ready
            } catch (e: Exception) {
                _state.value = SandboxState.Error("Install failed: ${e.message}")
            }
        }
    }

    fun arePackagesInstalled(): Boolean {
        if (_state.value !is SandboxState.Ready) return false
        return File(layout.rootPrefix, "bin/python3").exists() ||
            File(layout.rootPrefix, "bin/git").exists()
    }

    fun getDiskUsageMB(): Long {
        if (!baseDir.isDirectory) return 0
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(baseDir)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = try { dir.listFiles() } catch (_: Throwable) { null } ?: continue
            for (child in children) {
                try {
                    when {
                        child.isDirectory -> stack.addLast(child)
                        child.isFile -> total += child.length()
                    }
                } catch (_: Throwable) {
                    // skip transient/inaccessible entry
                }
            }
        }
        return total / (1024 * 1024)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:desktopTest --tests "com.inspiredandroid.kai.sandbox.DesktopLinuxSandboxManagerTest"`
Expected: `BUILD SUCCESSFUL`, 5 tests passed.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/desktopMain/kotlin/com/inspiredandroid/kai/sandbox/DesktopLinuxSandboxManager.kt composeApp/src/desktopTest/kotlin/com/inspiredandroid/kai/sandbox/DesktopLinuxSandboxManagerTest.kt
git commit -m "Add DesktopLinuxSandboxManager: micromamba install + package state machine"
```

---

### Task 6: `DesktopPersistentShell` — plain shell with micromamba on PATH

**Files:**
- Create: `composeApp/src/desktopMain/kotlin/com/inspiredandroid/kai/sandbox/DesktopPersistentShell.kt`
- Test: `composeApp/src/desktopTest/kotlin/com/inspiredandroid/kai/sandbox/DesktopPersistentShellTest.kt`

**Interfaces:**
- Consumes: `MicromambaLayout` (Task 3).
- Produces: `class DesktopPersistentShell(private val layout: MicromambaLayout)` with `suspend fun run(command: String, timeoutSeconds: Long, onStdout: ((String) -> Unit)? = null, onStderr: ((String) -> Unit)? = null): Map<String, Any>` returning the same result-map shape as Android's `PersistentSandboxShell.run` (`success`, `stdout`, `stderr`, `exit_code`, `timed_out`, `cwd`, `shell_died`) — consumed directly by Task 7's `DesktopSandboxController`. Also `fun reset()` and `fun cancelForeground()`.

- [ ] **Step 1: Write the failing tests**

Create `composeApp/src/desktopTest/kotlin/com/inspiredandroid/kai/sandbox/DesktopPersistentShellTest.kt`:
```kotlin
package com.inspiredandroid.kai.sandbox

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopPersistentShellTest {

    private lateinit var baseDir: File
    private lateinit var shell: DesktopPersistentShell

    @BeforeTest
    fun setUp() {
        baseDir = Files.createTempDirectory("desktop-persistent-shell-test").toFile()
        shell = DesktopPersistentShell(MicromambaLayout(baseDir))
    }

    @AfterTest
    fun tearDown() {
        shell.reset()
        baseDir.deleteRecursively()
    }

    @Test
    fun runsSimpleCommandAndCapturesStdout() = runTest {
        val result = shell.run("echo hello-desktop-shell", timeoutSeconds = 10)
        assertEquals(true, result["success"])
        assertEquals("hello-desktop-shell", result["stdout"])
        assertEquals(0, result["exit_code"])
    }

    @Test
    fun preservesCwdAcrossCommands() = runTest {
        shell.run("cd /tmp", timeoutSeconds = 10)
        val result = shell.run("pwd", timeoutSeconds = 10)
        assertEquals("/tmp", result["stdout"])
    }

    @Test
    fun reportsNonZeroExitCode() = runTest {
        val result = shell.run("exit 7", timeoutSeconds = 10)
        assertEquals(false, result["success"])
        assertEquals(7, result["exit_code"])
    }

    @Test
    fun pathIncludesMicromambaRootBin() = runTest {
        val result = shell.run("echo \$PATH", timeoutSeconds = 10)
        val stdout = result["stdout"] as String
        assertTrue(stdout.contains(File(baseDir, "root/bin").absolutePath))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:desktopTest --tests "com.inspiredandroid.kai.sandbox.DesktopPersistentShellTest"`
Expected: FAIL with "Unresolved reference: DesktopPersistentShell".

- [ ] **Step 3: Implement**

Create `composeApp/src/desktopMain/kotlin/com/inspiredandroid/kai/sandbox/DesktopPersistentShell.kt`:
```kotlin
package com.inspiredandroid.kai.sandbox

import java.io.BufferedReader
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val MAX_OUTPUT_LENGTH = 15_000
private const val RS = ""
private const val US = ""
private const val PID_PROBE_PREFIX = "$RS" + "KAIDESKTOPPID$US"

/**
 * A persistent, non-chroot bash shell with micromamba's installed environment
 * on PATH. Structurally the same sentinel-based command-boundary protocol as
 * Android's PersistentSandboxShell, without any chroot/proot layer — desktop
 * has no separate fake root, so this is just an ordinary shell with env vars set.
 */
class DesktopPersistentShell(private val layout: MicromambaLayout) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var process: Process? = null
    @Volatile private var shellPid: Long? = null
    private var watchdog: Job? = null
    private val currentSink = AtomicReference<CommandSink?>(null)

    private class CommandSink(
        val nonce: String,
        val stdoutBuf: StringBuilder = StringBuilder(),
        val stderrBuf: StringBuilder = StringBuilder(),
        val onStdout: ((String) -> Unit)? = null,
        val onStderr: ((String) -> Unit)? = null,
        val done: CompletableDeferred<Result> = CompletableDeferred(),
    )

    private data class Result(val exitCode: Int, val cwd: String, val shellDied: Boolean = false)

    suspend fun run(
        command: String,
        timeoutSeconds: Long,
        onStdout: ((String) -> Unit)? = null,
        onStderr: ((String) -> Unit)? = null,
    ): Map<String, Any> = mutex.withLock {
        ensureShell()
        val nonce = (0 until 16).map { "0123456789abcdef".random() }.joinToString("")
        val sink = CommandSink(nonce, onStdout = onStdout, onStderr = onStderr)
        currentSink.set(sink)

        val line = "$command; __kai_st=\$?; " +
            "printf '\\n\\036%s\\037%d\\037%s\\036\\n' '$nonce' \"\$__kai_st\" \"\$PWD\" >&2"
        writeLine(line)

        val result = withTimeoutOrNull(timeoutSeconds.seconds) { sink.done.await() }
        currentSink.set(null)
        if (result == null) {
            reset()
            return@withLock mapOf(
                "success" to false,
                "stdout" to sink.stdoutBuf.toString(),
                "stderr" to sink.stderrBuf.toString(),
                "exit_code" to -1,
                "timed_out" to true,
                "cwd" to "",
                "shell_died" to true,
            )
        }
        mapOf(
            "success" to (!result.shellDied && result.exitCode == 0),
            "stdout" to sink.stdoutBuf.toString(),
            "stderr" to sink.stderrBuf.toString(),
            "exit_code" to result.exitCode,
            "timed_out" to false,
            "cwd" to result.cwd,
            "shell_died" to result.shellDied,
        )
    }

    fun cancelForeground() {
        reset()
    }

    fun reset() {
        watchdog?.cancel()
        watchdog = null
        process?.destroyForcibly()
        process = null
        shellPid = null
        currentSink.getAndSet(null)?.done?.complete(Result(exitCode = -1, cwd = "", shellDied = true))
    }

    private fun writeLine(line: String) {
        val p = process ?: return
        p.outputStream.write((line + "\n").toByteArray())
        p.outputStream.flush()
    }

    private fun ensureShell() {
        if (process != null) return
        val env = HashMap(System.getenv())
        val existingPath = env["PATH"].orEmpty()
        env["PATH"] = "${File(layout.rootPrefix, "bin").absolutePath}:$existingPath"
        env["MAMBA_ROOT_PREFIX"] = layout.rootPrefix.absolutePath

        val pb = ProcessBuilder("bash", "--noprofile", "--norc")
        pb.environment().putAll(env)
        pb.redirectErrorStream(false)
        val p = pb.start()
        process = p

        val stdoutThread = Thread { streamLines(p.inputStream.bufferedReader(), isStderr = false) }
        val stderrThread = Thread { streamLines(p.errorStream.bufferedReader(), isStderr = true) }
        stdoutThread.isDaemon = true
        stderrThread.isDaemon = true
        stdoutThread.start()
        stderrThread.start()

        watchdog = scope.launch {
            while (p.isAlive) delay(200.milliseconds)
            currentSink.getAndSet(null)?.done?.complete(Result(exitCode = -1, cwd = "", shellDied = true))
            process = null
        }
    }

    private fun streamLines(reader: BufferedReader, isStderr: Boolean) {
        try {
            while (true) {
                val line = reader.readLine() ?: break
                if (isStderr) dispatchStderr(line) else dispatchStdout(line)
            }
        } catch (_: Exception) {
            // stream closed under us (process destroyed)
        }
    }

    private fun dispatchStdout(line: String) {
        val sink = currentSink.get() ?: return
        appendBounded(sink.stdoutBuf, line)
        sink.onStdout?.invoke(line)
    }

    private fun dispatchStderr(line: String) {
        val sink = currentSink.get() ?: return
        if (line.length >= 2 && line.startsWith(RS) && line.endsWith(RS)) {
            val payload = line.substring(1, line.length - 1)
            val parts = payload.split(US)
            if (parts.size == 3 && parts[0] == sink.nonce) {
                val exit = parts[1].toIntOrNull() ?: -1
                val cwd = parts[2]
                sink.done.complete(Result(exitCode = exit, cwd = cwd))
                return
            }
        }
        appendBounded(sink.stderrBuf, line)
        sink.onStderr?.invoke(line)
    }

    private fun appendBounded(buf: StringBuilder, line: String) {
        if (buf.length >= MAX_OUTPUT_LENGTH) return
        if (buf.isNotEmpty()) buf.append('\n')
        buf.append(line)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:desktopTest --tests "com.inspiredandroid.kai.sandbox.DesktopPersistentShellTest"`
Expected: `BUILD SUCCESSFUL`, 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/desktopMain/kotlin/com/inspiredandroid/kai/sandbox/DesktopPersistentShell.kt composeApp/src/desktopTest/kotlin/com/inspiredandroid/kai/sandbox/DesktopPersistentShellTest.kt
git commit -m "Add DesktopPersistentShell: non-chroot shell with micromamba on PATH"
```

---

### Task 7: `DesktopSandboxController` — wire it all into `SandboxController.jvm.kt`

**Files:**
- Modify: `composeApp/src/desktopMain/kotlin/com/inspiredandroid/kai/SandboxController.jvm.kt`
- Test: manual (see Task 9) — this class is almost entirely orchestration/glue over already-tested pieces; its own value-add is thin enough that a unit test would mostly re-test Tasks 5/6.

**Interfaces:**
- Consumes: `DesktopLinuxSandboxManager` (Task 5), `DesktopPersistentShell` (Task 6), `MicromambaDownloader`/`micromambaDownloadUrl` (Tasks 3–4), `getAppFilesDirectory()` (`Platform.jvm.kt`), `ConversationStorage` (existing, injected via Koin — already a `single` in `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/AppModule.kt`).
- Produces: `actual fun createSandboxController(): SandboxController` returning `DesktopSandboxController`, implementing the full `SandboxController` interface from `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/SandboxController.kt`.

**Design note (deviation from the spec's Components table):** the spec listed a separate `DesktopSandboxModule.kt` for Koin wiring. Investigation during planning found `desktopMain`'s Koin setup (`composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/App.kt:103`, `modules(appModule)`) has no equivalent to Android's extra `modules(appModule, sandboxModule)` call — commonMain's `App.kt` only ever loads `appModule`. Adding a second desktop-only module would require touching shared `App.kt`. Instead, `DesktopLinuxSandboxManager` is constructed directly as a `by lazy` property inside `DesktopSandboxController` (itself already a Koin `single`, so this only happens once), injecting only `ConversationStorage` — which is already registered in the shared `appModule` — via the same `KoinJavaComponent.inject` pattern already used elsewhere in `desktopMain` (e.g. `Platform.jvm.kt:147-150`). No new Koin module needed.

- [ ] **Step 1: Read the current file in full**

Run: `cat composeApp/src/desktopMain/kotlin/com/inspiredandroid/kai/SandboxController.jvm.kt`
(Already known from prior exploration — the entire file is the `NoOpSandboxController` shown below, being replaced in Step 2.)

- [ ] **Step 2: Replace the file contents**

Replace `composeApp/src/desktopMain/kotlin/com/inspiredandroid/kai/SandboxController.jvm.kt` in full:
```kotlin
package com.inspiredandroid.kai

import com.inspiredandroid.kai.data.ConversationStorage
import com.inspiredandroid.kai.sandbox.DesktopLinuxSandboxManager
import com.inspiredandroid.kai.sandbox.DesktopPersistentShell
import com.inspiredandroid.kai.sandbox.MicromambaDownloader
import com.inspiredandroid.kai.sandbox.SandboxState
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

actual fun createSandboxController(): SandboxController = DesktopSandboxController()

private const val SANDBOX_NOT_READY = "Sandbox is not ready"

class DesktopSandboxController : SandboxController {

    private val conversationStorage: ConversationStorage by inject(ConversationStorage::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val sandboxManager: DesktopLinuxSandboxManager by lazy {
        val baseDir = File(getAppFilesDirectory(), "linux-sandbox/micromamba")
        DesktopLinuxSandboxManager(baseDir, MicromambaDownloader(HttpClient(CIO)))
    }

    private val shells = ConcurrentHashMap<String, DesktopPersistentShell>()
    private val _sessions = MutableStateFlow<List<String>>(emptyList())
    override val sessions: StateFlow<List<String>> = _sessions

    private var previousState: SandboxState? = null
    private var cachedDiskUsageMB = 0L
    private val _status = MutableStateFlow(SandboxStatus())
    override val status: StateFlow<SandboxStatus> = _status

    init {
        val initial = sandboxManager.state.value
        _status.value = mapState(initial)
        scope.launch {
            sandboxManager.state.collect { state ->
                _status.value = mapState(state)
                previousState = state
            }
        }
    }

    private fun mapState(state: SandboxState): SandboxStatus = when (state) {
        is SandboxState.NotInstalled -> SandboxStatus(statusText = "Not installed")
        is SandboxState.Downloading -> SandboxStatus(
            working = true,
            progress = state.progress,
            statusText = "Downloading micromamba...",
        )
        is SandboxState.Extracting -> SandboxStatus(working = true, statusText = "Extracting...")
        is SandboxState.Installing -> SandboxStatus(
            installed = sandboxManager.layout.binaryFile.exists(),
            working = true,
            statusText = state.detail.ifEmpty { "Installing..." },
            diskUsageMB = cachedDiskUsageMB,
        )
        is SandboxState.Ready -> {
            if (previousState !is SandboxState.Ready) {
                cachedDiskUsageMB = sandboxManager.getDiskUsageMB()
            }
            SandboxStatus(
                installed = true,
                ready = true,
                statusText = "Ready",
                diskUsageMB = cachedDiskUsageMB,
                packagesInstalled = sandboxManager.arePackagesInstalled(),
            )
        }
        is SandboxState.Error -> SandboxStatus(error = true, statusText = "Error: ${state.message}")
    }

    override fun setup() = sandboxManager.setup()
    override fun cancel() = sandboxManager.cancel()
    override fun reset() = sandboxManager.reset()
    override fun installPackages() = sandboxManager.installPackages()

    override fun closeSession(sessionId: String) {
        shells.remove(sessionId)?.reset()
        _sessions.value = shells.keys.toList()
    }

    private fun shellFor(sessionId: String): DesktopPersistentShell =
        shells.getOrPut(sessionId) {
            _sessions.value = shells.keys.toList() + sessionId
            DesktopPersistentShell(sandboxManager.layout)
        }

    override suspend fun executeCommand(command: String, sessionId: String): String = withContext(Dispatchers.IO) {
        if (sandboxManager.state.value !is SandboxState.Ready) return@withContext SANDBOX_NOT_READY
        val result = shellFor(sessionId).run(command, timeoutSeconds = 30)
        val stdout = result["stdout"] as? String ?: ""
        val stderr = result["stderr"] as? String ?: ""
        val exitCode = result["exit_code"] as? Int
        buildString {
            if (stdout.isNotEmpty()) append(stdout)
            if (stderr.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append(stderr)
            }
            if (exitCode != null && exitCode != 0 && isEmpty()) append("Exit code: $exitCode")
        }
    }

    override suspend fun executeCommandStreaming(
        command: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        sessionId: String,
    ): CommandHandle {
        if (sandboxManager.state.value !is SandboxState.Ready) {
            onStderr(SANDBOX_NOT_READY)
            return NoOpCommandHandle
        }
        val shell = shellFor(sessionId)
        val deferred = CompletableDeferred<Map<String, Any>>()
        val cancelled = AtomicBoolean(false)
        scope.launch {
            runCatching {
                shell.run(command, timeoutSeconds = 24L * 60 * 60, onStdout = onStdout, onStderr = onStderr)
            }.onSuccess { deferred.complete(it) }
                .onFailure { deferred.complete(mapOf("exit_code" to -1)) }
        }
        return object : CommandHandle {
            override fun cancel() {
                cancelled.set(true)
                shell.cancelForeground()
            }
            override fun isCancelled(): Boolean = cancelled.get()
            override suspend fun writeInput(line: String) { /* no interactive stdin forwarding in this pass */ }
            override suspend fun awaitExit(): Int = (deferred.await()["exit_code"] as? Int) ?: -1
        }
    }

    override suspend fun listDirectory(path: String): List<SandboxFileEntry> = emptyList()
    override suspend fun readTextFile(path: String, maxBytes: Int): String? = null
    override suspend fun writeTextFile(path: String, content: String): Boolean = false
    override suspend fun openFile(path: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Sandbox file browser not yet implemented for desktop"))
    override suspend fun deleteEntry(path: String, recursive: Boolean): Boolean = false
    override suspend fun renameEntry(path: String, newName: String): Result<String> =
        Result.failure(UnsupportedOperationException("Sandbox file browser not yet implemented for desktop"))
}
```

Note: `NoOpCommandHandle` is NOT redefined in this file — `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/SandboxController.kt:25` already declares `internal object NoOpCommandHandle : CommandHandle`, and since `desktopMain` and `commonMain` compile into the same Gradle module (`composeApp`) for the desktop target, that `internal` declaration is directly visible here with no import needed — both files are in the same `com.inspiredandroid.kai` package. Defining a second one in this file would be a redeclaration compile error.

Note: the file browser methods (`listDirectory`/`readTextFile`/`writeTextFile`/`openFile`/`deleteEntry`/`renameEntry`) are stubbed out returning empty/failure, matching the spec's scope — the design doc didn't call for a desktop sandbox file browser, only shell command execution. This mirrors `NoOpSandboxController`'s existing behavior for those specific methods, so no regression from today.

- [ ] **Step 3: Compile the desktop target**

Run: `./gradlew :composeApp:compileKotlinDesktop`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/desktopMain/kotlin/com/inspiredandroid/kai/SandboxController.jvm.kt
git commit -m "Wire DesktopSandboxController into SandboxController.jvm.kt, replacing the no-op"
```

---

### Task 8: "Dev Tools" framing for desktop settings UI

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/settings/SettingsScreen.kt` (tab label, 1 call site)
- Modify: `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/settings/SandboxSettings.kt` (card title + description, in `SandboxSettingsCard`)
- Modify: `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/sandbox/SandboxTabsContent.kt` (card title + description, in the full sandbox tab's not-installed state)

**Interfaces:**
- Consumes: `currentPlatform`, `Platform.Desktop.Linux` (existing, from `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/Platform.kt`). `shouldShowSandboxTab` (Task 2) is not reused here — this task only changes displayed text, not visibility.

**Scope note:** the spec approved "Dev Tools" framing but the underlying strings (`settings_tab_sandbox`, `settings_sandbox_description`) are localized into 40+ languages in `composeApp/src/commonMain/composeResources/values*/strings.xml`. Re-translating a new string across all locales is out of scope for this plan. This task overrides only the most user-visible text — the tab label, and the card title + description that appear before install — with hardcoded English literals on desktop Linux specifically, leaving the underlying localized resources untouched for Android/iOS. Two hardcoded `"Alpine Linux"` literals were found during planning (`SandboxSettings.kt:54`, `SandboxTabsContent.kt:118`) — these are more prominent than the tab label and actively inaccurate on desktop (there's no Alpine anywhere in this design), so fixing them is part of this task, not a separate one. Other sandbox strings (Install/Uninstall/Cancel button labels, disk usage format) stay as-is since they're generic enough to remain accurate under either framing.

- [ ] **Step 1: Update the tab label in `SettingsScreen.kt`**

In `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/settings/SettingsScreen.kt`, find (around line 589):
```kotlin
                            SettingsTab.Sandbox -> stringResource(Res.string.settings_tab_sandbox)
```
Replace with:
```kotlin
                            SettingsTab.Sandbox -> if (currentPlatform is Platform.Desktop.Linux) {
                                "Dev Tools"
                            } else {
                                stringResource(Res.string.settings_tab_sandbox)
                            }
```
Confirm `com.inspiredandroid.kai.Platform` and `com.inspiredandroid.kai.currentPlatform` are already imported in this file (both are used extensively elsewhere in `SettingsScreen.kt` per earlier exploration); if not already imported, add:
```kotlin
import com.inspiredandroid.kai.Platform
import com.inspiredandroid.kai.currentPlatform
```

- [ ] **Step 2: Update the card title + description in `SandboxSettings.kt`**

In `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/settings/SandboxSettings.kt`, add these imports near the top (after the existing `import` block, before line 34's `import org.jetbrains.compose.resources.stringResource`):
```kotlin
import com.inspiredandroid.kai.Platform
import com.inspiredandroid.kai.currentPlatform
```

Find (lines 52–73):
```kotlin
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Alpine Linux",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (sandboxState.sandboxReady) {
                    if (sandboxState.sandboxDiskUsageMB > 0) {
                        Text(
                            text = stringResource(Res.string.settings_sandbox_disk_usage, sandboxState.sandboxDiskUsageMB),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.settings_sandbox_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
```
Replace with:
```kotlin
            Column(modifier = Modifier.weight(1f)) {
                val isDesktopLinux = currentPlatform is Platform.Desktop.Linux
                Text(
                    text = if (isDesktopLinux) "Dev Tools" else "Alpine Linux",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (sandboxState.sandboxReady) {
                    if (sandboxState.sandboxDiskUsageMB > 0) {
                        Text(
                            text = stringResource(Res.string.settings_sandbox_disk_usage, sandboxState.sandboxDiskUsageMB),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isDesktopLinux) {
                            "Install dev tools (git, python, node, and more) so the AI can run shell commands, scripts, and tools on your behalf."
                        } else {
                            stringResource(Res.string.settings_sandbox_description)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
```

- [ ] **Step 3: Update the card title + description in `SandboxTabsContent.kt`**

In `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/sandbox/SandboxTabsContent.kt`, add these imports if not already present (check first with `grep -n "^import com.inspiredandroid.kai.Platform\|^import com.inspiredandroid.kai.currentPlatform" composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/sandbox/SandboxTabsContent.kt`):
```kotlin
import com.inspiredandroid.kai.Platform
import com.inspiredandroid.kai.currentPlatform
```

Find (lines 116–129):
```kotlin
            SettingsCard {
                Text(
                    text = "Alpine Linux",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(Res.string.settings_sandbox_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
```
Replace with:
```kotlin
            SettingsCard {
                val isDesktopLinux = currentPlatform is Platform.Desktop.Linux
                Text(
                    text = if (isDesktopLinux) "Dev Tools" else "Alpine Linux",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = if (isDesktopLinux) {
                        "Install dev tools (git, python, node, and more) so the AI can run shell commands, scripts, and tools on your behalf."
                    } else {
                        stringResource(Res.string.settings_sandbox_description)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
```

- [ ] **Step 4: Compile and manually verify**

Run: `./gradlew :composeApp:compileKotlinDesktop`
Expected: `BUILD SUCCESSFUL`.

Since this is a UI text change, no unit test is meaningful here (Compose UI rendering isn't covered by `kotlin.test` in this codebase's existing patterns) — verification is via Task 9's manual `/verify` pass, specifically confirming the desktop settings screen shows "Dev Tools" as both the tab label and card title, with no remaining "Alpine Linux" text anywhere on desktop.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/settings/SettingsScreen.kt composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/settings/SandboxSettings.kt composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/sandbox/SandboxTabsContent.kt
git commit -m "Show 'Dev Tools' framing instead of 'Alpine Linux'/'Linux Sandbox' on desktop"
```

---

### Task 9: End-to-end manual verification

**Files:** none (manual verification task, per the spec's Testing section).

- [ ] **Step 1: Build and launch the desktop app**

Run: `./gradlew :composeApp:run`
Expected: app launches, navigate to Settings.

- [ ] **Step 2: Verify the tab appears and is labeled correctly**

Confirm the settings screen shows a "Dev Tools" tab (not "Linux Sandbox"), and that the card title inside it reads "Dev Tools" (not "Alpine Linux") both before and after install, matching Task 8.

- [ ] **Step 3: Verify install flow**

Click "Install" in the Dev Tools tab. Confirm progress updates, then status reaches "Ready" and disk usage shows a non-zero value. Confirm `~/.kai/linux-sandbox/micromamba/bin/micromamba` exists on disk and is executable.

- [ ] **Step 4: Verify package install**

Click "Install Basic Packages". Confirm each of `git`, `curl`, `wget`, `jq`, `python`, `nodejs`, `openssh`, `lftp`, `rsync` installs without error and status returns to "Ready".

- [ ] **Step 5: Verify shell execution via the chat agent**

In a chat conversation, ask the agent to run `git --version` and `python3 --version` via its shell tool. Confirm both return real version strings (not "Sandbox is not ready").

- [ ] **Step 6: Verify persistence across restart**

Fully quit the app (not just close the window) and relaunch. Confirm the Dev Tools tab still shows "Ready" without needing to reinstall, and `git --version` still works.

- [ ] **Step 7: Verify Flatpak build specifically, if a local Flatpak build is available**

If testing inside the Flatpak sandbox (per the approved spec's primary target), repeat Steps 1–6 via `flatpak run io.github.simonschubert.Kai` against a locally rebuilt Flatpak bundle, since this plan's core value proposition depends on working correctly there specifically (not just in an unsandboxed dev build).

- [ ] **Step 8: Record results**

If all steps pass, this plan is complete. If anything fails, do not mark it complete — file the specific failure against the relevant task above and fix before considering the feature done.
