package com.inspiredandroid.kai.email

/**
 * Platform-specific TCP/TLS connection for email protocols.
 */
interface EmailConnection {
    suspend fun readLine(): String
    suspend fun writeLine(line: String)
    suspend fun upgradeToTls(host: String)
    suspend fun close()
}

/**
 * Creates a TCP connection, optionally wrapped in TLS (for implicit TLS like IMAP port 993).
 */
expect suspend fun createEmailConnection(host: String, port: Int, tls: Boolean): EmailConnection
