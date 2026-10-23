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

These slots belong to Spoiled Milk/Core game implementation. The same manager
also owns the fresh standalone toolkit at `/home/justin/rsc-dev-toolkit`, using
dedicated `rsc-dev-toolkit-ai-N` worktrees. No separate editor manager is needed.
See [rsc-dev-toolkit.md](rsc-dev-toolkit.md). Sharing a manager does not combine
Git histories, releases or source trees.

Core also coordinates independent asset-authoring repositories. The dedicated
[RSC Model Studio](rsc-model-studio.md) workspace owns 3D modeling tools and local
asset projects; it does not use Core game-worker slots. Finished, approved
exports require a separate Core integration. Sprite Trainer remains the separate
2D authoring workflow, and [Sprite Baker](rsc-sprite-baker.md) retains its own role.

The former independent Editor and embedded-runtime management structure is
retired. Its repositories and branches remain preserved historical inputs, not
active assignment destinations. Existing game loader behavior is not removed
by this organizational reset. New toolkit reuse is selective and reviewed.

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

## Parking a superseded handoff

When a clean READY task should be preserved rather than merged (for example,
a retired compatibility experiment), the manager can free its seat with:

```bash
./scripts/ai-manager.sh park ai-2
```

Parking verifies the exact remote backup, retains both topic branches and a
durable `refs/ai-parked/ai-N/COMMIT` ref, and saves the original READY record
under the shared Git directory's `ai-workspaces/parked` folder. It switches the
seat to detached published main without overwriting ignored-file collisions.
Databases, assets and other ignored local files are not cleanup targets. The
generated `AI_WORKSPACE.md` is replaced with the idle guide; its old contents
are preserved alongside the record.

Only clean READY work can be parked. If switching is interrupted, rerun the
same command; recovery checks the original record and pinned destination.
An incomplete record or unexpected state requires manager inspection, not a
forced checkout. No merge, branch deletion, publication or deployment occurs.
Parked unmerged branches still block the normal release check as unmanaged
work; parking is not a way to claim they were integrated.

To resume a parked handoff, the manager must first verify an idle clean seat,
the retained local and remote tips, and the saved exact commit; then deliberately
reattach the retained branch using `git switch --no-overwrite-ignore BRANCH`
and run `ai-workspace.sh handoff` from that worker to re-register its READY
state. Never reattach over an active task. Ordinary task allocation, merge,
recycle and release requirements remain unchanged, including a clean published
manager checkout. Parking alone can preserve work while the manager has local
configuration edits, since it does not edit or integrate manager sources.

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
