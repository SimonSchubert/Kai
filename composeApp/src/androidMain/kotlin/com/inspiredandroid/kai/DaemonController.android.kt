package com.inspiredandroid.kai

import android.content.Context
import android.content.Intent
import android.os.Build
import org.koin.java.KoinJavaComponent.inject

actual fun createDaemonController(): DaemonController = AndroidDaemonController()

class AndroidDaemonController : DaemonController {

    private val context: Context by inject(Context::class.java)

    override fun start() {
        val intent = Intent(context, DaemonService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    override fun stop() {
        val intent = Intent(context, DaemonService::class.java)
        context.stopService(intent)
    }
}
