# Spoiled Milk AI Collaboration Rules

This repository is designed for one manager AI and up to three worker AI
sessions. The directory identifies the role; the branch identifies the task.

Before changing anything, identify the session role and run its matching
preflight. The manager and maintainer-owned workers run:

```bash
git status --short --branch
./scripts/ai-workspace.sh status
```

External AI contributors must not run the maintainer workspace script. From
their own clone on Windows they run:

```powershell
git status --short --branch
py -3 scripts/contributor-workspace.py status
```

On other systems, use `python3` instead of `py -3`.

## Roles

- `/home/justin/Core-Framework` is the manager checkout. It owns `main`,
  integrates finished work, runs final tests, publishes `main`, and builds
  releases. Do not use it for ordinary feature implementation.
- `/home/justin/Core-Framework-ai-1` through `-ai-3` are neutral worker slots.
  A worker may edit only after the manager starts a focused topic branch in
  that slot.
- `/tmp/spoiled-milk-live-main` is the detached live deployment. Never edit,
  commit, build experimental work, or switch branches there.

## External AI Contributors

External contributors do not join the maintainer's `ai-N` worktrees and must
not run `ai-workspace.sh`, `ai-manager.sh`, release scripts, or live-server
scripts. They use one descriptive, username-namespaced topic branch per task,
push checkpoints, and submit an exact-commit pull-request handoff with
`scripts/contributor-workspace.py`.

They never push `main`, access live credentials or databases, decide merges,
build releases, or deploy. If work is dirty or confusing, preserve it with the
contributor rescue command before cleanup. The beginner-safe workflow is in
[`docs/workspaces/external-contributor.md`](docs/workspaces/external-contributor.md).

## Public Server Shutdown Gate

- Building, tagging, uploading, publishing, or being asked to "release" or
  "deploy" does **not** authorize stopping or restarting the public server.
- Never initiate a public-server shutdown without fresh, explicit user
  permission for that shutdown in the current maintenance window. Permission
  must specifically authorize stopping, restarting, or running `::update` on
  the public/live server. Do not infer it from earlier permission, a general
  approval, a release request, a deployment request, or a report that no
  players are online.
- The in-game `::update [seconds] [reason]` command warns players, displays the
  countdown, and schedules the graceful shutdown. Because it initiates the
  shutdown, obtain permission **before** asking an administrator to run it or
  running it by any available mechanism.
- After permission, back up the live database, have an administrator run
  `::update`, and let the full warning window complete. If the AI cannot issue
  the in-game command, it must pause and ask the user to run it and confirm the
  countdown completed. Never bypass the warning window with `kill`, Ctrl-C,
  `stop-hosted-server.sh`, or another signal.
- `stop-hosted-server.sh` is only a guarded fallback for a process that remains
  after the authorized `::update` window. Its acknowledgement flags are
  attestations, not permission; pass them only when those facts are true.
- Release preparation and publication may proceed without interruption. If
  shutdown permission is absent, leave the current public server running and
  report that live activation is waiting for authorization.

## Worker Rules

1. One task and one topic branch per slot. Never work on `main` or detached
   `HEAD`.
2. Use a descriptive branch such as `fix/prayer-display`,
   `feat/mining-expansion`, or `docs/renderer-plan`; never name a branch after
   the slot.
3. Run `./scripts/ai-workspace.sh checkpoint -m "message"` at useful
   milestones and before the session may end. A checkpoint commits tracked and
   untracked project files and pushes the same branch to `spoiled-milk`.
   Review any sensitive/large-file quarantine instead of bypassing it casually.
4. Run `./scripts/ai-workspace.sh handoff -m "message"` only when the exact
   pushed commit is ready for manager review.
5. Report changed files, tests, known risks, and whether the handoff is ready.

## Manager Rules

1. Keep the manager checkout on clean `main` except for deliberate integration
   or repository-management work.
2. Begin collection with `./scripts/ai-manager.sh status`.
3. If a session disappeared with uncommitted work, use
   `./scripts/ai-manager.sh rescue <slot> -m "message"` before doing anything
   else to that slot.
4. Inspect the branch diff, then merge only an exact READY handoff with
   `./scripts/ai-manager.sh merge <branch>`. Run the relevant tests before
   publishing.
5. Push tested `main`, then recycle a merged slot with
   `./scripts/ai-workspace.sh recycle <slot>`. Recycling must refuse any branch
   not contained in published `main`.
6. Run `./scripts/ai-manager.sh release-check` before live deployment and use
   `./scripts/ai-manager.sh release ...` for player packaging.
7. For live activation, follow the Public Server Shutdown Gate above and
   `docs/workspaces/live-deployment.md`. `deploy-live-main.sh` refuses to change
   tracked live files while the public port is occupied.

## Preservation Rules

- Never use `git stash`, `git clean`, `git reset --hard`, forced checkout,
  forced branch deletion, or forced worktree removal as routine workflow.
- Never delete a dirty slot. Rescue and push it first.
- Do not assume ignored files are disposable. Databases, credentials, logs,
  and release artifacts live outside Git and must be audited separately.
- Do not run two AI sessions in the same worktree.
- Collaboration scripts reject callers whose current directory is outside a
  registered Core-Framework manager/worker worktree. This prevents another
  project manager from accidentally activating or collecting these slots.
- Do not launch the public server from the manager or a worker slot.
- If Git state is unexpected, stop editing and let the manager inventory it.

The full workflow is documented in
[`docs/workspaces/README.md`](docs/workspaces/README.md).
