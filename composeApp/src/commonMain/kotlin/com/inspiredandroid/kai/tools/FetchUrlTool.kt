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
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.utils.io.readRemaining
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_fetch_url_description
import kai.composeapp.generated.resources.tool_fetch_url_name
import kotlinx.io.readByteArray

private val ALLOWED_METHODS = setOf("GET", "POST", "HEAD")
private val HTML_TAG_REGEX = Regex("<[^>]*>")
private const val MAX_REDIRECTS = 5

// Results are cut to 20K chars downstream; reading more than this only costs memory (a large
// download used to be buffered whole before truncation). Generous enough for HTML that shrinks
// a lot once tags are stripped.
private const val MAX_BODY_BYTES = 512L * 1024

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
        // Redirects are followed by hand so every hop's host is checked — automatic following
        // let a public URL bounce the request to localhost or the LAN.
        followRedirects = false
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

        var url = runCatching { Url(urlArg) }.getOrNull()
            ?: return mapOf("success" to false, "error" to "invalid URL")
        rejectUrl(url)?.let { return mapOf("success" to false, "error" to it) }

        return try {
            var response = send(url, methodArg, bodyArg, contentTypeArg)
            // POST is never redirected (matches the previous behavior and avoids replaying a body).
            var hops = 0
            while (methodArg != "POST" && response.status.value in 300..399) {
                val location = response.headers[HttpHeaders.Location] ?: break
                if (++hops > MAX_REDIRECTS) {
                    return mapOf("success" to false, "error" to "too many redirects")
                }
                url = runCatching { resolveRedirect(url, location) }.getOrNull()
                    ?: return mapOf("success" to false, "error" to "invalid redirect: $location")
                rejectUrl(url)?.let { return mapOf("success" to false, "error" to "redirect $it") }
                response = send(url, methodArg, bodyArg = null, contentTypeArg = null)
            }

            val responseCt = response.headers["Content-Type"].orEmpty()
            val rawBody = if (methodArg == "HEAD") "" else response.bodyAsChannel().readRemaining(MAX_BODY_BYTES).readByteArray().decodeToString()
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
            if (e is kotlinx.coroutines.CancellationException) throw e
            mapOf("success" to false, "error" to "fetch failed: ${e.message}")
        }
    }

    private suspend fun send(url: Url, methodArg: String, bodyArg: String?, contentTypeArg: String?): HttpResponse = client.request(url) {
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

    /** Null when [url] may be fetched, otherwise the reason it is refused. */
    private fun rejectUrl(url: Url): String? {
        val scheme = url.protocol.name.lowercase()
        if (scheme != "http" && scheme != "https") return "only http and https schemes are allowed"
        if (isBlockedFetchHost(url.host)) return "blocked host: ${url.host}"
        return null
    }

    val toolInfo = ToolInfo(
        id = "fetch_url",
        name = "Fetch URL",
        description = "Fetch the contents of a URL and return the response body to the agent",
        nameRes = Res.string.tool_fetch_url_name,
        descriptionRes = Res.string.tool_fetch_url_description,
    )
}

/**
 * Hosts fetch_url refuses: loopback, private, link-local and unspecified addresses, plus numeric
 * spellings a resolver would turn into one (`2130706433`, `127.1`, `0x7f.0.0.1`). IPv6 rules apply
 * only to IP literals — a hostname like `fdic.gov` is not an fd00::/8 address. Hostnames that
 * merely *resolve* to private addresses are not caught here (no DNS in common code).
 */
internal fun isBlockedFetchHost(host: String): Boolean {
    val h = host.lowercase().trim('[', ']').trimEnd('.')
    if (h.isEmpty()) return true
    if (h == "localhost" || h.endsWith(".localhost")) return true
    if (':' in h) return isBlockedIpv6(h)
    val octets = h.split(".")
    if (octets.size == 4 && octets.all { (it.toIntOrNull() ?: -1) in 0..255 }) {
        return isBlockedIpv4(octets.map { it.toInt() })
    }
    // Shortened (127.1), single-integer (2130706433) or hex/octal forms: not a normal hostname,
    // and resolvers map them onto arbitrary IPv4 addresses.
    if (h.all { it.isDigit() || it == '.' } || h.startsWith("0x") || h.contains(".0x")) return true
    return false
}

private fun isBlockedIpv4(o: List<Int>): Boolean {
    val (a, b) = o[0] to o[1]
    return a == 127 || a == 10 || a == 0 ||
        (a == 169 && b == 254) ||
        (a == 192 && b == 168) ||
        (a == 172 && b in 16..31) ||
        (a == 100 && b in 64..127) // carrier-grade NAT, often used for internal services
}

private fun isBlockedIpv6(h: String): Boolean {
    if (h == "::" || h == "::1" || h == "0:0:0:0:0:0:0:1") return true
    if (h.startsWith("fe8") || h.startsWith("fe9") || h.startsWith("fea") || h.startsWith("feb")) return true // fe80::/10
    if (h.startsWith("fc") || h.startsWith("fd")) return true // fc00::/7
    // IPv4-mapped/compatible (::ffff:127.0.0.1, ::ffff:7f00:1): judge the embedded IPv4 address.
    val mapped = h.removePrefix("::ffff:").removePrefix("0:0:0:0:0:ffff:")
    if (mapped != h) {
        val dotted = mapped.split(".")
        if (dotted.size == 4) return dotted.all { (it.toIntOrNull() ?: -1) in 0..255 } && isBlockedIpv4(dotted.map { it.toInt() })
        val groups = mapped.split(":")
        if (groups.size == 2) {
            val hi = groups[0].toIntOrNull(16) ?: return true
            val lo = groups[1].toIntOrNull(16) ?: return true
            return isBlockedIpv4(listOf(hi shr 8, hi and 0xff, lo shr 8, lo and 0xff))
        }
        return true
    }
    return false
}

/**
 * Resolves a Location header against the current URL. takeFrom only overwrites the parts the
 * location spells out, so the old query and fragment are cleared first — otherwise they leak
 * into the redirect target.
 */
internal fun resolveRedirect(current: Url, location: String): Url = URLBuilder(current).apply {
    parameters.clear()
    fragment = ""
}.takeFrom(location).build()
