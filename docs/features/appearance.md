# Appearance

**Last verified:** 2026-10-02

Kai has a four-way theme picker — **System**, **Light**, **Dark**, and **OLED** — exposed in Settings on every platform. The default is System, which follows the operating system's dark/light preference. The other three force a specific theme regardless of system state. Dark uses a soft dark background (`#121212`) with slightly lighter surfaces (`#1E1E1E`); OLED flattens the background and the lowest surface tier to pure black (`#000000`) for users who want to save power on OLED panels.

Cards, dialogs, bottom sheets, and menus stay visually lifted in either dark variant because only the lowest surface tiers are affected; container tiers keep their default Material 3 elevation.

## Behavior

- **System**: `isSystemInDarkTheme()` decides between the light and dark schemes.
- **Light**: forces the Material 3 light scheme.
- **Dark**: forces the Material 3 dark scheme. `background` renders `#121212` and `surface` renders `#1E1E1E`. `surfaceContainer*` tiers use their default Material 3 dark values so elevated components remain visible. `onBackground` / `onSurface` stay white.
- **OLED**: forces dark + pure-black override. `background`, `surface`, and `surfaceContainerLowest` render pure black. The elevated `surfaceContainer*` tiers are unchanged so cards and menus stay visible against black.
- **Material You (Android 12+)**: the full wallpaper-derived light/dark schemes replace Kai's built-in palettes, so on these devices Dark uses the dynamic dark background and surfaces rather than `#121212` / `#1E1E1E`. When OLED is selected, the black override is layered on top of the dynamic dark scheme so accents and buttons continue to track the wallpaper. Older Android versions and all other platforms use Kai's built-in purple-accented palettes.
- **System bars (Android)**: status and navigation bars are transparent (edge-to-edge); their icon style follows the resolved theme — Dark and OLED always get light icons, Light always gets dark icons, System follows the OS.
- **Reactivity**: changing the theme picker recomposes the theme immediately without an app restart.

The picker exists on every platform because system theme detection is unreliable on some desktop window systems (notably Linux/Wayland), so users there need an explicit override.

## Component guidance

When adding new surfaces in dark mode, **do not** bind fills to `surface` if the element should stand out from the page background with OLED selected — in OLED `surface` becomes black and the element will be invisible against the background. Use `surfaceContainer` (or higher) for anything that represents a raised card, pill, or control.

Many cards (settings list items, service/MCP/skill/tool cards, the heartbeat and pending-SMS banners in chat, sandbox file browser rows, dynamic UI cards) use a shared adaptive card style: normally a half-transparent `surfaceVariant` fill with no border; with OLED selected the fill becomes transparent and a thin `outlineVariant` border outlines the card instead, keeping the background pure black. New card-like surfaces should reuse this style rather than picking their own fill.

## Key Files

| File | Purpose |
|------|---------|
| `composeApp/.../ui/Theme.kt` | `DarkColorScheme` / `LightColorScheme` constants; `withBlackBackground()` extension that flattens a dark scheme to pure black; adaptive card colors/border/surface helpers that switch to outlined-transparent in OLED |
| `composeApp/.../data/AppSettings.kt` | `ThemeMode` enum; `themeModeFlow` / `getThemeMode()` / `setThemeMode()` — the persistent setting, with one-time migration from the legacy OLED boolean |
| `composeApp/.../App.kt` | Shared `AppContent` — observes `themeModeFlow`, picks `lightColorScheme` / `darkColorScheme` / `darkColorScheme.withBlackBackground()` based on the selected mode |
| `composeApp/.../ui/settings/GeneralSettings.kt` | Theme mode dropdown in the General tab |
| `androidApp/.../MainActivity.kt` | Android entry — supplies dynamic-color light/dark schemes; the resolved `isDarkTheme` (from `themeMode` + system) drives the system-bar icon style |
| `androidApp/.../res/values-night/styles.xml` | Pre-Compose window background set to `#FF121212` to match the default dark frame |
| `composeApp/.../iosMain/.../MainViewController.kt` | iOS entry — uses common `App` defaults |
| `composeApp/.../desktopMain/.../main.kt` | Desktop entry — uses common `App` defaults; also configures HiDPI hints and an initial 1280×800 `WindowState` so the window opens at a usable size on Linux/Wayland |
