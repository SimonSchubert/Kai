package com.inspiredandroid.kai.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.inspiredandroid.kai.data.SmsMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

// Whether READ_SMS is declared in the merged manifest. The `foss` flavor declares
// it, `playStore` does not — so this is a compile-time property per flavor, safe
// to cache for the process lifetime. Shared with Platform.android.kt's
// `isSmsSupported`.
internal fun Context.declaresReadSms(): Boolean = try {
    packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        .requestedPermissions?.contains(Manifest.permission.READ_SMS) == true
} catch (_: Exception) {
    false
}

actual class SmsReader actual constructor() {
    private val context: Context by inject(Context::class.java)
    private val supported: Boolean by lazy { context.declaresReadSms() }

    actual fun isSupported(): Boolean = supported

    actual fun hasPermission(): Boolean {
        if (!supported) return false
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    actual suspend fun readInboxSince(lastSeenId: Long, limit: Int): List<SmsMessage> {
        if (!hasPermission()) return emptyList()
        return withContext(Dispatchers.IO) {
            query(
                selection = "${Telephony.Sms.TYPE} = ? AND ${Telephony.Sms._ID} > ?",
                selectionArgs = arrayOf(
                    Telephony.Sms.MESSAGE_TYPE_INBOX.toString(),
                    lastSeenId.toString(),
                ),
                // ASC so we fetch oldest-new-message first; overflow past `limit`
                // picks up on the next poll.
                sortOrder = "${Telephony.Sms._ID} ASC LIMIT $limit",
            )
        }
    }

    actual suspend fun readById(id: Long): SmsMessage? {
        if (!hasPermission()) return null
        return withContext(Dispatchers.IO) {
            query(
                selection = "${Telephony.Sms._ID} = ?",
                selectionArgs = arrayOf(id.toString()),
                sortOrder = null,
            ).firstOrNull()
        }
    }

    actual suspend fun search(query: String, limit: Int): List<SmsMessage> {
        if (!hasPermission()) return emptyList()
        if (query.isBlank()) return emptyList()
        // No ESCAPE clause on the LIKE, so `%` / `_` in the user's query act as
        // wildcards — fine for free-text search against SMS.
        val needle = "%$query%"
        return withContext(Dispatchers.IO) {
            query(
                selection = "${Telephony.Sms.TYPE} = ? AND " +
                    "(${Telephony.Sms.ADDRESS} LIKE ? OR ${Telephony.Sms.BODY} LIKE ?)",
                selectionArgs = arrayOf(
                    Telephony.Sms.MESSAGE_TYPE_INBOX.toString(),
                    needle,
                    needle,
                ),
                sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $limit",
            )
        }
    }

    actual suspend fun currentMaxInboxId(): Long {
        if (!hasPermission()) return 0L
        return withContext(Dispatchers.IO) {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms.TYPE} = ?",
                arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString()),
                "${Telephony.Sms._ID} DESC LIMIT 1",
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            } ?: 0L
        }
    }

    private fun query(
        selection: String,
        selectionArgs: Array<String>,
        sortOrder: String?,
    ): List<SmsMessage> = try {
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            PROJECTION,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateCol = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val readCol = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
            buildList {
                while (cursor.moveToNext()) {
                    val body = cursor.getString(bodyCol).orEmpty()
                    add(
                        SmsMessage(
                            id = cursor.getLong(idCol),
                            address = cursor.getString(addressCol).orEmpty(),
                            date = cursor.getLong(dateCol),
                            preview = body.take(PREVIEW_CHARS),
                            body = body,
                            read = cursor.getInt(readCol) != 0,
                        ),
                    )
                }
            }
        } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    companion object {
        private const val PREVIEW_CHARS = 200
        private val PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
        )
    }
}
