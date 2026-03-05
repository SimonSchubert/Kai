package com.inspiredandroid.kai.email

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

actual suspend fun createEmailConnection(host: String, port: Int, tls: Boolean): EmailConnection = withContext(Dispatchers.IO) {
    val socket = if (tls) {
        SSLSocketFactory.getDefault().createSocket(host, port) as SSLSocket
    } else {
        Socket(host, port)
    }
    socket.soTimeout = 30_000
    JvmEmailConnection(socket, host)
}

private class JvmEmailConnection(
    private var socket: Socket,
    private val host: String,
) : EmailConnection {
    private var reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
    private var writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8), true)

    override suspend fun readLine(): String = withContext(Dispatchers.IO) {
        reader.readLine() ?: throw Exception("Connection closed")
    }

    override suspend fun writeLine(line: String) = withContext(Dispatchers.IO) {
        writer.print("$line\r\n")
        writer.flush()
    }

    override suspend fun upgradeToTls(host: String) = withContext(Dispatchers.IO) {
        val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val sslSocket = sslFactory.createSocket(
            socket,
            host,
            socket.port,
            true,
        ) as SSLSocket
        sslSocket.startHandshake()
        socket = sslSocket
        reader = BufferedReader(InputStreamReader(sslSocket.getInputStream(), Charsets.UTF_8))
        writer = PrintWriter(OutputStreamWriter(sslSocket.getOutputStream(), Charsets.UTF_8), true)
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        try {
            socket.close()
        } catch (_: Exception) {
        }
    }
}
