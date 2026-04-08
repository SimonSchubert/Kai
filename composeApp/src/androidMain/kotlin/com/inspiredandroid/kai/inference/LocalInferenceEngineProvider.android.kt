package com.inspiredandroid.kai.inference

actual fun createLocalInferenceEngine(): LocalInferenceEngine? = LiteRTInferenceEngine()
