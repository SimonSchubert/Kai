package com.inspiredandroid.kai

interface DaemonController {
    fun start()
    fun stop()
}

expect fun createDaemonController(): DaemonController
