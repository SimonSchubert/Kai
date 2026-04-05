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

# BouncyCastle ships as a cryptographically signed JCE provider jar. Any
# bytecode modification by ProGuard invalidates the per-class SHA-256 digests
# recorded in META-INF/BCKEY.SF, causing "SHA-256 digest error" at runtime
# when the JCE framework verifies the provider. BC's own docs say their jar
# must not be processed by ProGuard. Size cost: ~6 MB (bcprov stays at 8.1 MB).
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
