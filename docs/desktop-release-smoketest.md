# Desktop Release Smoke Test

**Last verified:** 2026-10-02

Before tagging a new version, walk through this checklist on a locally-built **release** desktop binary (not `./gradlew :composeApp:run` — that uses the unminified classpath and hides ProGuard bugs).

## Why this exists

Desktop release builds run through ProGuard. To keep that safe, the desktop policy only lets ProGuard shrink the Compose packages (by far the bulk of the distribution) and keeps the rest of the classpath intact, because many libraries in the dependency graph use reflection, `ServiceLoader`, JNI symbol lookup, or jar-manifest class discovery that ProGuard cannot trace statically. The signed BouncyCastle jar is additionally swapped back for the original after ProGuard runs. Even so, when a needed keep rule is missing the build succeeds and packages fine but crashes at runtime on the first code path that touches the affected code. There is no ProGuard step in the PR-time CI anymore — the release build is first exercised by the tag-time release pipeline, which packages but never executes the binary. This manual run is the only guardrail against runtime-only regressions until an automated smoke test is in place.

## How to run

```bash
./gradlew :composeApp:runRelease
```

If the JetBrains Runtime hits a `java.xml` hash error locally on macOS, force Oracle JDK 21:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  ./gradlew :composeApp:runRelease -Dorg.gradle.java.home=$JAVA_HOME
```

## Checklist

Each step exercises a library that has historically broken under ProGuard. A crash here means the release is not shippable.

- [ ] **App launches to main screen** — exercises Compose runtime, Skiko native load, and the Koin DI graph
- [ ] **First outbound network call succeeds** (any provider ping, any chat message) — exercises Ktor CIO engine discovery and the kotlinx.serialization JSON plugin (both `ServiceLoader`-registered)
- [ ] **Send a chat message and receive a response** — exercises the full Ktor HTTP client + content-negotiation + streaming path
- [ ] **Splinterlands / Hive login** — exercises BouncyCastle ECDSA via `HiveCrypto.jvmShared.kt`. BouncyCastle's signed JCE provider jar is especially sensitive; any ProGuard modification invalidates its SHA-256 class digests
- [ ] **Toggle text-to-speech on a message** — exercises `nl.marc_apps.tts` and FreeTTS voice directory discovery (jar manifest + `Class.forName`)
- [ ] **Change a setting and restart the app** — exercises multiplatform-settings persistence via the encrypted file backend
- [ ] **After the restart, reopen an earlier conversation** — exercises SQLite-backed conversation storage, which needs the `java.sql` module in the bundled runtime
- [ ] **Load a downloaded on-device model and send a message** — exercises LiteRT's JVM bindings and native library loading
- [ ] **Open the file picker, pick a file, save a file** — exercises FileKit and its JNA-backed native dialogs
- [ ] **Render a markdown response with code blocks** — exercises the multiplatform markdown renderer and code highlighter
- [ ] **Open Settings → export/import** — exercises the JSON serialization path end-to-end

## When a new crash surfaces

The crash signature dictates the fix:

- **`ServiceLoader: Provider X not found`** — add `-keep class * implements <ServiceInterface> { <init>(); }` to `composeApp/proguard-rules.pro`. Prefer this pattern over hard-coding the implementation class name so the rule self-adapts if the library renames its classes.
- **`ClassNotFoundException` / `NoClassDefFoundError` on a library-internal class** — usually jar-manifest or `Class.forName` reflection. Keep the whole package with `-keep class <pkg>.** { *; }`.
- **`SHA-256 digest error for X.class`** — the jar is cryptographically signed and must not be modified. Keep the entire library untouched: `-keep class <pkg>.** { *; }` plus `-dontwarn <pkg>.**`.
- **`NoSuchMethodException` / `NoSuchFieldException` with an obfuscation-looking name** — should not happen because `obfuscate` is off by default in Compose Desktop; if it does, check that `buildTypes.release.proguard` in `composeApp/build.gradle.kts` has not been changed to enable obfuscation.
- **`VerifyError` ("Bad return type") in coroutine builders** — ProGuard method specialization; the desktop rules disable it globally, so check that line has not been removed.

Desktop-only rules go in `composeApp/proguard-desktop.pro`; rules that Android R8 also needs go in `composeApp/proguard-rules.pro` (both are applied to desktop).

Always add rules with a comment explaining which library needs it and why, so future readers (and future-you) can tell which rules are still load-bearing.

## Known non-shrinkable libraries

On desktop, everything outside the Compose packages is kept wholesale by `composeApp/proguard-desktop.pro`. The libraries below are additionally kept in the shared rules (which Android R8 uses too) because they cannot be safely minified anywhere. Do not try to tighten these rules without a corresponding runtime test.

| Library | Reason | Rule location |
|---|---|---|
| BouncyCastle (`org.bouncycastle.**`) | Signed JCE provider jar; ProGuard invalidates SHA-256 class digests, so the desktop build also restores the original jar after ProGuard | `composeApp/proguard-rules.pro`, `composeApp/build.gradle.kts` |
| FreeTTS (`com.sun.speech.freetts.**`) | Voice directories loaded via jar manifest + `Class.forName`; cannot be traced | `composeApp/proguard-rules.pro` |

## Key Files

| File | Purpose |
|---|---|
| `composeApp/proguard-rules.pro` | Shared ProGuard/R8 rules for Android and Desktop |
| `composeApp/proguard-desktop.pro` | Desktop-only policy: shrink Compose, keep the rest of the classpath, no method specialization |
| `composeApp/build.gradle.kts` | `compose.desktop { application { buildTypes.release.proguard { ... } } }`, jlink modules, and the signed BouncyCastle jar restore |
| `.github/workflows/release.yml` | Tag-time full release pipeline using `packageReleaseDmg`/`Msi`/`Deb`/`Rpm` and `createReleaseDistributable` (AppImage, Flatpak, tarball) |
