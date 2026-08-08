package com.inspiredandroid.kai

interface DaemonController {
    fun start()
    fun stop()
}

/** Background daemon mode is Android-only; every other target gets this. */
class NoOpDaemonController : DaemonController {
    override fun start() {}
    override fun stop() {}
}

expect fun createDaemonController(): DaemonController
