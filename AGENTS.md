# AGENTS.md

This document outlines guidelines, environment setup, build and testing procedures, and coding standards for AI agents operating in the Kai repository.

## Repository Overview

Kai is a Compose Multiplatform & Kotlin Multiplatform (KMP) application supporting Android, Desktop (JVM), iOS, and Web platforms.

- **Primary Repository Context**: Kai AI application
- **Context7 Endpoint**: `https://context7.com/aeldergentics/kai`
- **KaiSkills Sandbox Architecture**:
  - CLI Manager Entrypoint: `python /root/kai_skills/manager.py`
  - Skill Index: `/root/kai_skills/registry.json`

---

## Environment & Build Setup

### Prerequisites
- JDK 21 (configured via `JAVA_HOME` if needed)
- Gradle wrapper (`./gradlew`)

### Core Gradle Build Commands
- **Check Spotless formatting**: `./gradlew spotlessCheck`
- **Apply Spotless auto-formatting**: `./gradlew spotlessApply`
- **Compile Desktop**: `./gradlew :composeApp:compileKotlinDesktop`
- **Run Desktop Release Build**: `./gradlew :composeApp:runRelease`

---

## Testing Guidelines

### Test Execution Commands
- **Run full check/tests**: `./gradlew check`
- **Run Android host tests**: `./gradlew :composeApp:testAndroidHostTest`
- **Run specific desktop unit tests**:
  - `ModelCatalog` tests: `./gradlew :composeApp:desktopTest --tests '*ModelCatalog*'`
  - `FreeTierModels` tests: `./gradlew :composeApp:desktopTest --tests '*FreeTierModels*' --tests '*ModelTransformations*'`
  - Any specific class: `./gradlew :composeApp:desktopTest --tests '<TestClassName>'`

### Screenshot Testing & Generation
- **Update README screenshots**: `./gradlew :screenshotTests:updateScreenshots`
- **Generate store screenshots**: `./gradlew :screenshotTests:generateStoreScreenshots`
- **Record Kai UI component screenshots**: `./gradlew :screenshotTests:recordKaiUiScreenshots`

---

## Coding Guidelines & Best Practices

### Multiplatform (KMP) Compatibility
- **Avoid JVM-specific methods** (e.g. `list.replaceAll`) in common code (`commonMain` / `commonTest`). Use multiplatform-compatible alternatives such as `list.map { ... }.toMutableList()`.
- **Use `kotlin.test` for common tests**: In `commonTest`, use `kotlin.test` assertions (`kotlin.test.Test`, `kotlin.test.assertEquals`, etc.) rather than JVM-specific libraries like JUnit.

### Performance & Collection Optimizations
- **Single-pass collection operations**: Avoid chaining `.filter {}` followed by `.map {}` (or `.filterNot {}` and `.mapNotNull {}`). Collapse them into a single `.mapNotNull {}` call to evaluate conditions and transform data in a single pass, avoiding intermediate list allocations.

### Code Formatting
- Always run `./gradlew spotlessApply` after making code modifications to maintain consistent code formatting across the repository.

---

## Documentation & Knowledge Bundles

### Feature Specifications (`docs/features/`)
- Describe features from a product/user behavior perspective — do not include Kotlin code blocks or internal class/function names in prose.
- When modifying feature logic:
  1. Update the corresponding specification in `docs/features/`.
  2. Update the "Last verified" date in the header.
  3. Ensure the Key Files table remains accurate.

### Knowledge Bundles (`docs/knowledge/`)
Agent-curated knowledge and snapshot policies are organized as OKF-style markdown under `docs/knowledge/`:
- `docs/knowledge/free-tier/` — paired with `FreeTierModels.kt`
- `docs/knowledge/model-catalog/` — paired with `ModelCatalog.kt`
- `docs/knowledge/popular-mcp/` — paired with `PopularMcpServers.kt`
- `docs/knowledge/litert/` — paired with `LocalModelCatalog.kt`

When updating knowledge snapshots, update both the markdown document and its corresponding Kotlin runtime file.

---

## Kai Skills Setup

To install or register a new skill in the sandbox environment:

```sh
mkdir -p /root/skills/<id> && cat > /root/skills/<id>/SKILL.md <<'SKILLEOF'
---
name: <id>
description: <description>
---
<body markdown here, verbatim>
SKILLEOF
```

Skills are indexed in `/root/kai_skills/registry.json` and managed via `python /root/kai_skills/manager.py`.
