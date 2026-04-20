package com.inspiredandroid.kai

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import com.inspiredandroid.kai.data.AppSettings
import org.koin.java.KoinJavaComponent.inject

actual fun createDaemonController(): DaemonController = AndroidDaemonController()

class AndroidDaemonController : DaemonController {

    private val context: Context by inject(Context::class.java)
    private val appSettings: AppSettings by inject(AppSettings::class.java)

    fun shouldAutoStart(): Boolean = appSettings.isDaemonEnabled()

    override fun start() {
        try {
            val intent = Intent(context, DaemonService::class.java)
            context.startForegroundService(intent)
        } catch (_: ForegroundServiceStartNotAllowedException) {
            // App is not in a foreground state — cannot start foreground service (Android 12+)
        }
    }

    override fun stop() {
        val intent = Intent(context, DaemonService::class.java)
        context.stopService(intent)
    }
}
