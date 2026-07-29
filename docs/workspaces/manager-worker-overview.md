# Manager and Worker AI Workflow

This document is the short, conceptual guide to Spoiled Milk's multi-AI
development setup. It explains how the pieces fit together, why the workflow
exists, and which parts are portable to another project.

## The Short Version

One AI session acts as the **manager**. It owns the stable `main` checkout,
reviews completed work, resolves integration conflicts, runs final tests,
publishes `main`, and prepares releases.

Other AI sessions act as **workers**. Each worker receives an isolated Git
worktree and one task branch. Workers can plan, discuss, investigate,
implement, and test normally. They periodically checkpoint their work to a
remote branch and eventually hand the manager one exact commit for review.

The scripts do not operate the AI or replace conversation with it. They only
control repository state: activating a safe branch, preserving work, recording
an exact handoff, merging reviewed work, and recycling the folder.

```text
                         published main
                               |
             +-----------------+-----------------+
             |                 |                 |
          worker 1          worker 2          worker 3
          task branch       task branch       task branch
             |                 |                 |
             +-------- pushed checkpoints ------+
                               |
                         READY handoffs
                               |
                         manager review
                               |
                    tests -> merge -> publish
                               |
                      detached live snapshot
                    (updated only deliberately)
```

## The Four Things To Keep Separate

The workflow becomes much easier to understand once these concepts are not
treated as interchangeable:

- **AI session:** the conversation and reasoning process.
- **Worktree or workspace:** a persistent folder that gives one AI session an
  isolated copy of the checked-out files.
- **Topic branch:** the temporary, task-specific Git history inside that
  workspace.
- **Checkpoint commit:** durable project state that survives a closed AI
  session, terminal, or computer.

A workspace is a reusable seat, not a type of work. `ai-1` can perform
renderer work today and server work tomorrow. The branch—not the folder—has
the descriptive name, such as `fix/bank-pinning` or
`refactor/renderer-performance`.

This prevents task-named folders from accumulating forever and makes it
possible to use the same three neutral worker seats repeatedly.

## Repository Layout

Spoiled Milk uses four development checkouts and one deployment checkout:

| Folder | Responsibility |
| --- | --- |
| `/home/justin/Core-Framework` | Manager checkout; owns `main`, integration, final testing, publishing, and releases |
| `/home/justin/Core-Framework-ai-1` | Neutral worker seat |
| `/home/justin/Core-Framework-ai-2` | Neutral worker seat |
| `/home/justin/Core-Framework-ai-3` | Neutral worker seat |
| `/tmp/spoiled-milk-live-main` | Detached deployment snapshot; never used for development |

All development folders are Git worktrees backed by the same local repository.
They share object storage and branch knowledge, but each has its own checked-out
files and index. Two workers can therefore change unrelated files
simultaneously without overwriting one another's working directory.

The usual limit of one manager plus three workers also matches four concurrent
AI sessions. Fewer workers can be used without changing the model.

## Task Lifecycle

Every ordinary task follows the same state transition:

```text
IDLE -> ACTIVE -> READY -> MERGED -> PUBLISHED -> IDLE
```

### 1. Manager inventories the repository

From the manager checkout:

```bash
git status --short --branch
./scripts/ai-manager.sh status
```

The manager checks that `main` is clean and identifies an idle worker. It also
looks for abandoned, dirty, or unpushed work before assigning anything new.

### 2. Manager activates one focused branch

```bash
./scripts/ai-workspace.sh start ai-1 fix/example-task
```

The command verifies that the slot is clean and safely idle, fetches the
published state, and creates the task branch from current published `main`.
The worker is then free to use normal AI collaboration: inspect the project,
develop a plan, ask questions, edit files, and run tests.

The accepted branch categories are `fix/`, `feat/`, `content/`, `balance/`,
`art/`, `idea/`, `docs/`, `refactor/`, `chore/`, and `test/`.

### 3. Worker checkpoints meaningful progress

From the worker folder:

```bash
./scripts/ai-workspace.sh checkpoint -m "Checkpoint example task"
```

A checkpoint stages tracked and untracked project files, checks for suspicious
or oversized files, creates a normal commit, pushes the same topic branch, and
verifies the remote backup.

Checkpointing is the answer to the fear of losing AI work. Important state
does not live only in the conversation, an unstaged folder, or a stash.

An exploratory or iterative assignment can remain active for many
checkpoints. For example, a renderer worker can repeatedly profile, discuss,
tweak, and privately test under one coherent performance task. It does not
need manager approval for every related question or milestone.

### 4. Worker creates an exact handoff

When the task is complete and tested:

```bash
./scripts/ai-workspace.sh handoff -m "Finish example task"
```

Handoff also commits and pushes, then records the exact commit as `READY`.
The worker reports:

- what changed;
- which tests were run;
- what was not tested;
- known risks or follow-up work;
- the exact branch and commit.

`READY` refers to one immutable commit. If the worker edits or commits again,
the old handoff is no longer valid and the worker must hand off the new tip.

### 5. Manager reviews and integrates

From the manager checkout:

```bash
./scripts/ai-manager.sh status
git log --oneline main..fix/example-task
git diff main...fix/example-task
./scripts/ai-manager.sh merge fix/example-task
```

The manager reviews the complete diff before merging. The merge command
refuses a dirty worker, an unpushed branch, a moved commit, or a branch that
was never marked `READY`.

After the local merge, the manager resolves integration issues, runs focused
tests and broader tests proportional to the risk, and publishes tested
`main`:

```bash
git push spoiled-milk main
```

Workers do not merge each other, update `main`, make releases, or deploy.
Centralizing those decisions is what lets several AI sessions work quickly
without each one needing a global understanding of every concurrent change.

### 6. Manager recycles the worker

Only after the handed-off commit is contained in published `main`:

```bash
./scripts/ai-workspace.sh recycle ai-1
```

Recycling deletes the completed task branch and returns the folder to a clean,
detached `IDLE` state. The folder remains available for a completely different
task.

Idle slots may appear behind `main`; that is harmless. Starting the next task
always uses current published `main`. Active branches do not update
automatically when another worker is merged.

## Division Of Responsibility

### Human project owner

- chooses priorities and product direction;
- gives workers goals and answers design questions;
- visually validates behavior that automated tests cannot judge;
- approves consequential releases and public-server maintenance;
- decides when an open-ended investigation has produced enough value.

### Manager AI

- keeps an inventory of every workspace and branch;
- activates task branches and rescues abandoned work;
- reviews exact handoffs rather than trusting a branch name;
- resolves conflicts between completed tasks;
- runs final integration tests and publishes `main`;
- builds releases from clean published history;
- protects the live server and data.

The manager generally avoids ordinary feature implementation. This keeps its
context focused on coordination, integration, and release state.

### Worker AI

- owns one coherent assignment and one topic branch;
- investigates and plans at normal conversational depth;
- implements only within that assignment;
- performs relevant local or private tests;
- creates frequent remote-backed checkpoints;
- hands off a clean, exact commit with evidence and risks.

Workers can have informal specialties—renderer, bugs, content—but the folders
remain neutral. A specialty is an assignment habit, not repository structure.

## Recovery Instead Of Destruction

If an AI session closes, becomes confused, or leaves a dirty workspace, the
manager preserves it before trying to interpret or clean it:

```bash
./scripts/ai-manager.sh rescue ai-2 -m "Rescue abandoned work"
```

Rescue creates a timestamped branch when needed, commits recoverable tracked
and untracked files, and pushes them for later review.

The normal workflow deliberately avoids:

- `git stash`;
- `git clean`;
- `git reset --hard`;
- forced checkout or branch deletion;
- deleting a dirty worktree;
- putting two AI sessions in the same worktree.

Preservation first is important because AI-produced work may be difficult to
reconstruct from chat history, especially after a crash or context loss.

## Release And Live-Server Separation

Publishing `main`, creating a release, and updating the public server are
different operations.

The live checkout is detached at an exact published commit. A merge or GitHub
release therefore cannot silently change files beneath the running server.
Public activation requires a separate deployment procedure and fresh,
explicit permission to initiate the in-game update countdown or stop/restart
the server.

This separation allows the manager to continue collecting and publishing work
without interrupting players.

## How A Remote Co-Developer Fits In

An external co-developer does not use the maintainer's sibling `ai-N`
worktrees. Those folders, their handoff metadata, release credentials, and
live deployment state exist only on the maintainer machine.

Instead, the co-developer uses:

1. one ordinary clone;
2. one username-namespaced topic branch per task;
3. pushed checkpoint commits;
4. a pull request containing an exact-commit handoff;
5. the maintainer manager AI for final collection, review, merge, and release.

For this repository, that flow is implemented by:

```powershell
py -3 scripts/contributor-workspace.py start fix/example-task
py -3 scripts/contributor-workspace.py checkpoint -m "Checkpoint example"
py -3 scripts/contributor-workspace.py handoff -m "Finish example"
```

The full Windows-friendly contributor guide is
[external-contributor.md](external-contributor.md).

A co-developer can independently reproduce the manager/worker pattern for
their own project, but Spoiled Milk's scripts are not generic drop-in tools.
They encode this repository's paths, remote names, branch rules, release
checks, and live-server boundaries.

## Portable Design Principles

To reproduce the model elsewhere:

1. Designate one canonical checkout as the only owner of `main`.
2. Create a small number of persistent, neutrally named worker worktrees.
3. Give every task a short-lived descriptive branch created from published
   `main`.
4. Make checkpointing commit and push both tracked and untracked project files.
5. Record handoffs by exact commit, not by a moving branch name.
6. Require manager review and tests before merging or publishing.
7. Refuse to recycle a worker until its handoff is contained in published
   `main`.
8. Provide a rescue path that preserves dirty or detached work.
9. Keep release and deployment state outside worker folders.
10. Put the role rules in the repository's AI instruction file so every new
    session discovers them before editing.

The central idea is simple: **parallel reasoning, isolated files, durable
commits, and centralized integration**.

## Common Mistakes This Prevents

- Naming permanent folders after temporary bugs or plans.
- Letting multiple AI sessions edit the same checkout.
- Treating an uncommitted working directory as durable storage.
- Losing work because a session or computer closed.
- Assuming an active worker branch automatically follows new `main` commits.
- Merging whichever commit happens to be at the tip later.
- Deleting a messy folder before checking for valuable untracked files.
- Allowing a feature worker to make release or live-server decisions.
- Restarting the public server merely because a release was created.

## Related Detailed Guides

- [README.md](README.md): setup and normal command cycle.
- [ai-slot.md](ai-slot.md): worker-specific rules.
- [manager.md](manager.md): manager collection, rescue, and release loops.
- [external-contributor.md](external-contributor.md): remote contributor flow.
- [live-deployment.md](live-deployment.md): guarded public deployment.
