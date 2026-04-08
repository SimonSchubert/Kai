package com.inspiredandroid.kai.inference

import com.inspiredandroid.kai.getAppFilesDirectory
import java.io.File
import java.lang.management.ManagementFactory

actual fun getModelStorageDirectory(): String = getAppFilesDirectory() + "/litert_models"

actual fun getModelCacheDirectory(): String = System.getProperty("java.io.tmpdir") ?: getAppFilesDirectory()

actual fun getAvailableMemoryBytes(): Long = Long.MAX_VALUE // Desktop OSes manage memory via swap and cache eviction; skip the check

actual fun getAvailableDiskSpaceBytes(path: String): Long {
    var dir = File(path)
    while (!dir.exists()) {
        dir = dir.parentFile ?: return 0L
    }
    return dir.usableSpace
}

actual fun startDownloadNotificationService() {
    // No foreground service needed on desktop
}

actual fun stopDownloadNotificationService() {
    // No foreground service needed on desktop
}

actual fun updateDownloadNotificationProgress(percent: Int) {
    // No notification on desktop
}
