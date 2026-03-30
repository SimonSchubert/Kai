package com.inspiredandroid.kai.sandbox

import com.inspiredandroid.kai.smartTruncate
import java.io.BufferedReader
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private const val MAX_OUTPUT_LENGTH = 15_000
private const val DEFAULT_TIMEOUT_SECONDS = 30L
private const val MAX_TIMEOUT_SECONDS = 180L

class ProotExecutor(
    private val prootPath: String,
    private val libDir: String,
    private val rootfsPath: String,
    private val homePath: String,
    private val tmpPath: String,
) {

    fun execute(
        command: String,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
        workingDir: String = "/root",
        extraEnv: Map<String, String> = emptyMap(),
    ): Map<String, Any> {
        val effectiveTimeout = timeoutSeconds.coerceIn(1, MAX_TIMEOUT_SECONDS)

        val processArgs = arrayOf(
            prootPath,
            "--rootfs=$rootfsPath",
            "--bind=/dev",
            "--bind=/proc",
            "--bind=/sys",
            "--bind=$homePath:/root",
            "--bind=$tmpPath:/tmp",
            "-0",
            "-w", workingDir,
            "/bin/sh", "-c", command,
        )

        val loaderPath = File(prootPath).parent.orEmpty() + "/libproot-loader.so"
        val baseEnv = arrayOf(
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "LD_LIBRARY_PATH=$libDir",
            "PROOT_TMP_DIR=$tmpPath",
            "PROOT_LOADER=$loaderPath",
        )
        val envVars = baseEnv + extraEnv.map { (k, v) -> "$k=$v" }.toTypedArray()

        return try {
            val process = Runtime.getRuntime().exec(processArgs, envVars, File(rootfsPath).parentFile)

            // Drain stdout/stderr concurrently to avoid pipe buffer deadlock
            val stdoutFuture = CompletableFuture.supplyAsync {
                readBounded(process.inputStream.bufferedReader())
            }
            val stderrFuture = CompletableFuture.supplyAsync {
                readBounded(process.errorStream.bufferedReader())
            }

            val completed = process.waitFor(effectiveTimeout, TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                return mapOf(
                    "success" to false,
                    "stdout" to stdoutFuture.get(1, TimeUnit.SECONDS).smartTruncate(MAX_OUTPUT_LENGTH),
                    "stderr" to stderrFuture.get(1, TimeUnit.SECONDS).smartTruncate(MAX_OUTPUT_LENGTH),
                    "exit_code" to -1,
                    "timed_out" to true,
                )
            }

            mapOf(
                "success" to (process.exitValue() == 0),
                "stdout" to stdoutFuture.get().smartTruncate(MAX_OUTPUT_LENGTH),
                "stderr" to stderrFuture.get().smartTruncate(MAX_OUTPUT_LENGTH),
                "exit_code" to process.exitValue(),
                "timed_out" to false,
            )
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "error" to (e.message ?: "Failed to execute command in sandbox"),
            )
        }
    }

    private fun readBounded(reader: BufferedReader): String {
        val sb = StringBuilder()
        val buf = CharArray(8192)
        var read: Int
        while (reader.read(buf).also { read = it } != -1) {
            sb.append(buf, 0, read)
            if (sb.length >= MAX_OUTPUT_LENGTH) break
        }
        if (sb.length >= MAX_OUTPUT_LENGTH) {
            while (reader.read(buf) != -1) { /* discard */ }
        }
        return sb.toString()
    }
}
