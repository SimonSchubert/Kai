package com.inspiredandroid.kai.ui.markdown

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * Minimal per-language syntax highlighter. Tokenizes keywords, strings, numbers, and comments
 * for a small allow-list of languages; unknown languages return plain text.
 *
 * Not semantic: regexes are line-local and can misfire on strings that contain keywords.
 * Accepted trade-off for chat-rendered code blocks.
 */
internal data class HighlightColors(
    val keyword: Color,
    val literal: Color,
    val comment: Color,
)

internal fun codeHighlightColors(scheme: ColorScheme): HighlightColors = HighlightColors(
    keyword = scheme.tertiary,
    literal = scheme.secondary,
    comment = scheme.outline,
)

private val KOTLIN_KEYWORDS = setOf(
    "fun", "val", "var", "class", "object", "interface", "sealed", "data", "open", "override",
    "private", "public", "internal", "protected", "abstract", "if", "else", "when", "for",
    "while", "do", "return", "null", "true", "false", "import", "package", "this", "super",
    "is", "as", "in", "out", "by", "typealias", "suspend", "companion", "const", "lateinit",
    "init", "try", "catch", "finally", "throw", "break", "continue", "enum",
)

private val JAVA_KEYWORDS = setOf(
    "public", "private", "protected", "static", "final", "abstract", "class", "interface",
    "extends", "implements", "new", "this", "super", "if", "else", "switch", "case", "default",
    "for", "while", "do", "return", "null", "true", "false", "void", "int", "long", "short",
    "byte", "float", "double", "char", "boolean", "try", "catch", "finally", "throw", "throws",
    "break", "continue", "import", "package", "enum", "instanceof",
)

private val PYTHON_KEYWORDS = setOf(
    "def", "class", "if", "elif", "else", "for", "while", "in", "not", "and", "or", "return",
    "yield", "import", "from", "as", "try", "except", "finally", "raise", "True", "False",
    "None", "with", "lambda", "pass", "break", "continue", "global", "nonlocal", "is", "self",
)

private val JS_KEYWORDS = setOf(
    "function", "const", "let", "var", "if", "else", "for", "while", "do", "return", "null",
    "true", "false", "class", "extends", "new", "this", "super", "import", "export", "default",
    "from", "async", "await", "try", "catch", "finally", "throw", "break", "continue", "switch",
    "case", "typeof", "instanceof", "in", "of", "delete", "void", "undefined",
)

private val BASH_KEYWORDS = setOf(
    "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac", "in",
    "function", "return", "exit", "break", "continue", "local", "export", "echo", "read", "test",
)

private val GO_KEYWORDS = setOf(
    "func", "var", "const", "type", "struct", "interface", "package", "import", "if", "else",
    "for", "range", "switch", "case", "default", "return", "nil", "true", "false", "break",
    "continue", "defer", "go", "select", "map", "chan",
)

private val RUST_KEYWORDS = setOf(
    "fn", "let", "mut", "const", "static", "struct", "enum", "impl", "trait", "pub", "mod",
    "use", "if", "else", "match", "for", "while", "loop", "return", "true", "false", "None",
    "Some", "self", "Self", "as", "in", "where", "type", "unsafe", "async", "await", "move",
    "ref", "dyn", "extern", "crate", "break", "continue",
)

private val SQL_KEYWORDS = setOf(
    "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE",
    "TABLE", "DROP", "ALTER", "ADD", "COLUMN", "INDEX", "UNIQUE", "JOIN", "INNER", "LEFT",
    "RIGHT", "OUTER", "ON", "AS", "AND", "OR", "NOT", "NULL", "IS", "IN", "LIKE", "BETWEEN",
    "ORDER", "BY", "GROUP", "HAVING", "LIMIT", "OFFSET", "DISTINCT", "UNION", "ALL", "CASE",
    "WHEN", "THEN", "ELSE", "END", "TRUE", "FALSE", "select", "from", "where", "insert", "into",
    "values", "update", "set", "delete", "create", "table", "drop", "alter", "as", "and", "or",
    "not", "null", "is", "in", "on", "order", "by", "group", "having", "limit", "distinct",
)

private val C_KEYWORDS = setOf(
    "int", "long", "short", "char", "float", "double", "void", "unsigned", "signed", "const",
    "volatile", "static", "extern", "register", "auto", "struct", "enum", "union", "typedef",
    "if", "else", "switch", "case", "default", "for", "while", "do", "return", "break",
    "continue", "goto", "sizeof", "NULL", "true", "false", "class", "public", "private",
    "protected", "new", "delete", "template", "namespace", "using", "virtual", "override",
)

private fun buildKeywordRegex(keywords: Set<String>): Regex = Regex("\\b(${keywords.joinToString("|")})\\b")

private val KEYWORD_REGEXES: Map<String, Regex> = buildMap {
    val kotlin = buildKeywordRegex(KOTLIN_KEYWORDS)
    put("kotlin", kotlin)
    put("kt", kotlin)
    put("kts", kotlin)
    put("java", buildKeywordRegex(JAVA_KEYWORDS))
    val python = buildKeywordRegex(PYTHON_KEYWORDS)
    put("python", python)
    put("py", python)
    val js = buildKeywordRegex(JS_KEYWORDS)
    put("javascript", js)
    put("js", js)
    put("typescript", js)
    put("ts", js)
    put("jsx", js)
    put("tsx", js)
    val bash = buildKeywordRegex(BASH_KEYWORDS)
    put("bash", bash)
    put("sh", bash)
    put("shell", bash)
    put("zsh", bash)
    val go = buildKeywordRegex(GO_KEYWORDS)
    put("go", go)
    put("golang", go)
    val rust = buildKeywordRegex(RUST_KEYWORDS)
    put("rust", rust)
    put("rs", rust)
    put("sql", buildKeywordRegex(SQL_KEYWORDS))
    val c = buildKeywordRegex(C_KEYWORDS)
    put("c", c)
    put("cpp", c)
    put("c++", c)
    put("h", c)
    put("hpp", c)
}

private val STRING_REGEX = Regex("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'")
private val NUMBER_REGEX = Regex("\\b\\d+(?:\\.\\d+)?\\b")
private val SLASH_COMMENT_REGEX = Regex("//.*")
private val HASH_COMMENT_REGEX = Regex("#.*")
private val BLOCK_COMMENT_REGEX = Regex("/\\*[\\s\\S]*?\\*/")

private data class Span(val range: IntRange, val color: Color)

internal fun highlightCode(code: String, language: String?, colors: HighlightColors): AnnotatedString {
    val lang = language?.lowercase() ?: return AnnotatedString(code)
    val keywordRegex = KEYWORD_REGEXES[lang]
    val hasStrings = lang !in setOf("plain", "text", "")
    val hasNumbers = hasStrings && lang !in setOf("xml", "html")
    if (keywordRegex == null && !hasStrings) return AnnotatedString(code)

    val spans = mutableListOf<Span>()
    val commentRegex = when (lang) {
        "python", "py", "bash", "sh", "shell", "zsh" -> HASH_COMMENT_REGEX
        "xml", "html" -> null
        else -> SLASH_COMMENT_REGEX
    }

    commentRegex?.let { r -> r.findAll(code).forEach { spans += Span(it.range, colors.comment) } }
    BLOCK_COMMENT_REGEX.findAll(code).forEach { spans += Span(it.range, colors.comment) }
    if (hasStrings) STRING_REGEX.findAll(code).forEach { spans += Span(it.range, colors.literal) }
    if (hasNumbers) NUMBER_REGEX.findAll(code).forEach { spans += Span(it.range, colors.literal) }
    keywordRegex?.findAll(code)?.forEach { spans += Span(it.range, colors.keyword) }

    spans.sortWith(compareBy({ it.range.first }, { -(it.range.last - it.range.first) }))
    val kept = mutableListOf<Span>()
    var lastEnd = -1
    for (s in spans) {
        if (s.range.first > lastEnd) {
            kept += s
            lastEnd = s.range.last
        }
    }

    return buildAnnotatedString {
        var pos = 0
        for (s in kept) {
            if (s.range.first > pos) append(code.substring(pos, s.range.first))
            withStyle(SpanStyle(color = s.color)) {
                append(code.substring(s.range.first, s.range.last + 1))
            }
            pos = s.range.last + 1
        }
        if (pos < code.length) append(code.substring(pos))
    }
}
