package com.inspiredandroid.kai.mcp

import androidx.compose.runtime.Immutable

@Immutable
data class PopularMcpServer(
    val name: String,
    val url: String,
    val description: String,
)

/**
 * Curated free MCP endpoints that require no API key.
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
