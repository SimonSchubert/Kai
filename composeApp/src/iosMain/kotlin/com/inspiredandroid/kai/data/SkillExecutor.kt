package com.inspiredandroid.kai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import platform.JavaScriptCore.JSContext

private const val MAX_OUTPUT_LENGTH = 10_000

@Suppress("UNUSED_PARAMETER")
actual class SkillExecutor actual constructor(skillStore: SkillStore) {

    private val mutex = Mutex()

    actual suspend fun execute(
        script: String,
        input: String?,
        dataJson: String?,
        timeoutMs: Long,
    ): SkillExecutionResult {
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    withTimeout(timeoutMs) {
                        val context = JSContext()

                        // Stub bindings for fetch/fs (not yet supported on iOS)
                        context.evaluateScript(
                            """
                            async function fetch() { throw new Error("fetch is not yet available on iOS"); }
                            var fs = {
                                readFile: function() { throw new Error("fs is not yet available on iOS"); },
                                writeFile: function() { throw new Error("fs is not yet available on iOS"); },
                                exists: function() { throw new Error("fs is not yet available on iOS"); },
                                listDir: function() { throw new Error("fs is not yet available on iOS"); }
                            };
                            var skill = {
                                run: async function() { throw new Error("skill.run is not yet available on iOS"); }
                            };
                            """.trimIndent(),
                        )

                        context.evaluateScript("var input = ${toJsLiteral(input)};")
                        context.evaluateScript("var data = ${toJsLiteral(dataJson?.ifEmpty { null })};")

                        val result = context.evaluateScript(script)
                        val exception = context.exception

                        if (exception != null) {
                            SkillExecutionResult(
                                success = false,
                                output = "",
                                error = exception.toString(),
                            )
                        } else {
                            SkillExecutionResult(
                                success = true,
                                output = result?.toString()?.take(MAX_OUTPUT_LENGTH) ?: "",
                            )
                        }
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    SkillExecutionResult(
                        success = false,
                        output = "",
                        error = "Script timed out after ${timeoutMs / 1000} seconds",
                        timedOut = true,
                    )
                } catch (e: Exception) {
                    SkillExecutionResult(
                        success = false,
                        output = "",
                        error = e.message ?: "Script execution failed",
                    )
                }
            }
        }
    }

    actual suspend fun validate(script: String): String? {
        return withContext(Dispatchers.IO) {
            val context = JSContext()
            context.evaluateScript("(function(){ $script })")
            val exception = context.exception
            exception?.toString()
        }
    }
}
