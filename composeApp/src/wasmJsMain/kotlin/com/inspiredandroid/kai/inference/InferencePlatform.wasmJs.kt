package com.inspiredandroid.kai.inference

actual fun getModelStorageDirectory(): String = ""

actual fun getModelCacheDirectory(): String = ""

actual fun getAvailableMemoryBytes(): Long = Long.MAX_VALUE

actual fun getTotalMemoryBytes(): Long = Long.MAX_VALUE

actual fun getAvailableDiskSpaceBytes(path: String): Long = 0L

actual fun startDownloadNotificationService() {}

actual fun stopDownloadNotificationService() {}

actual fun updateDownloadNotificationProgress(percent: Int) {}
