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
