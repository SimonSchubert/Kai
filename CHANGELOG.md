## v2.2.1 — 2026-04-20

### Features
- Add local Qwen 3 0.6B model

### Improvements
- Upgrade LiteRT
- Defensive desktop ProGuard rules

## v2.2.0 — 2026-04-16

### Features
- Add custom markdown parser
- Preserve previous Kai UI output and allow editing

### Improvements
- Pulse animation on stop button
- Add missing localizations
- Add missing model info

## v2.1.5 — 2026-04-15

### Features
- Add copy-to-clipboard button for code blocks

### Fixes
- Filter out Gemini API responses marked as internal thoughts
- Add size safeguards for PDF and image compression failures

### Improvements
- Highlight currently selected model in model picker
- Improve Linux sandbox interactive interface
- Adjust Gemma 4 device performance expectations
- Upgrade BouncyCastle and remove version checker dependency
- UI polish and alignment

## v2.1.4 — 2026-04-12

### Features
- Add fast and expert mode toggles for free services
- Add Windows menu entry
- Enrich model catalog with arena scores, release dates, and context windows
- Support multi-file upload for attachments
- Enable simple tools for local Gemma 4
- Select TTS voice based on current locale

### Improvements
- Improve conversation restore on app restart and settings navigation
- Upgrade SDKs

## v2.1.2 — 2026-04-09

### Features
- Add local model context configuration

### Fixes
- Fix undo snackbars not executing after leaving screen

### Improvements
- Improve filetype checks for file attachments

## v2.1.1 — 2026-04-09

### Fixes
- Fix crash when opening Settings on devices without downloaded local models

## v2.1.0 — 2026-04-08

### Features
- Add support for local Gemma 4 via LiteRT
- Add PublicAI, Fireworks, DeepInfra, and OpenCode services

### Improvements
- Add missing localizations

## v2.0.3 — 2026-04-07

### Fixes
- Fix Bouncycastle and Coil proguard rules on desktop

### Improvements
- Simplify and improve chat input UI
- Extract reusable UI components
- Improve heartbeat error messages

## v2.0.2 — 2026-04-06

### Fixes
- Fix toolbar vertical padding
- Fix removeLast crash

### Improvements
- Add custom JSON parser for more flexible and resilient UI parsing
- Improve desktop release performance and reduce app size
- Simplify UI spacing by handling padding definitions locally
- General UI/UX improvements

## v2.0.1 — 2026-04-04

### Features
- Add possibility to define service for heartbeat
- Add additional MCP servers

### Improvements
- Improve kai-ui prompt and split-block parsing
- Replace Lottie animation with custom implementation
- Improve tool use UI
- Add missing localizations
- Clean up implementations

## v2.0.0 — 2026-04-03

### Features
- Add experimental interactive UI screen
- Add conversation import and export
- Add dedicated GitHub Pages screen
- Add custom MCP server headers

### Improvements
- Improve Kai UI parsing
- Use Gemini header auth instead of URL parameter

## v1.12.3 — 2026-04-02

### Features
- Add Dynamic UI — AI can create interactive buttons, forms, and cards inline in chat responses (opt-out in Settings)

### Fixes
- Fix hover pointer not showing over text inside buttons on desktop and web

## v1.12.2 — 2026-03-31

### Fixes
- Fix daemon start crash
- Fix heartbeat thinking in wrong conversation
- Fix leaked path in sandbox

### Improvements
- Improve shell integrations
- Add uninstall linux sandbox confirmation dialog
- Add NDK 29 requirement and reproducible build flags

## v1.12.1 — 2026-03-29

### Improvements
- Replace standard tabs with custom piles
- Build proot from source

## v1.12.0 — 2026-03-29

### Features
- Add Linux sandbox for Android shell commands with ANSI terminal output
- Add AiHubMix service
- Add desktop scrollbars

### Fixes
- Fix macOS window application appearance

### Improvements
- Improve settings UI
- Migrate extended icons to custom icons
- Upgrade Ktor and add slf4j-nop

## v1.11.2 — 2026-03-25

### Improvements
- Improve heartbeat reliability with previous response context and failure logging
- Add proper context compaction for conversations
- Upgrade SDKs

## v1.11.1 — 2026-03-24

### Fixes
- Fix crash when restoring encrypted backups

## v1.11.0 — 2026-03-24

### Features
- Add skills feature
- Support text and PDF file attachments
- Add minimax and zai services
- Show LLM model performance ranking for Splinterlands auto battle
- Add undo snackbars to revert deletions
- Replace re-order arrows with drag and drop

### Improvements
- Move conversations to secure platform-specific encrypted storage
- Replace location-from-IP tool with HTTPS-secured alternative
- Improve UI performance
- Add missing localizations and hover pointers
- Upgrade Compose Multiplatform, Lifecycle, and Spotless SDKs

## v1.10.0 — 2026-03-18

### Features
- Add Splinterlands LLM auto-battle integration
- Add web loading spinner
- List sponsors on free tier UI
- Add bottom scroll pointer icon and rounded corners stop icon

### Fixes
- Fix heartbeat and tasks handling
- Fix icon button sizes

### Improvements
- Upgrade Kotlin to 2.3.20
- Upgrade Koin

## v1.9.8 — 2026-03-15

### Features
- Add scroll to bottom button
- Show selected model on service rows in settings

### Fixes
- Fix model filter

### Improvements
- Improve startup time and migrate deprecated APIs
- Improve performance

## v1.9.7 — 2026-03-14

### Features
- Add HuggingFace, LongCat, Moonshot, Together and Venice service support

### Improvements
- Sort services in modal alphabetically
- Clean up service quick select and history modal UI

## v1.9.6 — 2026-03-14

### Features
- Add chat histories and separate heartbeat chat

### Improvements
- Remove hard-coded /v1 path in OpenAI-compatible service setup

## v1.9.5 — 2026-03-12

### Features
- Add quick service toggle
- Add delete buttons to completed tasks

### Fixes
- Fix reoccurring tasks on fail
- Strip markers in the content field
- Rename daemon notification title

### Improvements
- Improve input layout arrangements
- Migrate project structure for AGP 9 requirements
- Upgrade SDKs

## v1.9.4 — 2026-03-11

### Features
- Add option to reset modified heartbeat
- Add button to cancel ongoing requests
- Add search input to model modal

### Fixes
- Fix settings import parsing
- Fix MCP server tool definitions
- Add missing UI pointer

## v1.9.3 — 2026-03-11

### Features
- Add confirmation dialog for settings import

### Fixes
- Fix silent import error

### Improvements
- Replace tasks and email icons with trash can icon

## v1.9.2 — 2026-03-10

### Features
- Add Anthropic service support

### Fixes
- Fix duplicate model key crash in models modal

## v1.9.1 — 2026-03-10

### Features
- Add settings import and export

### Fixes
- Fix deprecated APIs and unused code
- Gracefully catch daemon service exceptions

## v1.9.0 — 2026-03-09

### Features
- Add MCP server support

### Fixes
- Fix dark mode text colors
- Fix broken translations

### Improvements
- Cleaner services box UI
- Show tool usage in single row with minimum display duration
- Remove unused strings

## v1.8.2 — 2026-03-08

### Features
- Add file:// URL opening support
- Add URL-opening tool
- Add attached image previews in chat UI
- Add configurable active hours for heartbeat
- Add localizations

### Improvements
- Smoother animations and loading indicators
- Remove unused code and simplify internals
- Add docs link to app settings

## v1.8.1 — 2026-03-07

### Features
- Add Ollama Cloud and Cerebras as AI services
- Add UI scale option for desktop
- Preselect Kimi K2.5 model if available
- Add localizations

### Fixes
- Fix service modal scroll behavior
- Fix old service migration deletion
- Fix iOS image scaling

### Improvements
- Separate screen navigation for web/desktop and mobile
- Set Linux default font size to 110%
- Reorder tools list
- Upgrade SDKs

## v1.8.0 — 2026-03-05

### Features
- Add multi-service support with fallback logic
- Add image upload support for all services
- Add email plugin

### Improvements
- Improve agentic retry and timeout logic
- Normalize content for text-to-speech
- Upgrade Ktor

## v1.7.11 — 2026-03-04

### Features
- Add heartbeat monitoring
- Show OpenAI-compatible URL endpoint in settings

### Fixes
- Fix clear icon on desktop and web

### Improvements
- Improve daemon reliability
- Make full toggle UI clickable
- Improve settings UI on large screens
- Add OS to user agent string
- Clean up tool logic
- Migrate deprecated datetime APIs
- Remove unused code and improve performance

## v1.7.10 — 2026-03-03

### Features
- Add AUR and Snap packaging
- Add Arch Linux install instructions

### Improvements
- Update SHA for Flathub

## v1.7.9 — 2026-03-03

### Features
- Add search tool with support for more providers
- Add task scheduling and background daemon

### Improvements
- Remove cancelled tasks from task list
- Fix string formatting

## v1.7.8 — 2026-03-02

### Features
- Add memory system for persistent context
- Add memory toggle in settings
- Add shell command execution for desktop and Android
- Add re-generate button on last bot response

### Fixes
- Fix scroll width issue
- Fix tests

### Improvements
- Remove topics feature
- Update localizations
- Upgrade SDKs

## v1.7.7 — 2026-02-26

### Fixes
- Fix RTL UI layout issues

### Improvements
- Add locale configurations for iOS and Android
- Add new store localizations
- Improve performance

## v1.7.6 — 2026-02-25

### Features
- Add identity.md settings for customizable AI persona
- Add tablet screenshots

### Fixes
- Fix unit test
- Fix minimum tablet screenshot size

## v1.7.5 — 2026-02-24

### Features
- Add OpenClaw service support
- Add missing localizations

### Fixes
- Fix unit tests

## v1.7.4 — 2026-02-24

### Features
- Add Android alarm tool

### Improvements
- Disable topics by default
- Disable tools for certain models
- Permit cleartext traffic for local network access

## v1.7.3 — 2026-02-21

### Features
- Make topics optional

### Fixes
- Fix wrong version code
- Fix topic UI issues
- Fix web dark mode background

### Improvements
- Upgrade Compottie animation library
- Unify and simplify service logic

## v1.7.2 — 2026-02-19

### Features
- Add Flathub packaging and Linux tarball release asset

### Fixes
- Fix race condition in service selection

## v1.7.1 — 2026-02-19

### Features
- Add topic headers
- Add content localizations
- Add automated Play Store screenshot localization

### Improvements
- Remove SMS permissions to pass Play Store review

## v1.7.0 — 2026-02-16

### Features
- Add explore screen with curated topics
- Add space topic
- Add input clear icons

### Fixes
- Fix color scheme background
- Fix outdated models endpoint
- Fix topics padding

### Improvements
- Simplify JSON parsing
- Adjust markdown header font sizes
- Upgrade SDKs

## v1.6.2 — 2026-02-11

### Improvements
- Add missing ProGuard rules
- Separate FOSS and Play Store build flavors

## v1.6.1 — 2026-02-11

### Features
- Add Nvidia as an AI service

### Improvements
- Always show privacy info
- Upgrade SDKs

## v1.6.0 — 2026-02-03

### Features
- Add tools/skills support for AI-driven actions
- Add SMS tool
- Add local conversation history storage
- Add markdown image loading support

### Fixes
- Fix generic TTS exception handling
- Fix encryption warning
- Fix deprecated edge-to-edge behavior

### Improvements
- Use multiplatform time for conversation history
- Upgrade date dependency

## v1.5.2 — 2026-01-31

### Features
- Add OpenRouter service support
- Add xAI service support

### Fixes
- Fix race condition
- Fix test

### Improvements
- Simplify Groq authentication
- Update Android JVM target to 21
- Upgrade SDKs

## v1.5.1 — 2026-01-28

### Improvements
- Improve Enter+Shift key handling for all platforms
- Improve dropdown click handling
- Upgrade SDKs

## v1.5.0 — 2026-01-27

### Features
- Add Ollama / OpenAI-compatible service support with authentication

### Improvements
- Keep focus on input after sending chat
- Add chat and settings ViewModel tests
- Clean up service separation
- Upgrade SDKs

## v1.4.1 — 2026-01-24

### Features
- Add iOS text-to-speech support
- Add iOS launch screen

### Fixes
- Fix cursor behavior
- Fix proper edge-to-edge display on iOS

## v1.4.0 — 2026-01-23

### Features
- Add free tier and iOS release

## v1.3.5 — 2026-01-18

### Features
- Add in-app review SDK
- Add localizations

## v1.3.4 — 2026-01-18

### Features
- Add AppImage and RPM build targets

## v1.3.3 — 2026-01-17

### Features
- Add Windows and Linux desktop builds

## v1.3.2 — 2026-01-17

### Improvements
- Harden code
- Add version codes to release files
- Upgrade SDKs

## v1.3.1 — 2025-12-29

### Features
- Add macOS DMG build
- Add code syntax highlighting

### Improvements
- Improve ChatScreen performance by stabilizing lambdas
- Separate state and actions for better Compose performance
- Hoist LazyListState for improved scroll performance

## v1.3.0 — 2025-12-21

### Features
- Add dynamic color scheme support
- Add localizations
- Move all user-facing strings to strings.xml

## v1.2.14 — 2025-12-20

### Improvements
- Upgrade SDKs

## v1.2.13 — 2025-12-13

### Improvements
- Add and sort Groq models by date
- Upgrade SDKs

## v1.2.12 — 2025-11-30

### Fixes
- Fix error mapping and UI styling

### Improvements
- Improve empty API key fallback behavior

## v1.2.11 — 2025-11-29

### Improvements
- Update SDKs and models

## v1.2.10 — 2025-11-15

### Improvements
- Remove markdown formatting
- Upgrade SDKs

## v1.2.9 — 2025-10-26

### Improvements
- Upgrade SDKs

## v1.2.8 — 2025-10-05

### Improvements
- Upgrade SDKs

## v1.2.7 — 2025-09-27

### Improvements
- Upgrade SDKs

## v1.2.5 — 2025-09-13

### Improvements
- Downgrade AGP for F-Droid compatibility

## v1.2.4 — 2025-09-06

### Improvements
- Upgrade SDKs

## v1.2.2 — 2025-08-03

### Improvements
- Migrate to new FileKit library
- Upgrade SDKs

## v1.2.1 — 2025-07-05

### Improvements
- Add new Gemini models
- Refactor ChatScreen and apply Compose best practices
- Upgrade SDKs

## v1.1.4 — 2025-05-29

### Features
- Add Gemini 2.5 Flash and Pro preview models
- Implement specific error handling for Gemini API

### Fixes
- Fix error handling catch and UI

### Improvements
- Improve code quality and apply best practices
- Upgrade SDKs

## v1.1.3 — 2025-05-11

### Improvements
- Add web navigation binding
- Upgrade SDKs

## v1.1.2 — 2025-05-04

### Improvements
- Upgrade SDKs

## v1.1.1 — 2025-04-18

### Features
- Add drag-and-drop support for attachments
- Add Gemini file upload support

### Fixes
- Fix TextToSpeechSynthesisInterruptedError crash on Android
- Fix dark/light mode status bar colors

### Improvements
- Add default Groq model
- Move appVersion to version catalog
- Upgrade SDKs

## v1.0.2 — 2025-01-03

### Features
- Add copy-to-clipboard, speak, and flag actions

### Improvements
- Remove hard-coded API key and add proxy for Groq endpoint

## v1.0.1 — 2024-12-30

### Features
- Add dark mode support

## v1.0.0 — 2024-12-30

### Features
- Initial release
- Chat interface with AI services
- Text-to-speech support
- F-Droid packaging
