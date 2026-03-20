package com.inspiredandroid.kai.splinterlands

import com.inspiredandroid.kai.httpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.signers.HMacDSAKCalculator
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.math.ec.ECAlgorithms
import org.bouncycastle.math.ec.ECPoint
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.time.Instant

private val curveSpec = ECNamedCurveTable.getParameterSpec("secp256k1")
private val ecDomain = ECDomainParameters(curveSpec.curve, curveSpec.g, curveSpec.n, curveSpec.h)

private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
private const val HIVE_CHAIN_ID = "beeab0de00000000000000000000000000000000000000000000000000000000"

private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

internal fun decodeWif(wif: String): ByteArray {
    val raw = base58Decode(wif)
    return raw.copyOfRange(1, 33)
}

private fun base58Decode(input: String): ByteArray {
    var num = BigInteger.ZERO
    for (c in input) {
        val digit = BASE58_ALPHABET.indexOf(c)
        if (digit < 0) throw IllegalArgumentException("Invalid base58 character: $c")
        num = num.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(digit.toLong()))
    }
    val bytes = num.toByteArray()
    val leadingZeros = input.takeWhile { it == '1' }.length
    val stripped = if (bytes.isNotEmpty() && bytes[0].toInt() == 0) bytes.copyOfRange(1, bytes.size) else bytes
    return ByteArray(leadingZeros) + stripped
}

internal fun bigIntTo32Bytes(n: BigInteger): ByteArray {
    val bytes = n.toByteArray()
    return when {
        bytes.size == 32 -> bytes
        bytes.size > 32 -> bytes.copyOfRange(bytes.size - 32, bytes.size)
        else -> ByteArray(32 - bytes.size) + bytes
    }
}

internal fun hexToBytes(hex: String): ByteArray {
    val data = ByteArray(hex.length / 2)
    for (i in data.indices) {
        data[i] = ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte()
    }
    return data
}

internal fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

private fun isCanonical(r: ByteArray, s: ByteArray): Boolean = (r[0].toInt() and 0x80) == 0 &&
    !(r[0].toInt() == 0 && (r[1].toInt() and 0x80) == 0) &&
    (s[0].toInt() and 0x80) == 0 &&
    !(s[0].toInt() == 0 && (s[1].toInt() and 0x80) == 0)

internal fun ecdsaSign(hash: ByteArray, privKeyBytes: ByteArray): String {
    val privKey = BigInteger(1, privKeyBytes)
    val halfN = ecDomain.n.shiftRight(1)

    var nonce = 0
    while (true) {
        val kCalc = HMacDSAKCalculator(SHA256Digest())
        val hashForK = if (nonce == 0) hash else sha256(hash + ByteArray(4) { ((nonce shr (it * 8)) and 0xFF).toByte() })
        kCalc.init(ecDomain.n, privKey, hashForK)
        val k = kCalc.nextK()

        val rPoint = ecDomain.g.multiply(k).normalize()
        val r = rPoint.xCoord.toBigInteger().mod(ecDomain.n)
        var s = k.modInverse(ecDomain.n).multiply(BigInteger(1, hash).add(privKey.multiply(r))).mod(ecDomain.n)

        val sNegated = s > halfN
        if (sNegated) s = ecDomain.n.subtract(s)

        val rBytes = bigIntTo32Bytes(r)
        val sBytes = bigIntTo32Bytes(s)

        if (!isCanonical(rBytes, sBytes)) {
            nonce++
            if (nonce > 100) throw RuntimeException("Could not find canonical signature")
            continue
        }

        var recId = if (rPoint.yCoord.toBigInteger().testBit(0)) 1 else 0
        if (sNegated) recId = recId xor 1

        val sig = ByteArray(65)
        sig[0] = (27 + recId + 4).toByte()
        rBytes.copyInto(sig, 1)
        sBytes.copyInto(sig, 33)
        return sig.toHexString()
    }
}

internal fun recoverPublicKey(hash: ByteArray, r: BigInteger, s: BigInteger, recId: Int): ECPoint? {
    val n = ecDomain.n
    val curve = ecDomain.curve
    val x = r.add(BigInteger.valueOf((recId / 2).toLong()).multiply(n))
    val prime = curve.field.characteristic
    if (x >= prime) return null
    val rPoint = decompressPoint(x, recId and 1 == 1)
    if (!rPoint.multiply(n).isInfinity) return null
    val e = BigInteger(1, hash)
    val eInv = BigInteger.ZERO.subtract(e).mod(n)
    val rInv = r.modInverse(n)
    return ECAlgorithms.sumOfTwoMultiplies(ecDomain.g, eInv.multiply(rInv).mod(n), rPoint, s.multiply(rInv).mod(n))
}

private fun decompressPoint(x: BigInteger, yOdd: Boolean): ECPoint {
    val encoded = ByteArray(33)
    encoded[0] = if (yOdd) 0x03 else 0x02
    bigIntTo32Bytes(x).copyInto(encoded, 1)
    return ecDomain.curve.decodePoint(encoded)
}

actual fun signMessage(message: String, postingKeyWif: String): String {
    val keyBytes = decodeWif(postingKeyWif)
    val msgHash = sha256(message.toByteArray(Charsets.UTF_8))
    return ecdsaSign(msgHash, keyBytes)
}

actual suspend fun buildSignedCustomJson(
    username: String,
    postingKeyWif: String,
    opId: String,
    jsonPayload: String,
): String {
    val client = httpClient()
    try {
        // Fetch dynamic global properties for ref_block
        val propsResp = client.post("https://api.hive.blog") {
            header("Content-Type", "application/json")
            setBody("""{"jsonrpc":"2.0","method":"condenser_api.get_dynamic_global_properties","params":[],"id":1}""")
        }
        val propsJson = Json.parseToJsonElement(propsResp.bodyAsText()).jsonObject
        val result = propsJson["result"]!!.jsonObject
        val headBlockNumber = result["head_block_number"]!!.jsonPrimitive.content.toLong()
        val headBlockId = result["head_block_id"]!!.jsonPrimitive.content

        val refBlockNum = (headBlockNumber and 0xFFFF).toInt()
        val refBlockPrefixBytes = hexToBytes(headBlockId.substring(8, 16))
        val refBlockPrefix = (refBlockPrefixBytes[0].toInt() and 0xFF) or
            ((refBlockPrefixBytes[1].toInt() and 0xFF) shl 8) or
            ((refBlockPrefixBytes[2].toInt() and 0xFF) shl 16) or
            ((refBlockPrefixBytes[3].toInt() and 0xFF) shl 24)

        val expiration = Instant.now().plusSeconds(60)
        val expirationStr = expiration.toString().substringBefore(".").substringBefore("Z")

        // Serialize transaction bytes
        val txBytes = serializeTransaction(refBlockNum, refBlockPrefix, expiration.epochSecond.toInt(), username, opId, jsonPayload)

        // Single SHA-256: Hive/Graphene signing protocol (NOT double like Bitcoin)
        val digest = sha256(hexToBytes(HIVE_CHAIN_ID) + txBytes)
        val sigHex = ecdsaSign(digest, decodeWif(postingKeyWif))

        // Build the custom_json operation JSON
        val escapedJson = jsonPayload.replace("\\", "\\\\").replace("\"", "\\\"")
        val operation = """["custom_json",{"required_auths":[],"required_posting_auths":["$username"],"id":"$opId","json":"$escapedJson"}]"""

        val refBlockPrefixUnsigned = refBlockPrefix.toLong() and 0xFFFFFFFFL
        return """{"ref_block_num":$refBlockNum,"ref_block_prefix":$refBlockPrefixUnsigned,"expiration":"$expirationStr","operations":[$operation],"extensions":[],"signatures":["$sigHex"]}"""
    } finally {
        client.close()
    }
}

private fun serializeTransaction(
    refBlockNum: Int,
    refBlockPrefix: Int,
    expirationSecs: Int,
    username: String,
    opId: String,
    jsonPayload: String,
): ByteArray {
    val buf = ByteArrayOutputStream()

    // ref_block_num: uint16 LE
    buf.write(refBlockNum and 0xFF)
    buf.write((refBlockNum shr 8) and 0xFF)

    // ref_block_prefix: uint32 LE
    buf.write(refBlockPrefix and 0xFF)
    buf.write((refBlockPrefix shr 8) and 0xFF)
    buf.write((refBlockPrefix shr 16) and 0xFF)
    buf.write((refBlockPrefix shr 24) and 0xFF)

    // expiration: uint32 LE
    buf.write(expirationSecs and 0xFF)
    buf.write((expirationSecs shr 8) and 0xFF)
    buf.write((expirationSecs shr 16) and 0xFF)
    buf.write((expirationSecs shr 24) and 0xFF)

    // operations count: varint(1)
    writeVarint(buf, 1)
    // custom_json op type = 18
    writeVarint(buf, 18)
    // required_auths: empty
    writeVarint(buf, 0)
    // required_posting_auths: [username]
    writeVarint(buf, 1)
    writeVarString(buf, username)
    // id
    writeVarString(buf, opId)
    // json payload
    writeVarString(buf, jsonPayload)
    // extensions: empty
    writeVarint(buf, 0)

    return buf.toByteArray()
}

private fun writeVarint(buf: ByteArrayOutputStream, value: Int) {
    var v = value
    while (v >= 0x80) {
        buf.write((v and 0x7F) or 0x80)
        v = v shr 7
    }
    buf.write(v and 0x7F)
}

private fun writeVarString(buf: ByteArrayOutputStream, s: String) {
    val bytes = s.toByteArray(Charsets.UTF_8)
    writeVarint(buf, bytes.size)
    buf.write(bytes)
}
