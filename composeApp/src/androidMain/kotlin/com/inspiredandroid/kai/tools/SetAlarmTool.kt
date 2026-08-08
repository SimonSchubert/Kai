package com.inspiredandroid.kai.tools

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_set_alarm_description
import kai.composeapp.generated.resources.tool_set_alarm_name

object SetAlarmTool {

    const val ID = "set_alarm"

    val toolInfo = ToolInfo(
        id = ID,
        name = "Set Alarm",
        description = "Set an alarm or countdown timer on the device",
        nameRes = Res.string.tool_set_alarm_name,
        descriptionRes = Res.string.tool_set_alarm_description,
    )

    fun create(context: Context): Tool = object : Tool {
        override val schema = ToolSchema(
            name = ID,
            description = "Set an alarm or countdown timer on the device. For alarms provide hour and minutes. For countdown timers provide duration_seconds.",
            parameters = mapOf(
                "hour" to ParameterSchema("integer", "Hour of the alarm in 24-hour format (0-23)", false),
                "minutes" to ParameterSchema("integer", "Minutes of the alarm (0-59)", false),
                "label" to ParameterSchema("string", "Label for the alarm or timer", false),
                "duration_seconds" to ParameterSchema("integer", "Duration in seconds for a countdown timer", false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val hour = (args["hour"] as? Number)?.toInt()
            val minutes = (args["minutes"] as? Number)?.toInt()
            val label = args["label"] as? String
            val durationSeconds = (args["duration_seconds"] as? Number)?.toInt()

            val intent = if (durationSeconds != null) {
                Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, durationSeconds)
                    if (label != null) putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                }
            } else if (hour != null && minutes != null) {
                Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                    if (label != null) putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                }
            } else {
                return mapOf(
                    "success" to false,
                    "error" to "Provide either hour+minutes for an alarm or duration_seconds for a timer",
                )
            }

            return try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                if (durationSeconds != null) {
                    mapOf(
                        "success" to true,
                        "type" to "timer",
                        "duration_seconds" to durationSeconds,
                        "message" to "Timer set for $durationSeconds seconds",
                    )
                } else {
                    mapOf(
                        "success" to true,
                        "type" to "alarm",
                        "hour" to hour!!,
                        "minutes" to minutes!!,
                        "message" to "Alarm set for %02d:%02d".format(hour, minutes),
                    )
                }
            } catch (e: Exception) {
                mapOf(
                    "success" to false,
                    "error" to (e.message ?: "Failed to set alarm"),
                )
            }
        }
    }
}
