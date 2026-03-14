package com.inspiredandroid.kai.data

import org.jetbrains.compose.resources.DrawableResource

data class ServiceEntry(
    val instanceId: String,
    val serviceId: String,
    val serviceName: String,
    val modelId: String,
    val icon: DrawableResource,
)
