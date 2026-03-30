package com.inspiredandroid.kai

import android.app.Application
import com.inspiredandroid.kai.sandbox.sandboxModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class KaiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@KaiApplication)
            modules(appModule, sandboxModule)
        }
    }
}
