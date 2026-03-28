package com.inspiredandroid.kai.sandbox

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val sandboxModule = module {
    single<LinuxSandboxManager> { LinuxSandboxManager(androidContext()) }
}
