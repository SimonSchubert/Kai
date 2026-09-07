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
    fun catalogContextDefaultsFitInsideTheirMaximum() {
        // A model whose export tops out at its own default (LFM2.5) has no headroom for a
        // typo to hide in: the settings slider would offer a size the engine refuses.
        for (model in MODEL_CATALOG) {
            assertTrue(
                model.defaultContextTokens in 1..model.maxContextTokens,
                "${model.id} default context ${model.defaultContextTokens} must fit in ${model.maxContextTokens}",
            )
        }
    }

    @Test
    fun catalogFileNamesAreDistinct() {
        // Two entries sharing a file name would collide in the imports/catalog path
        // resolution and each would take over the other's digest marker.
        val fileNames = MODEL_CATALOG.map { it.fileName }
        assertEquals(fileNames.distinct(), fileNames)
    }

    @Test
    fun samplerDefaultsTreatUndeclaredValuesAsNoOpinion() {
        assertEquals(null, localSamplerDefaultsOrNull(temperature = 0f, topK = 0, topP = 0f))
        assertEquals(null, localSamplerDefaultsOrNull(temperature = 0.8f, topK = 0, topP = 0.95f))
        assertEquals(null, localSamplerDefaultsOrNull(temperature = 0f, topK = 40, topP = 0.95f))
        assertEquals(
            LocalSamplerDefaults(temperature = 1.0f, topK = 64, topP = 0.95f),
            localSamplerDefaultsOrNull(temperature = 1.0f, topK = 64, topP = 0.95f),
        )
    }

    @Test
    fun recommendedModelIsInCatalog() {
        assertNotEquals(0, MODEL_CATALOG.count { it.isRecommended })
    }
}
