package com.inspiredandroid.kai

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.data.EmailStore
import com.inspiredandroid.kai.data.HeartbeatManager
import com.inspiredandroid.kai.data.MemoryStore
import com.inspiredandroid.kai.data.TaskStore
import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema
import com.inspiredandroid.kai.tools.CalendarPermissionController
import com.inspiredandroid.kai.tools.CalendarRepository
import com.inspiredandroid.kai.tools.CalendarResult
import com.inspiredandroid.kai.tools.CommonTools
import com.inspiredandroid.kai.tools.EmailTools
import com.inspiredandroid.kai.tools.HeartbeatTools
import com.inspiredandroid.kai.tools.NotificationHelper
import com.inspiredandroid.kai.tools.NotificationPermissionController
import com.inspiredandroid.kai.tools.NotificationResult
import com.inspiredandroid.kai.tools.SchedulingTools
import com.inspiredandroid.kai.tools.ShellCommandTool
import com.inspiredandroid.kai.tools.WebSearchTool
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.vinceglb.filekit.PlatformFile
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.android.Android
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_create_calendar_event_description
import kai.composeapp.generated.resources.tool_create_calendar_event_name
import kai.composeapp.generated.resources.tool_send_notification_description
import kai.composeapp.generated.resources.tool_send_notification_name
import kai.composeapp.generated.resources.tool_set_alarm_description
import kai.composeapp.generated.resources.tool_set_alarm_name
import kotlinx.coroutines.Dispatchers
import org.koin.java.KoinJavaComponent.inject
import kotlin.coroutines.CoroutineContext

actual fun httpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Android) {
    config(this)
}

actual fun getBackgroundDispatcher(): CoroutineContext = Dispatchers.IO

actual fun onDragAndDropEventDropped(event: DragAndDropEvent): PlatformFile? = null

actual val BackIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack

actual val isMobilePlatform: Boolean = true

actual val isDesktopPlatform: Boolean = false

actual val defaultUiScale: Float = 1.0f

actual val platformName: String = "Android"

actual val isEmailSupported: Boolean = true

actual suspend fun compressImageBytes(bytes: ByteArray, mimeType: String): ByteArray {
    if (!mimeType.startsWith("image/")) return bytes
    return try {
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val maxDim = 1024
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }
        val outputStream = java.io.ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, outputStream)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        outputStream.toByteArray()
    } catch (_: Exception) {
        bytes
    }
}

actual fun getAppFilesDirectory(): String {
    val context: Context by inject(Context::class.java)
    return context.filesDir.absolutePath
}

actual fun createSecureSettings(): Settings {
    val context: Context by inject(Context::class.java)
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "kai_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    return SharedPreferencesSettings(encryptedPrefs)
}

actual fun createLegacySettings(): Settings? {
    val context: Context by inject(Context::class.java)
    val prefs = context.getSharedPreferences("com.inspiredandroid.kai_preferences", Context.MODE_PRIVATE)
    return SharedPreferencesSettings(prefs)
}

// Tool definitions for Android platform
actual fun getPlatformToolDefinitions(): List<ToolInfo> = listOf(
    ShellCommandTool.toolInfo,
    WebSearchTool.toolInfo,
    CommonTools.localTimeToolInfo,
    CommonTools.ipLocationToolInfo,
    ToolInfo(
        id = "send_notification",
        name = "Send Notification",
        description = "Send a push notification to the device",
        nameRes = Res.string.tool_send_notification_name,
        descriptionRes = Res.string.tool_send_notification_description,
    ),
    ToolInfo(
        id = "create_calendar_event",
        name = "Create Calendar Event",
        description = "Create a calendar event on the user's device",
        nameRes = Res.string.tool_create_calendar_event_name,
        descriptionRes = Res.string.tool_create_calendar_event_description,
    ),
    ToolInfo(
        id = "set_alarm",
        name = "Set Alarm",
        description = "Set an alarm or countdown timer on the device",
        nameRes = Res.string.tool_set_alarm_name,
        descriptionRes = Res.string.tool_set_alarm_description,
    ),
) + SchedulingTools.schedulingToolDefinitions +
    HeartbeatTools.heartbeatToolDefinitions +
    EmailTools.emailToolDefinitions

actual fun getAvailableTools(): List<Tool> {
    val context: Context by inject(Context::class.java)
    val appSettings: AppSettings by inject(AppSettings::class.java)
    val memoryStore: MemoryStore by inject(MemoryStore::class.java)
    val taskStore: TaskStore by inject(TaskStore::class.java)
    val heartbeatManager: HeartbeatManager by inject(HeartbeatManager::class.java)
    val calendarPermissionController: CalendarPermissionController by inject(CalendarPermissionController::class.java)
    val calendarRepository = CalendarRepository(context, calendarPermissionController)
    val emailStore: EmailStore by inject(EmailStore::class.java)

    return buildList {
        if (appSettings.isMemoryEnabled()) {
            addAll(CommonTools.getMemoryTools(memoryStore))
        }
        if (appSettings.isSchedulingEnabled()) {
            addAll(SchedulingTools.getSchedulingTools(taskStore))
            addAll(HeartbeatTools.getHeartbeatTools(heartbeatManager, memoryStore, appSettings))
        }
        if (appSettings.isToolEnabled(CommonTools.localTimeTool.schema.name)) {
            add(CommonTools.localTimeTool)
        }

        if (appSettings.isToolEnabled(CommonTools.ipLocationTool.schema.name)) {
            add(CommonTools.ipLocationTool)
        }

        if (appSettings.isToolEnabled(WebSearchTool.schema.name)) {
            add(WebSearchTool)
        }

        if (appSettings.isToolEnabled("send_notification")) {
            val notificationPermissionController: NotificationPermissionController by inject(NotificationPermissionController::class.java)
            val notificationHelper = NotificationHelper(context, notificationPermissionController)

            add(
                object : Tool {
                    override val schema = ToolSchema(
                        "send_notification",
                        "Send a push notification to the device",
                        mapOf(
                            "title" to ParameterSchema("string", "Notification title", false),
                            "message" to ParameterSchema("string", "Notification content/body", true),
                        ),
                    )

                    override suspend fun execute(args: Map<String, Any>): Any {
                        val title = args["title"] as? String ?: "Kai"
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
                },
            )
        }

        if (appSettings.isToolEnabled("create_calendar_event")) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        "create_calendar_event",
                        "Create a calendar event on the user's device",
                        mapOf(
                            "title" to ParameterSchema("string", "Event title", true),
                            "start_time" to ParameterSchema("string", "Start time in ISO 8601 format (e.g., 2024-03-15T14:30:00)", true),
                            "end_time" to ParameterSchema("string", "End time in ISO 8601 format. Defaults to 1 hour after start", false),
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
                },
            )
        }

        if (appSettings.isToolEnabled("set_alarm")) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        "set_alarm",
                        "Set an alarm or countdown timer on the device. For alarms provide hour and minutes. For countdown timers provide duration_seconds.",
                        mapOf(
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
                },
            )
        }

        if (appSettings.isToolEnabled(ShellCommandTool.schema.name, defaultEnabled = false)) {
            add(ShellCommandTool)
        }

        if (appSettings.isEmailEnabled()) {
            addAll(EmailTools.getEmailTools(emailStore))
        }
    }
}

actual fun decodeToImageBitmap(bytes: ByteArray): ImageBitmap? = try {
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
} catch (_: Exception) {
    null
}
