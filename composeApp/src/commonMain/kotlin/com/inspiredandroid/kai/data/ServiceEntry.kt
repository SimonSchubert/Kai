package com.inspiredandroid.kai.data

import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.DrawableResource

@Immutable
data class ServiceEntry(
    val instanceId: String,
    val serviceId: String,
    val serviceName: String,
    val modelId: String,
    val icon: DrawableResource,
)
