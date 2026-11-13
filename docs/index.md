# Kai

An **open-source AI assistant with persistent memory** that runs on **Android, iOS, Windows, Mac, Linux, and Web**.

[:material-download: Get Started](getting-started.md){ .md-button .md-button--primary }
[:material-github: GitHub](https://github.com/SimonSchubert/Kai){ .md-button }

## Overview

Kai is built with Kotlin Multiplatform and Compose Multiplatform. It connects to 30 LLM providers (plus a keyless Free tier and on-device models) with automatic fallback, remembers important details across conversations, and can act autonomously via scheduled heartbeats and tool execution.

## Key Features

- **Persistent memory** — Kai remembers important details across conversations and uses them automatically
- **Customizable soul** — Define the AI's personality and behavior with an editable system prompt
- **Multi-service fallback** — Configure multiple providers; Kai automatically tries the next one on failure
- **On-device inference** — Run models locally with LiteRT on Android, iOS and desktop, no internet needed
- **Tool execution** — Web search, notifications, calendar events, shell commands, and more
- **MCP server support** — Connect to remote tool servers via the Model Context Protocol
- **Interactive UI** — The AI can answer with interactive screens (forms, buttons, multi-step flows) instead of plain text
- **Skills** — Install reusable instruction bundles in the SKILL.md format (Android)
- **Autonomous heartbeat & scheduled tasks** — Periodic self-checks plus one-time or recurring tasks the AI schedules
- **Linux sandbox** — On Android, the AI can run shell commands, scripts and packages in a Debian or Alpine environment
- **Kai Build** — An Android coding environment with terminals and coding agents (Claude Code, Grok, OpenCode)
- **Encrypted storage** — Conversations are stored locally with encryption
- **Settings export/import** — Back up and restore all settings as a JSON file
- **Text to speech** — Listen to AI responses
- **Image attachments** — Attach images to any conversation

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

## Supported Services

| Service | API Type |
|---|---|
| **[Atlas Cloud](https://www.atlascloud.ai?utm_source=github&utm_medium=link&utm_campaign=Kai)** | OpenAI-compatible |
| [Anthropic](https://console.anthropic.com) | Anthropic native |
| [OpenAI](https://openai.com) | OpenAI-compatible (Responses API for models that require it) |
| [Gemini](https://aistudio.google.com) | Gemini native |
| [DeepSeek](https://www.deepseek.com) | OpenAI-compatible |
| [Mistral](https://mistral.ai) | OpenAI-compatible |
| [xAI](https://x.ai) | OpenAI-compatible |
| [OpenRouter](https://openrouter.ai) | OpenAI-compatible |
| [Requesty](https://requesty.ai) | OpenAI-compatible |
| [Groq](https://groq.com) | OpenAI-compatible |
| [NVIDIA](https://developer.nvidia.com) | OpenAI-compatible |
| [Cerebras](https://cerebras.ai) | OpenAI-compatible |
| [Ollama Cloud](https://ollama.com) | OpenAI-compatible |
| [LongCat](https://longcat.chat) | OpenAI-compatible |
| [Together AI](https://together.ai) | OpenAI-compatible |
| [Hugging Face](https://huggingface.co) | OpenAI-compatible |
| [Venice AI](https://venice.ai) | OpenAI-compatible |
| [Moonshot AI](https://moonshot.cn) | OpenAI-compatible |
| [Z.AI](https://z.ai) (standard and Coding Plan) | OpenAI-compatible |
| [MiniMax](https://minimax.io) | OpenAI-compatible |
| [AIHubMix](https://aihubmix.com) | OpenAI-compatible |
| [Deep Infra](https://deepinfra.com) | OpenAI-compatible |
| [Fireworks AI](https://fireworks.ai) | OpenAI-compatible |
| [OpenCode](https://opencode.ai) | OpenAI-compatible |
| [Public AI](https://publicai.co) | OpenAI-compatible |
| [AI Horde](https://stablehorde.net/) | OpenAI-compatible |
| [1min.AI](https://1min.ai) | OpenAI-compatible |
| [Perplexity](https://www.perplexity.ai) | OpenAI-compatible |
| OpenAI-Compatible API (Ollama, LM Studio, etc.) | OpenAI-compatible |
| Local Model (Android, iOS, desktop) | LiteRT on-device |

Plus a built-in **Free** tier that requires no API key.

## Platforms

| Platform | Distribution |
|---|---|
| Android | Google Play, F-Droid, APK |
| iOS | App Store |
| macOS | Homebrew, DMG |
| Windows | Winget, MSI |
| Linux | DEB, RPM, AppImage, Flatpak, tarball, AUR |
| Web | Browser |

## Feature Documentation

- **[Chat & Conversations](features/chat.md)** — Message history, conversation persistence, image attachments, and speech output
- **[Multi-Service](features/multi-service.md)** — Provider configuration, fallback chain, and connection validation
- **[Tools](features/tools.md)** — Available tools, execution flow, safety guards, and enablement
- **[Memories](features/memories.md)** — Memory lifecycle, categories, reinforcement, and promotion
- **[Heartbeat](features/heartbeat.md)** — Autonomous self-checks, active hours, and configuration
- **[Tasks](features/tasks.md)** — Scheduled tasks, future execution, and task management
- **[Daemon](features/daemon.md)** — Background service for scheduled tasks and heartbeat execution
- **[MCP Servers](features/mcp.md)** — Remote tool servers over the Model Context Protocol
- **[Dynamic UI](features/dynamic-ui.md)** — AI-generated interactive screens rendered inline in chat
- **[On-Device Inference](features/on-device-inference.md)** — Local LiteRT models, downloads, and integrity checks
- **[Reasoning](features/reasoning.md)** — How reasoning traces are sent back to providers and shown
- **[System Prompts](features/system-prompts.md)** — How each prompt variant is composed
- **[Skills](features/skills.md)** — Installable SKILL.md instruction bundles
- **[Linux Sandbox](features/sandbox.md)** — On-device Debian/Alpine shell for the AI and the user (Android)
- **[Kai Build](features/kai-build.md)** — Coding environment with terminals and coding agents (Android)
- **[Notifications](features/notifications.md)** — Reading other apps' notifications (Android, FOSS build)
- **[SMS](features/sms.md)** — Reading SMS and drafting replies for the user to send (Android, FOSS build)
- **[Appearance](features/appearance.md)** — Theme picker
- **[Encryption](features/encryption.md)** — Secure settings storage per platform
- **[Settings Export & Import](features/settings-export-import.md)** — JSON backup and restore
- **[Splinterlands](features/splinterlands.md)** — Auto-battle integration

## Links

- [GitHub Repository](https://github.com/SimonSchubert/Kai)
- [Issue Tracker](https://github.com/SimonSchubert/Kai/issues)
- [Releases](https://github.com/SimonSchubert/Kai/releases)
- [Web App](https://kai9000.com/app/)
