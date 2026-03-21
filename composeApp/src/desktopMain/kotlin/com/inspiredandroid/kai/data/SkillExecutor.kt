package com.inspiredandroid.kai.data

import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val MAX_OUTPUT_LENGTH = 10_000
private const val HTTP_TIMEOUT_MS = 30_000
private const val MAX_SKILL_CALL_DEPTH = 5

private val JS_PREAMBLE = """
async function fetch(url, options) {
    var raw = JSON.parse(await _nativeFetch(url, JSON.stringify(options || {})));
    return {
        ok: raw.ok,
        status: raw.status,
        body: raw.body,
        text: function() { return raw.body; },
        json: function() { return JSON.parse(raw.body); }
    };
}
var skill = {
    run: async function(name, input) {
        return await _nativeSkillRun(name, input || null);
    }
};
""".trimIndent()

actual class SkillExecutor actual constructor(skillStore: SkillStore) {

    private val skillStore = skillStore
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    actual suspend fun execute(
        script: String,
        input: String?,
        dataJson: String?,
        timeoutMs: Long,
    ): SkillExecutionResult = mutex.withLock { executeInternal(script, input, dataJson, 0, timeoutMs) }

    private suspend fun executeInternal(
        script: String,
        input: String?,
        dataJson: String?,
        callDepth: Int,
        timeoutMs: Long,
    ): SkillExecutionResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(timeoutMs) {
                val fullScript = buildString {
                    append(JS_PREAMBLE)
                    append('\n')
                    append("var input = ")
                    append(toJsLiteral(input))
                    append(";\n")
                    append("var data = ")
                    append(toJsLiteral(dataJson?.ifEmpty { null }))
                    append(";\n")
                    append(script)
                }
                val result = quickJs {
                    asyncFunction("_nativeFetch") { args ->
                        val url = args.firstOrNull()?.toString()
                            ?: throw IllegalArgumentException("fetch requires a URL")
                        val optionsJson = args.getOrNull(1)?.toString() ?: "{}"
                        httpFetch(url, optionsJson)
                    }

                    asyncFunction("_nativeSkillRun") { args ->
                        val name = args.firstOrNull()?.toString()
                            ?: throw IllegalArgumentException("skill.run requires a skill name")
                        val runInput = args.getOrNull(1)?.toString()
                        if (callDepth + 1 >= MAX_SKILL_CALL_DEPTH) {
                            throw IllegalStateException("Maximum skill call depth ($MAX_SKILL_CALL_DEPTH) exceeded")
                        }
                        val skill = skillStore.getSkill(name)
                            ?: throw IllegalArgumentException("Skill not found: $name")
                        val subResult = executeInternal(
                            script = skill.script,
                            input = runInput,
                            dataJson = skill.dataJson.ifEmpty { null },
                            callDepth = callDepth + 1,
                            timeoutMs = timeoutMs,
                        )
                        if (!subResult.success) {
                            throw RuntimeException(subResult.error ?: "Skill '$name' failed")
                        }
                        subResult.output
                    }

                    define("fs") {
                        function("readFile") { args ->
                            val path = args.firstOrNull()?.toString()
                                ?: throw IllegalArgumentException("readFile requires a path")
                            File(path).readText()
                        }
                        function("writeFile") { args ->
                            val path = args.firstOrNull()?.toString()
                                ?: throw IllegalArgumentException("writeFile requires a path")
                            val content = args.getOrNull(1)?.toString() ?: ""
                            File(path).parentFile?.mkdirs()
                            File(path).writeText(content)
                            true
                        }
                        function("exists") { args ->
                            val path = args.firstOrNull()?.toString()
                                ?: throw IllegalArgumentException("exists requires a path")
                            File(path).exists()
                        }
                        function("listDir") { args ->
                            val path = args.firstOrNull()?.toString()
                                ?: throw IllegalArgumentException("listDir requires a path")
                            File(path).list()?.joinToString("\n") ?: ""
                        }
                    }

                    evaluate<Any?>(fullScript)
                }
                SkillExecutionResult(
                    success = true,
                    output = result?.toString()?.take(MAX_OUTPUT_LENGTH) ?: "",
                )
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

    actual suspend fun validate(script: String): String? = withContext(Dispatchers.IO) {
        try {
            val fullScript = JS_PREAMBLE + "\n" + script
            quickJs { compile(fullScript) }
            null
        } catch (e: Exception) {
            e.message ?: "Syntax error"
        }
    }

    private fun httpFetch(url: String, optionsJson: String): String {
        val options = try {
            json.parseToJsonElement(optionsJson).jsonObject
        } catch (_: Exception) {
            null
        }
        val method = options?.get("method")?.jsonPrimitive?.content?.uppercase() ?: "GET"
        val body = options?.get("body")?.jsonPrimitive?.content
        val headers = options?.get("headers")?.jsonObject

        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = HTTP_TIMEOUT_MS
            connection.readTimeout = HTTP_TIMEOUT_MS

            headers?.forEach { (key, value) ->
                connection.setRequestProperty(key, value.jsonPrimitive.content)
            }

            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray()) }
            }

            val responseCode = connection.responseCode
            val responseBody = try {
                val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                stream?.bufferedReader()?.readText()?.take(MAX_OUTPUT_LENGTH) ?: ""
            } catch (_: Exception) {
                ""
            }

            buildString {
                append("{\"ok\":")
                append(responseCode in 200..299)
                append(",\"status\":")
                append(responseCode)
                append(",\"body\":")
                append(jsonStringify(responseBody))
                append("}")
            }
        } finally {
            connection.disconnect()
        }
    }
}
