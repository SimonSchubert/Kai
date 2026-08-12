package com.inspiredandroid.kai.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LocalModelCatalogTest {

    private val hfResolve =
        Regex(
            """^https://huggingface\.co/litert-community/([^/]+)/resolve/([0-9a-f]{40})/(.+)$""",
        )
    private val hex64 = Regex("^[0-9a-f]{64}$")

    @Test
    fun catalogPinsImmutableHuggingFaceCommits() {
        assertTrue(MODEL_CATALOG.isNotEmpty())
        val ids = MODEL_CATALOG.map { it.id }
        assertEquals(ids.distinct(), ids)

        for (model in MODEL_CATALOG) {
            assertTrue(model.downloadUrl.isNotBlank(), model.id)
            assertTrue(
                "/resolve/main/" !in model.downloadUrl,
                "${model.id} must not pin a moving branch",
            )
            val match = hfResolve.matchEntire(model.downloadUrl)
            assertTrue(match != null, "${model.id} URL must be litert-community resolve/<commit>/<file>")
            assertEquals(model.fileName, match?.groupValues?.get(3), model.id)
            assertTrue(hex64.matches(model.sha256), "${model.id} sha256 must be 64 lowercase hex")
            assertTrue(model.sizeBytes > 0L, model.id)
        }
    }

    @Test
    fun recommendedModelIsInCatalog() {
        assertNotEquals(0, MODEL_CATALOG.count { it.isRecommended })
    }
}
