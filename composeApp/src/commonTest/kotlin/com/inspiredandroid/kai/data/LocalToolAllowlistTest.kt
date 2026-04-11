package com.inspiredandroid.kai.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Locks in the contract of [LOCAL_TOOL_ALLOWLIST] — the set of tools exposed to the
 * on-device LiteRT engine. If you add/remove a tool from the allowlist, update this test
 * and [docs/features/on-device-inference.md] at the same time.
 *
 * Renaming a real tool (e.g. `memory_store` → `save_memory`) without updating the
 * allowlist will silently drop the tool from the on-device path. This test fails loudly
 * in that case — the set literal below is the canonical expected list.
 */
class LocalToolAllowlistTest {

    @Test
    fun `allowlist contains exactly the expected tool names`() {
        val expected = setOf(
            "get_local_time",
            "get_location_from_ip",
            "web_search",
            "open_url",
            "memory_store",
            "memory_forget",
            "memory_reinforce",
            "execute_shell_command",
        )
        assertEquals(expected, LOCAL_TOOL_ALLOWLIST)
    }
}
