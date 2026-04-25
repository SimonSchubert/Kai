package com.inspiredandroid.kai.splinterlands

import com.inspiredandroid.kai.data.SharedJson
import com.inspiredandroid.kai.httpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

class SplinterlandsApi {

    private val json = SharedJson
    private val client = httpClient()
    private val apiBase = "https://api.splinterlands.com"
    private val api2Base = "https://api2.splinterlands.com"
    private val battleBase = "https://battle.splinterlands.com"

    @OptIn(ExperimentalTime::class)
    suspend fun login(username: String, postingKeyWif: String): String {
        val ts = Clock.System.now().toEpochMilliseconds()
        val message = username + ts
        val sig = signMessage(message, postingKeyWif)

        val resp = client.get("$api2Base/players/login") {
            parameter("name", username)
            parameter("ts", ts)
            parameter("sig", sig)
            header("Origin", "https://splinterlands.com")
            header("Referer", "https://splinterlands.com/")
        }
        val data = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        return data["jwt_token"]?.jsonPrimitive?.content
            ?: throw RuntimeException("Login failed: ${resp.bodyAsText()}")
    }

    suspend fun getAvatarId(username: String): Int {
        val resp = client.get("$api2Base/players/details") {
            parameter("name", username)
            header("Origin", "https://splinterlands.com")
            header("Referer", "https://splinterlands.com/")
        }
        val data = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        return data["avatar_id"]?.jsonPrimitive?.int ?: 0
    }

    @OptIn(ExperimentalTime::class)
    suspend fun getEnergyPublic(username: String): Int {
        val resp = client.get("$api2Base/players/balances") {
            parameter("username", username)
            header("Origin", "https://splinterlands.com")
            header("Referer", "https://splinterlands.com/")
        }
        val data = json.parseToJsonElement(resp.bodyAsText())
        if (data is JsonArray) {
            for (item in data.jsonArray) {
                val obj = item.jsonObject
                if (obj["token"]?.jsonPrimitive?.content == "ECR") {
                    val balance = obj["balance"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    val lastRewardTime = obj["last_reward_time"]?.jsonPrimitive?.contentOrNull ?: ""
                    if (lastRewardTime.isNotBlank()) {
                        try {
                            val lastMs = kotlin.time.Instant.parse(lastRewardTime).toEpochMilliseconds()
                            val nowMs = Clock.System.now().toEpochMilliseconds()
                            val elapsedSec = (nowMs - lastMs) / 1000.0
                            return (balance + elapsedSec / 3600.0).toInt().coerceAtMost(50)
                        } catch (_: Exception) { /* fall through */ }
                    }
                    return balance.toInt().coerceAtMost(50)
                }
            }
        }
        return 0
    }

    suspend fun getOutstandingMatch(username: String, jwt: String): JsonObject? {
        val resp = client.get("$apiBase/players/outstanding_match") {
            parameter("username", username)
            applyAuth(jwt)
        }
        val text = resp.bodyAsText()
        val trimmed = text.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) return null
        val data = json.parseToJsonElement(trimmed)
        return if (data is JsonObject && data.isNotEmpty()) data.jsonObject else null
    }

    suspend fun pollForMatch(username: String, jwt: String, timeoutMs: Long = 180_000, intervalMs: Long = 3_000): JsonObject {
        val deadline = currentTimeMs() + timeoutMs
        while (currentTimeMs() < deadline) {
            val match = getOutstandingMatch(username, jwt)
            if (match != null && match["opponent"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true) {
                return match
            }
            delay(intervalMs.milliseconds)
        }
        throw RuntimeException("No match found within ${timeoutMs / 1000}s")
    }

    suspend fun getBattleResult(battleId: String, jwt: String, timeoutMs: Long = 120_000, intervalMs: Long = 5_000): JsonObject {
        val deadline = currentTimeMs() + timeoutMs
        while (currentTimeMs() < deadline) {
            val resp = client.get("$apiBase/battle/result") {
                parameter("id", battleId)
                parameter("key", "1")
                applyAuth(jwt)
            }
            val text = resp.bodyAsText()
            val trimmed = text.trim()
            if (trimmed.isEmpty() || !trimmed.startsWith("{")) {
                delay(intervalMs.milliseconds)
                continue
            }
            val data = json.parseToJsonElement(trimmed)
            if (data is JsonObject && data["winner"]?.jsonPrimitive?.content?.isNotBlank() == true) {
                return data.jsonObject
            }
            delay(intervalMs.milliseconds)
        }
        throw RuntimeException("Battle result not available within ${timeoutMs / 1000}s")
    }

    suspend fun getCollection(username: String, jwt: String): JsonArray {
        val resp = client.get("$api2Base/cards/collection/$username") {
            applyAuth(jwt)
        }
        val data = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        return data["cards"]?.jsonArray ?: JsonArray(emptyList())
    }

    suspend fun getCardDetails(): JsonArray {
        val resp = client.get("$api2Base/cards/get_details")
        return json.parseToJsonElement(resp.bodyAsText()).jsonArray
    }

    suspend fun postBattleTx(signedTx: String, jwt: String): JsonObject {
        val resp = client.submitForm(
            url = "$battleBase/battle/battle_tx",
            formParameters = parameters {
                append("signed_tx", signedTx)
            },
        ) {
            header("Authorization", "Bearer $jwt")
            header("Origin", "https://splinterlands.com")
            header("Referer", "https://splinterlands.com/")
        }
        val body = resp.bodyAsText()
        val trimmedBody = body.trim()
        if (trimmedBody.isEmpty() || !trimmedBody.startsWith("{")) {
            return json.parseToJsonElement("""{"success":true}""").jsonObject
        }
        val element = json.parseToJsonElement(trimmedBody)
        return if (element is JsonObject) {
            element.jsonObject
        } else {
            json.parseToJsonElement("""{"success":true}""").jsonObject
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyAuth(jwt: String) {
        header("Authorization", "Bearer $jwt")
        header("Origin", "https://splinterlands.com")
        header("Referer", "https://splinterlands.com/")
    }

    @OptIn(ExperimentalTime::class)
    private fun currentTimeMs(): Long = Clock.System.now().toEpochMilliseconds()
}
