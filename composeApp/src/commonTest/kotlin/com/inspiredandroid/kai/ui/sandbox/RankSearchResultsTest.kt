package com.inspiredandroid.kai.ui.sandbox

import kotlin.test.Test
import kotlin.test.assertEquals

class RankSearchResultsTest {

    private fun pkg(name: String, description: String? = null) = PackageEntry(name = name, version = "1.0.0-r0", description = description)

    @Test
    fun `name prefix matches rank above description-only matches`() {
        val input = listOf(
            pkg("abseil-cpp-dev", "Common libraries with fast helpers"),
            pkg("afl++", "Fuzzer relying on genetic algorithms"),
            pkg("android-tools", "Android platform tools"),
            pkg("fast_float", "Fast from_chars for floats"),
            pkg("fastbase64", "Unicode validation"),
            pkg("fastfetch", "neofetch-like system info"),
            pkg("libfastjson", "A fast JSON library"),
        )

        val ranked = rankSearchResults(input, "fast").map { it.name }

        assertEquals(
            listOf(
                "fast_float",
                "fastbase64",
                "fastfetch",
                "libfastjson",
                "abseil-cpp-dev",
                "afl++",
                "android-tools",
            ),
            ranked,
        )
    }

    @Test
    fun `exact name match ranks first`() {
        val input = listOf(
            pkg("fastfetch-bash-completion"),
            pkg("fastfetch"),
            pkg("py3-fastfetch"),
        )

        val ranked = rankSearchResults(input, "fastfetch").map { it.name }

        assertEquals(
            listOf("fastfetch", "fastfetch-bash-completion", "py3-fastfetch"),
            ranked,
        )
    }

    @Test
    fun `segment prefix ranks above plain contains`() {
        val input = listOf(
            pkg("breakfast"), // contains "fast" mid-word
            pkg("py3-fastapi"), // segment prefix after '-'
            pkg("lib_fastcgi"), // segment prefix after '_'
        )

        val ranked = rankSearchResults(input, "fast").map { it.name }

        assertEquals(
            listOf("lib_fastcgi", "py3-fastapi", "breakfast"),
            ranked,
        )
    }

    @Test
    fun `blank query leaves order unchanged`() {
        val input = listOf(pkg("z"), pkg("a"), pkg("m"))
        assertEquals(input, rankSearchResults(input, "  "))
    }

    @Test
    fun `ranking is case insensitive`() {
        val input = listOf(
            pkg("FastFetch"),
            pkg("abseil", "Very FAST helpers"),
        )

        val ranked = rankSearchResults(input, "FAST").map { it.name }

        assertEquals(listOf("FastFetch", "abseil"), ranked)
    }
}
