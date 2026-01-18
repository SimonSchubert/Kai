package com.inspiredandroid.kai

import com.russhwolf.settings.Settings

class AppOpenTracker(private val settings: Settings) {

    fun trackAppOpen(): Int {
        val currentCount = settings.getInt(Key.APP_OPENS, 0)
        val newCount = currentCount + 1
        settings.putInt(Key.APP_OPENS, newCount)
        return newCount
    }

    fun getAppOpens(): Int = settings.getInt(Key.APP_OPENS, 0)
}
