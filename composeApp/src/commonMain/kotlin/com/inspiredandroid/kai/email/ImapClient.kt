package com.inspiredandroid.kai.email

import com.inspiredandroid.kai.data.EmailMessage

/**
 * Minimal IMAP client supporting the subset of commands needed for email reading.
 * Uses tagged commands (e.g., "A001 LOGIN ...") per IMAP spec.
 */
private val imapExistsRegex = Regex("\\* (\\d+) EXISTS")
private val imapTaggedResponseRegex = Regex("^A\\d+ (OK|NO|BAD) .*")
private val mimeBoundaryRegex = Regex("^--([\\w'()+,-./:=? ]+)\\s*$", RegexOption.MULTILINE)

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
        conn.writeLine("$tag FETCH $uid (BODY.PEEK[HEADER.FIELDS (FROM TO SUBJECT DATE MESSAGE-ID)] BODY[TEXT])")
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
        var body = ""
        var isRead = false

        val lines = raw.lines()
        for (i in lines.indices) {
            val line = lines[i].trim()
            val lower = line.lowercase()
            when {
                lower.startsWith("from:") -> from = line.substringAfter(":").trim()
                lower.startsWith("to:") -> to = line.substringAfter(":").trim()
                lower.startsWith("subject:") -> subject = line.substringAfter(":").trim()
                lower.startsWith("date:") -> date = line.substringAfter(":").trim()
                lower.startsWith("message-id:") -> messageId = line.substringAfter(":").trim()
            }
        }

        // Check flags for \Seen
        if (raw.contains("\\Seen")) isRead = true

        // Extract body from BODY[TEXT] section
        body = extractBodyFromResponse(raw)

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
            messageId = messageId,
            isRead = isRead,
        )
    }

    /**
     * Extract readable body text from the IMAP FETCH response.
     * Handles both plain text and multipart MIME messages.
     */
    private fun extractBodyFromResponse(raw: String): String {
        // Find BODY[TEXT] or BODY.PEEK[TEXT] section
        val bodyIdx = raw.indexOfAny("BODY[TEXT]", "BODY.PEEK[TEXT]")
        if (bodyIdx == -1) {
            // Fallback: try to find body after double newline
            return extractFallbackBody(raw)
        }

        // Skip past the literal indicator: BODY[TEXT] {nnn}\n or BODY[TEXT]<0.200> {nnn}\n
        val afterMarker = raw.substring(bodyIdx)
        val firstNewline = afterMarker.indexOf('\n')
        if (firstNewline == -1) return ""

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
            return extractTextPlainFromMultipart(cleaned, boundary)
        }

        return cleaned.trim()
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
     * Extract text/plain part from multipart MIME content.
     */
    private fun extractTextPlainFromMultipart(content: String, boundary: String): String {
        val parts = content.split("--$boundary")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isEmpty() || trimmed == "--") continue

            // Check if this part is text/plain
            val lowerPart = trimmed.lowercase()
            val isTextPlain = lowerPart.contains("content-type: text/plain") ||
                // First part with no explicit content-type is usually text/plain
                (!lowerPart.contains("content-type:") && parts.indexOf(part) == 1)

            if (isTextPlain) {
                // Body starts after the blank line separating MIME headers from content
                val blankLineIdx = trimmed.indexOf("\n\n")
                if (blankLineIdx != -1) {
                    return trimmed.substring(blankLineIdx + 2).trim()
                }
                // If there's a Content-Type header but no blank line, try after first header block
                return trimmed.lines()
                    .dropWhile { it.contains(":") || it.startsWith(" ") || it.startsWith("\t") }
                    .dropWhile { it.isBlank() }
                    .joinToString("\n")
                    .trim()
            }
        }
        // Fallback: return first non-empty part stripped of MIME headers
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isEmpty() || trimmed == "--") continue
            val blankLineIdx = trimmed.indexOf("\n\n")
            if (blankLineIdx != -1) {
                return trimmed.substring(blankLineIdx + 2).trim()
            }
        }
        return content.trim()
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
}
