package com.inspiredandroid.kai.data

import kotlinx.serialization.json.Json

private val jsUtilJson = Json { ignoreUnknownKeys = true }

/**
 * Convert a Kotlin string to a JS literal for injection into script source.
 * - null → null
 * - Valid JSON object/array → passed as-is so JS sees an object
 * - number/boolean → passed as-is
 * - anything else → JSON-escaped string literal
 */
fun toJsLiteral(value: String?): String {
    if (value == null) return "null"
    val trimmed = value.trim()
    // JSON object or array — validate before passing through
    if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
        (trimmed.startsWith("[") && trimmed.endsWith("]"))
    ) {
        return try {
            jsUtilJson.parseToJsonElement(trimmed)
            trimmed
        } catch (_: Exception) {
            jsonStringify(value)
        }
    }
    if (trimmed.toDoubleOrNull() != null) return trimmed
    if (trimmed == "true" || trimmed == "false") return trimmed
    return jsonStringify(value)
}

fun jsonStringify(value: String): String = buildString {
    append('"')
    for (c in value) {
        when (c) {
            '"' -> append("\\\"")

            '\\' -> append("\\\\")

            '\n' -> append("\\n")

            '\r' -> append("\\r")

            '\t' -> append("\\t")

            '\b' -> append("\\b")

            else -> if (c.code < 0x20) {
                append("\\u")
                append(c.code.toString(16).padStart(4, '0'))
            } else {
                append(c)
            }
        }
    }
    append('"')
}

fun prettyPrintJs(code: String): String {
    val indent = "  "
    var level = 0
    val result = StringBuilder()
    var i = 0
    var inString = false
    var stringChar = ' '

    while (i < code.length) {
        val c = code[i]

        if (inString) {
            result.append(c)
            if (c == stringChar && (i == 0 || code[i - 1] != '\\')) inString = false
            i++
            continue
        }
        if (c == '\'' || c == '"' || c == '`') {
            inString = true
            stringChar = c
            result.append(c)
            i++
            continue
        }

        when (c) {
            '{', '[' -> {
                result.append(c)
                level++
                result.append('\n')
                result.append(indent.repeat(level))
            }

            '}', ']' -> {
                level = maxOf(0, level - 1)
                result.append('\n')
                result.append(indent.repeat(level))
                result.append(c)
            }

            ';' -> {
                result.append(c)
                val next = code.getOrNull(i + 1)
                if (next != null && next != '\n' && next != '}' && next != ']') {
                    result.append('\n')
                    result.append(indent.repeat(level))
                }
            }

            ',' -> {
                result.append(c)
                result.append('\n')
                result.append(indent.repeat(level))
            }

            '\n' -> {
                result.append('\n')
                result.append(indent.repeat(level))
                while (i + 1 < code.length && code[i + 1] == ' ') i++
            }

            else -> result.append(c)
        }
        i++
    }
    return result.toString().trimEnd()
}
