# Dot assignment: reconcile the shared combat Slow foundation

Assigned by Core with owner approval, October 24, 2026. This is an implementation
assignment for the existing Slow foundation, ending at an exact tested worker
handoff. Weapon-coating implementation is a subsequent assignment.

## Workspace, role and handoff

- Worktree: `/home/justin/Core-Framework-ai-2`.
- Assigned branch: `feat/shared-slow-reconciliation`, activated by Core.
- Role: **maintainer-owned worker** under this worktree's `AGENTS.md`. Dot is
  using the owner's local worker slot, not the external-contributor workflow.
- Read `AGENTS.md` and `docs/workspaces/README.md` before editing. Run
  `git status --short --branch` and `./scripts/ai-workspace.sh status` from the
  assigned worktree. Confirm the branch and clean state before proceeding.
- Use only this worktree for implementation. Core manager, other worker slots,
  preserved baseline copies, Model Studio and Toolkit are read-only references.
- Do not run manager, allocation, recycle, release or deployment commands. Do
  not push `main`, merge branches into it, change Git histories, or launch/stop
  any server or client. Offline fixtures/builds in this worker are authorized.
- Never access account databases, credentials, the live checkout or owner map
  projects. Do not alter the owner's installed editor or running preview.
- Preserve unexpected work; do not stash, clean, hard-reset or force-push.
- Checkpoint useful milestones with
  `./scripts/ai-workspace.sh checkpoint -m "descriptive message"`.
  Finish using `./scripts/ai-workspace.sh handoff -m "descriptive message"`.
  Core alone reviews, merges, publishes and schedules gameplay testing.

This worktree is exclusive to Dot for this task. No other implementation session
should run there until Core collects or explicitly reassigns it.

## Current state and authoritative inputs

Game baseline at assignment preparation:
`b6f1c7dea5fbfe8713b0668a0614ac252d5c29ff` (published Core main).
The assignment document is added in a subsequent documentation-only commit;
the activated worker must include that commit. Record the actual starting HEAD.

Preserved implementation commit:
`5e79e8f069c5c6f2cb7a4ed37bd50ce5e39d6e34`,
**Implement shared combat Slow power and latched action cooldowns**.
It is reachable from `spoiled-milk/art/slayer-item-sprite-candidates`, whose
reviewed reference tip at preparation is
`85ed0e8a05c13143d318336f680e786849a0b75a`. Resolve and record the reference
before reading it. The implementation commit above is the fixed source anchor
regardless of later branch movement.

The implementation exists in that preserved lineage but is absent from current
main: `content/Slow.java`, `SlowFixture.java`, the `test_slow` Ant target and the
foundation document have not been integrated. Current main retains older
percentage/attack-count Slow producers. This is reconciliation of preserved
work with today's code, not a greenfield redesign.

Inspect the source without switching or modifying its worktree:

```sh
git show --stat 5e79e8f069c5c6f2cb7a4ed37bd50ce5e39d6e34
git show 5e79e8f069c5c6f2cb7a4ed37bd50ce5e39d6e34:docs/myworld/in-progress-work-plans/shared-slow-foundation.md
git show 5e79e8f069c5c6f2cb7a4ed37bd50ce5e39d6e34:server/src/com/openrsc/server/content/Slow.java
git show 5e79e8f069c5c6f2cb7a4ed37bd50ce5e39d6e34:server/test/com/openrsc/server/combat/SlowFixture.java
```

Also read current lifecycle, combat clock, scheduling and HUD implementations,
plus `docs/myworld/rough-drafts/slayer-unique-equipment-design.md` and
`docs/myworld/in-progress-work-plans/effect-standardization-follow-up.md`.
Their older 20/50-power examples are historical proposals; the approved
10-power rule below supersedes them for this foundation.

The source branch contains unrelated sprites, antidote work and other changes.
Port focused Slow changes and necessary compatibility adaptations. Do not merge
the entire Slayer branch or replace whole modern files with historical copies.
Inspect dependencies first, including the preceding antidote commit; implement
the Slow task against current poison/lifecycle/HUD APIs without importing that
separate overhaul by accident. Report a genuine prerequisite with a minimal
dependency explanation if it cannot be resolved within this scope.

## Approved mechanical contract

1. Each player/NPC has one shared transient Slow-power pool. Applications from
   multiple sources add to it. Effective tier is `floor(power / 10)`.
2. Approved source caps are **12** (Slow 1) and **22** (Slow 2), including the
   owner's two-power maintenance buffer. Do not let a tier cap imply unlimited
   stored power. Keep arithmetic bounded and safe from overflow/invalid inputs.
3. Retain the highest received cap until power reaches zero. A weaker source
   can refill that retained cap. Once completely cleared, a new weaker source
   starts with its own lower cap.
4. Remove **2 power every 4 game ticks**, clamped at zero. Reapplication must
   not restart decay. Power 1–9 is retained but causes no effective Slow tier.
5. Scope is **combat action cadence**: melee, ranged/thrown attacks, offensive
   spell casting, and actor-owned NPC combat scheduling. Movement, food,
   potions, skilling, utility spells and transports keep their current timing.
6. Add the effective tier in whole ticks after ordinary action-speed modifiers.
   The one-tick Dagger of Terror therefore takes `1 + tier` ticks while slowed.
7. Capture the additional delay once when committing the next action deadline.
   Repeated application or decay must not keep pushing an already scheduled
   action away, shorten that deadline, or double-charge the same Slow penalty.
   Readiness polling must not repeatedly reschedule the action.
8. Death, logout/session replacement, NPC respawn/removal and relevant combat
   lifetime invalidation clear the pool and invalidate old decay/deadline work.
   Slow is not saved to accounts and does not tick offline. Avoid stale event
   mutation, duplicate decay events or accumulating abandoned state.
9. Display **Slow 1 / Slow 2** using the existing status HUD and Earth-rune
   placeholder icon. No visible tier for subthreshold power. Tier transitions
   update silently; same-tier top-ups update duration only on an actual power
   change. Capped applications must not cause chat or packet spam.
   Preserve all existing status identities; verify client/server ID parity.

### Existing producers to convert

Preserve each producer's existing eligibility, hit/proc chance, damage, owner
attribution and random-roll order. Replace only its Slow application/cadence.

| Source | Added power | Source cap | Existing trigger |
| --- | ---: | ---: | --- |
| Earth Strike / Rock Throw | 4 | 12 | Positive resolved damage |
| Earth Bolt / Earth Hammer | 6 | 12 | Positive resolved damage |
| Earth Blast / Earth Burst | 8 | 22 | Positive resolved damage |
| Earth Wave / Earth Impale | 10 | 22 | Positive resolved damage |
| Basic Withering | 8 | 12 | Positive damage to a surviving target |
| Advanced Withering | 12 | 22 | Positive damage to a surviving target |
| Earth sword | 10 | 12 | Existing 5% proc |
| Moss Giant leather set | 10 | 12 | Existing 20% proc |
| Earth Dragon leather set | 10 | 12 | Existing 20% proc; auxiliary damage unchanged |
| King Black Dragon earth branch | 12 | 22 | Existing elemental follow-up choice |

Retired equipment must stay retired. Supporting an existing grandfathered set
effect must not restore its crafting, drops or tradability. Keep legacy APIs
only where compatibility needs them, and identify remaining callers explicitly.
The historical projectile envelope used earth rank multiplied by three; adapt
its impact mapping without casually changing published constructor contracts.

### Special scheduling and separate effects

- Banshee, Naga and Bloodveld cooldowns capture Slow at attack commitment.
- Abyssal stab cadence is `1 + tier`. Keep its four-tick spike animation recovery
  intact, then apply the captured extra delay before the next attack.
- Keep Dark Beast's ten-tick charge and one-tick recovery intact. Capture the
  Slow penalty and delay its next attack separately; approach movement remains
  possible. Damage, marking, resistance and charge mechanics stay unchanged.
- Existing projectiles in flight, active windups, periodic damage and
  retaliation are not stretched or retroactively rescheduled.
- Startle/Ogre attack cancellation, Frostbite, Abyssal Whip delay, Sticky Skin's
  next-attack delay and frog/cockatrice/demon immobilization retain their
  separate behavior. Do not convert them all to accumulating Slow or change
  Shield of Mobility coverage. Verify their interactions with Slow.
- PvP remains disabled. The old reciprocal `CombatEvent` shares an alternating
  event between actors; adding Slow to its shared timer would slow both sides.
  Preserve the explicit PvP limitation and document it for later actor-owned
  scheduling work. Do not enable PvP or undertake that redesign here.

## Implementation sequence and evidence

1. Record starting HEAD, preserved source commit and branch state. Build a
   concise old-to-current path/dependency map covering producers, consumers,
   HUD, lifecycle and tests. Characterize relevant current behavior with the
   existing offline combat harness before modifying it.
2. Reconcile the shared pool, cap/decay/lifetime rules and HUD with current Core.
   Review historical code for correctness; historical tests are inputs, not
   automatic proof of today's behavior. Checkpoint the working foundation.
3. Integrate producers and all intended actor-owned combat scheduling paths.
   Update accurate descriptions through current authoritative item generators
   where necessary. Preserve IDs, unrelated stats, recipes and assets. Avoid
   duplicate legacy percentage penalties on converted routes.
4. Restore/adapt `test_slow` and exercise real scheduling paths, then run the
   current regression gates. Distinguish intended Slow behavior changes from
   unrelated regressions, and preserve exact damage/RNG checks.
5. Consolidate the documentation: restore/update `shared-slow-foundation.md`,
   link it from the older effect/coating plans, and label superseded examples.
   Keep the broader post-Slayer review reminder active. Record the implementation
   commit, tested commands, source mapping, limitations and deferred coating
   decisions in a task review report.
6. Finish a clean, pushed exact READY handoff. Stop before Core integration or
   release. If a genuine blocker occurs, preserve a checkpoint and report the
   concrete missing requirement; do not silently narrow coverage or claim pass.

## Required verification

Use deterministic offline fixtures and production scheduling/clock paths. Build
jobs should run sequentially with bounded heaps; do not launch gameplay servers.

- Thresholds 9/10/19/20, exact caps12/22, multi-source accumulation, cap retention,
  weaker-source refill, fresh lower cap after full clearance, overflow/invalid
  inputs, zero-clamping and subthreshold decay.
- Exact four-tick decay boundaries, reapplication before/at a boundary, and one
  decay process per target. No wall-clock sleeps as correctness assertions.
- Player and NPC melee, player ranged/thrown, legacy NPC ranged events, profile
  ranged/magic attacks and all manual/queued/autocast offensive spell gates.
- Both gaining and losing Slow after a deadline is committed; rapid repeated
  applications; attack cancellation and re-engagement; no double penalty or
  extension of a currently active projectile/telegraph.
- Dagger of Terror, all eight Slayer enemies' relevant combat paths, fixed
  Dark Beast charge and Abyssal spike recovery, and the separate lock/delay
  mechanics above. Preserve current spatial-domain and native-terrain checks.
- Actual death, logout/relogin/session replacement, NPC respawn and removal:
  stale events must not affect a replacement lifetime, and HUD/deadlines clear.
- HUD decoding/identity parity, tier changes and duration projection, silent
  same-tier top-ups and capped no-op applications. Unrelated statuses survive.
- Every converted producer's positive/zero hit policy, proc and RNG behavior.
  Retained percentage APIs must have an audited caller list.

The original validation targets were `ant test_slow`, `ant test_combat_strict`,
the client build, and HUD/leather/item-generator regressions. Restore `test_slow`
on the current build system; use the **current** combat suite rather than freezing
the historical 144-scenario count. Never remove coverage just to obtain green.
Representative local commands (from the assigned worker only):

```sh
export JAVA_HOME=/home/justin/jdk8u482-b08
export PATH="$JAVA_HOME/bin:$PATH"
export ANT_OPTS=-Xmx768m
(cd server && sh ../tools/vendor/apache-ant-1.10.5/bin/ant test_slow)
(cd server && sh ../tools/vendor/apache-ant-1.10.5/bin/ant test_combat_strict)
bash scripts/build-client.sh
python3 tests/myworld/test-cleric-status-hud.py
python3 tests/myworld/test-leather-set-bonuses.py
```

Inspect and run the appropriate current generator and Slayer-effect tests for
the touched paths as well. Record baseline failures separately, justify any
fixture adaptations, and report commands/scenario totals for the final commit.
Do not claim private gameplay acceptance; Core will arrange it after review.

## Preservation and completion boundaries

Preserve current loader/layering, spatial authority, terrain, ground-item death
fixes, status protocols, combat lifecycle and projectile ownership safeguards.
All approved art/model archives, item/scenery IDs (including windows1349–1352),
equipment mappings, prices and owner-local configurations remain intact.
No map edits, editor code, asset regeneration, database migration, broad combat
refactor, poison overhaul, live activation or player release belongs here.

**Do not implement the weapon coating in this assignment.** Its ingredients
(Cockatrice Eye and Sticky Saliva Gland) are already established, but herb,
recipe level/yield, per-hit poison/Slow amounts, cap and lifetime/charges are
still undecided. Retain them as collectible ingredients and list these questions
for the subsequent design pass. Existing stuns/roots stay separate from Slow.

Final handoff must include branch, full pushed commit, starting main commit,
changed-file summary, original-source provenance, producer/consumer coverage,
tests and totals, remaining legacy callers, known limitations and readiness.
Core should be able to review the focused diff and rerun the work without
depending on the old Slayer checkout or accidentally importing its other work.
