package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.httpClient
import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_fetch_url_description
import kai.composeapp.generated.resources.tool_fetch_url_name

private val ALLOWED_METHODS = setOf("GET", "POST", "HEAD")
private val HTML_TAG_REGEX = Regex("<[^>]*>")

object FetchUrlTool : Tool {
    override val schema = ToolSchema(
        name = "fetch_url",
        description = "Fetch the contents of an https/http URL and return the status and response body. " +
            "Use this to read web pages, hit API endpoints, or act on links from emails (e.g. RFC 8058 " +
            "one-click unsubscribe: POST to the https list-unsubscribe URL with body `List-Unsubscribe=One-Click`). " +
            "Redirects are followed for GET/HEAD only. Private/loopback addresses are blocked. " +
            "HTML responses are stripped of tags; large responses are truncated.",
        parameters = mapOf(
            "url" to ParameterSchema(
                type = "string",
                description = "The absolute http(s) URL to fetch",
                required = true,
            ),
            "method" to ParameterSchema(
                type = "string",
                description = "HTTP method: GET (default), POST, or HEAD",
                required = false,
            ),
            "body" to ParameterSchema(
                type = "string",
                description = "Request body (POST only). For RFC 8058 one-click unsubscribe use `List-Unsubscribe=One-Click`.",
                required = false,
            ),
            "content_type" to ParameterSchema(
                type = "string",
                description = "Content-Type header for the request body. Defaults to application/x-www-form-urlencoded when a body is present.",
                required = false,
            ),
        ),
    )

    private val client = httpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
    }

    override suspend fun execute(args: Map<String, Any>): Any {
        val urlArg = args["url"]?.toString()
            ?: return mapOf("success" to false, "error" to "url is required")
        val methodArg = (args["method"]?.toString() ?: "GET").uppercase()
        val bodyArg = args["body"]?.toString()
        val contentTypeArg = args["content_type"]?.toString()

        if (methodArg !in ALLOWED_METHODS) {
            return mapOf("success" to false, "error" to "method must be one of $ALLOWED_METHODS")
        }

        val parsed = runCatching { Url(urlArg) }.getOrNull()
            ?: return mapOf("success" to false, "error" to "invalid URL")

        val scheme = parsed.protocol.name.lowercase()
        if (scheme != "http" && scheme != "https") {
            return mapOf("success" to false, "error" to "only http and https schemes are allowed")
        }
        if (isBlockedHost(parsed.host)) {
            return mapOf("success" to false, "error" to "blocked host: ${parsed.host}")
        }

        return try {
            val response = client.request(urlArg) {
                method = HttpMethod.parse(methodArg)
                header("User-Agent", "Mozilla/5.0 (compatible; Kai/1.0)")
                if (bodyArg != null && methodArg == "POST") {
                    contentType(
                        contentTypeArg?.let { runCatching { ContentType.parse(it) }.getOrNull() }
                            ?: ContentType.Application.FormUrlEncoded,
                    )
                    setBody(bodyArg)
                }
            }

            val responseCt = response.headers["Content-Type"].orEmpty()
            val rawBody = if (methodArg == "HEAD") "" else response.bodyAsText()
            val body = if (responseCt.startsWith("text/html", ignoreCase = true)) {
                rawBody.replace(HTML_TAG_REGEX, "").decodeHtmlEntities()
            } else {
                rawBody
            }

            mapOf(
                "success" to response.status.isSuccess(),
                "status" to response.status.value,
                "final_url" to response.call.request.url.toString(),
                "content_type" to responseCt,
                "body" to body,
            )
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "fetch failed: ${e.message}")
        }
    }

    private fun isBlockedHost(host: String): Boolean {
        val h = host.lowercase().trim('[', ']')
        if (h.isEmpty()) return true
        if (h == "localhost" || h.endsWith(".localhost")) return true
        if (h == "::1" || h == "0:0:0:0:0:0:0:1") return true
        if (h.startsWith("fe80:") || h.startsWith("fc") || h.startsWith("fd")) return true
        val octets = h.split(".")
        if (octets.size == 4 && octets.all { it.toIntOrNull() != null }) {
            val (a, b) = octets[0].toInt() to octets[1].toInt()
            return when {
                a == 127 -> true
                a == 10 -> true
                a == 0 -> true
                a == 169 && b == 254 -> true
                a == 192 && b == 168 -> true
                a == 172 && b in 16..31 -> true
                else -> false
            }
        }
        return false
    }

    val toolInfo = ToolInfo(
        id = "fetch_url",
        name = "Fetch URL",
        description = "Fetch the contents of a URL and return the response body to the agent",
        nameRes = Res.string.tool_fetch_url_name,
        descriptionRes = Res.string.tool_fetch_url_description,
    )
}
