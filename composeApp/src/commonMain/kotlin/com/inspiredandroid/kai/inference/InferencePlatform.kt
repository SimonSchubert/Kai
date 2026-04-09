package com.inspiredandroid.kai.inference

expect fun getModelStorageDirectory(): String

expect fun getModelCacheDirectory(): String

expect fun getAvailableMemoryBytes(): Long

expect fun getTotalMemoryBytes(): Long

expect fun getAvailableDiskSpaceBytes(path: String): Long

expect fun startDownloadNotificationService()

expect fun stopDownloadNotificationService()

expect fun updateDownloadNotificationProgress(percent: Int)
