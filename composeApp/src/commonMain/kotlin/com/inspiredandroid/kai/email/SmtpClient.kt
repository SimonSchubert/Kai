package com.inspiredandroid.kai.email

import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.abs
import kotlin.time.Clock

/**
 * Minimal SMTP client for sending email replies.
 * Supports EHLO, AUTH LOGIN, STARTTLS, MAIL FROM, RCPT TO, DATA.
 */
class SmtpClient(
    private val host: String,
    private val port: Int = 587,
    private val useStartTls: Boolean = true,
) {
    private var connection: EmailConnection? = null

    suspend fun connect() {
        connection = createEmailConnection(host, port, tls = !useStartTls)
        // Read server greeting
        readResponse()
    }

    suspend fun ehlo(domain: String = "localhost") {
        writeLine("EHLO $domain")
        readResponse()
    }

    suspend fun startTls() {
        if (!useStartTls) return
        writeLine("STARTTLS")
        val response = readResponse()
        if (!response.startsWith("220")) {
            throw Exception("STARTTLS failed: $response")
        }
        connection?.upgradeToTls(host)
        // Re-issue EHLO after TLS
        ehlo()
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun authenticate(username: String, password: String) {
        writeLine("AUTH LOGIN")
        val authResponse = readResponse()
        if (!authResponse.startsWith("334")) {
            throw Exception("AUTH LOGIN not supported: $authResponse")
        }

        // Send base64-encoded username
        writeLine(Base64.encode(username.encodeToByteArray()))
        val userResponse = readResponse()
        if (!userResponse.startsWith("334")) {
            throw Exception("Auth username rejected: $userResponse")
        }

        // Send base64-encoded password
        writeLine(Base64.encode(password.encodeToByteArray()))
        val passResponse = readResponse()
        if (!passResponse.startsWith("235")) {
            throw Exception("Authentication failed: $passResponse")
        }
    }

    /**
     * Sends the message and returns its raw RFC 5322 form on success (so callers
     * can store a copy in the Sent folder), or null if the server rejected it.
     */
    suspend fun sendReply(
        from: String,
        to: String,
        subject: String,
        body: String,
        inReplyTo: String? = null,
    ): String? {
        writeLine("MAIL FROM:<$from>")
        var response = readResponse()
        if (!response.startsWith("250")) throw Exception("MAIL FROM failed: $response")

        writeLine("RCPT TO:<$to>")
        response = readResponse()
        if (!response.startsWith("250")) throw Exception("RCPT TO failed: $response")

        writeLine("DATA")
        response = readResponse()
        if (!response.startsWith("354")) throw Exception("DATA failed: $response")

        // Build email headers + body
        val headers = buildString {
            appendLine("Date: ${rfc5322Date()}")
            appendLine("From: $from")
            appendLine("To: $to")
            appendLine("Subject: $subject")
            appendLine("MIME-Version: 1.0")
            appendLine("Content-Type: text/plain; charset=UTF-8")
            if (inReplyTo != null) {
                appendLine("In-Reply-To: $inReplyTo")
                appendLine("References: $inReplyTo")
            }
            appendLine()
        }

        // Send message content line by line, escaping leading dots
        val fullMessage = headers + body
        for (line in fullMessage.lines()) {
            val escaped = if (line.startsWith(".")) ".$line" else line
            writeLine(escaped)
        }

        // End with <CRLF>.<CRLF>
        writeLine(".")
        response = readResponse()
        return if (response.startsWith("250")) fullMessage else null
    }

    private fun rfc5322Date(): String {
        val now = Clock.System.now()
        val timeZone = TimeZone.currentSystemDefault()
        val dateTime = now.toLocalDateTime(timeZone)
        val offsetSeconds = timeZone.offsetAt(now).totalSeconds
        val offsetSign = if (offsetSeconds < 0) "-" else "+"
        val offsetMinutes = abs(offsetSeconds) / 60
        val offset = "$offsetSign${pad2(offsetMinutes / 60)}${pad2(offsetMinutes % 60)}"
        val dayName = dateTime.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        val monthName = dateTime.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        return "$dayName, ${dateTime.day} $monthName ${dateTime.year} " +
            "${pad2(dateTime.hour)}:${pad2(dateTime.minute)}:${pad2(dateTime.second)} $offset"
    }

    private fun pad2(value: Int): String = value.toString().padStart(2, '0')

    suspend fun quit() {
        try {
            writeLine("QUIT")
            readResponse()
            connection?.close()
        } catch (_: Exception) {
            // Best-effort quit
        } finally {
            connection = null
        }
    }

    private suspend fun writeLine(line: String) {
        val conn = connection ?: throw IllegalStateException("Not connected")
        conn.writeLine(line)
    }

    private suspend fun readResponse(): String {
        val conn = connection ?: throw IllegalStateException("Not connected")
        val result = StringBuilder()
        // SMTP responses can be multiline (e.g., "250-PIPELINING\r\n250 SIZE...")
        while (true) {
            val line = conn.readLine()
            result.appendLine(line)
            // Final line has space after status code, continuation lines have dash
            if (line.length >= 4 && line[3] == ' ') break
            if (line.length < 4) break
        }
        return result.toString().trim()
    }
}
