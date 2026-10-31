# Dot: ongoing hit-splat visual rework with Justin

Assigned by Core at Justin's request, October 31, 2026.

## Purpose and completion

This is an **ongoing, owner-led collaboration**, not a fixed implementation
brief. Justin and Dot will work directly together on the game's hit-splat
visuals from initial discussion through previews, revisions and the finished
implementation. Justin will explain the desired changes after Dot acknowledges
this assignment. No particular visual design is prescribed here.

The task is complete **when Justin explicitly says the overall task is complete
and ready to hand back to Core**. An approved image, successful build, satisfied
individual revision or temporary pause is not automatically that final signal.
Do not invent phase approvals or send intermediate READY handoffs.

Core continues the separate Cleric/combat work. This assignment lets Justin work
on hit-splat visuals with Dot without repeatedly returning to the manager.

## Reserved workspace and role

- Exclusive worktree: `/home/justin/Core-Framework-ai-2`.
- Assigned branch: `feat/hitsplat-visual-rework`, activated by Core.
- Dot is a **maintainer-owned local worker** under this worktree's `AGENTS.md`,
  rather than an external contributor using a separate clone.
- Read `AGENTS.md`, `AI_WORKSPACE.md` and `docs/workspaces/README.md`. From this
  worktree, run `git status --short --branch` and
  `./scripts/ai-workspace.sh status`. Confirm the assigned branch and record
  starting HEAD. Preserve and report unexpected state instead of cleaning it.
- Implement only in this worktree. Other workers, the manager checkout, the
  installed toolkit and the prepared next-update package are not destinations
  for these changes. Core will reconcile concurrent changes at final review.
- The slot is reserved for Dot until the task is handed off or Justin changes
  the assignment. Do not put a second implementation session in this worktree.

## Working directly with Justin

1. First acknowledge that you understand the role, open-ended completion rule
   and workspace. Then wait for Justin's detailed visual requirements. Do not
   redesign hit splats based only on this overview.
2. Once those requirements arrive, investigate, implement, build, preview and
   revise directly with Justin. Keep going through the agreed work without
   periodic manager approval or status relays. Ask Justin about real visual or
   product choices; make ordinary engineering choices independently.
3. Justin may expand or adjust the visual design throughout the task. Keep a
   short working record of accepted decisions, current preview instructions,
   unfinished work and the latest accepted appearance so the session can resume
   after a crash or context change. Use a task-specific document on this branch.
4. Use the normal checkpoint command at useful milestones and before ending a
   session: `./scripts/ai-workspace.sh checkpoint -m "descriptive message"`.
   These pushed backups are not manager review requests and do not finish the
   task. Keep the assignment ACTIVE until Justin's explicit final instruction.
5. Only interrupt this independent arrangement for a genuine blocker, conflicting
   workspace ownership or a required operation outside the authorized scope.
   Preserve progress and describe the specific issue. Routine previews,
   revisions and checkpoints do not require returning to Core.

## General scope and separation

The subject is the **presentation of hit splats**: the on-screen damage/healing
indicators and whatever related visual behavior Justin chooses to rework.
Client code, visual assets, necessary presentation metadata and focused tests
may be changed to support that design. Keep actual combat calculations, effect
chances, damage settlement and attack timing unchanged. A new gameplay rule or
protocol requirement must be identified and discussed with Justin explicitly,
rather than introduced silently as a visual adjustment.

For orientation, current presentation is in `Client_Base/src/orsc/mudclient.java`
and hit-splat state in `Client_Base/src/orsc/ORSCharacter.java`. Those are starting
references, not a prescribed implementation or a request to start now.

Crit/Stagger foundations have just been integrated. Current Cosmic values are
unchanged, and hostile Cleric debuffs are not enabled. This visual assignment
does not approve a robe rebalance, new crit source or combat-effect redesign.
Do not absorb unrelated feature work or edit other tasks' planning documents.

## Previews, preservation and verification

Build and run task-local visual previews as needed for Justin's review. Prefer
the actual client rendering path; identify a mockup clearly when one is used.
Keep assets, outputs and repeatable preview instructions organized. A preview
approval does not substitute for testing the final integrated rendering path.

If Justin requests an in-game preview during this task, prepare an isolated
private setup with its own files, fresh test data and unused localhost ports.
Start a preview server in its **own terminal**. Do not reuse or disturb another
session's running services, accounts, configurations or ports. Do not touch the
public server, live deployment, owner maps, credentials or account databases.
Local visual previews need no manager checkpoint or approval ceremony.

Keep resource use modest: run heavy builds/tests serially with bounded heaps,
and track any preview processes you start. Follow repository preservation rules;
never stash, clean, hard-reset, force-push or overwrite unexpected work. Do not
run manager/allocation/recycle/release/deployment commands, merge or push main,
or install changes into the owner's normal client/editor/update package.

Validate the behavior that actually changed and document practical limits.
Keep player/NPC rendering and affected renderer/profile paths consistent. Match
test coverage to the final agreed design; do not replace visual review with a
large collection of source-text assertions. No prescribed look, feature list,
fixed iteration count or artificial deadline is implied by this assignment.

## One final Core handoff

After Justin explicitly says the overall task is complete and ready for Core:

1. Finish the relevant build/checks, record Justin's final visual acceptance,
   and prepare one consolidated review document on the topic branch.
2. Include the finished design, changed files, asset provenance, exact tests and
   results, preview commands/locations, before/after evidence, compatibility or
   protocol implications, remaining limitations and any owned preview processes.
   Include sufficient information for Core to review without the chat history.
3. Run `./scripts/ai-workspace.sh handoff -m "Finish owner-approved hit-splat visual rework"`
   from the assigned worker. Verify the worktree is clean and the exact commit
   is pushed and marked READY. Do not edit after that handoff without updating it.
4. Give Justin one concise message to relay to Core: branch, full commit, review
   document, acceptance/validation summary and any integration concerns. A final
   package must preserve editable assets and include reproducible build/preview
   instructions; Core owns final integration, publication and deployment.

Until that explicit final signal, continue the collaboration with Justin and
preserve checkpoints. Do not declare the overall task finished on his behalf.
