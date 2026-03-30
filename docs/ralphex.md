# Ralphex Workflow Notes

This file stores working notes for using `ralphex` with the current Codex-based setup.

## Current Setup

- Global `ralphex` config: `~/.config/ralphex/config`
- Claude-compatible wrapper: `~/.config/ralphex/scripts/codex-as-claude.sh`
- Task executor: `codex` through the wrapper above
- External review tool: `codex`
- Model: `gpt-5.4`
- Reasoning effort: `xhigh`
- Sandbox mode: `danger-full-access`

`ralphex` config precedence is:

1. CLI flags
2. Local project config in `.ralphex/`
3. Global config in `~/.config/ralphex/`
4. Embedded defaults

## Required Tools

- `ralphex`
- `codex`
- `jq`
- `git`

## Plan Format

Store plans in `docs/plans/`.

You can either write a plan file manually or ask `ralphex` to create one interactively.

Recommended file name:

```text
docs/plans/YYYY-MM-DD-topic.md
```

Recommended structure:

```markdown
# Title

## Overview

## Context

## Development Approach

## Implementation Steps

### Task 1: ...
- [ ] implementation
- [ ] tests
- [ ] validation command
```

Important behavior:

- `ralphex` completes one `### Task N:` section per task iteration.
- Each task should contain explicit validation steps.
- Completed plans may be moved into `docs/plans/completed/`.

## Creating Plans

Create a new plan interactively from a request:

```bash
ralphex --plan "add user authentication"
```

What this does:

- `ralphex` explores the repo and asks follow-up questions when needed
- it prepares a draft plan first
- after you accept the draft, it writes the final plan into `docs/plans/`

Use manual plan files when you already know the exact task breakdown and want full control over the checklist.

## Common Commands

Run the full pipeline:

```bash
ralphex docs/plans/YYYY-MM-DD-topic.md
```

Run only the task phase:

```bash
ralphex --tasks-only docs/plans/YYYY-MM-DD-topic.md
```

Run the full review pipeline without task execution:

```bash
ralphex --review --base-ref main docs/plans/completed/YYYY-MM-DD-topic.md
```

Run only the external Codex review:

```bash
ralphex --external-only --base-ref main docs/plans/completed/YYYY-MM-DD-topic.md
```

Useful runtime controls:

```bash
ralphex \
  --session-timeout=15m \
  --idle-timeout=5m \
  --max-iterations=10 \
  docs/plans/YYYY-MM-DD-topic.md
```

## Day-To-Day Workflow

1. Create a plan with `ralphex --plan "..."` or write one manually in `docs/plans/`.
2. Make sure the repository is already a valid `git` repo.
3. Run `--tasks-only` while implementation is still in progress.
4. When task checkboxes are done, run `--review` or `--external-only`.
5. Read progress logs in `.ralphex/progress/`.
6. Keep `main` clean and use the generated working branch for the run.

## Logs And Local State

`ralphex` writes local runtime state into `.ralphex/`.

Most useful files:

- `.ralphex/progress/*.txt` for task and review logs
- `.ralphex/.gitignore` if `ralphex` initializes local project state

This directory is local-only and should not be committed.

## Notes

- `--base-ref` can be a branch name or a commit hash.
- If a review run leaves an uncommitted fix behind, finish that fix before starting another pass.
- If `git` locking shows up during concurrent phases, wait for the active process to finish and retry.
- If Codex auth or network is broken, both task and external-review phases will fail early through the wrapper.
