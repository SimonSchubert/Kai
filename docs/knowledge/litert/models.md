---
type: Catalog
title: Pinned LiteRT models
description: Immutable HuggingFace commit, SHA-256, and size for each on-device catalog model.
tags: [litert, on-device, integrity]
status: stable
stale_after: 2026-09-11
generated: { by: process:update-litert-models, at: 2026-08-12T20:52:35Z }
verified: { by: process:desktopTest-LocalModel, at: 2026-08-12T20:54:10Z }
sources:
  - id: hf-e2b
    resource: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
    title: litert-community/gemma-4-E2B-it-litert-lm
  - id: hf-e4b
    resource: https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm
    title: litert-community/gemma-4-E4B-it-litert-lm
  - id: hf-12b
    resource: https://huggingface.co/litert-community/gemma-4-12B-it-litert-lm
    title: litert-community/gemma-4-12B-it-litert-lm
  - id: hf-qwen
    resource: https://huggingface.co/litert-community/Qwen3-0.6B
    title: litert-community/Qwen3-0.6B
  - id: pin-policy
    resource: /pin-policy.md
    title: Pin and bump policy
  - id: litert-playbook
    resource: /refresh-playbook.md
    title: Refresh LiteRT pins playbook
---

# Policy

A pin is **attested** when the HuggingFace tree API for the **pinned commit** reports the same SHA-256 and byte size as Kotlin. `main` moving is not a reason to bump if those bytes are unchanged. See [pin-policy.md](pin-policy.md).

Replace this snapshot only via the [refresh playbook](refresh-playbook.md). Never write `/resolve/main/`.

# Current pins

Snapshot mirrored into `MODEL_CATALOG` (runtime). 4 models. GPU baseline and context defaults stay in Kotlin only.

| Id | Repo | Pinned commit | File | SHA-256 | sizeBytes | Pin vs tree | `main` commit | `main` file |
|---|---|---|---|---|---|---|---|---|
| `gemma-4-e2b-it` | `litert-community/gemma-4-E2B-it-litert-lm` | `9262660a1676eed6d0c477ab1a86344430854664` | `gemma-4-E2B-it.litertlm` | `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c` | 2588147712 | attested | `6b78abd019e61a1ca4cbe3b212d2c9ce8ff38a94` | same oid + size |
| `gemma-4-e4b-it` | `litert-community/gemma-4-E4B-it-litert-lm` | `f7ad3343bd6ebc9607f4dc3bc4f2398bd5749bc5` | `gemma-4-E4B-it.litertlm` | `0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0` | 3659530240 | attested | `2eee7ac325f20eb8c9ac1d0e972f7c84663062da` | same oid + size |
| `gemma-4-12b-it` | `litert-community/gemma-4-12B-it-litert-lm` | `c65da4643badfd9ae0748b5df0145d8fddaef47e` | `gemma-4-12B-it.litertlm` | `74fc29a10c20eb5b3ced6c389471a7994a0ffd657255b2a1c764262fb9054aef` | 6547589312 | attested | `b33be37e07c25ee94e6d99dd0a484b32158f7b49` | same oid + size |
| `qwen3-0.6b` | `litert-community/Qwen3-0.6B` | `dd97997951bb15a2a71f539ba17f604707c0b11a` | `Qwen3-0.6B.litertlm` | `555579ff2f4fd13379abe69c1c3ab5200f7338bc92471557f1d6614a6e5ab0b4` | 614236160 | attested | `8414150f2e9dcc82449bcc9c5abc404b399a4d06` | same oid + size |

# Snapshot

| Field | Value |
|---|---|
| Fetched | 2026-08-12T20:52:35Z |
| Source | HuggingFace models API + tree API at the pinned commit and at `main` |
| Attested | 4 / 4 pins match tree oid + size |
| Bumped | none (`main` commits moved; `.litertlm` bytes unchanged) |

# Notes

- Runtime download URLs stay on the pinned commits. A newer `main` sha with the same LFS oid is repo metadata, not a new weight file.
- User imports are not in this catalog.
