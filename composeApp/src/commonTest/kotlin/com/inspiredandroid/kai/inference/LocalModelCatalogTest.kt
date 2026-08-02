package com.inspiredandroid.kai.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalModelCatalogTest {

    @Test
    fun `every catalog model pins an immutable revision`() {
        for (model in MODEL_CATALOG) {
            assertFalse(
                model.downloadUrl.contains("/resolve/main/"),
                "${model.id} downloads from a mutable branch; pin it to a commit hash instead",
            )
            assertTrue(model.downloadUrl.startsWith("https://"), "${model.id} must download over HTTPS")
            // The pinned revision sits between /resolve/ and the file name.
            val revision = model.downloadUrl.substringAfter("/resolve/", "").substringBefore('/')
            assertEquals(40, revision.length, "${model.id} revision is not a full commit hash: $revision")
            assertTrue(revision.all { it in "0123456789abcdef" }, "${model.id} revision is not lowercase hex: $revision")
        }
    }

    @Test
    fun `every catalog model pins a sha256 digest`() {
        for (model in MODEL_CATALOG) {
            assertEquals(64, model.sha256.length, "${model.id} digest is not a 64-char SHA-256: ${model.sha256}")
            assertTrue(model.sha256.all { it in "0123456789abcdef" }, "${model.id} digest is not lowercase hex: ${model.sha256}")
        }
    }

    @Test
    fun `catalog ids and file names are unique`() {
        assertEquals(MODEL_CATALOG.size, MODEL_CATALOG.map { it.id }.toSet().size)
        assertEquals(MODEL_CATALOG.size, MODEL_CATALOG.map { it.fileName }.toSet().size)
        // A shared digest across entries would mean a copy-paste slip.
        assertEquals(MODEL_CATALOG.size, MODEL_CATALOG.map { it.sha256 }.toSet().size)
    }

    @Test
    fun `download url ends with the model file name`() {
        for (model in MODEL_CATALOG) {
            assertTrue(model.downloadUrl.endsWith("/${model.fileName}"), "${model.id} url does not serve ${model.fileName}")
        }
    }

    @Test
    fun `findCatalogModelById resolves known ids only`() {
        assertNotNull(findCatalogModelById("qwen3-0.6b"))
        assertEquals("qwen3-0.6b", findCatalogModelById("qwen3-0.6b")?.id)
        assertNull(findCatalogModelById("custom-foo"))
        assertNull(findCatalogModelById(""))
    }

    @Test
    fun `digestMarkerFileName sits beside the model file`() {
        assertEquals("Qwen3-0.6B.litertlm.sha256", digestMarkerFileName("Qwen3-0.6B.litertlm"))
        // Must not collide with the imports scan, which matches on a .litertlm extension.
        assertFalse(isLitertlmExtension(digestMarkerFileName("Qwen3-0.6B.litertlm")))
    }

    @Test
    fun `digestMatches accepts equal digests regardless of case and padding`() {
        val expected = "555579ff2f4fd13379abe69c1c3ab5200f7338bc92471557f1d6614a6e5ab0b4"
        assertTrue(digestMatches(expected, expected))
        assertTrue(digestMatches(expected, expected.uppercase()))
        assertTrue(digestMatches(expected, "  $expected\n"))
    }

    @Test
    fun `digestMatches rejects wrong missing or truncated digests`() {
        val expected = "555579ff2f4fd13379abe69c1c3ab5200f7338bc92471557f1d6614a6e5ab0b4"
        assertFalse(digestMatches(expected, null))
        assertFalse(digestMatches(expected, ""))
        assertFalse(digestMatches(expected, expected.dropLast(1)))
        assertFalse(digestMatches(expected, expected.dropLast(1) + "0"))
    }

    @Test
    fun `digestMatches accepts anything when no digest is pinned`() {
        // Imported models carry no digest — the user supplied those bytes directly.
        assertTrue(digestMatches("", null))
        assertTrue(digestMatches("", "whatever"))
        assertTrue(customLocalModel("foo.litertlm", sizeBytes = 1_000_000L).sha256.isEmpty())
    }

    @Test
    fun `an import named like a catalog model takes over that catalog slot`() {
        // This is why user-supplied files need a provenance marker: the import lands under
        // the catalog id, so the pinned digest would otherwise be applied to it.
        val target = resolveImportTarget("Qwen3-0.6B.litertlm")
        assertNotNull(target)
        assertTrue(target.matchedCatalog)
        assertEquals("qwen3-0.6b", target.modelId)
        assertNotNull(findCatalogModelById(target.modelId)?.sha256?.takeIf { it.isNotBlank() })
    }

    @Test
    fun `the user-supplied marker is never mistaken for a digest`() {
        val pinned = findCatalogModelById("qwen3-0.6b")!!.sha256
        assertFalse(digestMatches(pinned, USER_SUPPLIED_MARKER))
        assertEquals(USER_SUPPLIED_MARKER, USER_SUPPLIED_MARKER.trim())
        // Must not look like hex, or a digest comparison could collide with it.
        assertFalse(USER_SUPPLIED_MARKER.all { it in "0123456789abcdef" })
        assertTrue(USER_SUPPLIED_MARKER.length != 64)
    }

    @Test
    fun `toDigestHex encodes bytes as lowercase hex`() {
        assertEquals("", byteArrayOf().toDigestHex())
        assertEquals("00", byteArrayOf(0).toDigestHex())
        assertEquals("0f", byteArrayOf(15).toDigestHex())
        assertEquals("ff", byteArrayOf(-1).toDigestHex())
        assertEquals("007f80ff", byteArrayOf(0, 127, -128, -1).toDigestHex())
    }

    @Test
    fun `toDigestHex output round-trips through digestMatches`() {
        val bytes = ByteArray(32) { (it * 7).toByte() }
        assertTrue(digestMatches(bytes.toDigestHex(), bytes.toDigestHex()))
        assertEquals(64, bytes.toDigestHex().length)
    }
}
