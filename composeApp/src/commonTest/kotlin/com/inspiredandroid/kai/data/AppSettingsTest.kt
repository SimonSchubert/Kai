package com.inspiredandroid.kai.data

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    // region Base URL v1 migration

    @Test
    fun `base URL migration appends v1 to plain host URL`() {
        val settings = MapSettings()
        val appSettings = AppSettings(settings)

        appSettings.setConfiguredServiceInstances(
            listOf(ServiceInstance("compat1", "openai-compatible")),
        )
        appSettings.setInstanceBaseUrl("compat1", "http://localhost:11434")

        appSettings.migrateBaseUrlsToV1PathIfNeeded()

        assertEquals("http://localhost:11434/v1", appSettings.getInstanceBaseUrl("compat1"))
    }

    @Test
    fun `base URL migration does not double-append v1`() {
        val settings = MapSettings()
        val appSettings = AppSettings(settings)

        appSettings.setConfiguredServiceInstances(
            listOf(ServiceInstance("compat1", "openai-compatible")),
        )
        appSettings.setInstanceBaseUrl("compat1", "http://localhost:11434/v1")

        appSettings.migrateBaseUrlsToV1PathIfNeeded()

        assertEquals("http://localhost:11434/v1", appSettings.getInstanceBaseUrl("compat1"))
    }

    @Test
    fun `base URL migration handles trailing slash`() {
        val settings = MapSettings()
        val appSettings = AppSettings(settings)

        appSettings.setConfiguredServiceInstances(
            listOf(ServiceInstance("compat1", "openai-compatible")),
        )
        appSettings.setInstanceBaseUrl("compat1", "http://localhost:11434/")

        appSettings.migrateBaseUrlsToV1PathIfNeeded()

        assertEquals("http://localhost:11434/v1", appSettings.getInstanceBaseUrl("compat1"))
    }

    @Test
    fun `base URL migration preserves custom version path`() {
        val settings = MapSettings()
        val appSettings = AppSettings(settings)

        appSettings.setConfiguredServiceInstances(
            listOf(ServiceInstance("compat1", "openai-compatible")),
        )
        appSettings.setInstanceBaseUrl("compat1", "https://my-provider.com/api/v1")

        appSettings.migrateBaseUrlsToV1PathIfNeeded()

        assertEquals("https://my-provider.com/api/v1", appSettings.getInstanceBaseUrl("compat1"))
    }

    @Test
    fun `base URL migration skips non-OpenAI-compatible instances`() {
        val settings = MapSettings()
        val appSettings = AppSettings(settings)

        appSettings.setConfiguredServiceInstances(
            listOf(ServiceInstance("openai", "openai")),
        )
        appSettings.setInstanceBaseUrl("openai", "https://api.openai.com")

        appSettings.migrateBaseUrlsToV1PathIfNeeded()

        // Should not be modified
        assertEquals("https://api.openai.com", appSettings.getInstanceBaseUrl("openai"))
    }

    @Test
    fun `base URL migration only runs once`() {
        val settings = MapSettings()
        val appSettings = AppSettings(settings)

        appSettings.setConfiguredServiceInstances(
            listOf(ServiceInstance("compat1", "openai-compatible")),
        )
        appSettings.setInstanceBaseUrl("compat1", "http://localhost:11434")

        appSettings.migrateBaseUrlsToV1PathIfNeeded()
        assertEquals("http://localhost:11434/v1", appSettings.getInstanceBaseUrl("compat1"))

        // Reset to a plain URL — second run should NOT migrate
        appSettings.setInstanceBaseUrl("compat1", "http://other-host:8080")
        appSettings.migrateBaseUrlsToV1PathIfNeeded()
        assertEquals("http://other-host:8080", appSettings.getInstanceBaseUrl("compat1"))
    }

    @Test
    fun `base URL migration also migrates legacy per-service key`() {
        val settings = MapSettings()
        val appSettings = AppSettings(settings)

        appSettings.setConfiguredServiceInstances(emptyList())
        appSettings.setBaseUrl(Service.OpenAICompatible, "http://localhost:11434")

        appSettings.migrateBaseUrlsToV1PathIfNeeded()

        assertEquals("http://localhost:11434/v1", appSettings.getBaseUrl(Service.OpenAICompatible))
    }

    @Test
    fun `base URL migration skips blank base URLs`() {
        val settings = MapSettings()
        val appSettings = AppSettings(settings)

        appSettings.setConfiguredServiceInstances(
            listOf(ServiceInstance("compat1", "openai-compatible")),
        )
        // Don't set a base URL — it should remain blank
        assertFalse(settings.getBoolean("base_url_v1_migration_complete", false))

        appSettings.migrateBaseUrlsToV1PathIfNeeded()

        assertEquals("", appSettings.getInstanceBaseUrl("compat1"))
        assertTrue(settings.getBoolean("base_url_v1_migration_complete", false))
    }

    // endregion

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
