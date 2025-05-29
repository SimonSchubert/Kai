package com.inspiredandroid.kai

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
actual val isDebugBuild: Boolean = kotlin.native.Platform.isDebugBinary
