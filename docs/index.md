# Kai

An **open-source AI assistant with persistent memory** that runs on **Android, iOS, Windows, Mac, Linux, and Web**.

[:material-download: Get Started](getting-started.md){ .md-button .md-button--primary }
[:material-github: GitHub](https://github.com/SimonSchubert/Kai){ .md-button }

## Overview

Kai is built with Kotlin Multiplatform and Compose Multiplatform. It connects to 11+ LLM providers with automatic fallback, remembers important details across conversations, and can act autonomously via scheduled heartbeats and tool execution.

## Key Features

- **Persistent memory** — Kai remembers important details across conversations and uses them automatically
- **Customizable soul** — Define the AI's personality and behavior with an editable system prompt
- **Multi-service fallback** — Configure multiple providers; Kai automatically tries the next one on failure
- **Tool execution** — Web search, notifications, calendar events, shell commands, and more
- **Autonomous heartbeat** — Periodic self-checks that surface anything needing attention
- **Encrypted storage** — Conversations are stored locally with encryption
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
| [OpenAI](https://openai.com) | OpenAI-compatible |
| [Gemini](https://aistudio.google.com) | Gemini native |
| [DeepSeek](https://www.deepseek.com) | OpenAI-compatible |
| [Mistral](https://mistral.ai) | OpenAI-compatible |
| [xAI](https://x.ai) | OpenAI-compatible |
| [OpenRouter](https://openrouter.ai) | OpenAI-compatible |
| [Groq](https://groq.com) | OpenAI-compatible |
| [NVIDIA](https://developer.nvidia.com) | OpenAI-compatible |
| [Cerebras](https://cerebras.ai) | OpenAI-compatible |
| [Ollama Cloud](https://ollama.com) | OpenAI-compatible |
| OpenAI-Compatible API (Ollama, LM Studio, etc.) | OpenAI-compatible |

Plus a built-in **Free** tier that requires no API key.

## Platforms

| Platform | Distribution |
|---|---|
| Android | Google Play, F-Droid, APK |
| iOS | App Store |
| macOS | Homebrew, DMG |
| Windows | MSI |
| Linux | DEB, RPM, AppImage, AUR |
| Web | Browser |

## Feature Documentation

- **[Chat & Conversations](features/chat.md)** — Message history, conversation persistence, image attachments, and speech output
- **[Multi-Service](features/multi-service.md)** — Provider configuration, fallback chain, and connection validation
- **[Tools](features/tools.md)** — Available tools, execution flow, safety guards, and enablement
- **[Memories](features/memories.md)** — Memory lifecycle, categories, reinforcement, and promotion
- **[Heartbeat](features/heartbeat.md)** — Autonomous self-checks, active hours, and configuration
- **[Tasks](features/tasks.md)** — Scheduled tasks, future execution, and task management
- **[Daemon](features/daemon.md)** — Background service for scheduled tasks and heartbeat execution

## Links

- [GitHub Repository](https://github.com/SimonSchubert/Kai)
- [Issue Tracker](https://github.com/SimonSchubert/Kai/issues)
- [Releases](https://github.com/SimonSchubert/Kai/releases)
- [Web App](https://simonschubert.github.io/Kai)
