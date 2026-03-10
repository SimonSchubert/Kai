package com.inspiredandroid.kai.data

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppSettingsTest {

    @Test
    fun `migration only runs once so deleted services stay deleted`() {
        val settings = MapSettings()
        val appSettings = AppSettings(settings)

        // Set up a legacy API key for OpenAI
        appSettings.setApiKey(Service.OpenAI, "sk-test-key")

        // First run — migrates and adds the service
        appSettings.migrateConfiguredServicesIfNeeded()
        assertEquals(1, appSettings.getConfiguredServiceInstances().size)
        assertEquals(Service.OpenAI.id, appSettings.getConfiguredServiceInstances()[0].serviceId)

        // User deletes the service
        appSettings.setConfiguredServiceInstances(emptyList())
        appSettings.removeInstanceSettings(Service.OpenAI.id)
        assertTrue(appSettings.getConfiguredServiceInstances().isEmpty())

        // Second run — flag prevents re-migration, deleted service stays deleted
        appSettings.migrateConfiguredServicesIfNeeded()
        assertTrue(appSettings.getConfiguredServiceInstances().isEmpty())
    }

    @Test
    fun `Anthropic credential persistence via instance settings`() {
        val settings = MapSettings()
        val appSettings = AppSettings(settings)

        val instanceId = appSettings.generateInstanceId(Service.Anthropic.id)
        assertEquals("anthropic", instanceId)

        appSettings.setInstanceApiKey(instanceId, "sk-ant-test-key")
        assertEquals("sk-ant-test-key", appSettings.getInstanceApiKey(instanceId))

        appSettings.setInstanceModelId(instanceId, "claude-sonnet-4-20250514")
        assertEquals("claude-sonnet-4-20250514", appSettings.getInstanceModelId(instanceId))
    }

    @Test
    fun `migration adds services with legacy API keys`() {
        val settings = MapSettings()
        val appSettings = AppSettings(settings)

        appSettings.setApiKey(Service.OpenAI, "sk-test-key")
        appSettings.setApiKey(Service.Gemini, "gemini-key")

        appSettings.migrateConfiguredServicesIfNeeded()

        val instances = appSettings.getConfiguredServiceInstances()
        assertEquals(2, instances.size)
        assertTrue(instances.any { it.serviceId == Service.OpenAI.id })
        assertTrue(instances.any { it.serviceId == Service.Gemini.id })
    }
}
