package com.inspiredandroid.kai.tools

/**
 * True if the URL points at a host on the local network — the traffic Android's local network
 * protection gates. Loopback stays false: it never leaves the device and isn't gated. Public DNS
 * names that happen to resolve to LAN addresses can't be detected without resolving them; those
 * still fail with a plain connection error.
 */
fun isLocalNetworkUrl(url: String): Boolean {
    if (url.isBlank()) return false
    val afterScheme = url.substringAfter("://")
    val authority = afterScheme.substringBefore("/").substringAfterLast("@").lowercase()
    val host = if (authority.startsWith("[")) {
        authority.substringAfter("[").substringBefore("]")
    } else {
        authority.substringBefore(":")
    }
    if (host.isEmpty()) return false

    // Loopback isn't gated by local network protection.
    if (host == "localhost" || host == "::1" || host.startsWith("127.")) return false

    // IPv6 link-local and unique-local addresses.
    if (host.contains(":")) {
        return host.startsWith("fe8") || host.startsWith("fe9") ||
            host.startsWith("fea") || host.startsWith("feb") ||
            host.startsWith("fc") || host.startsWith("fd")
    }

    // Private and link-local IPv4 ranges.
    val octets = host.split(".").mapNotNull { it.toIntOrNull() }
    if (octets.size == 4 && octets.all { it in 0..255 }) {
        val first = octets[0]
        val second = octets[1]
        return first == 10 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168) ||
            (first == 169 && second == 254)
    }

    // mDNS names and bare hostnames resolve on the local network.
    return host.endsWith(".local") || !host.contains(".")
}
