package com.inspiredandroid.kai.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ModelCatalogTest {

    @Test
    fun `minimax-m2_7 has 204800 context window not 1M`() {
        val info = ModelCatalog.lookup("minimax-m2.7")
        assertNotNull(info)
        assertEquals("MiniMax M2.7", info.displayName)
        assertEquals(204_800L, info.contextWindow)
    }
}
