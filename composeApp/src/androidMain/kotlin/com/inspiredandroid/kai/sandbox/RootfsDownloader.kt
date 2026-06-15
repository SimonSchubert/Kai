package com.inspiredandroid.kai.sandbox

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

// Kali NetHunter full rootfs — hosted on kali.download (official CDN)
private const val KALI_BASE_URL = "https://kali.download/nethunter-images/current/rootfs"
private const val KALI_VARIANT = "full"
private const val BUFFER_SIZE = 8192

// Maps Android/Linux arch names → Kali rootfs arch suffixes
private val KALI_ARCH_MAP = mapOf(
    "aarch64" to "arm64",
    "armhf" to "armhf",
    "x86_64" to "amd64",
    "x86" to "i386",
)

private const val TAR_BLOCK_SIZE = 512
private const val TAR_NAME_OFFSET = 0
private const val TAR_MODE_OFFSET = 100
private const val TAR_SIZE_OFFSET = 124
private const val TAR_TYPE_OFFSET = 156
private const val TAR_LINK_OFFSET = 157
private const val TAR_PREFIX_OFFSET = 345

class RootfsDownloader(private val httpClient: HttpClient) {

    /** Single-entry list kept for compatibility with LinuxSandboxManager mirror iteration. */
    val mirrors: List<String> = listOf(KALI_BASE_URL)

    fun getDownloadUrls(arch: String): List<String> {
        val kaliArch = KALI_ARCH_MAP[arch] ?: arch
        val filename = "kali-nethunter-rootfs-$KALI_VARIANT-$kaliArch.tar.xz"
        return listOf("$KALI_BASE_URL/$filename")
    }

    suspend fun download(
        arch: String,
        targetFile: File,
        onProgress: (Float) -> Unit,
    ) {
        val urls = getDownloadUrls(arch)
        var lastError: Exception? = null
        for ((index, url) in urls.withIndex()) {
            try {
                downloadFrom(url, targetFile, onProgress)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (targetFile.exists()) targetFile.delete()
                if (index < urls.lastIndex) onProgress(0f)
            }
        }
        throw IOException("Kali rootfs download failed", lastError)
    }

    private suspend fun downloadFrom(
        url: String,
        targetFile: File,
        onProgress: (Float) -> Unit,
    ) {
        httpClient.prepareGet(url).execute { response ->
            if (!response.status.isSuccess()) {
                throw IOException("HTTP ${response.status.value} from $url")
            }
            val totalBytes = response.contentLength() ?: -1L
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(BUFFER_SIZE)
            var downloadedBytes = 0L

            FileOutputStream(targetFile).use { output ->
                while (!channel.isClosedForRead) {
                    val bytesRead = channel.readAvailable(buffer)
                    if (bytesRead <= 0) break
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalBytes > 0) {
                        onProgress(downloadedBytes.toFloat() / totalBytes)
                    }
                }
            }
        }
    }

    /**
     * Extracts a .tar.xz file (Kali NetHunter rootfs format).
     * Kali tarballs may have a top-level directory prefix (e.g. `kali-arm64/`);
     * that prefix is stripped so extraction always lands flat in [targetDir].
     */
    fun extractTarXz(tarXzFile: File, targetDir: File) {
        targetDir.mkdirs()
        XZInputStream(BufferedInputStream(FileInputStream(tarXzFile))).use { xzStream ->
            extractTar(xzStream, targetDir)
        }
    }

    private fun extractTar(inputStream: java.io.InputStream, targetDir: File) {
        val headerBuffer = ByteArray(TAR_BLOCK_SIZE)
        val dataBuffer = ByteArray(BUFFER_SIZE)

        // Detect and strip a single top-level directory prefix (e.g. "kali-arm64/").
        // We do a two-pass approach: first entry tells us the prefix if it's a directory.
        var topLevelPrefix: String? = null
        var firstEntry = true

        while (true) {
            val headerBytesRead = readFully(inputStream, headerBuffer)
            if (headerBytesRead < TAR_BLOCK_SIZE) break

            val name = readTarString(headerBuffer, TAR_NAME_OFFSET, 100)
            if (name.isEmpty()) break

            val prefix = readTarString(headerBuffer, TAR_PREFIX_OFFSET, 155)
            val fullName = if (prefix.isNotEmpty()) "$prefix/$name" else name

            val sizeStr = readTarString(headerBuffer, TAR_SIZE_OFFSET, 12)
            val size = if (sizeStr.isNotEmpty()) sizeStr.toLong(8) else 0L

            val modeStr = readTarString(headerBuffer, TAR_MODE_OFFSET, 8)
            val mode = if (modeStr.isNotEmpty()) modeStr.toInt(8) else 0
            val typeFlag = headerBuffer[TAR_TYPE_OFFSET]
            val linkName = readTarString(headerBuffer, TAR_LINK_OFFSET, 100)

            // Detect top-level directory prefix on the very first entry
            if (firstEntry) {
                firstEntry = false
                val firstSegment = fullName.substringBefore('/')
                if (firstSegment.startsWith("kali-") && fullName.contains('/')) {
                    topLevelPrefix = "$firstSegment/"
                    if (fullName == topLevelPrefix) {
                        // Skip this directory entry itself
                        if (size > 0) skipBytes(inputStream, alignToBlock(size))
                        continue
                    }
                }
            }

            // Strip the top-level prefix from all paths
            val effectiveName = if (topLevelPrefix != null && fullName.startsWith(topLevelPrefix)) {
                fullName.removePrefix(topLevelPrefix)
            } else {
                fullName
            }
            if (effectiveName.isEmpty() || effectiveName == "./") {
                if (size > 0) skipBytes(inputStream, alignToBlock(size))
                continue
            }

            val outFile = File(targetDir, effectiveName)

            if (!outFile.canonicalPath.startsWith(targetDir.canonicalPath + File.separator)) {
                skipBytes(inputStream, alignToBlock(size))
                continue
            }

            when (typeFlag.toInt().toChar()) {
                '5', 'D' -> outFile.mkdirs()

                '2' -> {
                    outFile.parentFile?.mkdirs()
                    try {
                        if (outFile.exists()) outFile.delete()
                        java.nio.file.Files.createSymbolicLink(
                            outFile.toPath(),
                            java.nio.file.Paths.get(linkName),
                        )
                    } catch (_: Exception) {
                    }
                }

                '1' -> {
                    val linkTarget = File(targetDir, linkName)
                    outFile.parentFile?.mkdirs()
                    if (linkTarget.exists()) {
                        linkTarget.copyTo(outFile, overwrite = true)
                    }
                }

                '0', '\u0000' -> {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output ->
                        var remaining = size
                        while (remaining > 0) {
                            val toRead = minOf(remaining, dataBuffer.size.toLong()).toInt()
                            val bytesRead = inputStream.read(dataBuffer, 0, toRead)
                            if (bytesRead <= 0) break
                            output.write(dataBuffer, 0, bytesRead)
                            remaining -= bytesRead
                        }
                    }
                    if (mode and 0b001_001_001 != 0) {
                        outFile.setExecutable(true, false)
                    }
                    val padding = alignToBlock(size) - size
                    if (padding > 0) skipBytes(inputStream, padding)
                    continue
                }

                else -> {}
            }

            if (size > 0 && typeFlag.toInt().toChar() != '0' && typeFlag.toInt().toChar() != '\u0000') {
                skipBytes(inputStream, alignToBlock(size))
            }
        }
    }

    private fun readTarString(buffer: ByteArray, offset: Int, length: Int): String {
        val end = minOf(offset + length, buffer.size)
        val nullIndex = (offset until end).firstOrNull { buffer[it] == 0.toByte() } ?: end
        return String(buffer, offset, nullIndex - offset, Charsets.US_ASCII).trim()
    }

    private fun readFully(inputStream: java.io.InputStream, buffer: ByteArray): Int {
        var totalRead = 0
        while (totalRead < buffer.size) {
            val bytesRead = inputStream.read(buffer, totalRead, buffer.size - totalRead)
            if (bytesRead <= 0) break
            totalRead += bytesRead
        }
        return totalRead
    }

    private fun skipBytes(inputStream: java.io.InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = inputStream.skip(remaining)
            if (skipped <= 0) {
                if (inputStream.read() < 0) break
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
    }

    private fun alignToBlock(size: Long): Long {
        val remainder = size % TAR_BLOCK_SIZE
        return if (remainder == 0L) size else size + (TAR_BLOCK_SIZE - remainder)
    }

    fun makeWritable(rootfsDir: File) {
        rootfsDir.walkTopDown().forEach { file ->
            if (file.isDirectory && !file.canWrite()) {
                file.setWritable(true, true)
            }
        }
    }

    fun writeResolvConf(rootfsDir: File) {
        val etcDir = File(rootfsDir, "etc")
        etcDir.mkdirs()
        File(etcDir, "resolv.conf").writeText(
            "nameserver 8.8.8.8\nnameserver 8.8.4.4\n",
        )
    }

    /**
     * Writes Kali's apt sources list.
     * [mirrorBase] is unused (Kali has a single official mirror) but kept for API
     * compatibility with LinuxSandboxManager's mirror-retry loop.
     */
    fun writeRepositories(rootfsDir: File, @Suppress("UNUSED_PARAMETER") mirrorBase: String) {
        val aptDir = File(rootfsDir, "etc/apt")
        aptDir.mkdirs()
        File(aptDir, "sources.list").writeText(
            "deb https://http.kali.org/kali kali-rolling main contrib non-free non-free-firmware\n",
        )
    }
}
