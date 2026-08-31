# Active Handoff

This file is the durable handoff for AI-agent sessions on this repository.
Use it when a chat is long, when Codex compaction fails, or when work must
continue in a new chat.

## How To Use

At the start of a new session:

1. Read `AGENTS.md`.
2. Read `DEVELOPER_CODE_PREFERENCES.md`.
3. Read `docs/agent-context/README.md`.
4. Read this file.
5. Run `git status --short` before assuming what changed.

Before ending a long session:

1. Update the sections below with the current focus, completed work, open
   decisions, verification, and next steps.
2. Prefer a concise handoff here over relying on remote `/compact` when the
   chat is already large.

## Codex Compaction Note

On 2026-08-30, Codex in IntelliJ IDEA reported:

```text
Error running remote compact task: unexpected status 404 Not Found: {"detail":"Not Found"}, url: https://chatgpt.com/backend-api/codex/responses/compact
```

Local checks at that time:

- `codex --version` returned `codex-cli 0.137.0`.
- No `~/.codex/config.toml` was found.
- No project `.codex/config.toml` was found.

Treat this as a Codex backend, account, rollout, or IDE/CLI version issue unless
new evidence points elsewhere. Practical workaround: write or update this
handoff, start a fresh chat, and ask the next agent to continue from this file.
If the issue repeats, capture the timestamp, request id, `cf-ray`, IDE plugin
version, `codex --version`, and whether the session was local, worktree, cloud,
or remote.

## Current Focus

- No active implementation focus has been recorded in this file yet.

## Completed

- Added this handoff file so future chats can continue without depending on
  remote compaction.

## In Progress

- Unknown. Run `git status --short` and inspect relevant diffs before making
  assumptions.

## Open Decisions

- Decide whether any current dirty working-tree changes should be kept,
  continued, committed, or reverted. Do not revert user changes without explicit
  instruction.

## Verification

- Documentation-only change. No Gradle tests were run for this handoff update.

## Next Steps

- At the start of future coding sessions, read this file after the standard
  project instructions.
- Before long sessions end, update this file with the actual implementation
  state and next action.
