package com.inspiredandroid.kai.email

import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises ImapClient's APPEND / LIST / CREATE support against a scripted
 * fake IMAP server on a local socket, verifying protocol details like the
 * literal byte count that can't be checked without a real peer.
 */
class ImapClientSentFolderTest {

    @Test
    fun `append sends literal with exact CRLF byte count`() {
        val message = "From: a@example.com\nTo: b@example.com\n\nhello äöü"
        var receivedLiteral = ""
        var declaredSize = -1
        var actualSize = -1

        val ok = withFakeImapServer(serverScript = { input, output ->
            output.writeLine("* OK ready")
            val appendCommand = input.readLineRaw()
            declaredSize = Regex("\\{(\\d+)\\}").find(appendCommand)!!.groupValues[1].toInt()
            output.writeLine("+ Ready for literal data")
            val literal = input.readNBytes(declaredSize)
            actualSize = literal.size
            receivedLiteral = literal.decodeToString()
            input.readLineRaw() // trailing CRLF terminating the command
            output.writeLine("A1 OK APPEND completed")
        }) { imap ->
            imap.appendToMailbox("Sent", message)
        }

        assertTrue(ok)
        assertEquals(declaredSize, actualSize)
        assertEquals("From: a@example.com\r\nTo: b@example.com\r\n\r\nhello äöü", receivedLiteral)
    }

    @Test
    fun `append returns false when server rejects the mailbox`() {
        val ok = withFakeImapServer(serverScript = { input, output ->
            output.writeLine("* OK ready")
            input.readLineRaw()
            output.writeLine("A1 NO [TRYCREATE] Mailbox doesn't exist: Sent")
        }) { imap ->
            imap.appendToMailbox("Sent", "From: a@b\n\nhi")
        }

        assertFalse(ok)
    }

    @Test
    fun `findSentMailbox returns special-use mailbox with quoted name`() {
        val folder = withFakeImapServer(serverScript = { input, output ->
            output.writeLine("* OK ready")
            input.readLineRaw()
            output.writeLine("* LIST (\\HasNoChildren) \"/\" INBOX")
            output.writeLine("* LIST (\\HasNoChildren \\Sent) \"/\" \"Sent Messages\"")
            output.writeLine("A1 OK LIST completed")
        }) { imap ->
            imap.findSentMailbox()
        }

        assertEquals("Sent Messages", folder)
    }

    @Test
    fun `findSentMailbox returns null without special-use attribute`() {
        val folder = withFakeImapServer(serverScript = { input, output ->
            output.writeLine("* OK ready")
            input.readLineRaw()
            output.writeLine("* LIST (\\HasNoChildren) \"/\" INBOX")
            output.writeLine("A1 OK LIST completed")
        }) { imap ->
            imap.findSentMailbox()
        }

        assertNull(folder)
    }

    @Test
    fun `createMailbox succeeds on OK response`() {
        val ok = withFakeImapServer(serverScript = { input, output ->
            output.writeLine("* OK ready")
            input.readLineRaw()
            output.writeLine("A1 OK CREATE completed")
        }) { imap ->
            imap.createMailbox("Sent")
        }

        assertTrue(ok)
    }

    private fun <T> withFakeImapServer(
        serverScript: (InputStream, OutputStream) -> Unit,
        clientBlock: suspend (ImapClient) -> T,
    ): T {
        val serverSocket = ServerSocket(0)
        val serverThread = thread {
            serverSocket.accept().use { socket ->
                serverScript(socket.getInputStream(), socket.getOutputStream())
            }
        }
        try {
            return runBlocking {
                val imap = ImapClient("127.0.0.1", serverSocket.localPort, tls = false)
                imap.connect()
                clientBlock(imap)
            }
        } finally {
            serverThread.join(5_000)
            serverSocket.close()
        }
    }

    private fun OutputStream.writeLine(line: String) {
        write("$line\r\n".encodeToByteArray())
        flush()
    }

    private fun InputStream.readLineRaw(): String {
        val sb = StringBuilder()
        while (true) {
            val b = read()
            if (b == -1 || b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
        }
        return sb.toString()
    }
}
