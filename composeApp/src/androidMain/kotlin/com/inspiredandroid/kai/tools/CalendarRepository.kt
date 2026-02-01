package com.inspiredandroid.kai.tools

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.TimeZone

private const val TAG = "CalendarRepository"

sealed class CalendarResult {
    data class Success(val eventId: Long, val title: String, val startTime: String) : CalendarResult()
    data class Error(val message: String) : CalendarResult()
}

class CalendarRepository(
    private val context: Context,
    private val permissionController: CalendarPermissionController,
) {

    fun hasCalendarPermission(): Boolean {
        val hasRead = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        val hasWrite = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        return hasRead && hasWrite
    }

    fun getPrimaryCalendarId(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.IS_PRIMARY,
        )

        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val selectionArgs = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Calendars.IS_PRIMARY} DESC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                if (idIndex >= 0) {
                    return cursor.getLong(idIndex)
                }
            }
        }
        return null
    }

    suspend fun createEvent(
        title: String,
        startTimeIso: String,
        endTimeIso: String?,
        description: String?,
        location: String?,
        allDay: Boolean,
        reminderMinutes: Int,
    ): CalendarResult {
        Log.d(TAG, "createEvent called: title=$title, startTime=$startTimeIso")

        // Request permission if not already granted
        if (!hasCalendarPermission()) {
            Log.d(TAG, "Permission not granted, requesting...")
            val granted = permissionController.requestPermission()
            Log.d(TAG, "Permission request result: $granted")
            if (!granted) {
                return CalendarResult.Error("Calendar permission denied. Please enable calendar access in Settings to create events.")
            }
        } else {
            Log.d(TAG, "Permission already granted")
        }

        val calendarId = getPrimaryCalendarId()
            ?: return CalendarResult.Error("No writable calendar found. Please set up a calendar account on your device.")

        val startMillis: Long
        val endMillis: Long
        val timeZone = TimeZone.getDefault().id

        try {
            val startDateTime = parseIsoDateTime(startTimeIso)
            startMillis = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            endMillis = if (endTimeIso != null) {
                val endDateTime = parseIsoDateTime(endTimeIso)
                endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } else {
                // Default to 1 hour after start
                startMillis + 60 * 60 * 1000
            }
        } catch (e: DateTimeParseException) {
            return CalendarResult.Error("Invalid date format. Please use ISO 8601 format (e.g., 2024-03-15T14:30:00)")
        }

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, timeZone)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
            description?.let { put(CalendarContract.Events.DESCRIPTION, it) }
            location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
        }

        return try {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = uri?.lastPathSegment?.toLongOrNull()

            if (eventId != null) {
                // Add reminder if specified
                if (reminderMinutes > 0) {
                    addReminder(eventId, reminderMinutes)
                }

                val formattedStart = formatForDisplay(startMillis)
                CalendarResult.Success(eventId, title, formattedStart)
            } else {
                CalendarResult.Error("Failed to create calendar event")
            }
        } catch (e: Exception) {
            CalendarResult.Error("Error creating event: ${e.message}")
        }
    }

    private fun addReminder(eventId: Long, minutesBefore: Int) {
        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, minutesBefore)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
    }

    private fun parseIsoDateTime(isoString: String): LocalDateTime {
        // Try various ISO 8601 formats
        val formatters = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        )

        for (formatter in formatters) {
            try {
                return LocalDateTime.parse(isoString.trim(), formatter)
            } catch (_: DateTimeParseException) {
                // Try next formatter
            }
        }

        // If all else fails, try parsing as Instant (for formats with timezone like 2024-03-15T14:30:00Z)
        try {
            val instant = Instant.parse(isoString.trim())
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        } catch (_: DateTimeParseException) {
            // Fall through to throw
        }

        throw DateTimeParseException("Unable to parse date: $isoString", isoString, 0)
    }

    private fun formatForDisplay(millis: Long): String {
        val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a")
        val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
        return dateTime.format(formatter)
    }
}
