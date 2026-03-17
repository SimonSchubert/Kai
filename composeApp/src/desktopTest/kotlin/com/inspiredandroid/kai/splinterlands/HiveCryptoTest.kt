package com.inspiredandroid.kai.splinterlands

import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.jce.ECNamedCurveTable
import java.math.BigInteger
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for Hive ECDSA signing with recovery ID verification.
 *
 * Uses WIF 5Kc1bduGE6QCwBWeZbG99NM7odT8y9MY9TdCnbDLcJhTGQPxnTE
 * Pub key (compressed): 023d2e4c41caf89bcc518ee4433a6dda4e6aa49b2b2f63d9056f1a6f23f8f2476a
 */
class HiveCryptoTest {

    private val testWif = "5Kc1bduGE6QCwBWeZbG99NM7odT8y9MY9TdCnbDLcJhTGQPxnTE"
    private val expectedPubKeyHex = "023d2e4c41caf89bcc518ee4433a6dda4e6aa49b2b2f63d9056f1a6f23f8f2476a"

    private val curveSpec = ECNamedCurveTable.getParameterSpec("secp256k1")
    private val ecDomain = ECDomainParameters(curveSpec.curve, curveSpec.g, curveSpec.n, curveSpec.h)

    @Test
    fun decodeWifProducesCorrectPrivateKey() {
        val expected = "eb84dc7ce42d0119215f2ec7dae8445a7b4253ed3f69017424a225eb547d10d8"
        assertEquals(expected, decodeWif(testWif).toHexString())
    }

    @Test
    fun loginSignatureRecoveryMatchesPublicKey() {
        val sigHex = signMessage("schalikan1773664000000", testWif)
        val hash = sha256("schalikan1773664000000".toByteArray(Charsets.UTF_8))
        assertRecoveryMatchesPubKey(sigHex, hash, "login")
    }

    @Test
    fun transactionSignatureRecoveryMatchesPublicKey() {
        // Simulate transaction signing: single SHA-256 of (chain_id + tx_bytes)
        // tx_bytes does NOT include signatures (Hive casts to transaction& which excludes them)
        val txBytesHex = "384b6fce49d75df8b7690112000109736368616c696b616e0d736d5f66696e645f6d617463684b7b226d617463685f74797065223a2257696c642052616e6b6564222c22617070223a2273706c696e7465726c616e64732f302e372e313736222c226e223a22746573744e6f6e636531227d00"
        val chainId = hexToBytes("beeab0de" + "0".repeat(56))
        val txBytes = hexToBytes(txBytesHex)
        val digest = sha256(chainId + txBytes)

        val privKeyBytes = decodeWif(testWif)
        val sigHex = ecdsaSign(digest, privKeyBytes)
        assertRecoveryMatchesPubKey(sigHex, digest, "transaction")
    }

    @Test
    fun recoveryWorksForManyDifferentHashes() {
        // Test with 20 different hashes to catch intermittent recovery ID bugs
        val privKeyBytes = decodeWif(testWif)
        var data = "seed".toByteArray()
        repeat(20) { i ->
            data = sha256(data)
            val sigHex = ecdsaSign(data, privKeyBytes)
            assertRecoveryMatchesPubKey(sigHex, data, "hash-$i")
        }
    }

    private fun assertRecoveryMatchesPubKey(sigHex: String, hash: ByteArray, label: String) {
        val sigBytes = hexToBytes(sigHex)
        val header = sigBytes[0].toInt() and 0xFF
        val recId = header - 27 - 4
        val r = BigInteger(1, sigBytes.copyOfRange(1, 33))
        val s = BigInteger(1, sigBytes.copyOfRange(33, 65))

        assert(recId in 0..3) { "$label: invalid recId $recId (header=0x${header.toString(16)})" }

        val recovered = recoverPublicKey(hash, r, s, recId)
        assertNotNull(recovered, "$label: recoverPublicKey returned null for recId=$recId")

        val recoveredNorm = recovered.normalize()
        val recoveredCompressed = ByteArray(33)
        recoveredCompressed[0] = if (recoveredNorm.yCoord.toBigInteger().testBit(0)) 0x03 else 0x02
        bigIntTo32Bytes(recoveredNorm.xCoord.toBigInteger()).copyInto(recoveredCompressed, 1)

        assertEquals(
            expectedPubKeyHex,
            recoveredCompressed.toHexString(),
            "$label: recovered public key doesn't match expected (recId=$recId, r=${r.toString(16).take(16)}...)",
        )
    }

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)
}
