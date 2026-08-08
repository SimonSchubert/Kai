package com.inspiredandroid.kai.mcp

import androidx.compose.runtime.Immutable

@Immutable
data class PopularMcpServer(
    val name: String,
    val url: String,
    val description: String,
    /**
     * Default request headers applied on one-tap add (no-auth servers).
     * Existing saved configs only receive keys they do not already define (never overwritten).
     */
    val headers: Map<String, String> = emptyMap(),
    /**
     * When true, selecting this popular entry prefills the add form and shows an API key field
     * instead of one-tap adding.
     */
    val requiresAuth: Boolean = false,
)

/**
 * Curated free MCP endpoints. Most require no API key (one-tap add).
 * Jina search tools need a free API key from jina.ai — selecting it prefills the form with an auth field.
 * Verified for Streamable HTTP initialize + tools/list (and sample tools/call where noted).
 */
val popularMcpServers = listOf(
    PopularMcpServer(
        name = "Context7",
        url = "https://mcp.context7.com/mcp",
        description = "Up-to-date library and framework docs",
    ),
    PopularMcpServer(
        name = "MDN",
        url = "https://mcp.mdn.mozilla.net",
        description = "Web docs, search, and browser compatibility",
    ),
    PopularMcpServer(
        name = "DeepWiki",
        url = "https://mcp.deepwiki.com/mcp",
        description = "AI-powered docs for any GitHub repo",
    ),
    PopularMcpServer(
        name = "Parallel Search",
        url = "https://search.parallel.ai/mcp",
        description = "Realtime web search and content extraction",
    ),
    PopularMcpServer(
        name = "Yahoo Finance",
        url = "https://gateway.mcpservers.org/yahoo-finance/mcp",
        description = "Stock data, market news, and price history",
    ),
    PopularMcpServer(
        name = "CoinGecko",
        url = "https://mcp.api.coingecko.com/mcp",
        description = "Real-time crypto prices and market data",
    ),
    PopularMcpServer(
        name = "Jina AI",
        url = "https://mcp.jina.ai/v1",
        description = "Convert URLs to markdown, web search, image search",
        requiresAuth = true,
    ),
    PopularMcpServer(
        name = "Open-Meteo Weather",
        url = "https://mcp.open-mcp.org/api/server/open-weather@latest/mcp",
        description = "Global weather forecasts and air quality",
    ),
    PopularMcpServer(
        name = "Kiwi.com",
        url = "https://mcp.kiwi.com",
        description = "Flexible flight search across airlines",
    ),
    PopularMcpServer(
        name = "Malwarebytes",
        url = "https://scamguard.malwarebytes.com/claude/mcp",
        description = "Check links, phones, and emails for scams",
    ),
    PopularMcpServer(
        name = "tldraw",
        url = "https://tldraw-mcp-app.tldraw.workers.dev/mcp",
        description = "Diagrams and whiteboards",
    ),
    PopularMcpServer(
        name = "Find-A-Domain",
        url = "https://api.findadomain.dev/mcp",
        description = "Domain availability across 1,444+ TLDs",
    ),
    PopularMcpServer(
        name = "Manifold Markets",
        url = "https://api.manifold.markets/v0/mcp",
        description = "Prediction market data and odds",
    ),
    PopularMcpServer(
        name = "SubwayInfo NYC",
        url = "https://subwayinfo.nyc/mcp",
        description = "Real-time NYC transit info",
    ),
)

/** Merge [defaults] into [existing], keeping any header key the user already set (case-insensitive). */
internal fun mergeMissingHeaders(
    existing: Map<String, String>,
    defaults: Map<String, String>,
): Map<String, String> {
    if (defaults.isEmpty()) return existing
    val existingKeysLower = existing.keys.map { it.lowercase() }.toSet()
    val toAdd = defaults.filterKeys { it.lowercase() !in existingKeysLower }
    return if (toAdd.isEmpty()) existing else existing + toAdd
}

/**
 * True when [savedUrl] is the same popular endpoint as [popularUrl].
 * Jina's `/v1` and `/sse` paths are aliases of the same host.
 */
internal fun matchesPopularMcpUrl(savedUrl: String, popularUrl: String): Boolean {
    val saved = normalizeMcpUrl(savedUrl)
    val popular = normalizeMcpUrl(popularUrl)
    if (saved == popular) return true
    val savedHost = mcpHost(saved)
    val popularHost = mcpHost(popular)
    return savedHost != null &&
        savedHost == popularHost &&
        savedHost == "mcp.jina.ai"
}

/**
 * Apply popular-server default headers to saved configs without overwriting user headers.
 * Returns the same list instance when nothing changes.
 */
internal fun applyPopularDefaultHeaders(
    servers: List<McpServerConfig>,
    popular: List<PopularMcpServer> = popularMcpServers,
): List<McpServerConfig> {
    if (servers.isEmpty()) return servers
    var changed = false
    val updated = servers.map { server ->
        val defaults = popular
            .firstOrNull { matchesPopularMcpUrl(server.url, it.url) && it.headers.isNotEmpty() }
            ?.headers
            ?: return@map server
        val merged = mergeMissingHeaders(server.headers, defaults)
        if (merged === server.headers || merged == server.headers) {
            server
        } else {
            changed = true
            server.copy(headers = merged)
        }
    }
    return if (changed) updated else servers
}

/** Normalize a pasted API key into an Authorization header value. */
internal fun authorizationHeaderValue(apiKey: String): String {
    val raw = apiKey.trim()
    if (raw.isEmpty()) return raw
    return if (raw.startsWith("Bearer ", ignoreCase = true)) raw else "Bearer $raw"
}

private fun normalizeMcpUrl(url: String): String = url.trim().trimEnd('/').lowercase()

private fun mcpHost(normalizedUrl: String): String? {
    val withoutScheme = when {
        normalizedUrl.startsWith("https://") -> normalizedUrl.removePrefix("https://")
        normalizedUrl.startsWith("http://") -> normalizedUrl.removePrefix("http://")
        else -> normalizedUrl
    }
    val host = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    return host.ifBlank { null }
}
