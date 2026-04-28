package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.data.NotificationRecord
import com.inspiredandroid.kai.data.NotificationStore
import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema
import com.inspiredandroid.kai.notifications.NotificationReader
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_check_notifications_description
import kai.composeapp.generated.resources.tool_check_notifications_name
import kai.composeapp.generated.resources.tool_read_notification_description
import kai.composeapp.generated.resources.tool_read_notification_name
import kai.composeapp.generated.resources.tool_search_notifications_description
import kai.composeapp.generated.resources.tool_search_notifications_name

object NotificationTools {

    private fun summary(record: NotificationRecord): Map<String, Any?> = mapOf(
        "id" to record.id,
        "package_name" to record.packageName,
        "app_label" to record.appLabel,
        "title" to record.title,
        "posted_at" to record.postedAtEpochMs,
        "preview" to record.preview,
    )

    fun checkNotificationsTool(store: NotificationStore, reader: NotificationReader) = object : Tool {
        override val schema = ToolSchema(
            name = "check_notifications",
            description = "List recent notifications that the user hasn't been shown yet. " +
                "Returns app name, title, posted time, and a short preview. Use read_notification with " +
                "the `id` to fetch the full body. If nothing is pending, call search_notifications " +
                "to find a known notification by app or text.",
            parameters = emptyMap(),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            if (!reader.isSupported()) {
                return mapOf("success" to false, "error" to "Notification reading is not available on this build")
            }
            if (!reader.hasAccess()) {
                return mapOf(
                    "success" to false,
                    "error" to "Notification access not granted. Ask the user to enable Kai under " +
                        "system Notification access settings.",
                )
            }
            val pending = store.getPending()
            return mapOf(
                "success" to true,
                "count" to pending.size,
                "notifications" to pending.map(::summary),
            )
        }
    }

    fun readNotificationTool(reader: NotificationReader) = object : Tool {
        override val schema = ToolSchema(
            name = "read_notification",
            description = "Read the full body of a specific notification by its id. " +
                "Use check_notifications or search_notifications first to find an id.",
            parameters = mapOf(
                "id" to ParameterSchema(
                    type = "string",
                    description = "The notification id returned by check_notifications or search_notifications",
                    required = true,
                ),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            if (!reader.isSupported()) {
                return mapOf("success" to false, "error" to "Notification reading is not available on this build")
            }
            if (!reader.hasAccess()) {
                return mapOf("success" to false, "error" to "Notification access not granted")
            }
            val id = args["id"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing id")
            val record = reader.getById(id)
                ?: return mapOf("success" to false, "error" to "No notification found with id $id")
            return mapOf(
                "success" to true,
                "id" to record.id,
                "package_name" to record.packageName,
                "app_label" to record.appLabel,
                "title" to record.title,
                "text" to record.text,
                "subtext" to record.subtext,
                "category" to record.category,
                "posted_at" to record.postedAtEpochMs,
            )
        }
    }

    fun searchNotificationsTool(reader: NotificationReader) = object : Tool {
        override val schema = ToolSchema(
            name = "search_notifications",
            description = "Search notifications by app name, title, or body text. Returns newest-first, " +
                "up to 20 matches. Optionally filter by `package_name` to restrict to one app.",
            parameters = mapOf(
                "query" to ParameterSchema(
                    type = "string",
                    description = "Text to match against app name, title, or body",
                    required = true,
                ),
                "package_name" to ParameterSchema(
                    type = "string",
                    description = "Optional package name filter (e.g. com.whatsapp)",
                    required = false,
                ),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            if (!reader.isSupported()) {
                return mapOf("success" to false, "error" to "Notification reading is not available on this build")
            }
            if (!reader.hasAccess()) {
                return mapOf("success" to false, "error" to "Notification access not granted")
            }
            val query = args["query"]?.toString()?.trim()
            if (query.isNullOrBlank()) {
                return mapOf("success" to false, "error" to "Missing query")
            }
            val packageName = args["package_name"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val matches = reader.search(query, SEARCH_LIMIT, packageName)
            return mapOf(
                "success" to true,
                "count" to matches.size,
                "notifications" to matches.map(::summary),
            )
        }
    }

    val checkNotificationsToolInfo = ToolInfo(
        id = "check_notifications",
        name = "Check Notifications",
        description = "List recent unread notifications",
        nameRes = Res.string.tool_check_notifications_name,
        descriptionRes = Res.string.tool_check_notifications_description,
    )

    val readNotificationToolInfo = ToolInfo(
        id = "read_notification",
        name = "Read Notification",
        description = "Read the full body of a notification",
        nameRes = Res.string.tool_read_notification_name,
        descriptionRes = Res.string.tool_read_notification_description,
    )

    val searchNotificationsToolInfo = ToolInfo(
        id = "search_notifications",
        name = "Search Notifications",
        description = "Search notifications by app or text",
        nameRes = Res.string.tool_search_notifications_name,
        descriptionRes = Res.string.tool_search_notifications_description,
    )

    val notificationToolDefinitions = listOf(
        checkNotificationsToolInfo,
        readNotificationToolInfo,
        searchNotificationsToolInfo,
    )

    fun getNotificationTools(store: NotificationStore, reader: NotificationReader): List<Tool> = listOf(
        checkNotificationsTool(store, reader),
        readNotificationTool(reader),
        searchNotificationsTool(reader),
    )

    private const val SEARCH_LIMIT = 20
}
