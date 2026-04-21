# Appearance

**Last verified:** 2026-04-21

Kai's theme follows the operating system's dark/light preference and has no in-app theme switcher. Dark mode uses pure-black (`#000000`) backgrounds on every platform — on OLED panels this saves power, and visually it reads as a modern "lights-out" aesthetic. Cards, dialogs, bottom sheets, and menus remain visually lifted because only the lowest surface tiers are flattened to black; container tiers keep their default Material 3 elevation.

## Behavior

- **Light mode**: unchanged Material 3 light scheme.
- **Dark mode**: `background`, `surface`, and `surfaceContainerLowest` render pure black. `surfaceContainer`, `surfaceContainerHigh`, and `surfaceContainerHighest` retain their default Material 3 dark values so elevated components remain visible against the background. `onBackground` / `onSurface` stay white.
- **Material You (Android 12+)**: wallpaper-derived accent colors (`primary`, `secondary`, `tertiary`) still apply. The pure-black override is layered on top of the dynamic scheme so accents and buttons continue to track the wallpaper.
- **System-following**: `isSystemInDarkTheme()` decides between the two schemes. There is no setting to force dark or light mode inside Kai.

## Component guidance

When adding new surfaces in dark mode, **do not** bind fills to `surface` if the element should stand out from the page background — it will be invisible. Use `surfaceContainer` (or higher) for anything that represents a raised card, pill, or control. `surface` is equivalent to the background in this theme and should only back elements that are meant to be flush with the page.

## Key Files

| File | Purpose |
|------|---------|
| `composeApp/.../ui/Theme.kt` | `DarkColorScheme` / `LightColorScheme` constants; `withBlackBackground()` extension that flattens a dynamic dark scheme to pure black |
| `composeApp/.../App.kt` | Default `Theme` wrapper — picks `DarkColorScheme` or `LightColorScheme` via `isSystemInDarkTheme()` |
| `androidApp/.../MainActivity.kt` | Android entry — applies Material You via `dynamicDarkColorScheme(ctx).withBlackBackground()` on API 31+, falls back to `DarkColorScheme` otherwise |
| `composeApp/.../iosMain/.../MainViewController.kt` | iOS entry — uses common `App` defaults |
| `composeApp/.../desktopMain/.../main.kt` | Desktop entry — uses common `App` defaults |
