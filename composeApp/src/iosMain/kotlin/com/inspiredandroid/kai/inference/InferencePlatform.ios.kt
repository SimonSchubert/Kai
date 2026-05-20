@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.inspiredandroid.kai.inference

import com.inspiredandroid.kai.getAppFilesDirectory
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSNumber
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

private val modelStorageDir: String by lazy {
    val dir = getAppFilesDirectory() + "/litert_models"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
    dir
}

private val modelCacheDir: String by lazy {
    val paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
    val cacheRoot = paths.firstOrNull() as? String ?: getAppFilesDirectory()
    val dir = "$cacheRoot/litert"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
    dir
}

actual fun getModelStorageDirectory(): String = modelStorageDir

actual fun getModelCacheDirectory(): String = modelCacheDir

// iOS surfaces low-memory conditions via UIApplication.didReceiveMemoryWarning rather
// than a queryable "available" value. Skip the pre-check (matches desktop) and rely on
// the system warning + initialize() retry-with-CPU-backend path.
actual fun getAvailableMemoryBytes(): Long = Long.MAX_VALUE

@OptIn(ExperimentalForeignApi::class)
actual fun getTotalMemoryBytes(): Long = NSProcessInfo.processInfo.physicalMemory.toLong()

actual fun getAvailableDiskSpaceBytes(path: String): Long {
    val attrs = NSFileManager.defaultManager.attributesOfFileSystemForPath(path, null)
    val free = attrs?.get(NSFileSystemFreeSize) as? NSNumber
    return free?.longLongValue ?: 0L
}

// iOS has no foreground-service equivalent. Download progress is surfaced in-app; if a
// user-visible notification is needed later, wire it through UNUserNotificationCenter.
actual fun startDownloadNotificationService() {}
actual fun stopDownloadNotificationService() {}
actual fun updateDownloadNotificationProgress(percent: Int) {}
