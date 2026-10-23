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

## Core-Managed RSC Development Toolkit

- The owner retired the old independent Editor/runtime management structure on
  October 9, 2026. Do not route new assignments to those managers or resume their
  feature branches. Preserve historical repositories, worktrees, saved projects,
  imports, assets and evidence; retirement is not deletion or gameplay rollback.
- `/home/justin/rsc-dev-toolkit` is a fresh, independent repository managed by
  this Core manager. Its dedicated workers are `rsc-dev-toolkit-ai-N`, activated
  from that repository. No additional manager session is required.
- Core may directly coordinate, review, integrate and test toolkit work in its
  own repository. Core game integration belongs in Core worker slots; toolkit
  implementation belongs in toolkit slots. Never cross-merge their histories.
- Historical Editor/runtime code is read-only reference input after preservation.
  Reuse requires an explicit salvage decision, provenance/license review and
  focused tests. No old game runtime, provider lock or instruction tree is an
  implicit dependency of the new tool.
- The standalone editor must not require a game server, login, accounts, gameplay
  networking or player database. Loader upgrades are separate reviewed operations.
- See `docs/workspaces/rsc-dev-toolkit.md` for ownership and worker lifecycle;
  the new repository's `docs/README.md` is the active implementation roadmap.

## Core-Managed RSC Sprite Baker

- RSC Sprite Baker is a separate ancillary repository at
  `/home/justin/rsc-sprite-baker`, with one exclusive worker at
  `/home/justin/rsc-sprite-baker-ai-1`.
- The Core manager AI may enter the Sprite Baker manager checkout to activate,
  review, merge, test, publish, and recycle its worker. This is a deliberate
  single-manager exception; it does not combine the repositories or authorize
  Sprite Baker changes in Core worktrees.
- Never activate `Core-Framework-ai-1` through `-ai-3` for Sprite Baker work,
  and never merge or cherry-pick Sprite Baker topic branches into Core `main`.
  Commands for that project run from its own manager or worker checkout and
  follow its `AGENTS.md`.
- `/home/justin/2009scape` is a read-only compatibility input. Do not copy its
  cache, models, textures, animations, or generated derivative assets into
  either repository. A later Core integration task may deliberately import
  reviewed exported sprites without importing the tool's Git history.
- See [`docs/workspaces/rsc-sprite-baker.md`](docs/workspaces/rsc-sprite-baker.md)
  for routing and lifecycle commands.

## Core-Managed RSC Model Studio

- `/home/justin/rsc-model-studio` is the independent 3D asset-authoring
  workspace. Core directly manages it, with one exclusive implementation worker
  at `/home/justin/rsc-model-studio-ai-1`; no separate manager is required.
- Use that repository's `AGENTS.md`, authoring standards and local lifecycle
  wrappers. Never allocate Core, toolkit or Sprite Baker workers for its tools,
  and never merge its Git history into Core.
- It owns Blender authoring scripts, editable local model projects, previews,
  geometry validation and reviewed export candidates, not gameplay or map editing.
  Keep references, models, textures, generated assets and tool binaries out of Git.
- Only a separate approved Core integration may install reviewed exports into
  game-owned content. Native conversion and in-game compatibility require proof;
  an interchange OBJ or Blender preview is not automatically game-ready.
- The standalone editor discovers finished game content; it must not bundle
  Model Studio's custom assets or a model-ID roster.
- See [`docs/workspaces/rsc-model-studio.md`](docs/workspaces/rsc-model-studio.md)
  for routing, ownership and the initial scope.

## Inventory / Ground Sprite References

- Before designing a new inventory/ground icon, read
  `dev/myworld/reference-library/items/README.md`, then the requested visual
  family's notes and contact sheet (for example `weapons/daggers`).
- Inspect the individual full-canvas PNG as the scale/position anchor. These
  are visual references, not gameplay item definitions or production assets.
  Keep the original export untouched and follow the library's canvas guidance.

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
8. Coordinate the new toolkit directly, using its dedicated worker worktrees.
   Do not resume retired Editor/runtime assignments or import their branches.

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
