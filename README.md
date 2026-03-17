# Kai

<img src="https://img.shields.io/badge/Platform-Web-f7df1c?logo=javascript" alt="Web"> <img src="https://img.shields.io/badge/Platform-Android-34a853.svg?logo=android" alt="Android" /> <img src="https://img.shields.io/badge/Platform-iOS-lightgrey.svg?logo=apple" alt="iOS" /> <img src="https://img.shields.io/badge/Platform-Windows/macOS/Linux-e10707.svg?logo=openjdk" alt="Platform JVM" />
<div align="center">
<img src="kai-mascot.png" height="80">

An **open-source AI assistant with persistent memory** that runs on **Android, iOS, Windows, Mac, Linux, and Web**.

**[Documentation](https://simonschubert.github.io/Kai/docs/)**
</div>

## Installation

[![App Store](https://raw.githubusercontent.com/SimonSchubert/Kai/main/screenshots/app_store_badge.png)](https://apps.apple.com/us/app/kai-ai/id6758148023)
[![Play Store](https://raw.githubusercontent.com/SimonSchubert/Kai/main/screenshots/play_store_badge.png)](https://play.google.com/store/apps/details?id=com.inspiredandroid.kai)
[![F-Droid](https://raw.githubusercontent.com/SimonSchubert/Kai/main/screenshots/fdroid_badge.png)](https://f-droid.org/en/packages/com.inspiredandroid.kai/)
[![Web](https://raw.githubusercontent.com/SimonSchubert/Kai/main/screenshots/web_badge.png)](https://simonschubert.github.io/Kai)

Homebrew (macOS):

```
brew install --cask simonschubert/tap/kai
```

AUR (Arch Linux):

```
yay -S kai-bin
```

Winget (Windows):

```
winget install SimonSchubert.Kai
```

### Direct Downloads

| Platform | Format | Download |
|----------|--------|----------|
| Android | APK | [GitHub Releases](https://github.com/SimonSchubert/Kai/releases) |
| macOS | DMG | [GitHub Releases](https://github.com/SimonSchubert/Kai/releases) |
| Windows | MSI | [GitHub Releases](https://github.com/SimonSchubert/Kai/releases) |
| Linux | DEB | [GitHub Releases](https://github.com/SimonSchubert/Kai/releases) |
| Linux | RPM | [GitHub Releases](https://github.com/SimonSchubert/Kai/releases) |
| Linux | AppImage | [GitHub Releases](https://github.com/SimonSchubert/Kai/releases) |

## Features

- **Persistent memory** — Kai remembers important details across conversations and uses them automatically
- **Customizable soul** — Define the AI's personality and behavior with an editable system prompt
- **Multi-service fallback** — 17 LLM providers with automatic failover
- **Tool execution** — Web search, notifications, calendar events, shell commands, and more
- **MCP server support** — Connect to remote tool servers via the Model Context Protocol
- **Autonomous heartbeat** — Periodic self-checks that surface anything needing attention
- **Settings export/import** — Backup and restore all settings as a JSON file
- **Encrypted storage** — Conversations stored locally with encryption
- **Text to speech** — Listen to AI responses
- **Image attachments** — Attach images to any conversation

## Screenshots

### Desktop

<img src="screenshots/desktop-1.png" alt="Desktop App" height="300">

### Web

<img src="screenshots/web-1.png" alt="Web App" height="300">

### Mobile

<img src="screenshots/mobile-1.png" alt="Mobile Screenshot 1" height="300"> <img src="screenshots/mobile-2.png" alt="Mobile Screenshot 2" height="300"> <img src="screenshots/mobile-3.png" alt="Mobile Screenshot 3" height="300"> <img src="screenshots/mobile-4.png" alt="Mobile Screenshot 4" height="300"> <img src="screenshots/mobile-5.png" alt="Mobile Screenshot 5" height="300"> <img src="screenshots/mobile-6.png" alt="Mobile Screenshot 6" height="300">

## How It Works

```
                        ┌────────┐
                        │  User  │
                        └───┬────┘
                            │ message
                            ▼
               ┌─────────────────────────┐
               │          Chat           │
               │                         │
               │  prompt + memories      │
               │        │                │
               │        ▼                │
               │    ┌────────┐           │
               │    │   AI   │◀─┐        │
               │    └───┬────┘  │        │
               │        │   tool calls   │
               │        │   & results    │
               │        ▼      │        │
               │    ┌────────┐ │        │
               │    │ Tools  │─┘        │
               │    └───┬────┘          │
               │        │               │
               └────────┼───────────────┘
                        │ store / recall
                        ▼
               ┌─────────────────┐    hitCount >= 5
               │     Memory      │───────────────────┐
               │                 │                   │
               │  facts, prefs,  │                   ▼
               │  learnings      │          ┌────────────────┐
               │                 │◀─delete──│ Promote into   │
               └─────────────────┘          │ System Prompt  │
                        ▲                   └────────────────┘
                        │ reviews
                        │
               ┌─────────────────┐
               │    Heartbeat    │
               │                 │
               │  autonomous     │
               │  self-check     │
               │  every 30 min   │
               │  (8am–10pm)     │
               │                 │
               │  all good?      │
               │  → stays silent │
               │  needs action?  │
               │  → notifies user│
               └─────────────────┘
```

- **Chat** — User sends a message. The AI responds, calling tools (memory, web search, shell, etc.) in a loop until it has a final answer.
- **Memory** — The AI stores and recalls facts, preferences, and learnings. Memories that prove useful (5+ hits) can be promoted into the system prompt permanently.
- **Heartbeat** — A background self-check runs every 30 minutes. It reviews memories, pending tasks, and emails. If something needs attention, it notifies the user. Otherwise, it stays silent.

## Supported Services

| Service | Website |
|---|---|
| Anthropic | https://console.anthropic.com |
| Cerebras | https://cerebras.ai |
| DeepSeek | https://www.deepseek.com |
| Gemini | https://aistudio.google.com |
| Groq | https://groq.com |
| Hugging Face | https://huggingface.co |
| LongCat | https://longcat.chat |
| Mistral | https://mistral.ai |
| Moonshot AI | https://moonshot.cn |
| NVIDIA | https://developer.nvidia.com |
| Ollama Cloud | https://ollama.com |
| OpenAI | https://openai.com |
| OpenRouter | https://openrouter.ai |
| Together AI | https://together.ai |
| Venice AI | https://venice.ai |
| xAI | https://x.ai |
| OpenAI-Compatible API | Ollama, LM Studio, etc. |

Plus a built-in **Free** tier that requires no API key.

## MCP Servers

Kai supports the [Model Context Protocol](https://modelcontextprotocol.io/) for connecting to external tool servers. Go to **Settings > Tools > Add MCP Server** to connect to any Streamable HTTP MCP endpoint, or pick from a curated list of popular free servers:

| Server | Description |
|--------|-------------|
| Fetch | Fetch web content and convert HTML to markdown |
| DeepWiki | AI-powered docs for any GitHub repo |
| Sequential Thinking | Structured step-by-step problem-solving |
| Context7 | Up-to-date library and framework docs |
| Globalping | Ping, traceroute, DNS from global probes |
| CoinGecko | Real-time crypto prices and market data |
| Manifold Markets | Prediction market data and odds |
| Find-A-Domain | Domain availability across 1,444+ TLDs |

All popular servers are free and require no API key. MCP servers auto-reconnect on app startup.

## Integrations

### Splinterlands Auto-Battle (Android & Desktop)

Kai can automatically play [Splinterlands](https://splinterlands.com) Wild Ranked battles. Configure one or more LLM services in priority order, add your Hive account, and hit Start -- Kai will continuously find matches, pick teams using LLM-powered strategy, and submit them on-chain. Falls back to a simple greedy picker if all LLM services fail. Available in **Settings > Integrations**.

## Supported Languages

Afrikaans, Albanian, Amharic, Arabic, Belarusian, Bengali, Bulgarian, Chinese (Simplified), Chinese (Traditional), Croatian, Czech, Danish, Dutch, English, Estonian, Filipino, Finnish, French, German, Greek, Gujarati, Hebrew, Hindi, Hungarian, Indonesian, Italian, Japanese, Kazakh, Korean, Latvian, Lithuanian, Malay, Marathi, Norwegian, Persian, Polish, Portuguese, Punjabi, Romanian, Romansh, Russian, Serbian, Slovak, Slovenian, Spanish, Swahili, Swedish, Tamil, Telugu, Thai, Turkish, Ukrainian, Urdu, Vietnamese, Zulu

## Contributing

### Screenshot Automation

Two separate screenshot pipelines exist, both using Compose screenshot tests:

**README screenshots** — Used for this README. CI runs this automatically on every push and auto-commits any changes.

```bash
./gradlew :screenshotTests:updateScreenshots
```

**Store screenshots** — Generates localized screenshots for the Play Store in all supported locales. Upload via fastlane.

```bash
./gradlew :screenshotTests:generateStoreScreenshots
bundle exec fastlane android upload_screenshots
```

## Sponsors

This project is open-source and maintained by a single developer. If you find this app useful, please consider sponsoring to help take it to the next level with more features and faster updates.

## Credits

- Lottie animation: https://lottiefiles.com/free-animation/loading-wDUukARCPj
- Mistral: https://mistral.ai/


## Usage

```python
# Usage examples
```
