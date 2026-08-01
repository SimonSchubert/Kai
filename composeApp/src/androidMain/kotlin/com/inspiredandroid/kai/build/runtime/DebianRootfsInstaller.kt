package com.inspiredandroid.kai.build.runtime

import android.os.Build
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val LXC_INDEX = "https://images.linuxcontainers.org/meta/1.0/index-user"
private const val LXC_BASE = "https://images.linuxcontainers.org"
private const val DEBIAN_RELEASE = "bookworm"
private const val BUFFER_SIZE = 64 * 1024

/**
 * Downloads a Debian [DEBIAN_RELEASE] rootfs from the Linux Containers image server.
 */
object DebianRootfsInstaller {

    /** Debian architecture name matching this device's ABI. */
    fun linuxArch(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return when {
            abi.startsWith("arm64") -> "arm64"
            abi.startsWith("armeabi") -> "armhf"
            abi.startsWith("x86_64") -> "amd64"
            abi.startsWith("x86") -> "i386"
            else -> "arm64"
        }
    }

    /**
     * Newest default image for this device's architecture, e.g.
     * `debian;bookworm;arm64;default;20260731_05:24;/images/debian/bookworm/arm64/default/20260731_05:24/`.
     */
    private fun resolveRootfsUrl(): String {
        val arch = linuxArch()
        val index = URL(LXC_INDEX).openStream().bufferedReader().use { it.readText() }
        val line = index.lineSequence()
            .filter { it.startsWith("debian;$DEBIAN_RELEASE;$arch;default;") }
            .maxOrNull()
            ?: throw IOException("No Debian $DEBIAN_RELEASE image for $arch in LXC index")
        val path = line.split(';').getOrNull(5)?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IOException("Malformed LXC index line: $line")
        return LXC_BASE + path.removeSuffix("/") + "/rootfs.tar.xz"
    }

    /** Blocking; stops early and leaves a partial file behind once [isActive] turns false. */
    fun download(targetFile: File, isActive: () -> Boolean, onProgress: (Float) -> Unit) {
        targetFile.parentFile?.mkdirs()
        targetFile.delete()

        val connection = (URL(resolveRootfsUrl()).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 60_000
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode} while downloading Debian")
            }
            val total = connection.contentLengthLong
            var downloaded = 0L
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (isActive()) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (isActive()) onProgress(1f)
        } finally {
            connection.disconnect()
        }
    }

    fun extract(archive: File, rootfsDir: File) {
        rootfsDir.deleteRecursively()
        TarExtractor.extractTarXz(archive, rootfsDir)
        TarExtractor.makeWritable(rootfsDir)
        TarExtractor.writeResolvConf(rootfsDir)
    }
}
