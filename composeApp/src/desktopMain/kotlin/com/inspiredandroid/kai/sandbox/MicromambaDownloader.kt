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

private const val BUFFER_SIZE = 8192

class MicromambaDownloader(private val httpClient: HttpClient) {

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
        targetFile.setExecutable(true, false)
    }
}
