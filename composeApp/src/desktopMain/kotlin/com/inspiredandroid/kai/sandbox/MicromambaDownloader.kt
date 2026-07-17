package com.inspiredandroid.kai.sandbox

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val BUFFER_SIZE = 8192
private const val EXTRACT_TIMEOUT_SECONDS = 60L

class MicromambaDownloader(private val httpClient: HttpClient) {

    /**
     * Downloads bytes from the micromamba release URL to [targetFile]. The
     * response body is the release archive itself (a `.tar.bz2`), not a raw
     * executable — callers that need a runnable binary must follow this with
     * [extractBinary].
     */
    suspend fun download(targetFile: File, onProgress: (Float) -> Unit) {
        val url = micromambaDownloadUrl()
        httpClient.prepareGet(url).execute { response ->
            if (!response.status.isSuccess()) {
                throw IOException("HTTP ${response.status.value} from $url")
            }
            targetFile.parentFile?.mkdirs()
            val totalBytes = response.contentLength() ?: -1L
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(BUFFER_SIZE)
            var downloadedBytes = 0L

            try {
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
            } catch (e: Exception) {
                targetFile.delete()
                throw e
            }
        }
    }

    /**
     * Extracts the `bin/micromamba` executable from [archiveFile] (a
     * `.tar.bz2` release archive, as fetched by [download]) into [baseDir],
     * landing at `baseDir/bin/micromamba` — the same path
     * [MicromambaLayout.binaryFile] resolves to. Shells out to the system
     * `tar`, matching the release's own documented install one-liner
     * (`curl -Ls <url> | tar -xvj bin/micromamba`) rather than adding a
     * bzip2-capable archive library dependency.
     */
    fun extractBinary(archiveFile: File, baseDir: File) {
        baseDir.mkdirs()
        val process = ProcessBuilder(
            "tar",
            "-xjf",
            archiveFile.absolutePath,
            "-C",
            baseDir.absolutePath,
            "bin/micromamba",
        ).redirectErrorStream(true).start()
        val finished = process.waitFor(EXTRACT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw IOException("Timed out extracting micromamba archive")
        }
        if (process.exitValue() != 0) {
            val output = process.inputStream.bufferedReader().readText()
            throw IOException("Failed to extract micromamba archive: ${output.take(500)}")
        }
        val binaryFile = File(baseDir, "bin/micromamba")
        if (!binaryFile.exists()) {
            throw IOException("Extraction succeeded but bin/micromamba was not found in the archive")
        }
        binaryFile.setExecutable(true, false)
    }
}
