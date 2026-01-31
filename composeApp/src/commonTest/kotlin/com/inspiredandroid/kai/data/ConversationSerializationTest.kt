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
                "title": "Test Conversation",
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
                "updatedAt": 2000,
                "serviceId": "gemini"
            }
        """.trimIndent()

        val conversation = json.decodeFromString<Conversation>(jsonString)

        assertEquals("conv-123", conversation.id)
        assertEquals("Test Conversation", conversation.title)
        assertEquals(1, conversation.messages.size)
        assertEquals("msg-1", conversation.messages[0].id)
        assertEquals("user", conversation.messages[0].role)
        assertEquals("Hello", conversation.messages[0].content)
        assertEquals("text/plain", conversation.messages[0].mimeType)
        assertEquals("base64data", conversation.messages[0].data)
        assertEquals(1000L, conversation.createdAt)
        assertEquals(2000L, conversation.updatedAt)
        assertEquals("gemini", conversation.serviceId)
    }

    @Test
    fun `deserialize conversation message with optional fields missing`() {
        val jsonString = """
            {
                "id": "conv-123",
                "title": "Test",
                "messages": [
                    {
                        "id": "msg-1",
                        "role": "assistant",
                        "content": "Hello there"
                    }
                ],
                "createdAt": 1000,
                "updatedAt": 2000,
                "serviceId": "free"
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
                "title": "Test",
                "messages": [],
                "createdAt": 1000,
                "updatedAt": 2000,
                "serviceId": "gemini",
                "unknownField": "should be ignored",
                "anotherUnknown": 42
            }
        """.trimIndent()

        val conversation = json.decodeFromString<Conversation>(jsonString)

        assertEquals("conv-123", conversation.id)
        assertEquals("Test", conversation.title)
    }

    @Test
    fun `deserialize message ignores unknown keys`() {
        val jsonString = """
            {
                "id": "conv-123",
                "title": "Test",
                "messages": [
                    {
                        "id": "msg-1",
                        "role": "user",
                        "content": "Hello",
                        "futureField": "ignored"
                    }
                ],
                "createdAt": 1000,
                "updatedAt": 2000,
                "serviceId": "gemini"
            }
        """.trimIndent()

        val conversation = json.decodeFromString<Conversation>(jsonString)

        assertEquals("Hello", conversation.messages[0].content)
    }

    @Test
    fun `serialize conversation includes all fields`() {
        val conversation = Conversation(
            id = "conv-456",
            title = "My Chat",
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
            serviceId = "groq",
        )

        val jsonString = json.encodeToString(conversation)
        val decoded = json.decodeFromString<Conversation>(jsonString)

        assertEquals(conversation, decoded)
    }

    @Test
    fun `serialize conversation with null optional fields`() {
        val conversation = Conversation(
            id = "conv-789",
            title = "Chat",
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
            serviceId = "free",
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
                        "title": "First",
                        "messages": [],
                        "createdAt": 1000,
                        "updatedAt": 2000,
                        "serviceId": "gemini"
                    },
                    {
                        "id": "conv-2",
                        "title": "Second",
                        "messages": [],
                        "createdAt": 3000,
                        "updatedAt": 4000,
                        "serviceId": "groq"
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

        assertEquals(1, data.version)
        assertEquals(0, data.conversations.size)
    }

    @Test
    fun `serialize ConversationsData includes version`() {
        val data = ConversationsData(
            version = 1,
            conversations = listOf(
                Conversation(
                    id = "conv-1",
                    title = "Test",
                    messages = emptyList(),
                    createdAt = 1000L,
                    updatedAt = 2000L,
                    serviceId = "free",
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
            title = "Full Conversation",
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
            serviceId = "gemini",
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
                "title": "Empty Chat",
                "messages": [],
                "createdAt": 1000,
                "updatedAt": 1000,
                "serviceId": "free"
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
                "title": "Special \"Chars\" Test",
                "messages": [
                    {
                        "id": "msg-1",
                        "role": "user",
                        "content": "Line1\nLine2\tTabbed"
                    }
                ],
                "createdAt": 1000,
                "updatedAt": 2000,
                "serviceId": "gemini"
            }
        """.trimIndent()

        val conversation = json.decodeFromString<Conversation>(jsonString)

        assertEquals("Special \"Chars\" Test", conversation.title)
        assertEquals("Line1\nLine2\tTabbed", conversation.messages[0].content)
    }

    @Test
    fun `deserialize conversation with unicode content`() {
        val jsonString = """
            {
                "id": "conv-unicode",
                "title": "日本語テスト",
                "messages": [
                    {
                        "id": "msg-1",
                        "role": "user",
                        "content": "Hello 世界 🌍"
                    }
                ],
                "createdAt": 1000,
                "updatedAt": 2000,
                "serviceId": "gemini"
            }
        """.trimIndent()

        val conversation = json.decodeFromString<Conversation>(jsonString)

        assertEquals("日本語テスト", conversation.title)
        assertEquals("Hello 世界 🌍", conversation.messages[0].content)
    }
}
