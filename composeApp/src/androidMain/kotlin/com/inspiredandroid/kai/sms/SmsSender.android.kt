package com.inspiredandroid.kai.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

actual class SmsSender actual constructor() {
    private val context: Context by inject(Context::class.java)

    actual fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.SEND_SMS,
    ) == PackageManager.PERMISSION_GRANTED

    actual suspend fun send(address: String, body: String): SmsSendResult {
        if (!hasPermission()) {
            return SmsSendResult.Failure("SEND_SMS permission not granted")
        }
        if (address.isBlank()) return SmsSendResult.Failure("Missing address")
        if (body.isEmpty()) return SmsSendResult.Failure("Empty body")

        return withContext(Dispatchers.IO) {
            try {
                val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                val parts = manager.divideMessage(body)
                if (parts.size <= 1) {
                    manager.sendTextMessage(address, null, body, null, null)
                } else {
                    manager.sendMultipartTextMessage(address, null, parts, null, null)
                }
                SmsSendResult.Success
            } catch (e: Exception) {
                SmsSendResult.Failure(e.message ?: e::class.simpleName ?: "Send failed")
            }
        }
    }
}
