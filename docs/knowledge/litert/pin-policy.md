---
type: Policy
title: LiteRT pin and bump policy
description: How on-device model download URLs, SHA-256 digests, and sizes are pinned and when they may change.
tags: [litert, on-device, integrity, policy]
status: stable
generated: { by: human:simon, at: 2026-08-12T20:52:00Z }
---

# Goal

Every catalog download is a **fixed byte sequence**. Users must not get a different file because someone pushed to `main`.

# Pin shape

Each catalog row pins **three** values that must change together:

1. HuggingFace **commit** (40-char lowercase hex) in the resolve URL — never the branch name `main` (or any other moving ref).
2. File **SHA-256** (64-char lowercase hex) — checked after download and at load.
3. Exact **sizeBytes** — gates free-space check and post-download length.

URL form:

`https://huggingface.co/litert-community/<repo>/resolve/<commit>/<fileName>`

# Attested

A pin is **attested** when the HuggingFace tree API for **that commit** reports:

- `.lfs.oid` (or the file sha) equal to the catalog SHA-256
- `.lfs.size` (or `size`) equal to `sizeBytes`

Cross-check: `curl -sI -L <downloadUrl>` `x-linked-etag` should echo the same digest.

# Must not

- Write `/resolve/main/` or any other branch ref into Kotlin or this bundle.
- Change commit without digest and size, or digest without commit.
- Auto-bump because `main` moved. Record the newer commit in the snapshot as **available**, leave runtime pins alone.
- Add or remove a catalog model without an explicit product decision.
- Apply a digest to user-imported files (imports are exempt).

# When to bump

Only when product asks to ship a newer revision (quality fix, new weights). Then set commit + SHA-256 + sizeBytes in one change, update this bundle, and keep display name / GPU baseline / context defaults unless those were part of the decision.

# When to keep a stale pin

If `main` is newer than the pin, the pin is still valid as long as the **pinned commit** still exists and its digest matches. That is the expected state between product bumps.

If the pinned commit is **gone** from HuggingFace or the digest at that commit no longer matches, keep runtime as-is (do not invent a new pin) and flag it in the log — a broken pin needs a product bump, not a silent rewrite.
