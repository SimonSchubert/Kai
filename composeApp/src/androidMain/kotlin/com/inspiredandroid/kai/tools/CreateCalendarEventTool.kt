package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_create_calendar_event_description
import kai.composeapp.generated.resources.tool_create_calendar_event_name

object CreateCalendarEventTool {

    const val ID = "create_calendar_event"

    val toolInfo = ToolInfo(
        id = ID,
        name = "Create Calendar Event",
        description = "Create a calendar event on the user's device",
        nameRes = Res.string.tool_create_calendar_event_name,
        descriptionRes = Res.string.tool_create_calendar_event_description,
    )

    fun create(calendarRepository: CalendarRepository): Tool = object : Tool {
        override val schema = ToolSchema(
            name = ID,
            description = "Create a calendar event on the user's device",
            parameters = mapOf(
                "title" to ParameterSchema("string", "Event title", true),
                "start_time" to ParameterSchema("string", "Start time as ISO 8601, e.g. '2024-03-15T14:30:00+02:00'. Naive (no offset) is treated as user's local time.", true),
                "end_time" to ParameterSchema("string", "End time, same format as start_time. Defaults to 1 hour after start.", false),
                "description" to ParameterSchema("string", "Event notes or description", false),
                "location" to ParameterSchema("string", "Event location", false),
                "all_day" to ParameterSchema("boolean", "Whether this is an all-day event", false),
                "reminder_minutes" to ParameterSchema("integer", "Minutes before event to send reminder (default: 15)", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val title = args["title"] as? String
                ?: return mapOf("success" to false, "error" to "Title is required")
            val startTime = args["start_time"] as? String
                ?: return mapOf("success" to false, "error" to "Start time is required")
            val endTime = args["end_time"] as? String
            val description = args["description"] as? String
            val location = args["location"] as? String
            val allDay = (args["all_day"] as? Boolean) ?: false
            val reminderMinutes = (args["reminder_minutes"] as? Number)?.toInt() ?: 15

            return when (
                val result = calendarRepository.createEvent(
                    title = title,
                    startTimeIso = startTime,
                    endTimeIso = endTime,
                    description = description,
                    location = location,
                    allDay = allDay,
                    reminderMinutes = reminderMinutes,
                )
            ) {
                is CalendarResult.Success -> mapOf(
                    "success" to true,
                    "event_id" to result.eventId,
                    "title" to result.title,
                    "scheduled_for" to result.startTime,
                    "message" to "Event '${result.title}' created successfully for ${result.startTime}",
                )

                is CalendarResult.Error -> mapOf(
                    "success" to false,
                    "error" to result.message,
                )
            }
        }
    }
}
