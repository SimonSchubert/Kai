# Appearance

**Last verified:** 2026-04-22

Kai's theme follows the operating system's dark/light preference and has no in-app theme switcher. Dark mode defaults to a soft dark background (`#121212`) with slightly lighter surfaces (`#1E1E1E`). An opt-in **OLED mode** setting flattens the background and low surface tiers to pure black (`#000000`) for users who want to save power on OLED panels. Cards, dialogs, bottom sheets, and menus remain visually lifted in either mode because only the lowest surface tiers are affected; container tiers keep their default Material 3 elevation.

## Behavior

- **Light mode**: unchanged Material 3 light scheme. The OLED toggle has no effect.
- **Dark mode (default)**: `background` renders `#121212` and `surface` renders `#1E1E1E`. `surfaceContainer`, `surfaceContainerHigh`, and `surfaceContainerHighest` use their default Material 3 dark values so elevated components remain visible. `onBackground` / `onSurface` stay white.
- **Dark mode with OLED mode enabled**: `background`, `surface`, and `surfaceContainerLowest` render pure black. The elevated `surfaceContainer*` tiers are unchanged so cards and menus stay visible against black.
- **Material You (Android 12+)**: wallpaper-derived accent colors (`primary`, `secondary`, `tertiary`) always apply. When OLED mode is on, the black override is layered on top of the dynamic scheme so accents and buttons continue to track the wallpaper.
- **System-following**: `isSystemInDarkTheme()` decides between light and dark schemes. There is no setting to force dark or light mode inside Kai.
- **Reactivity**: toggling OLED mode in Settings recomposes the theme immediately without an app restart.

## Component guidance

When adding new surfaces in dark mode, **do not** bind fills to `surface` if the element should stand out from the page background with OLED mode on — in OLED mode `surface` becomes black and the element will be invisible against the background. Use `surfaceContainer` (or higher) for anything that represents a raised card, pill, or control.

## Key Files

| File | Purpose |
|------|---------|
| `composeApp/.../ui/Theme.kt` | `DarkColorScheme` / `LightColorScheme` constants; `withBlackBackground()` extension that flattens a dark scheme to pure black |
| `composeApp/.../App.kt` | Shared `AppContent` — observes `AppSettings.oledModeFlow` and applies `withBlackBackground()` when OLED mode is on and the system is in dark mode |
| `composeApp/.../data/AppSettings.kt` | `oledModeFlow` / `isOledModeEnabled()` / `setOledModeEnabled()` — the persistent flag |
| `composeApp/.../ui/settings/SettingsScreen.kt` | `OledModeToggle` in the General tab |
| `androidApp/.../MainActivity.kt` | Android entry — picks a base dynamic or static dark scheme. The OLED transform is applied downstream in `AppContent`. |
| `androidApp/.../res/values-night/styles.xml` | Pre-Compose window background set to `#FF121212` to match the default dark frame |
| `composeApp/.../iosMain/.../MainViewController.kt` | iOS entry — uses common `App` defaults |
| `composeApp/.../desktopMain/.../main.kt` | Desktop entry — uses common `App` defaults |
