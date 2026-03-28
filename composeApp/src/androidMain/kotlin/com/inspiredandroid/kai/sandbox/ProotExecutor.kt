package com.inspiredandroid.kai.sandbox

import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private const val MAX_OUTPUT_LENGTH = 5_000
private const val DEFAULT_TIMEOUT_SECONDS = 30L
private const val MAX_TIMEOUT_SECONDS = 60L

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

        val loaderPath = File(prootPath).parent + "/libproot-loader.so"
        val envVars = arrayOf(
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "LD_LIBRARY_PATH=$libDir",
            "PROOT_TMP_DIR=$tmpPath",
            "PROOT_LOADER=$loaderPath",
        )

        return try {
            val process = Runtime.getRuntime().exec(processArgs, envVars, File(rootfsPath).parentFile)

            // Drain stdout/stderr concurrently to avoid pipe buffer deadlock
            val stdoutFuture = CompletableFuture.supplyAsync {
                process.inputStream.bufferedReader().readText().take(MAX_OUTPUT_LENGTH)
            }
            val stderrFuture = CompletableFuture.supplyAsync {
                process.errorStream.bufferedReader().readText().take(MAX_OUTPUT_LENGTH)
            }

            val completed = process.waitFor(effectiveTimeout, TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                return mapOf(
                    "success" to false,
                    "stdout" to stdoutFuture.get(1, TimeUnit.SECONDS),
                    "stderr" to stderrFuture.get(1, TimeUnit.SECONDS),
                    "exit_code" to -1,
                    "timed_out" to true,
                )
            }

            mapOf(
                "success" to (process.exitValue() == 0),
                "stdout" to stdoutFuture.get(),
                "stderr" to stderrFuture.get(),
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
}
