package com.inspiredandroid.kai.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConversationSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `deserialize conversation with all fields`() {
        val jsonString = """
            {
                "id": "conv-123",
                "messages": [
                    {
                        "id": "msg-1",
                        "role": "user",
                        "content": "Hello",
                        "mimeType": "text/plain",
                        "data": "base64data"
                    }
                ],
                "createdAt": 1000,
                "updatedAt": 2000
            }
        """.trimIndent()

        val conversation = json.decodeFromString<Conversation>(jsonString)

        assertEquals("conv-123", conversation.id)
        assertEquals(1, conversation.messages.size)
        assertEquals("msg-1", conversation.messages[0].id)
        assertEquals("user", conversation.messages[0].role)
        assertEquals("Hello", conversation.messages[0].content)
        assertEquals("text/plain", conversation.messages[0].mimeType)
        assertEquals("base64data", conversation.messages[0].data)
        assertEquals(1000L, conversation.createdAt)
        assertEquals(2000L, conversation.updatedAt)
    }

    @Test
    fun `deserialize conversation message with optional fields missing`() {
        val jsonString = """
            {
                "id": "conv-123",
                "messages": [
                    {
                        "id": "msg-1",
                        "role": "assistant",
                        "content": "Hello there"
                    }
                ],
                "createdAt": 1000,
                "updatedAt": 2000
            }
        """.trimIndent()

        val conversation = json.decodeFromString<Conversation>(jsonString)

        assertEquals(1, conversation.messages.size)
        val message = conversation.messages[0]
        assertEquals("msg-1", message.id)
        assertEquals("assistant", message.role)
        assertEquals("Hello there", message.content)
        assertNull(message.mimeType)
        assertNull(message.data)
    }

    @Test
    fun `deserialize conversation ignores unknown keys`() {
        val jsonString = """
            {
                "id": "conv-123",
                "messages": [],
                "createdAt": 1000,
                "updatedAt": 2000,
                "unknownField": "should be ignored",
                "anotherUnknown": 42
            }
        """.trimIndent()

        val conversation = json.decodeFromString<Conversation>(jsonString)

        assertEquals("conv-123", conversation.id)
    }

    @Test
    fun `deserialize conversation ignores legacy fields`() {
        val jsonString = """
            {
                "id": "conv-123",
                "title": "Old Title",
                "messages": [],
                "createdAt": 1000,
                "updatedAt": 2000,
                "serviceId": "gemini"
            }
        """.trimIndent()

        val conversation = json.decodeFromString<Conversation>(jsonString)

        assertEquals("conv-123", conversation.id)
    }

    @Test
    fun `deserialize message ignores unknown keys`() {
        val jsonString = """
            {
                "id": "conv-123",
                "messages": [
                    {
                        "id": "msg-1",
                        "role": "user",
                        "content": "Hello",
                        "futureField": "ignored"
                    }
                ],
                "createdAt": 1000,
                "updatedAt": 2000
            }
        """.trimIndent()

        val conversation = json.decodeFromString<Conversation>(jsonString)

        assertEquals("Hello", conversation.messages[0].content)
    }

    @Test
    fun `serialize conversation includes all fields`() {
        val conversation = Conversation(
            id = "conv-456",
            messages = listOf(
                Conversation.Message(
                    id = "msg-1",
                    role = "user",
                    content = "Hi",
                    mimeType = "image/png",
                    data = "imagedata",
                ),
            ),
            createdAt = 5000L,
            updatedAt = 6000L,
        )

        val jsonString = json.encodeToString(conversation)
        val decoded = json.decodeFromString<Conversation>(jsonString)

        assertEquals(conversation, decoded)
    }

    @Test
    fun `serialize conversation with null optional fields`() {
        val conversation = Conversation(
            id = "conv-789",
            messages = listOf(
                Conversation.Message(
                    id = "msg-1",
                    role = "assistant",
                    content = "Response",
                    mimeType = null,
                    data = null,
                ),
            ),
            createdAt = 1000L,
            updatedAt = 2000L,
        )

        val jsonString = json.encodeToString(conversation)
        val decoded = json.decodeFromString<Conversation>(jsonString)

        assertEquals(conversation, decoded)
        assertNull(decoded.messages[0].mimeType)
        assertNull(decoded.messages[0].data)
    }

    @Test
    fun `deserialize ConversationsData with version`() {
        val jsonString = """
            {
                "version": 1,
                "conversations": [
                    {
                        "id": "conv-1",
                        "messages": [],
                        "createdAt": 1000,
                        "updatedAt": 2000
                    },
                    {
                        "id": "conv-2",
                        "messages": [],
                        "createdAt": 3000,
                        "updatedAt": 4000
                    }
                ]
            }
        """.trimIndent()

        val data = json.decodeFromString<ConversationsData>(jsonString)

        assertEquals(1, data.version)
        assertEquals(2, data.conversations.size)
        assertEquals("conv-1", data.conversations[0].id)
        assertEquals("conv-2", data.conversations[1].id)
    }

    @Test
    fun `deserialize ConversationsData with default version`() {
        val jsonString = """
            {
                "conversations": []
            }
        """.trimIndent()

        val data = json.decodeFromString<ConversationsData>(jsonString)

        assertEquals(2, data.version)
        assertEquals(0, data.conversations.size)
    }

    @Test
    fun `serialize ConversationsData includes version`() {
        val data = ConversationsData(
            version = 2,
            conversations = listOf(
                Conversation(
                    id = "conv-1",
                    messages = emptyList(),
                    createdAt = 1000L,
                    updatedAt = 2000L,
                ),
            ),
        )

        val jsonString = json.encodeToString(data)
        val decoded = json.decodeFromString<ConversationsData>(jsonString)

        assertEquals(data, decoded)
    }

    @Test
    fun `round trip conversation with multiple messages`() {
        val original = Conversation(
            id = "conv-full",
            messages = listOf(
                Conversation.Message(
                    id = "msg-1",
                    role = "user",
                    content = "What is 2+2?",
                ),
                Conversation.Message(
                    id = "msg-2",
                    role = "assistant",
                    content = "2+2 equals 4.",
                ),
                Conversation.Message(
                    id = "msg-3",
                    role = "user",
                    content = "Thanks!",
                ),
            ),
            createdAt = 1000L,
            updatedAt = 3000L,
        )

        val jsonString = json.encodeToString(original)
        val decoded = json.decodeFromString<Conversation>(jsonString)

        assertEquals(original, decoded)
        assertEquals(3, decoded.messages.size)
    }

    @Test
    fun `deserialize empty conversations list`() {
        val jsonString = """
            {
                "version": 1,
                "conversations": []
            }
        """.trimIndent()

        val data = json.decodeFromString<ConversationsData>(jsonString)

        assertEquals(1, data.version)
        assertEquals(0, data.conversations.size)
    }

    @Test
    fun `deserialize conversation with empty messages list`() {
        val jsonString = """
            {
                "id": "conv-empty",
                "messages": [],
                "createdAt": 1000,
                "updatedAt": 1000
            }
        """.trimIndent()

        val conversation = json.decodeFromString<Conversation>(jsonString)

        assertEquals("conv-empty", conversation.id)
        assertEquals(0, conversation.messages.size)
    }

    @Test
    fun `deserialize conversation with special characters in content`() {
        val jsonString = """
            {
                "id": "conv-special",
                "messages": [
                    {
                        "id": "msg-1",
                        "role": "user",
                        "content": "Line1\nLine2\tTabbed"
                    }
                ],
                "createdAt": 1000,
                "updatedAt": 2000
            }
        """.trimIndent()

        val conversation = json.decodeFromString<Conversation>(jsonString)

        assertEquals("Line1\nLine2\tTabbed", conversation.messages[0].content)
    }

    @Test
    fun `serialize and deserialize conversation with multi-attachment message`() {
        val original = Conversation(
            id = "conv-multi",
            messages = listOf(
                Conversation.Message(
                    id = "msg-1",
                    role = "user",
                    content = "look at these",
                    attachments = listOf(
                        Attachment(data = "imgdata1", mimeType = "image/jpeg", fileName = null),
                        Attachment(data = "imgdata2", mimeType = "image/png", fileName = null),
                        Attachment(data = "textdata", mimeType = "text/plain", fileName = "notes.txt"),
                    ),
                ),
            ),
            createdAt = 1000L,
            updatedAt = 2000L,
        )

        val jsonString = json.encodeToString(original)
        val decoded = json.decodeFromString<Conversation>(jsonString)

        assertEquals(original, decoded)
        assertEquals(3, decoded.messages[0].attachments.size)
        assertEquals("image/jpeg", decoded.messages[0].attachments[0].mimeType)
        assertEquals("notes.txt", decoded.messages[0].attachments[2].fileName)
    }

    @Test
    fun `deserialize legacy single-file message keeps old fields readable`() {
        // Conversations saved by earlier versions have data/mimeType/fileName on the message
        // and no `attachments` field. The schema must still accept this shape; the conversion
        // to an `attachments` list happens in RemoteDataRepository.loadConversation.
        val jsonString = """
            {
                "id": "conv-legacy",
                "messages": [
                    {
                        "id": "msg-1",
                        "role": "user",
                        "content": "legacy attach",
                        "mimeType": "image/jpeg",
                        "data": "legacybase64",
                        "fileName": "old.jpg"
                    }
                ],
                "createdAt": 1000,
                "updatedAt": 2000
            }
        """.trimIndent()

        val conversation = json.decodeFromString<Conversation>(jsonString)

        val m = conversation.messages.single()
        assertEquals("image/jpeg", m.mimeType)
        assertEquals("legacybase64", m.data)
        assertEquals("old.jpg", m.fileName)
        assertEquals(0, m.attachments.size)
    }

    @Test
    fun `deserialize conversation with unicode content`() {
        val jsonString = """
            {
                "id": "conv-unicode",
                "messages": [
                    {
                        "id": "msg-1",
                        "role": "user",
                        "content": "Hello 世界 🌍"
                    }
                ],
                "createdAt": 1000,
                "updatedAt": 2000
            }
        """.trimIndent()

        val conversation = json.decodeFromString<Conversation>(jsonString)

        assertEquals("Hello 世界 🌍", conversation.messages[0].content)
    }
}
