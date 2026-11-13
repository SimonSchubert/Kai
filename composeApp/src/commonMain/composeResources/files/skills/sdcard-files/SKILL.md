---
name: sdcard-files
description: Read, write, move, and delete files under /sdcard and other shared storage using privileged access. Use when the user mentions a file, folder, download, photo, or document on the phone.
---

You are working with the phone's shared storage through the `android_files` tool. It bypasses
scoped storage by going through root or Shizuku, so it can touch anything the user can see in a
file manager.

## Allowed paths

Only `/sdcard`, `/storage`, and `/mnt/media_rw` are accepted. `/storage/emulated/0` and `/sdcard`
are the same directory — either spelling works. Anything else (including `/data`) is rejected by
the tool; use `execute_shell_command` for those, and only when the user asked for it.

## Actions

| Action | Parameters | Notes |
| --- | --- | --- |
| `list` | `path` | Default action. `ls -la` output: permissions, size, date, name. |
| `read` | `path` | First 256 KB. Sets `truncated: true` when the file is longer. |
| `write` | `path`, `content` | Overwrites. Creates the file, not missing parent folders. |
| `append` | `path`, `content` | Appends to the end. |
| `delete` | `path`, `recursive` | `recursive` defaults to true. Deleting a folder needs it. |
| `move` | `path`, `destination` | Rename or relocate. Both paths must be in shared storage. |
| `mkdir` | `path` | Creates parents as needed. |
| `stat` | `path` | Size, mode, mtime for one entry. |

## Practical rules

- **Find before acting.** If the user names a file loosely ("my shopping list"), `list` the
  likely folder (`/sdcard/Documents`, `/sdcard/Download`) and match names case-insensitively
  before touching anything.
- **`write` does not create folders.** `mkdir` the parent first, or the write fails.
- **Destructive actions need a reason.** `delete` and overwriting `write` are irreversible; if the
  user's intent is ambiguous, ask once, briefly.
- **Big reads.** `read` caps at 256 KB. For anything larger, use `execute_shell_command` with
  `wc -l` / `head` / `grep` to narrow down instead of pulling the whole file into context.
- **Binary files.** `read` returns text. Images and archives should be handled with
  `execute_shell_command` (`file`, `stat`) — do not try to read them as text.
- **Reporting.** Say what changed and where ("Created /sdcard/Documents/list.txt, 12 lines"),
  not the full file contents.

## Common requests

- "What's in my Downloads?" → `{"action": "list", "path": "/sdcard/Download"}`
- "Save this text to a file" → `mkdir /sdcard/Documents` (if needed) → `write` with the content.
- "Rename that file" → `list` to confirm the exact name → `move` with `destination`.
- "Delete the temp folder" → `list` it first so you can state what will be removed.
