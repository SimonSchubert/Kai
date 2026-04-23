package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.data.SmsDraft
import com.inspiredandroid.kai.data.SmsDraftStore
import com.inspiredandroid.kai.data.SmsMessage
import com.inspiredandroid.kai.data.SmsStore
import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema
import com.inspiredandroid.kai.sms.SmsReader
import com.inspiredandroid.kai.sms.SmsSender
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_check_sms_description
import kai.composeapp.generated.resources.tool_check_sms_name
import kai.composeapp.generated.resources.tool_read_sms_description
import kai.composeapp.generated.resources.tool_read_sms_name
import kai.composeapp.generated.resources.tool_reply_sms_description
import kai.composeapp.generated.resources.tool_reply_sms_name
import kai.composeapp.generated.resources.tool_search_sms_description
import kai.composeapp.generated.resources.tool_search_sms_name
import kai.composeapp.generated.resources.tool_send_sms_description
import kai.composeapp.generated.resources.tool_send_sms_name
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object SmsTools {

    private fun summary(msg: SmsMessage): Map<String, Any?> = mapOf(
        "id" to msg.id,
        "from" to msg.address,
        "date" to msg.date,
        "preview" to msg.preview,
        "is_read" to msg.read,
    )

    fun checkSmsTool(smsStore: SmsStore, smsReader: SmsReader) = object : Tool {
        override val schema = ToolSchema(
            name = "check_sms",
            description = "List recently received SMS messages that the user hasn't been shown yet. " +
                "Returns sender, date, and a short preview. Use read_sms with the `id` to fetch the " +
                "full body. If nothing is pending, call search_sms to find a known message by sender or text.",
            parameters = emptyMap(),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            if (!smsReader.isSupported()) {
                return mapOf("success" to false, "error" to "SMS is not available on this build")
            }
            if (!smsReader.hasPermission()) {
                return mapOf(
                    "success" to false,
                    "error" to "SMS permission not granted. Ask the user to enable SMS in Settings.",
                )
            }
            val pending = smsStore.getPending()
            return mapOf(
                "success" to true,
                "count" to pending.size,
                "messages" to pending.map(::summary),
            )
        }
    }

    fun readSmsTool(smsReader: SmsReader) = object : Tool {
        override val schema = ToolSchema(
            name = "read_sms",
            description = "Read the full body of a specific SMS by its id. Use check_sms or search_sms first to find an id.",
            parameters = mapOf(
                "id" to ParameterSchema(type = "integer", description = "The SMS id returned by check_sms or search_sms", required = true),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            if (!smsReader.isSupported()) {
                return mapOf("success" to false, "error" to "SMS is not available on this build")
            }
            if (!smsReader.hasPermission()) {
                return mapOf("success" to false, "error" to "SMS permission not granted")
            }
            val id = (args["id"] as? Number)?.toLong()
                ?: return mapOf("success" to false, "error" to "Missing id")
            val msg = smsReader.readById(id)
                ?: return mapOf("success" to false, "error" to "No SMS found with id $id")
            return mapOf(
                "success" to true,
                "id" to msg.id,
                "from" to msg.address,
                "date" to msg.date,
                "body" to msg.body,
                "is_read" to msg.read,
            )
        }
    }

    fun searchSmsTool(smsReader: SmsReader) = object : Tool {
        override val schema = ToolSchema(
            name = "search_sms",
            description = "Search SMS messages by sender (phone number) or body text. Returns newest-first, up to 20 matches.",
            parameters = mapOf(
                "query" to ParameterSchema(type = "string", description = "Text to match against sender or body", required = true),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            if (!smsReader.isSupported()) {
                return mapOf("success" to false, "error" to "SMS is not available on this build")
            }
            if (!smsReader.hasPermission()) {
                return mapOf("success" to false, "error" to "SMS permission not granted")
            }
            val query = args["query"]?.toString()?.trim()
            if (query.isNullOrBlank()) {
                return mapOf("success" to false, "error" to "Missing query")
            }
            val matches = smsReader.search(query, SEARCH_LIMIT)
            return mapOf(
                "success" to true,
                "count" to matches.size,
                "messages" to matches.map(::summary),
            )
        }
    }

    val checkSmsToolInfo = ToolInfo(
        id = "check_sms",
        name = "Check SMS",
        description = "List recent unread SMS messages",
        nameRes = Res.string.tool_check_sms_name,
        descriptionRes = Res.string.tool_check_sms_description,
    )

    val readSmsToolInfo = ToolInfo(
        id = "read_sms",
        name = "Read SMS",
        description = "Read the full body of an SMS message",
        nameRes = Res.string.tool_read_sms_name,
        descriptionRes = Res.string.tool_read_sms_description,
    )

    val searchSmsToolInfo = ToolInfo(
        id = "search_sms",
        name = "Search SMS",
        description = "Search SMS messages by sender or text",
        nameRes = Res.string.tool_search_sms_name,
        descriptionRes = Res.string.tool_search_sms_description,
    )

    val sendSmsToolInfo = ToolInfo(
        id = "send_sms",
        name = "Send SMS",
        description = "Draft an SMS for the user to review and send",
        nameRes = Res.string.tool_send_sms_name,
        descriptionRes = Res.string.tool_send_sms_description,
    )

    val replySmsToolInfo = ToolInfo(
        id = "reply_sms",
        name = "Reply SMS",
        description = "Draft a reply to an SMS for the user to review and send",
        nameRes = Res.string.tool_reply_sms_name,
        descriptionRes = Res.string.tool_reply_sms_description,
    )

    val smsReadToolDefinitions = listOf(checkSmsToolInfo, readSmsToolInfo, searchSmsToolInfo)
    val smsSendToolDefinitions = listOf(sendSmsToolInfo, replySmsToolInfo)
    val smsToolDefinitions = smsReadToolDefinitions + smsSendToolDefinitions

    fun getSmsReadTools(smsStore: SmsStore, smsReader: SmsReader): List<Tool> = listOf(
        checkSmsTool(smsStore, smsReader),
        readSmsTool(smsReader),
        searchSmsTool(smsReader),
    )

    @OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
    fun sendSmsTool(smsDraftStore: SmsDraftStore, smsSender: SmsSender) = object : Tool {
        override val schema = ToolSchema(
            name = "send_sms",
            description = "Draft an outgoing SMS. The draft is staged in a banner at the top of the chat " +
                "so the user must explicitly tap Send before anything is actually sent. You cannot bypass this — " +
                "the tool only creates the draft. After calling, tell the user what you drafted and ask them to review.",
            parameters = mapOf(
                "to" to ParameterSchema(type = "string", description = "Recipient phone number", required = true),
                "body" to ParameterSchema(type = "string", description = "Message text", required = true),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            if (!smsSender.hasPermission()) {
                return mapOf(
                    "success" to false,
                    "error" to "SMS send permission not granted. Ask the user to enable 'Send SMS' in Settings.",
                )
            }
            val to = args["to"]?.toString()?.trim()
            val body = args["body"]?.toString()
            if (to.isNullOrBlank()) return mapOf("success" to false, "error" to "Missing to")
            if (body.isNullOrEmpty()) return mapOf("success" to false, "error" to "Missing body")

            val draft = SmsDraft(
                id = Uuid.random().toString(),
                address = to,
                body = body,
                createdAtEpochMs = Clock.System.now().toEpochMilliseconds(),
            )
            smsDraftStore.addDraft(draft)
            return mapOf(
                "success" to true,
                "draft_id" to draft.id,
                "to" to to,
                "body" to body,
                "message" to "Draft created. Waiting for the user to tap Send in the review banner — nothing has been sent yet.",
            )
        }
    }

    @OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
    fun replySmsTool(smsDraftStore: SmsDraftStore, smsReader: SmsReader, smsSender: SmsSender) = object : Tool {
        override val schema = ToolSchema(
            name = "reply_sms",
            description = "Draft a reply to a received SMS. Looks up the original by id to pick the sender, then stages a draft " +
                "in the review banner — the user must tap Send to actually send.",
            parameters = mapOf(
                "sms_id" to ParameterSchema(type = "integer", description = "Id of the SMS being replied to (from check_sms / search_sms)", required = true),
                "body" to ParameterSchema(type = "string", description = "Reply text", required = true),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            if (!smsSender.hasPermission()) {
                return mapOf(
                    "success" to false,
                    "error" to "SMS send permission not granted. Ask the user to enable 'Send SMS' in Settings.",
                )
            }
            val smsId = (args["sms_id"] as? Number)?.toLong()
                ?: return mapOf("success" to false, "error" to "Missing sms_id")
            val body = args["body"]?.toString()
            if (body.isNullOrEmpty()) return mapOf("success" to false, "error" to "Missing body")

            val original = smsReader.readById(smsId)
                ?: return mapOf("success" to false, "error" to "No SMS found with id $smsId")
            if (original.address.isBlank()) {
                return mapOf("success" to false, "error" to "Original SMS has no sender address to reply to")
            }

            val draft = SmsDraft(
                id = Uuid.random().toString(),
                address = original.address,
                body = body,
                createdAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                inReplyToSmsId = smsId,
            )
            smsDraftStore.addDraft(draft)
            return mapOf(
                "success" to true,
                "draft_id" to draft.id,
                "to" to original.address,
                "body" to body,
                "in_reply_to" to smsId,
                "message" to "Reply draft created. Waiting for the user to tap Send in the review banner — nothing has been sent yet.",
            )
        }
    }

    fun getSmsSendTools(
        smsDraftStore: SmsDraftStore,
        smsReader: SmsReader,
        smsSender: SmsSender,
    ): List<Tool> = listOf(
        sendSmsTool(smsDraftStore, smsSender),
        replySmsTool(smsDraftStore, smsReader, smsSender),
    )

    private const val SEARCH_LIMIT = 20
}
