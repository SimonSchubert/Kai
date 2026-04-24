# Encryption & Secure Storage

**Last verified:** 2026-04-24

All sensitive data (API keys, email passwords, conversation history) is stored through a platform-specific `Settings` implementation selected by `createSecureSettings()`. Each platform uses the strongest available mechanism.

## Sensitive Data

The following data is stored in secure settings:
- Service API keys (per-instance)
- Email account passwords
- Conversation history (may contain user messages with file attachments)
- Encryption key for legacy conversation migration

## Platform Implementations

### Android
- **Mechanism:** `EncryptedSharedPreferences` via `dev.spght:encryptedprefs-ktx` (community fork of deprecated `androidx.security:security-crypto`)
- **Key encryption:** AES-256-SIV
- **Value encryption:** AES-256-GCM
- **Key management:** Android Keystore (`MasterKey` with `AES256_GCM` scheme)
- **Size limit:** ~2 MB per value (SharedPreferences limit)
- **File location:** App-private `kai_secure_prefs` SharedPreferences

### iOS
- **Mechanism:** `KeychainSettings` (`com.russhwolf/multiplatform-settings`)
- **Encryption:** Hardware-backed Keychain encryption (AES-256-GCM via Secure Enclave on supported devices)
- **Key management:** Managed by iOS Keychain Services
- **Size limit:** Effectively unlimited
- **Service identifier:** `com.inspiredandroid.kai`

### Desktop (macOS, Windows, Linux)
- **Mechanism:** `EncryptedFileSettings` — custom file-backed `Settings` implementation
- **Encryption:** AES-256-GCM via `javax.crypto`
- **Key management:** 256-bit random key generated via `SecureRandom`, stored at `~/.kai/settings.key`
- **IV:** 12-byte random IV per write, prepended to ciphertext
- **Authentication tag:** 128-bit GCM tag (integrity + authenticity)
- **File format:** `[12-byte IV][AES-GCM ciphertext + tag]`
- **File location:** `~/.kai/settings.aes`
- **Size limit:** None (file-based)
- **Migration:** On first run, migrates existing data from Java Preferences (`Preferences.userRoot()`) and clears the old store. Java Preferences was the previous backend but has an 8 KB per-value hard limit.

### Web (WASM)
- **Mechanism:** `StorageSettings` (browser `localStorage`)
- **Encryption:** None (browser sandbox provides isolation)
- **Size limit:** ~5 MB total (browser-dependent)

## Legacy Conversation Storage

Prior to the current Settings-based storage, conversations were stored in an encrypted file (`conversations.enc`) using XOR encryption with a 32-byte random key. This is migrated automatically on first load:

1. Read `conversations.enc` from the app files directory
2. Decrypt using XOR with the key from `encryption_key` in settings
3. Persist to current Settings-based storage
4. Delete the legacy file

The XOR encryption key is retained in settings for any devices that haven't migrated yet.

## Key Files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/.../Platform.kt` | `expect fun createSecureSettings()` declaration |
| `composeApp/src/androidMain/.../Platform.android.kt` | Android EncryptedSharedPreferences setup |
| `composeApp/src/iosMain/.../Platform.ios.kt` | iOS KeychainSettings setup |
| `composeApp/src/desktopMain/.../Platform.jvm.kt` | Desktop EncryptedFileSettings wiring |
| `composeApp/src/desktopMain/.../data/EncryptedFileSettings.kt` | AES-256-GCM file-backed Settings implementation |
| `composeApp/src/wasmJsMain/.../Platform.wasmJs.kt` | Web localStorage setup |
| `composeApp/src/commonMain/.../data/ConversationStorage.kt` | Legacy XOR migration logic |
| `composeApp/src/commonMain/.../data/AppSettings.kt` | Settings access layer for all app data |
