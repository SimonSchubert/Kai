package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.getAppFilesDirectory
import com.russhwolf.settings.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val SETTINGS_FILE = "settings.aes"
private const val KEY_FILE = "settings.key"
private const val GCM_IV_LENGTH = 12
private const val GCM_TAG_LENGTH = 128

/**
 * AES-256-GCM encrypted file-backed Settings for desktop.
 * Replaces Java Preferences which has an 8KB per-value limit.
 */
class EncryptedFileSettings : Settings {

    private val json = Json { encodeDefaults = true }
    private val map: MutableMap<String, String> = mutableMapOf()

    init {
        load()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyFile = File(getAppFilesDirectory(), KEY_FILE)
        if (keyFile.exists()) {
            val keyBytes = keyFile.readBytes()
            if (keyBytes.size == 32) return SecretKeySpec(keyBytes, "AES")
        }
        val keyBytes = ByteArray(32)
        SecureRandom().nextBytes(keyBytes)
        keyFile.writeBytes(keyBytes)
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun load() {
        val file = File(getAppFilesDirectory(), SETTINGS_FILE)
        if (!file.exists()) {
            migrateFromPreferences()
            return
        }
        try {
            val encrypted = file.readBytes()
            if (encrypted.size < GCM_IV_LENGTH) return
            val iv = encrypted.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = encrypted.copyOfRange(GCM_IV_LENGTH, encrypted.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val decrypted = cipher.doFinal(ciphertext).decodeToString()
            val loaded: Map<String, String> = json.decodeFromString(decrypted)
            map.putAll(loaded)
        } catch (_: Exception) {
            // Corrupted file — start fresh
        }
    }

    private fun migrateFromPreferences() {
        try {
            val prefs = java.util.prefs.Preferences.userRoot().node("com.inspiredandroid.kai")
            for (key in prefs.keys()) {
                map[key] = prefs.get(key, "")
            }
            if (map.isNotEmpty()) {
                persist()
                prefs.clear()
                prefs.flush()
            }
        } catch (_: Exception) {
            // Migration is best-effort
        }
    }

    private fun persist() {
        val plaintext = json.encodeToString(map.toMap())
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val encrypted = cipher.doFinal(plaintext.encodeToByteArray())
        val file = File(getAppFilesDirectory(), SETTINGS_FILE)
        file.writeBytes(iv + encrypted)
    }

    override val keys: Set<String> get() = map.keys.toSet()
    override val size: Int get() = map.size

    override fun clear() {
        map.clear()
        persist()
    }

    override fun remove(key: String) {
        map.remove(key)
        persist()
    }

    override fun hasKey(key: String): Boolean = key in map

    override fun putInt(key: String, value: Int) {
        map[key] = value.toString()
        persist()
    }
    override fun getInt(key: String, defaultValue: Int): Int = map[key]?.toIntOrNull() ?: defaultValue
    override fun getIntOrNull(key: String): Int? = map[key]?.toIntOrNull()

    override fun putLong(key: String, value: Long) {
        map[key] = value.toString()
        persist()
    }
    override fun getLong(key: String, defaultValue: Long): Long = map[key]?.toLongOrNull() ?: defaultValue
    override fun getLongOrNull(key: String): Long? = map[key]?.toLongOrNull()

    override fun putString(key: String, value: String) {
        map[key] = value
        persist()
    }
    override fun getString(key: String, defaultValue: String): String = map[key] ?: defaultValue
    override fun getStringOrNull(key: String): String? = map[key]

    override fun putFloat(key: String, value: Float) {
        map[key] = value.toString()
        persist()
    }
    override fun getFloat(key: String, defaultValue: Float): Float = map[key]?.toFloatOrNull() ?: defaultValue
    override fun getFloatOrNull(key: String): Float? = map[key]?.toFloatOrNull()

    override fun putDouble(key: String, value: Double) {
        map[key] = value.toString()
        persist()
    }
    override fun getDouble(key: String, defaultValue: Double): Double = map[key]?.toDoubleOrNull() ?: defaultValue
    override fun getDoubleOrNull(key: String): Double? = map[key]?.toDoubleOrNull()

    override fun putBoolean(key: String, value: Boolean) {
        map[key] = value.toString()
        persist()
    }
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = map[key]?.toBooleanStrictOrNull() ?: defaultValue
    override fun getBooleanOrNull(key: String): Boolean? = map[key]?.toBooleanStrictOrNull()
}
