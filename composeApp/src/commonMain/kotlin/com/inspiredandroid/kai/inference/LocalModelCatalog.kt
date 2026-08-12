package com.inspiredandroid.kai.inference

/**
 * Download URLs are pinned to an immutable HuggingFace commit, never to `main`. A `main`
 * URL is a moving target: whoever controls the repo — or anyone who can tamper with the
 * transfer — could swap the file, and the app would load it with no version bump and no
 * warning. Every entry also carries the file's SHA-256, which is checked after download
 * and before the model is ever handed to the inference engine.
 *
 * This list is the **runtime** source of truth for pins — not a live HuggingFace fetch.
 * Pin policy, last check, and the bump playbook live in the OKF bundle
 * `docs/knowledge/litert/`. Check or bump via the `update-litert-models` skill
 * (never silently move a pin to `main`).
 *
 * To bump a model, refresh the commit and the digest together:
 *
 *   curl -s "https://huggingface.co/api/models/<repo>"                        -> .sha is the commit
 *   curl -s "https://huggingface.co/api/models/<repo>/tree/main?recursive=true" -> .lfs.oid is the
 *                                                                                 file's SHA-256,
 *                                                                                 .lfs.size its size
 *
 * The digest is also echoed as the `x-linked-etag` header on the resolve redirect, so a
 * `curl -sI -L <url>` cross-checks it. [sizeBytes] must be the exact byte count — it gates
 * both the pre-download free-space check and the post-download length check.
 */
val MODEL_CATALOG = listOf(
    LocalModel(
        id = "gemma-4-e2b-it",
        displayName = "Gemma 4 E2B IT",
        fileName = "gemma-4-E2B-it.litertlm",
        sizeBytes = 2_588_147_712L,
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/9262660a1676eed6d0c477ab1a86344430854664/gemma-4-E2B-it.litertlm",
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
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
        sizeBytes = 3_659_530_240L,
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/f7ad3343bd6ebc9607f4dc3bc4f2398bd5749bc5/gemma-4-E4B-it.litertlm",
        sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
        gpuMemoryMb = 710,
        defaultContextTokens = 4_096,
        maxContextTokens = 32_768,
        kvPerTokenBytes = 75_000,
    ),
    LocalModel(
        id = "gemma-4-12b-it",
        displayName = "Gemma 4 12B IT",
        fileName = "gemma-4-12B-it.litertlm",
        sizeBytes = 6_547_589_312L,
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-12B-it-litert-lm/resolve/c65da4643badfd9ae0748b5df0145d8fddaef47e/gemma-4-12B-it.litertlm",
        sha256 = "74fc29a10c20eb5b3ced6c389471a7994a0ffd657255b2a1c764262fb9054aef",
        gpuMemoryMb = 4000,
        defaultContextTokens = 8_192,
        maxContextTokens = 32_768,
        kvPerTokenBytes = 140_000,
    ),
    LocalModel(
        id = "qwen3-0.6b",
        displayName = "Qwen3 0.6B",
        fileName = "Qwen3-0.6B.litertlm",
        sizeBytes = 614_236_160L,
        downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/dd97997951bb15a2a71f539ba17f604707c0b11a/Qwen3-0.6B.litertlm",
        sha256 = "555579ff2f4fd13379abe69c1c3ab5200f7338bc92471557f1d6614a6e5ab0b4",
        gpuMemoryMb = 300,
        defaultContextTokens = 4_096,
        maxContextTokens = 32_768,
        kvPerTokenBytes = 35_000,
    ),
)

fun findCatalogModelById(id: String): LocalModel? = MODEL_CATALOG.find { it.id == id }

/**
 * Name of the sibling file that records the verified digest of [fileName]. Written next to
 * a model once its bytes have been checked, so the check is not repeated on every load.
 */
fun digestMarkerFileName(fileName: String): String = "$fileName.sha256"

/**
 * Marker contents recording that the user supplied this file themselves. An import whose
 * name matches a catalog model takes over that catalog slot, so without this the pinned
 * digest would be applied to bytes Kai never downloaded and had no business checking.
 */
const val USER_SUPPLIED_MARKER = "user-supplied"

/**
 * True when [actual] satisfies [expected]. A blank [expected] means the model carries no
 * pinned digest — imported models, which the user supplies directly — and is accepted.
 */
fun digestMatches(expected: String, actual: String?): Boolean = expected.isBlank() ||
    (actual != null && actual.trim().equals(expected.trim(), ignoreCase = true))

private const val HEX_DIGITS = "0123456789abcdef"

/**
 * Lowercase hex encoding of raw digest bytes, matching the form the catalog stores.
 * Hand-rolled rather than using the stdlib's `toHexString`, which still needs an opt-in.
 */
fun ByteArray.toDigestHex(): String = buildString(size * 2) {
    for (byte in this@toDigestHex) {
        val value = byte.toInt() and 0xFF
        append(HEX_DIGITS[value ushr 4])
        append(HEX_DIGITS[value and 0x0F])
    }
}

private val THINK_BLOCK_REGEX = Regex("(?s)<think>.*?</think>")

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
