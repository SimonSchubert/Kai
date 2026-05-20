package com.inspiredandroid.kai.inference

val MODEL_CATALOG = listOf(
    LocalModel(
        id = "gemma-4-e2b-it",
        displayName = "Gemma 4 E2B IT",
        fileName = "gemma-4-E2B-it.litertlm",
        sizeBytes = 2_580_000_000L,
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        gpuMemoryMb = 676,
        defaultContextTokens = 4_096,
        maxContextTokens = 32_768,
        kvPerTokenBytes = 50_000,
        isRecommended = true,
    ),
    LocalModel(
        id = "gemma-4-e4b-it",
        displayName = "Gemma 4 E4B IT",
        fileName = "gemma-4-E4B-it.litertlm",
        sizeBytes = 3_650_000_000L,
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        gpuMemoryMb = 710,
        defaultContextTokens = 4_096,
        maxContextTokens = 32_768,
        kvPerTokenBytes = 75_000,
    ),
    LocalModel(
        id = "qwen3-0.6b",
        displayName = "Qwen3 0.6B",
        fileName = "Qwen3-0.6B.litertlm",
        sizeBytes = 614_236_160L,
        downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
        gpuMemoryMb = 300,
        defaultContextTokens = 4_096,
        maxContextTokens = 32_768,
        kvPerTokenBytes = 35_000,
    ),
)

private val THINK_BLOCK_REGEX = Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL)

// Qwen3 emits <think>…</think> blocks as part of its chat template; strip them before
// the user sees them. Safe for Gemma 4, which never emits these tags.
fun stripThinkBlocks(s: String): String = THINK_BLOCK_REGEX.replace(s, "").trim()

/**
 * Drops UTF-16 surrogate halves from the string. The litert-lm JNI layer passes
 * strings to the native runtime as *modified* UTF-8, which encodes supplementary-plane
 * characters (U+10000–U+10FFFF — most emoji like 🗺️, 🎉, 🔥) as surrogate-pair
 * sequences where each half becomes a 3-byte block. That is invalid as *standard*
 * UTF-8, and the native runtime's `nlohmann::json` parser crashes with "ill-formed
 * UTF-8 byte" the moment it hits one. The Swift bridge on iOS hits the same parser.
 *
 * Filtering surrogates drops every supplementary character (both halves are surrogate
 * code units in UTF-16) while leaving BMP characters — including BMP-only emoji like
 * ⚔️, ♻️, ❤️, and all CJK / extended Latin / accented characters — untouched.
 * No-op for strings that don't contain any supplementary character.
 */
fun sanitizeForLiteRt(s: String?): String? {
    if (s == null) return null
    if (s.none { it.isSurrogate() }) return s
    return s.filter { !it.isSurrogate() }
}
