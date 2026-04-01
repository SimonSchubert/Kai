package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.httpClient
import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_web_search_description
import kai.composeapp.generated.resources.tool_web_search_name

private const val MAX_RESULTS = 5

object WebSearchTool : Tool {
    private val linkRegex = Regex("""<a[^>]+class=['"]result-link['"][^>]*>([\s\S]*?)</a>""")
    private val hrefRegex = Regex("""href=['"]([^'"]*?)['"]""")
    private val snippetRegex = Regex("""<td[^>]+class=['"]result-snippet['"][^>]*>([\s\S]*?)</td>""")
    private val fullLinkRegex = Regex("""<a\s[^>]*class=['"]result-link['"][^>]*>""")
    private val uddgRegex = Regex("""uddg=([^&]+)""")
    private val htmlTagRegex = Regex("<[^>]*>")

    override val schema = ToolSchema(
        name = "web_search",
        description = "Search the web for current information. Returns titles, URLs, and snippets. Before answering questions about recent events, news, current prices, weather, or anything time-sensitive, search first. Also use this when you're unsure about facts or the user asks you to look something up.",
        parameters = mapOf(
            "query" to ParameterSchema("string", "The search query", true),
        ),
    )

    private val client = httpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
        }
    }

    override suspend fun execute(args: Map<String, Any>): Any {
        val query = args["query"]?.toString()
            ?: return mapOf("success" to false, "error" to "Query is required")

        return try {
            val encoded = query.encodeURLQueryComponent()
            val response = client.get("https://lite.duckduckgo.com/lite/?q=$encoded") {
                header("User-Agent", "Mozilla/5.0 (compatible; Kai/1.0)")
            }
            val html = response.bodyAsText()
            val results = parseResults(html)

            if (results.isEmpty()) {
                mapOf("success" to true, "results" to emptyList<Any>(), "message" to "No results found")
            } else {
                mapOf("success" to true, "results" to results)
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Search failed: ${e.message}")
        }
    }

    private fun parseResults(html: String): List<Map<String, String>> {
        val results = mutableListOf<Map<String, String>>()

        // DuckDuckGo Lite returns results in a table structure
        // Links: <a rel="nofollow" href="//duckduckgo.com/l/?uddg=URL" class='result-link'>Title</a>
        // Snippets: <td class='result-snippet'>...</td>
        val linkTags = fullLinkRegex.findAll(html).toList()
        val links = linkRegex.findAll(html).toList()
        val snippets = snippetRegex.findAll(html).toList()

        for (i in links.indices) {
            if (results.size >= MAX_RESULTS) break
            val linkTag = linkTags.getOrNull(i)?.value ?: continue
            val href = hrefRegex.find(linkTag)?.groupValues?.get(1) ?: continue
            val title = links[i].groupValues[1].stripHtml().trim()
            val snippet = snippets.getOrNull(i)?.groupValues?.get(1)?.stripHtml()?.trim() ?: ""

            // Extract the actual URL from DDG redirect: //duckduckgo.com/l/?uddg=ENCODED_URL
            val url = extractUrlFromRedirect(href)

            if (url.isNotBlank() && title.isNotBlank()) {
                results.add(
                    mapOf(
                        "title" to title,
                        "url" to url,
                        "snippet" to snippet,
                    ),
                )
            }
        }

        return results
    }

    private fun extractUrlFromRedirect(href: String): String {
        val uddgParam = uddgRegex.find(href)?.groupValues?.get(1)
        if (uddgParam != null) {
            return decodeURLComponent(uddgParam)
        }
        // Not a redirect, use as-is (add https: if protocol-relative)
        return if (href.startsWith("//")) "https:$href" else href
    }

    private fun decodeURLComponent(encoded: String): String = buildString {
        var i = 0
        while (i < encoded.length) {
            when {
                encoded[i] == '%' && i + 2 < encoded.length -> {
                    val hex = encoded.substring(i + 1, i + 3)
                    val byte = hex.toIntOrNull(16)
                    if (byte != null) {
                        append(byte.toChar())
                        i += 3
                    } else {
                        append(encoded[i])
                        i++
                    }
                }

                encoded[i] == '+' -> {
                    append(' ')
                    i++
                }

                else -> {
                    append(encoded[i])
                    i++
                }
            }
        }
    }

    private fun String.stripHtml(): String = replace(htmlTagRegex, "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#x27;", "'")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")

    private fun String.encodeURLQueryComponent(): String = buildString {
        for (c in this@encodeURLQueryComponent) {
            when {
                c.isLetterOrDigit() || c in "-_.~" -> append(c)

                c == ' ' -> append('+')

                else -> {
                    val bytes = c.toString().encodeToByteArray()
                    for (b in bytes) {
                        append('%')
                        append(
                            b.toInt().and(0xFF).toString(16).uppercase().padStart(2, '0'),
                        )
                    }
                }
            }
        }
    }

    val toolInfo = ToolInfo(
        id = "web_search",
        name = "Web Search",
        description = "Search the web for current information",
        nameRes = Res.string.tool_web_search_name,
        descriptionRes = Res.string.tool_web_search_description,
    )
}
