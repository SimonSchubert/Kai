package com.inspiredandroid.kai.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalModelImportTest {

    @Test
    fun isLitertlmExtension_acceptsCaseInsensitive() {
        assertTrue(isLitertlmExtension("model.litertlm"))
        assertTrue(isLitertlmExtension("model.LITERTLM"))
        assertTrue(isLitertlmExtension("model.LiteRtLm"))
        assertFalse(isLitertlmExtension("model.gguf"))
        assertFalse(isLitertlmExtension("model"))
        assertFalse(isLitertlmExtension("litertlm"))
    }

    @Test
    fun sanitizeImportFileName_stripsPathAndUnsafeChars() {
        assertEquals("gemma-4-E2B-it.litertlm", sanitizeImportFileName("gemma-4-E2B-it.litertlm"))
        assertEquals("model_name.litertlm", sanitizeImportFileName("model name.litertlm"))
        assertEquals("foo.litertlm", sanitizeImportFileName("/path/to/foo.litertlm"))
        assertEquals("foo.litertlm", sanitizeImportFileName("C:\\Users\\x\\foo.litertlm"))
        assertEquals("model.litertlm", sanitizeImportFileName(".."))
        assertEquals("model.litertlm", sanitizeImportFileName(""))
        assertEquals("noext.litertlm", sanitizeImportFileName("noext"))
    }

    @Test
    fun resolveImportTarget_matchesCatalogByFileName() {
        val catalogFile = MODEL_CATALOG.first().fileName
        val target = resolveImportTarget(catalogFile)
        assertNotNull(target)
        assertTrue(target.matchedCatalog)
        assertEquals(MODEL_CATALOG.first().id, target.modelId)
        assertEquals(MODEL_CATALOG.first().id, target.relativeDir)
        assertEquals(MODEL_CATALOG.first().fileName, target.fileName)
    }

    @Test
    fun resolveImportTarget_customGoesToImports() {
        val target = resolveImportTarget("MyCoolModel.litertlm")
        assertNotNull(target)
        assertFalse(target.matchedCatalog)
        assertEquals(IMPORTS_DIR, target.relativeDir)
        assertEquals("MyCoolModel.litertlm", target.fileName)
        assertEquals("${CUSTOM_MODEL_ID_PREFIX}MyCoolModel", target.modelId)
        assertEquals("MyCoolModel", target.displayName)
    }

    @Test
    fun resolveImportTarget_avoidsNameCollisions() {
        val existing = setOf("MyCoolModel.litertlm", "MyCoolModel_2.litertlm")
        val target = resolveImportTarget("MyCoolModel.litertlm", existing)
        assertNotNull(target)
        assertEquals("MyCoolModel_3.litertlm", target.fileName)
        assertEquals("${CUSTOM_MODEL_ID_PREFIX}MyCoolModel_3", target.modelId)
    }

    @Test
    fun resolveImportTarget_rejectsNonLitertlm() {
        assertNull(resolveImportTarget("model.gguf"))
        assertNull(resolveImportTarget("model.task"))
    }

    @Test
    fun customLocalModel_synthesizesMetadata() {
        val model = customLocalModel("foo.litertlm", sizeBytes = 2L * 1024 * 1024 * 1024)
        assertEquals("${CUSTOM_MODEL_ID_PREFIX}foo", model.id)
        assertEquals("foo", model.displayName)
        assertEquals("foo.litertlm", model.fileName)
        assertEquals(CUSTOM_DEFAULT_CONTEXT_TOKENS, model.defaultContextTokens)
        assertEquals(CUSTOM_MAX_CONTEXT_TOKENS, model.maxContextTokens)
        assertTrue(model.gpuMemoryMb >= 300)
        assertTrue(model.downloadUrl.isEmpty())
    }

    @Test
    fun isCustomModelId_detectsPrefix() {
        assertTrue(isCustomModelId("custom-foo"))
        assertFalse(isCustomModelId("gemma-4-e2b-it"))
    }

    @Test
    fun findCatalogModelByFileName_isCaseInsensitive() {
        val expected = MODEL_CATALOG.first()
        val found = findCatalogModelByFileName(expected.fileName.uppercase())
        assertEquals(expected.id, found?.id)
    }
}
