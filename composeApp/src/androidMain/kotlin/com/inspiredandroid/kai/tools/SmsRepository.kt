package com.inspiredandroid.kai.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat

private const val TAG = "SmsRepository"
private const val MAX_CONVERSATIONS = 10
private const val MAX_MESSAGES_PER_CONVERSATION = 5

data class SmsMessage(
    val id: Long,
    val body: String,
    val timestamp: Long,
    val isFromMe: Boolean,
)

data class SmsConversation(
    val conversationId: String,
    val contactName: String?,
    val messages: List<SmsMessage>,
)

sealed class SmsResult {
    data class Success(val conversations: List<SmsConversation>) : SmsResult()
    data class Error(val message: String) : SmsResult()
}

sealed class SmsSendResult {
    data class Success(val conversationId: String) : SmsSendResult()
    data class Error(val message: String) : SmsSendResult()
}

class SmsRepository(
    private val context: Context,
    private val permissionController: SmsPermissionController,
) {
    // Maps conversationId -> phoneNumber (uses thread ID for stable IDs across queries)
    private val conversationIdMap = mutableMapOf<String, String>()

    fun hasReadSmsPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_SMS,
    ) == PackageManager.PERMISSION_GRANTED

    fun hasSendSmsPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.SEND_SMS,
    ) == PackageManager.PERMISSION_GRANTED

    fun hasReadContactsPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Returns the phone number for a given conversation ID.
     * Returns null if the conversation ID is not found.
     */
    fun getPhoneNumberForConversation(conversationId: String): String? = conversationIdMap[conversationId]

    suspend fun getRecentConversations(): SmsResult {
        Log.d(TAG, "getRecentConversations called")

        // Request SMS read permission if not already granted
        if (!hasReadSmsPermission()) {
            Log.d(TAG, "SMS permission not granted, requesting...")
            val granted = permissionController.requestReadPermission()
            Log.d(TAG, "SMS permission request result: $granted")
            if (!granted) {
                return SmsResult.Error("SMS read permission denied. Please enable SMS access in Settings to check messages.")
            }
        } else {
            Log.d(TAG, "SMS permission already granted")
        }

        // Request contacts permission if not already granted (for contact name lookup)
        val hasContactsAccess = if (!hasReadContactsPermission()) {
            Log.d(TAG, "Contacts permission not granted, requesting...")
            val granted = permissionController.requestContactsPermission()
            Log.d(TAG, "Contacts permission request result: $granted")
            granted
        } else {
            Log.d(TAG, "Contacts permission already granted")
            true
        }

        return try {
            val conversations = queryRecentConversations(hasContactsAccess)
            SmsResult.Success(conversations)
        } catch (e: Exception) {
            Log.e(TAG, "Error querying SMS", e)
            SmsResult.Error("Error reading messages: ${e.message}")
        }
    }

    private fun queryRecentConversations(hasContactsAccess: Boolean): List<SmsConversation> {
        val conversationMessages = mutableMapOf<Long, MutableList<SmsMessage>>()
        val threadAddresses = mutableMapOf<Long, String>()
        val threadLastTimestamp = mutableMapOf<Long, Long>()

        // Query all SMS messages, ordered by date descending
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
        )

        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(Telephony.Sms._ID)
            val threadIdIndex = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
            val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)
            val typeIndex = cursor.getColumnIndex(Telephony.Sms.TYPE)

            while (cursor.moveToNext()) {
                val threadId = cursor.getLong(threadIdIndex)
                val type = cursor.getInt(typeIndex)
                val address = cursor.getString(addressIndex) ?: continue
                val timestamp = cursor.getLong(dateIndex)

                // Store the address for this thread (first one we see is most recent)
                if (!threadAddresses.containsKey(threadId)) {
                    threadAddresses[threadId] = address
                    threadLastTimestamp[threadId] = timestamp
                }

                // Only collect up to MAX_MESSAGES_PER_CONVERSATION per thread
                val messages = conversationMessages.getOrPut(threadId) { mutableListOf() }
                if (messages.size >= MAX_MESSAGES_PER_CONVERSATION) {
                    continue
                }

                val isFromMe = type == Telephony.Sms.MESSAGE_TYPE_SENT ||
                    type == Telephony.Sms.MESSAGE_TYPE_OUTBOX

                messages.add(
                    SmsMessage(
                        id = cursor.getLong(idIndex),
                        body = cursor.getString(bodyIndex) ?: "",
                        timestamp = timestamp,
                        isFromMe = isFromMe,
                    ),
                )
            }
        }

        // Sort threads by most recent message and take top MAX_CONVERSATIONS
        val sortedThreadIds = threadLastTimestamp.entries
            .sortedByDescending { it.value }
            .take(MAX_CONVERSATIONS)
            .map { it.key }

        // Build conversation ID mapping and result list
        // Use thread ID for stable conversation IDs - same conversation always has the same ID
        return sortedThreadIds.mapNotNull { threadId ->
            val messages = conversationMessages[threadId] ?: return@mapNotNull null
            val phoneNumber = threadAddresses[threadId] ?: return@mapNotNull null

            val conversationId = "conv_$threadId"

            // Store/update mapping for later use when sending
            conversationIdMap[conversationId] = phoneNumber

            val contactName = if (hasContactsAccess) getContactName(phoneNumber) else null

            SmsConversation(
                conversationId = conversationId,
                contactName = contactName,
                messages = messages.sortedBy { it.timestamp }, // Oldest first within conversation
            )
        }
    }

    private fun getContactName(phoneNumber: String): String? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber),
        )

        return try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        cursor.getString(nameIndex)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up contact name", e)
            null
        }
    }

    suspend fun sendSms(conversationId: String, message: String): SmsSendResult {
        Log.d(TAG, "sendSms called: conversationId=$conversationId")

        // Resolve conversation ID to phone number
        val phoneNumber = conversationIdMap[conversationId]
            ?: return SmsSendResult.Error("Invalid conversation ID '$conversationId'. You can only reply to conversations from the recent SMS list.")

        // Request permission if not already granted
        if (!hasSendSmsPermission()) {
            Log.d(TAG, "Permission not granted, requesting...")
            val granted = permissionController.requestSendPermission()
            Log.d(TAG, "Permission request result: $granted")
            if (!granted) {
                return SmsSendResult.Error("SMS send permission denied. Please enable SMS access in Settings to send messages.")
            }
        } else {
            Log.d(TAG, "Permission already granted")
        }

        return try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            if (smsManager == null) {
                return SmsSendResult.Error("SMS service not available on this device.")
            }

            // Handle long messages by splitting them
            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            }

            Log.d(TAG, "SMS sent successfully to conversation $conversationId")
            SmsSendResult.Success(conversationId)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS", e)
            SmsSendResult.Error("Failed to send SMS: ${e.message}")
        }
    }
}
