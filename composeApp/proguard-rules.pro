-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy

# kotlinx.serialization — keep serializer infrastructure for R8 full mode
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,SourceFile,LineNumberTable

# Ktor plugins loaded via ServiceLoader
-keep class * implements io.ktor.serialization.kotlinx.KotlinxSerializationExtensionProvider { <init>(); }

# SLF4J providers loaded via ServiceLoader (slf4j-nop on desktop)
-keep class * implements org.slf4j.spi.SLF4JServiceProvider { <init>(); }

# FreeTTS — nl.marc_apps.tts uses it on desktop regardless of the engine enum.
# Voice directories are loaded via jar-manifest + Class.forName, which ProGuard
# cannot trace. Keep freetts broadly; size cost is ~400 KB since ProGuard
# could barely shrink it anyway.
-keep class com.sun.speech.freetts.** { *; }
-dontwarn com.sun.speech.freetts.**

# Ktor — HTTP client and networking (used by Coil for image loading).
# ProGuard strips ~50% of classes including JVM I/O adapters, auth headers,
# and content handlers that break HTTPS connections.
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Okio — I/O primitives used by Ktor CIO engine for sockets, TLS, compression.
# ProGuard strips Socket, CipherSink/Source, GzipSource, AsyncTimeout etc.
-keep class okio.** { *; }
-dontwarn okio.**

# Coil — ProGuard strips platform-specific Kotlin top-level function classes
# (SkiaImageDecoder_jvmKt, RealImageLoader_nonAndroidKt, FileSystems_jvmKt, …)
# that Coil needs at runtime on desktop. Keep broadly to avoid breakage.
# Coil does not ship consumer ProGuard rules (coil-kt/coil#2546).
-keep class coil3.** { *; }
-dontwarn coil3.**

# BouncyCastle ships as a cryptographically signed JCE provider jar.
# -keep alone is not enough: ProGuard still rewrites the jar, stripping the
# META-INF signatures (BCKEY.SF / BCKEY.DSA) and invalidating per-class
# SHA-256 digests. A Gradle doLast in build.gradle.kts copies the original
# signed jar back over the ProGuard output; the -keep rule is still needed
# so ProGuard does not report "missing class" warnings for the rest of the app.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# LiteRT LM — on-device inference SDK with JNI native bridge.
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**

# JNA — FileKit uses it on Windows (Shell32 IFileDialog / IShellItem COM) and
# macOS (Cocoa Foundation bindings). JNA resolves native symbols by exact
# method name via reflection; any rename produces errors like
# "Can't obtain static method dispose from class com.sun.jna.Native". See #167, #175.
-keep class com.sun.jna.** { *; }
-keep class * extends com.sun.jna.** { *; }
-keepclassmembers class * implements com.sun.jna.** {
    <methods>;
    <fields>;
}
-dontwarn com.sun.jna.**
-dontwarn java.awt.**

# FileKit — defines JNA Structure subclasses and COM interface proxies in
# .dialogs.platform.windows.jna.* and DBus interface proxies in
# .dialogs.platform.xdg.*. Method names must survive to match the native ABI.
-keep class io.github.vinceglb.filekit.** { *; }
-keep interface io.github.vinceglb.filekit.** { *; }
-dontwarn io.github.vinceglb.filekit.**

# DBus (dbus-java by hypfvieh) — used by FileKit's Linux XDG desktop-portal
# probe. Without it, the portal check fails and Linux falls back to
# java.awt.FileDialog which is unthemed on KDE/GNOME dark sessions. See #173.
-keep class org.freedesktop.dbus.** { *; }
-keep interface org.freedesktop.dbus.** { *; }
-keep class com.github.hypfvieh.** { *; }
-dontwarn org.freedesktop.dbus.**
-dontwarn com.github.hypfvieh.**

# kotlinx.coroutines — defensive on desktop. DebugProbesKt is a well-known
# dontwarn target and the coroutines runtime uses reflection for
# service-loader based CoroutineExceptionHandler discovery.
-keep class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# ---- Desktop-release hardening (Windows/Linux/macOS) ----
# The Compose Gradle plugin ships default-compose-desktop-rules.pro with only
# targeted keeps (Skia, Skiko, SliderDefaults, SnapshotStateKt__DerivedStateKt,
# coroutine volatile fields, kotlinx.serialization infra). We add broader,
# defensive keeps for the libraries Kai actually uses on desktop. Size cost is
# small because the app already references most of these APIs.

# kotlinx.io — Ktor 3.x public functions take kotlinx.io.Source/Sink/Buffer/
# RawSource/RawSink/files.Path as parameters. ProGuard prints
# "Note: ... but not the descriptor class 'kotlinx.io.Source'" ×dozens during
# the release build, meaning Ktor entry points reference kotlinx.io classes
# that are shrunk. Any Ktor I/O path then NoClassDefFoundErrors at runtime.
-keep class kotlinx.io.** { *; }
-dontwarn kotlinx.io.**

# kotlinx.datetime — the Compose plugin only dontwarns; app uses Instant,
# LocalDateTime, TimeZone extensively. Small (~60 KB) defensive keep.
-keep class kotlinx.datetime.** { *; }
-dontwarn kotlinx.datetime.**

# Koin — DI runtime resolves definitions by KClass identity. koinViewModel<T>()
# embeds a reified KClass at the call site that must match the class kept by
# ProGuard. The library itself uses kotlin-reflect heavily.
-keep class org.koin.** { *; }
-keepclassmembers class org.koin.** { *; }
-dontwarn org.koin.**

# App classes — Koin resolves dependencies via KClass lookup (get<Foo>()) and
# reified koinViewModel<T>(). ViewModel constructors take DataRepository,
# TaskScheduler, DaemonController, etc. as parameters; without keeping them,
# ProGuard flags "descriptor class missing" and the reflective KClass handle
# Koin holds would point at a renamed class. Also makes user-submitted
# stack traces readable without a mapping file.
-keep class com.inspiredandroid.kai.** { *; }

# androidx.lifecycle / savedstate — required by compose-navigation and
# Koin ViewModel support. Not covered by the Compose plugin defaults.
-keep class androidx.lifecycle.** { *; }
-keep class androidx.savedstate.** { *; }
-dontwarn androidx.lifecycle.**
-dontwarn androidx.savedstate.**

# Navigation Compose — typed routes are @Serializable, NavType lookup uses
# reflection, and the library references Android-only IntentActivity classes
# that don't exist on desktop (harmless but noisy without dontwarn).
-keep class androidx.navigation.** { *; }
-keep class org.jetbrains.androidx.navigation.** { *; }
-dontwarn androidx.navigation.**
-dontwarn org.jetbrains.androidx.navigation.**

# multiplatform-settings (russhwolf) — JVM backend delegates to
# java.util.prefs and uses reflection to pick the platform implementation.
-keep class com.russhwolf.settings.** { *; }
-dontwarn com.russhwolf.settings.**

# kotlinx.collections.immutable — ImmutableList / ImmutableMap are exposed
# from public APIs in the app; keeping whole app package above pins references
# to these types which must also survive.
-keep class kotlinx.collections.immutable.** { *; }
-dontwarn kotlinx.collections.immutable.**

# sh.calvin.reorderable — Compose drag-to-reorder library; scope types are
# referenced from composable parameters in the app.
-keep class sh.calvin.reorderable.** { *; }
-dontwarn sh.calvin.reorderable.**

# nl.marc-apps:tts — TextToSpeech instance type leaks into app signatures.
-keep class nl.marc_apps.tts.** { *; }
-dontwarn nl.marc_apps.tts.**

# SLF4J API — Ktor client logging binds to it; slf4j-nop provider is loaded
# via ServiceLoader (already kept above) and needs the Logger interface intact.
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**

# Compose runtime / UI / material3 / foundation / animation — the plugin's
# defaults only keep a few hotspots (SliderDefaults, SnapshotStateKt__DerivedStateKt).
# Broad defensive keeps prevent future regressions when Compose adds new
# reflective entry points; cost is low because the app references most of
# these APIs anyway.
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.animation.** { *; }
-keep class androidx.compose.material.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class org.jetbrains.compose.** { *; }
-dontwarn androidx.compose.**
-dontwarn org.jetbrains.compose.**
-dontwarn androidx.annotation.**

# Gson — pulled in transitively by com.google.ai.edge.litertlm:0.10.2. Uses
# heavy runtime reflection (Field.getType, Class.getGenericSuperclass,
# Unsafe.allocateInstance) to map JSON to classes. The generic-type attributes
# must survive; without Signature, Gson can't resolve List<Foo> → Foo.
-keep class com.google.gson.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.google.gson.annotations.JsonAdapter *;
}
-dontwarn com.google.gson.**
-dontwarn sun.misc.Unsafe
