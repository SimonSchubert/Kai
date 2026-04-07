package com.inspiredandroid.kai.mcp

import androidx.compose.runtime.Immutable

@Immutable
data class PopularMcpServer(
    val name: String,
    val url: String,
    val description: String,
)

val popularMcpServers = listOf(
    PopularMcpServer(
        name = "Context7",
        url = "https://mcp.context7.com/mcp",
        description = "Up-to-date library and framework docs",
    ),
    PopularMcpServer(
        name = "CoinGecko",
        url = "https://mcp.api.coingecko.com/mcp",
        description = "Real-time crypto prices and market data",
    ),
    PopularMcpServer(
        name = "Manifold Markets",
        url = "https://api.manifold.markets/v0/mcp",
        description = "Prediction market data and odds",
    ),
    PopularMcpServer(
        name = "Fetch",
        url = "https://remote.mcpservers.org/fetch/mcp",
        description = "Fetch web content and convert HTML to markdown",
    ),
    PopularMcpServer(
        name = "DeepWiki",
        url = "https://mcp.deepwiki.com/mcp",
        description = "AI-powered docs for any GitHub repo",
    ),
    PopularMcpServer(
        name = "Sequential Thinking",
        url = "https://remote.mcpservers.org/sequentialthinking/mcp",
        description = "Structured step-by-step problem-solving",
    ),
    PopularMcpServer(
        name = "Find-A-Domain",
        url = "https://api.findadomain.dev/mcp",
        description = "Domain availability across 1,444+ TLDs",
    ),
    PopularMcpServer(
        name = "SubwayInfo NYC",
        url = "https://subwayinfo.nyc/mcp",
        description = "Real-time NYC transit info",
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
)
