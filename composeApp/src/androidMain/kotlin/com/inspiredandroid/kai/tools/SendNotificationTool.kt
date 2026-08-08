package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_send_notification_description
import kai.composeapp.generated.resources.tool_send_notification_name

object SendNotificationTool {

    const val ID = "send_notification"

    val toolInfo = ToolInfo(
        id = ID,
        name = "Send Notification",
        description = "Send a push notification to the device",
        nameRes = Res.string.tool_send_notification_name,
        descriptionRes = Res.string.tool_send_notification_description,
    )

    fun create(notificationHelper: NotificationHelper): Tool = object : Tool {
        override val schema = ToolSchema(
            name = ID,
            description = "Send a push notification to the device",
            parameters = mapOf(
                "title" to ParameterSchema("string", "Notification title", false),
                "message" to ParameterSchema("string", "Notification content/body", true),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val title = args["title"] as? String ?: "Kai 9000"
            val message = args["message"] as? String
                ?: return mapOf("success" to false, "error" to "Message is required")

            return when (val result = notificationHelper.sendNotification(title, message)) {
                is NotificationResult.Success -> mapOf(
                    "success" to true,
                    "notification_id" to result.notificationId,
                    "message" to "Notification sent successfully",
                )

                is NotificationResult.Error -> mapOf(
                    "success" to false,
                    "error" to result.message,
                )
            }
        }
    }
}
