package com.inspiredandroid.kai

import android.app.Application
import com.inspiredandroid.kai.sandbox.sandboxModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class KaiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@KaiApplication)
            modules(appModule, sandboxModule)
        }

        // Defer daemon auto-start off the main thread
        MainScope().launch(Dispatchers.Default) {
            val daemonController: DaemonController = get()
            if (daemonController is AndroidDaemonController && daemonController.shouldAutoStart()) {
                daemonController.start()
            }
        }
    }
}
