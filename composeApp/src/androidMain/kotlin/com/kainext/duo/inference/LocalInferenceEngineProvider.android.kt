package com.kainext.duo.inference

actual fun createLocalInferenceEngine(): LocalInferenceEngine? = if (android.os.Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) LiteRTInferenceEngine() else null
