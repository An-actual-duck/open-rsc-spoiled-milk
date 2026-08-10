# AI Workspaces

Spoiled Milk separates stable folders from temporary task branches. A folder
is an AI seat; it is not a category of work.

```text
/home/justin/Core-Framework          manager AI; main only
/home/justin/Core-Framework-ai-1     neutral worker slot
/home/justin/Core-Framework-ai-2     neutral worker slot
/home/justin/Core-Framework-ai-3     neutral worker slot
/tmp/spoiled-milk-live-main          detached published live snapshot
```

Three workers plus the manager match the usual four concurrent AI sessions.
Use fewer slots if fewer sessions are needed.

## Project boundary

These slots belong only to Spoiled Milk/Core-Framework. World Editor tooling
uses `/home/justin/rsc-world-editor` and its own workers. Embedded World Editor
client/server runtime work uses `/home/justin/rsc-world-editor-runtime` and its
own workers. A World Editor provider-lock request, correction document, or
handoff must be handled by those managers; it must never be assigned to or
merged from a `Core-Framework-ai-*` slot.

The historical `world-builder/runtime/adaptive-v1` branch is retained only as
migration evidence. It is not part of Core `main`, not an active provider
route, and not a reason for this manager to monitor World Editor work.

For a single end-to-end explanation of the model, including why it works and
how a remote co-developer fits into it, read
[manager-worker-overview.md](manager-worker-overview.md).

## First-Time Setup

From the manager checkout:

```bash
./scripts/ai-workspace.sh init 3
./scripts/ai-workspace.sh status
```

Idle workers remain detached at a commit already contained in published
`main`; the folder may lag harmlessly while idle. Starting a task always
creates its topic branch from the current fetched published `main`:

```bash
./scripts/ai-workspace.sh start ai-1 fix/prayer-display
```

Open `/home/justin/Core-Framework-ai-1` in that worker AI session. The branch
describes the work; `ai-1` only identifies the seat.

## Normal Work Cycle

The worker saves progress durably:

```bash
./scripts/ai-workspace.sh checkpoint -m "Checkpoint prayer display"
./scripts/ai-workspace.sh handoff -m "Finish prayer display"
```

Both commands commit project changes, including untracked files, and push the
topic branch to `spoiled-milk`. Handoff also records the exact pushed commit as
READY. Any later edit or commit invalidates that handoff.

Before committing, the workflow lists staged paths and blob sizes. Likely
secrets and oversized files are left staged in a local `QUARANTINED` state,
never silently pushed.

The manager collects the result:

```bash
./scripts/ai-manager.sh status
./scripts/ai-manager.sh merge fix/prayer-display
./scripts/test.sh
git push spoiled-milk main
./scripts/ai-workspace.sh recycle ai-1
```

Recycling is permitted only after the exact worker tip is contained in both
local and published `main`. It deletes the merged local and remote topic branch
and returns the slot to detached IDLE state. Use `--keep-remote` only for a
deliberate long-lived remote record.

## When A Session Leaves A Mess

Do not delete, reset, clean, or stash the slot. From the manager checkout run:

```bash
./scripts/ai-manager.sh status
./scripts/ai-manager.sh rescue ai-2 -m "Rescue abandoned ai-2 work"
```

Rescue creates a timestamped `rescue/ai-2/...` branch when necessary, commits
tracked and untracked project files, and pushes the branch. The mess becomes a
normal reviewable branch instead of hidden local state.

## Safety Properties

- Only the manager owns `main`.
- Workers cannot begin on dirty or occupied slots.
- Checkpoints are commits backed up on the remote, not stashes.
- READY refers to one immutable commit, not a vague branch name.
- Merge and recycle refuse unpublished or uncontained work.
- The live server uses a detached published commit, so advancing `main` cannot
  alter files underneath a running server.
- Live databases and backups live under
  `~/.local/share/spoiled-milk/live`, outside every worktree.

See [ai-slot.md](ai-slot.md), [manager.md](manager.md), and
[live-deployment.md](live-deployment.md) for role-specific instructions.

External contributors do not use these maintainer slots or commands. Their
portable topic-branch, checkpoint, pull-request, and exact-commit handoff flow
is documented in [external-contributor.md](external-contributor.md).
