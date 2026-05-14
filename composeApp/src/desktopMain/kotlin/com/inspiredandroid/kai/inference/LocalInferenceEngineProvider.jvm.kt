package com.inspiredandroid.kai.inference

import java.io.File

actual fun createLocalInferenceEngine(): LocalInferenceEngine? = if (isLiteRtSupported()) LiteRTInferenceEngine() else null

/**
 * LiteRT's native binary is compiled with AVX2+ instructions and SIGILLs the JVM on
 * pre-AVX2 x86_64 hardware (e.g. Ivy Bridge / 3rd-gen Core) before any JNI exception
 * can surface. Gate engine creation on a CPU feature probe so unsupported machines
 * silently hide the on-device service instead of crashing on model load. See #188.
 */
private fun isLiteRtSupported(): Boolean {
    val arch = System.getProperty("os.arch")?.lowercase().orEmpty()
    if (arch != "x86_64" && arch != "amd64") return true
    val os = System.getProperty("os.name")?.lowercase().orEmpty()
    if (!os.contains("linux")) return true
    return runCatching {
        File("/proc/cpuinfo").useLines { lines ->
            lines
                .filter { it.startsWith("flags", ignoreCase = true) }
                .any { it.split(Regex("\\s+")).contains("avx2") }
        }
    }.getOrElse { true }
}
