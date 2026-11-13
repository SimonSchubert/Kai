package com.kainext.duo.tools

import android.app.usage.UsageStatsManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.provider.MediaStore
import com.kainext.duo.network.tools.ParameterSchema
import com.kainext.duo.network.tools.Tool
import com.kainext.duo.network.tools.ToolInfo
import com.kainext.duo.network.tools.ToolSchema
import kotlin.time.Duration.Companion.seconds
import org.koin.java.KoinJavaComponent
import org.koin.java.KoinJavaComponent.inject

/**
 * Personal-context tools: clipboard, photo library and recent app usage — the parts of "what is
 * on this phone right now" that Kai could not answer before, because its existing device tools
 * only shell out and drive the screen.
 *
 * ## Design note
 *
 * The shape of this tool (small read-only actions grouped by data source, each returning a short
 * list plus an explicit count and the exact window it covered, and writes verified by reading
 * back) follows the personal-context design of [Eta](https://github.com/Mangi-11/Eta)
 * (GPL-3.0, Copyright © 2026 蛮吉 Mangi-11). The implementation here is written independently
 * against the public Android APIs Kai already has — no Eta source is copied — see
 * `docs/CREDITS-eta.md`.
 *
 * ## Why every reply carries its window and count
 *
 * "No photos" and "no photos in the last 7 days that match" are different answers, and an agent
 * that cannot tell them apart will confidently report an empty library. Every action therefore
 * states the time range and filter it used, and lists are capped with an explicit
 * `truncated` flag rather than silently shortened.
 */
class AndroidContextTool : Tool {

    private val context: Context by inject(Context::class.java)

    override val schema = ToolSchema(
        name = ID,
        description = "Read personal context from the device: clipboard, recent photos, recent app usage, " +
            "and a device snapshot. Actions: clipboard_read, clipboard_write, photos_recent, apps_recent, " +
            "device_state. photos_recent needs READ_MEDIA_IMAGES (Android 13+) and apps_recent needs usage " +
            "access (Settings › Special app access › Usage access).",
        parameters = mapOf(
            "action" to ParameterSchema(
                "string",
                "One of: clipboard_read, clipboard_write, photos_recent, apps_recent, device_state.",
                required = true,
            ),
            "text" to ParameterSchema("string", "Text to put on the clipboard (clipboard_write).", required = false),
            "limit" to ParameterSchema("number", "Maximum rows to return (default 20, max 100).", required = false),
            "days" to ParameterSchema("number", "How far back to look, in days (default 7, max 365).", required = false),
            "query" to ParameterSchema("string", "Optional case-insensitive filter on photo display name or app label.", required = false),
        ),
    )

    override val timeout = 30.seconds

    override suspend fun execute(args: Map<String, Any>): Any {
        val action = args["action"]?.toString()?.lowercase()
            ?: return mapOf("success" to false, "error" to "Missing action")
        val limit = ((args["limit"] as? Number)?.toInt() ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val days = ((args["days"] as? Number)?.toInt() ?: DEFAULT_DAYS).coerceIn(1, MAX_DAYS)
        val query = args["query"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }

        return when (action) {
            "clipboard_read" -> readClipboard()
            "clipboard_write" -> {
                val text = args["text"]?.toString()
                if (text.isNullOrEmpty()) return mapOf("success" to false, "error" to "Missing text")
                writeClipboard(text)
            }
            "photos_recent" -> photosRecent(limit, days, query)
            "apps_recent" -> appsRecent(limit, days, query)
            "device_state" -> deviceState()
            else -> mapOf("success" to false, "error" to "Unknown action: $action")
        }
    }

    /**
     * Reads the clipboard.
     *
     * On Android 10+ only the app holding focus may read the clipboard, and Kai usually is not
     * that app while a tool runs — the failure is reported as a permission-ish condition with the
     * reason instead of an empty string, because "the clipboard is empty" is a different (and
     * often wrong) conclusion.
     */
    private fun readClipboard(): Map<String, Any> {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return mapOf("success" to false, "error" to "clipboard service unavailable")
        val clip = runCatching { manager.primaryClip }.getOrNull()
        val item = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        val text = item?.coerceToText(context)?.toString().orEmpty()
        return buildMap {
            put("success", true)
            put("empty", text.isEmpty())
            if (text.isNotEmpty()) {
                put("length", text.length)
                put("text", if (text.length <= MAX_CLIPBOARD_CHARS) text else text.take(MAX_CLIPBOARD_CHARS))
                if (text.length > MAX_CLIPBOARD_CHARS) put("truncated", true)
                item?.text?.toString()?.let { put("is_text", true) }
            } else {
                put(
                    "note",
                    "the clipboard is empty or unreadable right now. On Android 10+ only the focused app " +
                        "may read it, so this can also mean Kai is in the background.",
                )
            }
            clip?.description?.label?.toString()?.takeIf { it.isNotBlank() }?.let { put("label", it) }
        }
    }

    /**
     * Writes the clipboard and verifies the write by reading it back.
     *
     * The read-back is the point: `setPrimaryClip` returns nothing, and a silent failure (no
     * clipboard access, a manager that dropped the clip) looks identical to success. Reporting
     * "copied" without checking is exactly the kind of claim that makes an agent skip a step the
     * user then has to redo by hand.
     */
    private fun writeClipboard(text: String): Map<String, Any> {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return mapOf("success" to false, "error" to "clipboard service unavailable")
        val written = runCatching {
            manager.setPrimaryClip(ClipData.newPlainText("Kai", text))
            true
        }.getOrDefault(false)
        if (!written) {
            return mapOf("success" to false, "error" to "setPrimaryClip was rejected")
        }
        val readBack = runCatching { manager.primaryClip }
            .getOrNull()
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
        return buildMap {
            put("success", readBack == text)
            put("length", text.length)
            put("verified", readBack == text)
            if (readBack != text) {
                put(
                    "note",
                    "the clip was written but reading it back did not match. Another app may have " +
                        "replaced it, or clipboard reads are restricted for a background app.",
                )
            }
        }
    }

    /**
     * Lists recent photos from MediaStore.
     *
     * Ordered by `DATE_ADDED` descending, which is when the item entered the library — not
     * `DATE_TAKEN`, which is frequently null for edited/imported files and would sort them to the
     * bottom as if they were ancient.
     */
    private fun photosRecent(limit: Int, days: Int, query: String?): Map<String, Any> {
        val sinceSeconds = (System.currentTimeMillis() - days * DAY_MS) / 1_000
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val selectionArgs = arrayOf(sinceSeconds.toString())
        val rows = mutableListOf<Map<String, Any>>()
        var total = 0
        val cursor = runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )
        }.getOrNull()
            ?: return mapOf(
                "success" to false,
                "error" to "MediaStore query failed. Grant photo access (READ_MEDIA_IMAGES) to Kai first.",
                "window_days" to days,
            )
        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            while (c.moveToNext()) {
                val name = c.getString(nameCol).orEmpty()
                if (query != null && !name.contains(query, ignoreCase = true)) continue
                total++
                if (rows.size >= limit) continue
                val id = c.getLong(idCol)
                rows += buildMap<String, Any> {
                    put("name", name)
                    put("date_added", c.getLong(dateCol))
                    put("size_bytes", c.getLong(sizeCol))
                    val width = c.getInt(widthCol)
                    val height = c.getInt(heightCol)
                    if (width > 0 && height > 0) {
                        put("width", width)
                        put("height", height)
                    }
                    put("uri", "content://media/external/images/media/$id")
                }
            }
        }
        return buildMap {
            put("success", true)
            put("count", rows.size)
            put("matched", total)
            put("truncated", total > rows.size)
            put("window_days", days)
            if (query != null) put("query", query)
            put("photos", rows)
        }
    }

    /**
     * Lists app usage for the window, most-used first.
     *
     * Usage access is a special permission that cannot be requested with a dialog; without it
     * `queryUsageStats` returns an empty list rather than throwing, which is why the empty case
     * here explains the permission instead of saying "no apps were used".
     */
    private fun appsRecent(limit: Int, days: Int, query: String?): Map<String, Any> {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return mapOf("success" to false, "error" to "usage stats service unavailable")
        val end = System.currentTimeMillis()
        val begin = end - days * DAY_MS
        val stats = runCatching {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, end)
        }.getOrNull().orEmpty()
        val usageByPackage = HashMap<String, Long>(stats.size)
        for (stat in stats) {
            if (stat.totalTimeInForeground <= 0L) continue
            usageByPackage[stat.packageName] = (usageByPackage[stat.packageName] ?: 0L) + stat.totalTimeInForeground
        }
        if (usageByPackage.isEmpty()) {
            return mapOf(
                "success" to true,
                "count" to 0,
                "window_days" to days,
                "note" to
                "no usage was recorded. Either the phone was idle in this window, or Kai lacks Usage " +
                    "access (Settings › Special app access › Usage access). The two look identical here, " +
                    "so confirm the permission before concluding nothing was used.",
            )
        }
        val packageManager = context.packageManager
        val rows = usageByPackage.entries
            .sortedByDescending { it.value }
            .mapNotNull { entry ->
                val label = runCatching {
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(entry.key, 0)).toString()
                }.getOrDefault(entry.key)
                if (query != null && !label.contains(query, ignoreCase = true) && !entry.key.contains(query, true)) {
                    null
                } else {
                    buildMap<String, Any> {
                        put("package", entry.key)
                        put("label", label)
                        put("foreground_minutes", entry.value / 60_000)
                    }
                }
            }
        return buildMap {
            put("success", true)
            put("count", rows.size.coerceAtMost(limit))
            put("truncated", rows.size > limit)
            put("window_days", days)
            put("apps", rows.take(limit))
        }
    }

    /** A cheap snapshot of the device itself: build, storage, and the class of hardware. */
    private fun deviceState(): Map<String, Any> {
        val stat = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
        val freeMb = stat?.let { it.availableBytes / (1024 * 1024) }
        val totalMb = stat?.let { it.totalBytes / (1024 * 1024) }
        return buildMap {
            put("success", true)
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("android_release", Build.VERSION.RELEASE)
            put("sdk_int", Build.VERSION.SDK_INT)
            put("app_package", context.packageName)
            if (freeMb != null) put("free_storage_mb", freeMb)
            if (totalMb != null) put("total_storage_mb", totalMb)
        }
    }

    companion object {
        const val ID = "android_context"

        val toolInfo = ToolInfo(
            id = ID,
            name = "Android Context",
            description = "Clipboard, recent photos, recent app usage and a device snapshot",
            isEnabled = false,
            userToggleable = false,
        )

        private const val DEFAULT_LIMIT = 20
        private const val MAX_LIMIT = 100
        private const val DEFAULT_DAYS = 7
        private const val MAX_DAYS = 365
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val MAX_CLIPBOARD_CHARS = 4_000
    }
}
