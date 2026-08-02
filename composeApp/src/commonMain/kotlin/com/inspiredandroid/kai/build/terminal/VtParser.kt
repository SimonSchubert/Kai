package com.inspiredandroid.kai.build.terminal

/**
 * Minimal VT100 / xterm CSI parser. Enough for shells, apt, and agent TUIs to
 * paint a usable screen.
 *
 * Login URLs often arrive as:
 * - OSC 8 hyperlinks (`ESC ] 8 ; ; url ST`) while cells only show "click here"
 * - OSC 52 clipboard (`ESC ] 52 ; c ; base64 ST`) when the TUI "copies" the URL
 */
internal class VtParser(private val screen: TerminalScreen) {

    private enum class State { Ground, Escape, Csi, Osc, OscEsc }

    private var state = State.Ground
    private val csiParams = StringBuilder()
    private val oscBuffer = StringBuilder()

    fun feed(text: String) {
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when (state) {
                State.Ground -> when (ch) {
                    '\u001b' -> state = State.Escape
                    else -> screen.putChar(ch)
                }
                State.Escape -> when (ch) {
                    '[' -> {
                        csiParams.clear()
                        state = State.Csi
                    }
                    ']' -> {
                        oscBuffer.clear()
                        state = State.Osc
                    }
                    'c' -> { // RIS — reset
                        screen.clear()
                        state = State.Ground
                    }
                    '7', '8' -> state = State.Ground // save/restore cursor — ignore for base
                    'D' -> {
                        screen.lineFeed()
                        state = State.Ground
                    }
                    'M' -> {
                        screen.reverseIndex()
                        state = State.Ground
                    }
                    'E' -> {
                        screen.setCursor(0, screen.cursorRow)
                        screen.lineFeed()
                        state = State.Ground
                    }
                    else -> state = State.Ground // drop unknown ESC sequences
                }
                State.Csi -> when {
                    ch in '@'..'~' -> {
                        dispatchCsi(ch, csiParams.toString())
                        csiParams.clear()
                        state = State.Ground
                    }
                    ch.code in 0x20..0x3F || ch == ';' || ch in '0'..'9' || ch == '?' || ch == '>' || ch == '=' || ch == ' ' || ch == '!' || ch == '"' || ch == '$' || ch == '\'' -> {
                        csiParams.append(ch)
                    }
                    else -> {
                        // malformed — abort
                        csiParams.clear()
                        state = State.Ground
                    }
                }
                State.Osc -> when (ch) {
                    '\u0007' -> finishOsc()
                    '\u001b' -> state = State.OscEsc
                    else -> {
                        // OAuth URLs can be long; keep a generous cap.
                        if (oscBuffer.length < 16_384) oscBuffer.append(ch)
                    }
                }
                State.OscEsc -> when (ch) {
                    '\\' -> finishOsc()
                    else -> {
                        oscBuffer.append('\u001b').append(ch)
                        state = State.Osc
                    }
                }
            }
            i++
        }
    }

    private fun finishOsc() {
        val payload = oscBuffer.toString()
        oscBuffer.clear()
        state = State.Ground
        dispatchOsc(payload)
    }

    /**
     * OSC payload without the leading ESC ]. Common forms:
     * - `0;title` / `2;title` window title
     * - `8;params;uri` hyperlink start (uri empty = end)
     * - `52;c;<base64>` clipboard set (xterm OSC 52)
     */
    private fun dispatchOsc(payload: String) {
        when {
            payload.startsWith("8;") -> {
                // 8 ; params ; uri
                val rest = payload.substring(2)
                val semi = rest.indexOf(';')
                val uri = if (semi >= 0) rest.substring(semi + 1) else ""
                if (uri.isNotEmpty()) screen.noteHyperlink(uri)
            }
            payload.startsWith("52;") -> {
                // 52 ; <pc=c> ; <base64 payload>
                val parts = payload.split(';', limit = 3)
                if (parts.size >= 3 && parts[2].isNotEmpty() && parts[2] != "?") {
                    decodeBase64Utf8(parts[2])?.let { decoded ->
                        screen.noteTextUrls(decoded)
                        // Whole clipboard may be a bare URL
                        screen.noteHyperlink(decoded.trim())
                    }
                }
            }
        }
    }

    private fun decodeBase64Utf8(data: String): String? {
        val cleaned = data.trim().replace("\r", "").replace("\n", "")
        if (cleaned.isEmpty()) return null
        return try {
            // Prefer kotlin.io.encoding when available (KMP).
            @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
            val bytes = kotlin.io.encoding.Base64.Default.decode(cleaned)
            bytes.decodeToString()
        } catch (_: Throwable) {
            try {
                // Fallback: very small pure decoder for URL-safe / standard base64
                decodeBase64Manual(cleaned)?.decodeToString()
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun decodeBase64Manual(input: String): ByteArray? {
        val table = IntArray(128) { -1 }
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        for (i in chars.indices) table[chars[i].code] = i
        table['-'.code] = 62
        table['_'.code] = 63
        val filtered = input.filter { it.code < 128 && table[it.code] >= 0 || it == '=' }
        if (filtered.isEmpty()) return null
        val out = ArrayList<Byte>(filtered.length * 3 / 4)
        var i = 0
        while (i + 3 < filtered.length) {
            val a = table[filtered[i].code]
            val b = table[filtered[i + 1].code]
            val c = if (filtered[i + 2] == '=') 0 else table[filtered[i + 2].code]
            val d = if (filtered[i + 3] == '=') 0 else table[filtered[i + 3].code]
            if (a < 0 || b < 0 || c < 0 || d < 0) return null
            val n = (a shl 18) or (b shl 12) or (c shl 6) or d
            out.add((n shr 16).toByte())
            if (filtered[i + 2] != '=') out.add((n shr 8).toByte())
            if (filtered[i + 3] != '=') out.add(n.toByte())
            i += 4
        }
        return out.toByteArray()
    }

    private fun dispatchCsi(final: Char, raw: String) {
        // Strip private markers (?, >, =) for param parse; still honor a few.
        val private = raw.firstOrNull()?.let { it == '?' || it == '>' || it == '=' } == true
        val paramStr = if (private) raw.drop(1) else raw
        val params = parseParams(paramStr)

        if (private) {
            // Private modes may be semicolon-lists: e.g. ?1;1000;1049h
            when (final) {
                'h' -> params.forEach { setPrivateMode(it, enable = true) }
                'l' -> params.forEach { setPrivateMode(it, enable = false) }
            }
            return
        }

        when (final) {
            'A' -> screen.moveCursor(0, -params.default(1))
            'B' -> screen.moveCursor(0, params.default(1))
            'C' -> screen.moveCursor(params.default(1), 0)
            'D' -> screen.moveCursor(-params.default(1), 0)
            'E' -> {
                screen.moveCursor(0, params.default(1))
                screen.setCursor(0, screen.cursorRow)
            }
            'F' -> {
                screen.moveCursor(0, -params.default(1))
                screen.setCursor(0, screen.cursorRow)
            }
            'G' -> screen.setCursor(params.default(1) - 1, screen.cursorRow)
            'H', 'f' -> {
                val row = params.getOrElse(0) { 1 }
                val col = params.getOrElse(1) { 1 }
                screen.setCursor(col - 1, row - 1)
            }
            'J' -> screen.eraseInDisplay(params.default(0))
            'K' -> screen.eraseInLine(params.default(0))
            'L' -> screen.insertLines(params.default(1))
            'M' -> screen.deleteLines(params.default(1))
            'P' -> screen.deleteChars(params.default(1))
            'X' -> screen.eraseChars(params.default(1))
            'd' -> screen.setCursor(screen.cursorCol, params.default(1) - 1)
            'm' -> screen.setSgr(if (params.isEmpty()) listOf(0) else params)
            'n' -> { /* DSR — no reply in base */ }
            's', 'u' -> { /* save/restore cursor — skip */ }
            // ignore modes, scrolls, etc. for base
        }
    }

    private fun parseParams(raw: String): List<Int> {
        if (raw.isEmpty()) return emptyList()
        // Drop intermediate bytes (spaces, etc.) — keep digits and semicolons
        val cleaned = buildString {
            for (c in raw) {
                if (c.isDigit() || c == ';') append(c)
            }
        }
        if (cleaned.isEmpty()) return emptyList()
        return cleaned.split(';').map { part ->
            if (part.isEmpty()) 0 else part.toIntOrNull() ?: 0
        }
    }

    private fun List<Int>.default(fallback: Int): Int =
        firstOrNull()?.takeIf { it != 0 } ?: fallback

    /**
     * Private DECSET/DECRST. Without a second buffer we treat alt-screen enter
     * as clear+home so TUI apps still paint on the single grid.
     */
    private fun setPrivateMode(mode: Int, enable: Boolean) {
        when (mode) {
            // DECCKM: decides whether arrow keys go back as CSI or SS3.
            1 -> screen.setApplicationCursorKeys(enable)
            25 -> screen.setCursorVisible(enable)
            // Alternate screen buffer (xterm): 47, 1047, 1049
            47, 1047, 1049 -> if (enable) {
                screen.eraseInDisplay(2)
                screen.setCursor(0, 0)
            }
            // Mouse reporting: apps turn this on to make cells clickable.
            1000, 1002, 1003 -> screen.setMouseTracking(mode, enable)
            1006 -> screen.setMouseSgrEncoding(enable)
            // Bracketed paste / focus — ignore for base
            else -> Unit
        }
    }
}
