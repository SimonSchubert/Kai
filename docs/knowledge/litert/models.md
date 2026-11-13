---
type: Catalog
title: Pinned LiteRT models
description: Immutable HuggingFace commit, SHA-256, and size for each on-device catalog model.
tags: [litert, on-device, integrity]
status: stable
stale_after: 2026-10-07
generated: { by: process:update-litert-models, at: 2026-09-07T00:00:00Z }
verified: { by: process:desktopTest-LocalModel, at: 2026-09-07T00:00:00Z }
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
  - id: hf-lfm25
    resource: https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct
    title: litert-community/LFM2.5-1.2B-Instruct
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

Snapshot mirrored into `MODEL_CATALOG` (runtime). 5 models. GPU baseline and context defaults stay in Kotlin only.

| Id | Repo | Pinned commit | File | SHA-256 | sizeBytes | Pin vs tree |
|---|---|---|---|---|---|---|
| `gemma-4-e2b-it` | `litert-community/gemma-4-E2B-it-litert-lm` | `9262660a1676eed6d0c477ab1a86344430854664` | `gemma-4-E2B-it.litertlm` | `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c` | 2588147712 | attested |
| `gemma-4-e4b-it` | `litert-community/gemma-4-E4B-it-litert-lm` | `f7ad3343bd6ebc9607f4dc3bc4f2398bd5749bc5` | `gemma-4-E4B-it.litertlm` | `0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0` | 3659530240 | attested |
| `gemma-4-12b-it` | `litert-community/gemma-4-12B-it-litert-lm` | `7a0b1ce0ea821bcd01c5f72af84155e02191152f` | `gemma-4-12B-it.litertlm` | `58fd31b778ca2c21c80d634fb34fc5a89d11d563a38dfd3cbf1b40dbf252a8b6` | 6883278368 | attested (bumped 2026-09-07) |
| `lfm2.5-1.2b-instruct` | `litert-community/LFM2.5-1.2B-Instruct` | `f45d8d8abe93bff4026efee20fa483150ce8e687` | `LFM2.5-1.2B-Instruct_int4_gpu.litertlm` | `36f7f0221bcc42c75291da1d7e3422901024a5b06b9bfa3c02d7feface04f70a` | 736220768 | attested (added 2026-09-07) |
| `qwen3-0.6b` | `litert-community/Qwen3-0.6B` | `dd97997951bb15a2a71f539ba17f604707c0b11a` | `Qwen3-0.6B.litertlm` | `555579ff2f4fd13379abe69c1c3ab5200f7338bc92471557f1d6614a6e5ab0b4` | 614236160 | attested |

# `main` at the time of the check

| Id | `main` commit | `main` file vs pin |
|---|---|---|
| `gemma-4-e2b-it` | `b3ca0d2f0767` | same oid + size |
| `gemma-4-e4b-it` | `2eee7ac325f2` | same oid + size |
| `gemma-4-12b-it` | `7a0b1ce0ea82` | pinned to `main` (that is where the new bytes are) |
| `lfm2.5-1.2b-instruct` | `f45d8d8abe93` | pinned to `main` |
| `qwen3-0.6b` | `8414150f2e9d` | same oid + size |

# Snapshot

| Field | Value |
|---|---|
| Fetched | 2026-09-07 |
| Source | HuggingFace models API + tree API at the pinned commit and at `main` |
| Attested | 5 / 5 pins match tree oid + size |
| Bumped | `gemma-4-12b-it` (new weights upstream, not a metadata move) |
| Added | `lfm2.5-1.2b-instruct` |

# Notes

- The `gemma-4-12b-it` bump is a real reweight, not a `main` drift: upstream commit `161028364f39` ("Updated the gemma-4-12B-it.litertlm file", 2026-09-03) replaced the file, and the byte size moved 6547589312 → 6883278368. The new build adds vision + audio modalities and Multi-Token Prediction, and its card requires **litert-lm ≥ 0.17** — which the Android/JVM runtime now is. Users who already hold the old file re-download 6.9 GB.
- `lfm2.5-1.2b-instruct` pins the `_int4_gpu` build deliberately. The plain `_int4` file delegates 536 of 579 ops and then fails engine creation (`GATHER_ND` plus INT64 tensors do not lower), while `_int4_gpu` runs on both GPU and CPU — required, because `initialize()` tries GPU first and falls back.
- Qwen3.5 (0.8B / 2B / 4B) exists in `litert-community` and was **not** added: those bundles ship a simplified ChatML template with the tool-calling and vision sections deliberately removed, which would repeat the Qwen3 0.6B problem.
- Runtime download URLs stay on the pinned commits. A newer `main` sha with the same LFS oid is repo metadata, not a new weight file.
- User imports are not in this catalog.
