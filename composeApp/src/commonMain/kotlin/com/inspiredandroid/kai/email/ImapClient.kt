@file:OptIn(ExperimentalEncodingApi::class)

package com.inspiredandroid.kai.email

import com.inspiredandroid.kai.data.EmailMessage
import com.inspiredandroid.kai.tools.decodeHtmlEntities
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Minimal IMAP client supporting the subset of commands needed for email reading.
 * Uses tagged commands (e.g., "A001 LOGIN ...") per IMAP spec.
 */
private val imapExistsRegex = Regex("\\* (\\d+) EXISTS")
private val imapTaggedResponseRegex = Regex("^A\\d+ (OK|NO|BAD) .*")
private val mimeBoundaryRegex = Regex("^--([\\w'()+,-./:=? ]+)\\s*$", RegexOption.MULTILINE)
private val transferEncodingRegex = Regex("content-transfer-encoding:\\s*([\\w-]+)", RegexOption.IGNORE_CASE)
private val scriptRegex = Regex("(?is)<script[^>]*>.*?</script>")
private val styleRegex = Regex("(?is)<style[^>]*>.*?</style>")
private val htmlTagRegex = Regex("<[^>]+>")
private val whitespaceRegex = Regex("\\s+")

class ImapClient(
    private val host: String,
    private val port: Int = 993,
    private val tls: Boolean = true,
) {
    private var connection: EmailConnection? = null
    private var tagCounter = 0

    private fun nextTag(): String = "A${++tagCounter}"

    suspend fun connect() {
        connection = createEmailConnection(host, port, tls)
        // Read server greeting
        readUntilTaggedOrGreeting(null)
    }

    suspend fun login(username: String, password: String): Boolean {
        val tag = nextTag()
        val conn = connection ?: throw IllegalStateException("Not connected")
        conn.writeLine("$tag LOGIN \"${escapeQuoted(username)}\" \"${escapeQuoted(password)}\"")
        val response = readUntilTaggedOrGreeting(tag)
        return response.contains("OK")
    }

    suspend fun selectInbox(): Int {
        val tag = nextTag()
        val conn = connection ?: throw IllegalStateException("Not connected")
        conn.writeLine("$tag SELECT INBOX")
        val response = readUntilTaggedOrGreeting(tag)
        // Parse EXISTS count from response like "* 42 EXISTS"
        val existsMatch = imapExistsRegex.find(response)
        return existsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    suspend fun searchUnseen(): List<Long> = search("SEARCH UNSEEN")

    suspend fun searchSince(date: String): List<Long> = search("SEARCH SINCE $date")

    suspend fun searchByFrom(sender: String): List<Long> = search("SEARCH FROM \"${escapeQuoted(sender)}\"")

    suspend fun searchBySubject(subject: String): List<Long> = search("SEARCH SUBJECT \"${escapeQuoted(subject)}\"")

    private suspend fun search(command: String): List<Long> {
        val tag = nextTag()
        val conn = connection ?: throw IllegalStateException("Not connected")
        conn.writeLine("$tag $command")
        val response = readUntilTaggedOrGreeting(tag)
        val searchLine = response.lines().find { it.startsWith("* SEARCH") } ?: return emptyList()
        return searchLine.removePrefix("* SEARCH").trim().split(" ")
            .filter { it.isNotBlank() }
            .mapNotNull { it.toLongOrNull() }
    }

    /**
     * Fetch email headers (From, Subject, Date, Message-ID) and a text preview.
     */
    suspend fun fetchHeaders(uids: List<Long>, accountId: String): List<EmailMessage> {
        if (uids.isEmpty()) return emptyList()
        val conn = connection ?: throw IllegalStateException("Not connected")
        val messages = mutableListOf<EmailMessage>()

        for (uid in uids.take(50)) { // Limit to 50 emails per fetch
            val tag = nextTag()
            conn.writeLine("$tag FETCH $uid (BODY.PEEK[HEADER.FIELDS (FROM SUBJECT DATE MESSAGE-ID)] BODY.PEEK[TEXT]<0.200> FLAGS)")
            val response = readUntilTaggedOrGreeting(tag)
            val msg = parseEmailFromFetch(uid, accountId, response)
            if (msg != null) messages.add(msg)
        }
        return messages
    }

    /**
     * Fetch full email body for a specific UID.
     */
    suspend fun fetchBody(uid: Long, accountId: String): EmailMessage? {
        val conn = connection ?: throw IllegalStateException("Not connected")
        val tag = nextTag()
        conn.writeLine("$tag FETCH $uid (BODY.PEEK[HEADER.FIELDS (FROM TO SUBJECT DATE MESSAGE-ID LIST-UNSUBSCRIBE LIST-UNSUBSCRIBE-POST)] BODY[TEXT])")
        val response = readUntilTaggedOrGreeting(tag)
        return parseEmailFromFetch(uid, accountId, response)
    }

    suspend fun markAsRead(uid: Long) {
        val conn = connection ?: throw IllegalStateException("Not connected")
        val tag = nextTag()
        conn.writeLine("$tag STORE $uid +FLAGS (\\Seen)")
        readUntilTaggedOrGreeting(tag)
    }

    suspend fun logout() {
        try {
            val conn = connection ?: return
            val tag = nextTag()
            conn.writeLine("$tag LOGOUT")
            readUntilTaggedOrGreeting(tag)
            conn.close()
        } catch (_: Exception) {
            // Best-effort logout
        } finally {
            connection = null
        }
    }

    private suspend fun readUntilTaggedOrGreeting(tag: String?): String {
        val conn = connection ?: throw IllegalStateException("Not connected")
        val result = StringBuilder()
        var lineCount = 0
        val maxLines = 500 // Safety limit

        while (lineCount < maxLines) {
            val line = conn.readLine()
            result.appendLine(line)
            lineCount++

            if (tag == null) {
                // Reading greeting - stop at first line starting with *
                if (line.startsWith("* OK") || line.startsWith("* NO") || line.startsWith("* BAD")) break
            } else {
                // Reading tagged response - stop when we see our tag
                if (line.startsWith("$tag ")) break
            }
        }
        return result.toString()
    }

    private fun parseEmailFromFetch(uid: Long, accountId: String, raw: String): EmailMessage? {
        var from = ""
        var to = ""
        var subject = ""
        var date = ""
        var messageId = ""
        var listUnsubscribe = ""
        var listUnsubscribePost = ""
        var isRead = false

        // Limit header parsing to the section before BODY[TEXT] — the body may
        // contain lines that look like headers (e.g. "From:" quoted replies).
        val headerSection = run {
            val bodyIdx = raw.indexOfAny("BODY[TEXT]", "BODY.PEEK[TEXT]")
            if (bodyIdx == -1) raw else raw.substring(0, bodyIdx)
        }

        // Unfold RFC 5322 header continuation lines (start with SP/HTAB) so
        // long headers like List-Unsubscribe aren't truncated.
        val unfolded = mutableListOf<String>()
        for (line in headerSection.lines()) {
            if (unfolded.isNotEmpty() && (line.startsWith(" ") || line.startsWith("\t"))) {
                unfolded[unfolded.lastIndex] = unfolded.last() + " " + line.trim()
            } else {
                unfolded += line
            }
        }
        for (headerLine in unfolded) {
            val line = headerLine.trim()
            val lower = line.lowercase()
            when {
                lower.startsWith("from:") -> from = line.substringAfter(":").trim()
                lower.startsWith("to:") -> to = line.substringAfter(":").trim()
                lower.startsWith("subject:") -> subject = line.substringAfter(":").trim()
                lower.startsWith("date:") -> date = line.substringAfter(":").trim()
                lower.startsWith("message-id:") -> messageId = line.substringAfter(":").trim()
                lower.startsWith("list-unsubscribe-post:") -> listUnsubscribePost = line.substringAfter(":").trim()
                lower.startsWith("list-unsubscribe:") -> listUnsubscribe = line.substringAfter(":").trim()
            }
        }

        // Check flags for \Seen
        if (raw.contains("\\Seen")) isRead = true

        // When the email has no text/plain part, derive a readable plain body
        // from the HTML so the agent always has something to work with.
        val (plainBody, bodyHtml) = extractBodyFromResponse(raw)
        val body = if (plainBody.isEmpty() && bodyHtml.isNotEmpty()) stripHtml(bodyHtml) else plainBody

        val preview = body.take(200).replace("\n", " ").trim()

        return EmailMessage(
            uid = uid,
            accountId = accountId,
            from = from,
            to = to,
            subject = subject,
            date = date,
            preview = preview,
            body = body,
            bodyHtml = bodyHtml,
            messageId = messageId,
            isRead = isRead,
            listUnsubscribe = listUnsubscribe,
            listUnsubscribePost = listUnsubscribePost,
        )
    }

    /**
     * Extract readable body text from the IMAP FETCH response. Returns
     * (plainText, htmlText) — htmlText is empty when the message has no HTML part.
     */
    private fun extractBodyFromResponse(raw: String): Pair<String, String> {
        // Find BODY[TEXT] or BODY.PEEK[TEXT] section
        val bodyIdx = raw.indexOfAny("BODY[TEXT]", "BODY.PEEK[TEXT]")
        if (bodyIdx == -1) {
            // Fallback: try to find body after double newline
            return extractFallbackBody(raw) to ""
        }

        // Skip past the literal indicator: BODY[TEXT] {nnn}\n or BODY[TEXT]<0.200> {nnn}\n
        val afterMarker = raw.substring(bodyIdx)
        val firstNewline = afterMarker.indexOf('\n')
        if (firstNewline == -1) return "" to ""

        val bodyContent = afterMarker.substring(firstNewline + 1)

        // Remove trailing IMAP response lines (tagged response, closing paren)
        val cleaned = bodyContent.lines()
            .takeWhile { line ->
                !line.matches(imapTaggedResponseRegex) && line.trimEnd() != ")"
            }
            .joinToString("\n")

        // Check if it's multipart MIME content
        val boundary = detectMimeBoundary(cleaned)
        if (boundary != null) {
            return extractPartsFromMultipart(cleaned, boundary)
        }

        return cleaned.trim() to ""
    }

    private fun extractFallbackBody(raw: String): String {
        // Try \n\n (appendLine uses \n)
        val bodyStart = raw.indexOf("\n\n")
        if (bodyStart == -1) return ""

        return raw.substring(bodyStart + 2)
            .lines()
            .takeWhile { line ->
                !line.matches(imapTaggedResponseRegex) && line.trimEnd() != ")"
            }
            .joinToString("\n")
            .trim()
    }

    /**
     * Detect MIME boundary from content. Looks for lines like "--boundary_string".
     */
    private fun detectMimeBoundary(content: String): String? {
        val match = mimeBoundaryRegex.find(content)
        return match?.groupValues?.get(1)
    }

    /**
     * Extract both the text/plain and text/html parts from multipart MIME content.
     * Returns (plainText, htmlText); either may be empty if absent. Decodes the parts
     * according to their Content-Transfer-Encoding (quoted-printable, base64).
     */
    private fun extractPartsFromMultipart(content: String, boundary: String): Pair<String, String> {
        val parts = content.split("--$boundary")
        var plain = ""
        var html = ""
        var firstBody = ""

        for ((index, part) in parts.withIndex()) {
            val trimmed = part.trim()
            if (trimmed.isEmpty() || trimmed == "--") continue

            val body = extractPartBody(trimmed)
            if (firstBody.isEmpty() && body.isNotEmpty()) firstBody = body

            val lowerPart = trimmed.lowercase()
            when {
                lowerPart.contains("content-type: text/plain") && plain.isEmpty() -> plain = body

                lowerPart.contains("content-type: text/html") && html.isEmpty() -> html = body

                // First part with no explicit content-type is conventionally text/plain.
                plain.isEmpty() && !lowerPart.contains("content-type:") && index == 1 -> plain = body
            }
        }

        if (plain.isEmpty() && html.isEmpty()) {
            return firstBody to ""
        }
        return plain to html
    }

    private fun extractPartBody(trimmed: String): String {
        val encoding = transferEncodingRegex.find(trimmed)?.groupValues?.get(1)?.lowercase()?.trim()

        val raw = run {
            val blankLineIdx = trimmed.indexOf("\n\n")
            if (blankLineIdx != -1) {
                trimmed.substring(blankLineIdx + 2).trim()
            } else {
                // If there's a Content-Type header but no blank line, try after first header block
                trimmed.lines()
                    .dropWhile { it.contains(":") || it.startsWith(" ") || it.startsWith("\t") }
                    .dropWhile { it.isBlank() }
                    .joinToString("\n")
                    .trim()
            }
        }

        return when (encoding) {
            "quoted-printable" -> decodeQuotedPrintable(raw)
            "base64" -> decodeBase64OrOriginal(raw)
            else -> raw
        }
    }

    private fun decodeQuotedPrintable(input: String): String {
        // Drop soft line breaks (= at end of line) then decode =HH escapes as UTF-8 bytes.
        val joined = input.replace("=\r\n", "").replace("=\n", "")
        val bytes = ArrayList<Byte>(joined.length)
        var i = 0
        while (i < joined.length) {
            val c = joined[i]
            if (c == '=' && i + 2 < joined.length) {
                val hex = joined.substring(i + 1, i + 3)
                val byte = hex.toIntOrNull(16)
                if (byte != null) {
                    bytes += byte.toByte()
                    i += 3
                    continue
                }
            }
            if (c.code < 0x80) {
                bytes += c.code.toByte()
            } else {
                for (b in c.toString().encodeToByteArray()) bytes += b
            }
            i++
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun decodeBase64OrOriginal(input: String): String = try {
        Base64.Mime.decode(input.encodeToByteArray()).decodeToString()
    } catch (_: Exception) {
        input
    }

    /**
     * Find first occurrence of any of the given strings.
     */
    private fun String.indexOfAny(vararg strings: String): Int {
        var minIdx = -1
        for (s in strings) {
            val idx = indexOf(s)
            if (idx != -1 && (minIdx == -1 || idx < minIdx)) {
                minIdx = idx
            }
        }
        return minIdx
    }

    private fun escapeQuoted(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun stripHtml(html: String): String = html
        .replace(scriptRegex, "")
        .replace(styleRegex, "")
        .replace(htmlTagRegex, " ")
        .decodeHtmlEntities()
        .replace(whitespaceRegex, " ")
        .trim()
}
