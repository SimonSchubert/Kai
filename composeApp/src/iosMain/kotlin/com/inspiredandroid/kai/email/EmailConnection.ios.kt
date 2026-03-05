@file:Suppress("DEPRECATION")

package com.inspiredandroid.kai.email

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.tls
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

actual suspend fun createEmailConnection(host: String, port: Int, tls: Boolean): EmailConnection {
    val selectorManager = SelectorManager(Dispatchers.IO)
    val builder = aSocket(selectorManager).tcp().connect(host, port)
    val socket = if (tls) {
        builder.tls(Dispatchers.IO) {
            serverName = host
        }
    } else {
        builder
    }
    return KtorEmailConnection(
        readChannel = socket.openReadChannel(),
        writeChannel = socket.openWriteChannel(autoFlush = false),
        closeable = socket,
        selectorManager = selectorManager,
        host = host,
    )
}

private class KtorEmailConnection(
    private var readChannel: ByteReadChannel,
    private var writeChannel: ByteWriteChannel,
    private var closeable: io.ktor.network.sockets.Socket,
    private val selectorManager: SelectorManager,
    private val host: String,
) : EmailConnection {

    override suspend fun readLine(): String = readChannel.readUTF8Line() ?: throw Exception("Connection closed")

    override suspend fun writeLine(line: String) {
        writeChannel.writeStringUtf8("$line\r\n")
        writeChannel.flush()
    }

    override suspend fun upgradeToTls(host: String) {
        // For STARTTLS: upgrade existing connection to TLS
        val tlsSocket = closeable.tls(Dispatchers.IO) {
            serverName = host
        }
        readChannel = tlsSocket.openReadChannel()
        writeChannel = tlsSocket.openWriteChannel(autoFlush = false)
        closeable = tlsSocket
    }

    override suspend fun close() {
        try {
            closeable.close()
            selectorManager.close()
        } catch (_: Exception) {
        }
    }
}
