package com.inspiredandroid.kai

actual fun createDaemonController(): DaemonController = NoOpDaemonController()

class NoOpDaemonController : DaemonController {
    override fun start() { /* No-op on web */ }
    override fun stop() { /* No-op on web */ }
}
