# Classic-Scape combat follow-up audit — 2026-08-10

## Scope and upstream evidence

This is a read-only comparison of the post-refactor fixes in
[`aicovergod/Classic-Scape`](https://github.com/aicovergod/Classic-Scape) with
Spoiled Milk `main` at `885cc3a3b1099e45688fc8e87740004edefb5b7b`.

The reviewed upstream baseline is the prior program audit’s final revision,
`f6def6ffb4608f45a493d2c9e5160d4bc7627bb3`. The follow-up range is
`f6def6ff..eadf40f2182b594e9bb52e756c5146f19b90c682` (upstream `main` as
cloned on 2026-08-10 into the disposable path
`/tmp/classic-scape-followup-audit`). No upstream code, commits, remotes, or
artifacts were imported into this repository.

There are 20 meaningful post-baseline candidates: 17 semantic combat fixes,
two test/edge-case hardening commits, and one mixed content/combat bundle.
Documentation-only and merge commits are excluded. “Covered” means the local
behavioral contract is independently evidenced, not that code shape matches
Classic-Scape.

## Per-fix semantic comparison

| # | Upstream ref | Upstream defect / affected path | Spoiled Milk assessment and local evidence | Recommendation |
| --- | --- | --- | --- | --- |
| 1 | `06d9fe6b` | Combat gate failed unclearly on clean checkout. | Covered differently: [`combat-test-gates.md`](combat-test-gates.md) defines a non-vacuous Ant gate; `server/build.xml` requires its positive scenario receipt. | No action. |
| 2 | `302b6c30` | NPC ordinary disengage was mistaken for corruption, forcing leash/reset instead of re-engagement (`NpcBehavior`, controller). | Partial. A10’s `NpcBehavior` lifecycle fixture covers logout/layer/leash boundaries, but there is no equivalent explicit `REENGAGE_PENDING` state or one-tile retreat-with-status/contribution regression. | Add characterization coverage first; high risk because current NPC status/contribution semantics are custom. |
| 3 | `8118c7a9` | Bow/thrown PvP ignored reattack immunity at command and repeat-launch boundaries. | Intentional current divergence: `CombatEligibility.validatePlayerRules` only enforces `canBeReattacked()` for melee, while `myworld.conf` keeps PvP disabled. `AttackHandler` still performs a legacy player check. | No production port while PvP is disabled; preserve as a future PvP re-enable prerequisite. |
| 4 | `6fbd4bcc` | Goblin’s Tenacity could roll twice through legacy and typed damage paths. | Vulnerable. `Skills.subtractLevel` applies Tenacity, while `Mob.damageAndGetActualDamage` and `PoisonEvent.settleTypedPoisonDamage` also call `Player.applyGoblinTenacity` before paths that can reach that skill setter. Existing combat fixtures preserve present display/HP compatibility, but do not assert one draw per eligible hit. | **P0: investigate and characterize; then make one bounded settlement-owner decision.** |
| 5 | `cffd3160` | DoT stack/no-effect feedback, adaptive chance, and owner identity diverged. | Partially covered with intentional design divergence. A08 has `PoisonTargetState`, `PoisonTargetPolicy`, durable records, and compiled lifecycle checks; generic burn was deliberately retired, so Classic’s burn policy cannot be copied. | Add only targeted poison no-op/proc-feedback coverage if absent; do not port the shared DoT service. |
| 6 | `a49aa05a` | `::shootme` debug command bypassed a typed projectile-debug service. | Not a gameplay authority issue locally. Debug/admin commands remain separately owned under `server/plugins/.../commands`; no equivalent shared service exists. | Investigate only if private-debug behavior is being revised; otherwise no action. |
| 7 | `e96a8562` | Plugin reload could invalidate/replay death hooks. | Partial and R2-adjacent. A05.5 proves exactly-once death callbacks and plugin-owned NPC compatibility, but reload-generation semantics belong to the Server R2 extension boundary. | Do not port on this program; hand its contract to R2 if reloadable combat hooks are enabled. |
| 8 | `e3588b69` | Secondary-target declarations did not consistently own aggro/duplicate/child-effect semantics. | Mostly covered by A05.4/A07’s `SecondaryEffectPolicy`, descriptor inventory, transaction parity fixtures, and child-damage tests. Local policies intentionally retain event-owned settlement rather than Classic’s central executor. | No broad port. Add a focused regression only if a concrete zero-damage/aggro mismatch is reproduced. |
| 9 | `b6913ddc` | One generic “in combat” predicate incorrectly governed unrelated actions. | Partial. A03/A04 distinguish intent, ownership, lifecycle and current action paths, but no single local `CombatActionPolicy` matrix exists. | P2 characterization of logout/teleport/bank/trade/object actions against outgoing/incoming/projectile states. |
| 10 | `c1aff6ca` | On-hit registry capacity could truncate configured effects. | Covered by different architecture: A07’s semantic catalog asserts 71 identities and specifically rejects the inherited fixed-32 capacity assumption; it has no generic on-hit registry/executor. | No action. |
| 11 | `79bdfa6b` | Scripted NPC retreat duration was ignored by normal behavior/reset. | Partial. Current `NpcBehavior` has its own retreat/leash compatibility paths, but no explicit script-duration lifecycle audit comparable to upstream. | Fold into #2’s NPC disengage/retreat characterization; do not port controller states. |
| 12 | `200cc0a7` | Low-risk edge cases in NPC death/lifecycle needed executable reproducers. | Broadly covered by A05.5/A08/A10’s death, respawn, poison, callback-failure, and profile-lifecycle fixtures. Upstream’s exact cases are not one semantic family. | No blanket change; compare individual reproducers only if a local failure is observed. |
| 13 | `4994c34c` | Player attack intents survived long enough to commit stale approach callbacks. | Covered. `PlayerAttackTransaction` owns a 100-tick lease, checks expiry in issue/get/prepare, and has `CurrentCombatCharacterizationTest.attackIntentLifecycle`. | No action. |
| 14 | `512594c4` | Mixed item/content bundle also touched attack/spell/DoT/NPC profile code. | Not a coherent combat follow-up fix: it combines Foundry Dragon, sprites, items, balance, client packaging, and Server R2 inventory updates. | No action; evaluate its content changes through their own plans only. |
| 15 | `c9a36e6e` | Menu/UI state could bypass combat logout restrictions. | Partial. A03/A04 exercise logout ownership cleanup, but the exact menu-open logout predicate is not in the compiled 134-scenario gate. | P2 add a regression characterization before any behavior change. |
| 16 | `4e210bb6` | Real projectile launches did not arm escape/logout cooldown. | Potentially vulnerable. Local projectile policy has launch snapshots/impact ledger, but no `PlayerProjectileLaunchRecord`; `Mob.setCombatTimer` is used by many owners without a typed launch-only record. | **P1: characterize player launch → logout/teleport/movement boundaries.** |
| 17 | `4abae6b6` | Autocast PvP reattack denial occurred after the higher-level attack committed. | Intentional inactive-PvP divergence. `MagicCombatEvent` uses the local transaction and `CombatEligibility` rejects player PvP globally first. | No production port now; make a PvP re-enable acceptance criterion alongside #3. |
| 18 | `f16b3aae` | Pure policy tests missed handler-level action-state effects. | Partial. The local compiled gate uses real handlers/events for many paths, but has no named end-to-end cross-action matrix. | P2 add a compact matrix fixture, beginning with #15/#16 scenarios. |
| 19 | `d162a06c` | Combat cooldown callers mixed authoritative game time and JVM wall time. | Vulnerable. `Mob.setCombatTimer` writes `GameClock.currentTimeMillis()`, but combat-adjacent consumers such as `NpcTalkTo` and `InterfaceOptionHandler` compare it with `System.currentTimeMillis()`. | **P1: inventory and characterize clock domains before any consolidation.** |
| 20 | `3971c19` | Script/admin damage directly mutated Hits and reconstructed death/presentation. | Vulnerable. `ArmyOfObscurity` directly subtracts Hits for Necronomicon; `ResetCrystal` and admin command paths pair direct Hits mutation with `killedBy`. A05 transactions deliberately did not absorb generic compatibility helpers/scripts. | **P0: create a bounded raw-Hits mutation inventory and per-path policy proposal.** |

## Prioritized shortlist

1. **P0 — Tenacity settlement ownership (#4).** Reproduce deterministic one- and
   two-roll paths for direct helper, poison, primary, reflection, and child
   damage. The fix must preserve the project’s current displayed damage and
   post-hit hook compatibility while making exactly one survival decision.
2. **P0 — Scripted/admin raw-Hits inventory (#20).** Classify each direct
   mutation as gameplay damage, deliberate administrative state transition, or
   setup/restoration. Do not send all of them through one pipeline without
   preserving current credit, death, and privilege semantics.
3. **P1 — Combat clock-domain audit (#19).** Identify every combatTimer writer
   and reader; distinguish intentionally real-time UI/session timers from
   gameplay cooldowns. Require deterministic deadline/replay fixtures before a
   change.
4. **P1 — Projectile escape boundary (#16).** Test real bow/thrown/magic
   launch, parent-event end, target/source lifecycle changes, and attempted
   logout/teleport/movement before deciding whether a new record is necessary.
5. **P2 — NPC disengage/scripted retreat (#2/#11).** Add runtime
   characterization for retreat/re-engagement with poison, partial damage,
   contribution, leash exit, logout, and layers. A controller port is not
   approved by this audit.
6. **P2 — Action-state matrix (#9/#15/#18).** Add only the matrix rows that
   exercise concrete current handlers; prioritize menu-open logout and launch
   escape interactions.

## Coverage result and boundaries

Of the 20 candidates, **five are already covered by the Spoiled Milk
adaptation** (#1, #10, #12, #13, and the non-combat portion of #14), **six are
partially covered but need no automatic port** (#2, #5, #7, #8, #9, #11),
**three are intentional inactive-PvP or debug/content divergences** (#3, #6,
#17), and **four merit focused follow-up investigation** (#4, #16, #19, #20).
The remaining two (#15, #18) are coverage-quality follow-ups, not confirmed
gameplay defects.

This report does not authorize balance changes, Classic-Scape controller/
pipeline imports, PvP enablement, Server R2 changes, plugin reload changes, or
any public-server operation. Each recommended follow-up needs its own branch,
owner decision, and executable parity scope.

## Follow-up hardening record

### #4 — Goblin’s Tenacity settlement ownership — completed

`PoisonEvent.settleTypedPoisonDamage` had applied Goblin’s Tenacity before
creating a resolved-damage request, while `Skills.subtractLevel` applied it
again when `ResolvedDamageTransaction` settled that request. A first failed
roll could therefore receive a second survival opportunity. Poison now records
the one compatibility roll needed to retain its historical post-mitigation
damage update and hitsplat, and explicitly marks that request as already
mitigated. The shared transaction passes that narrow fact to `Skills` so it
does not replay the roll.

The compiled scenario
`generic_poison_applies_goblin_tenacity_once_at_settlement` scripts a missed
first roll followed by a would-be successful second roll and verifies that the
player follows the first result into the ordinary terminal lifecycle. Existing
poison factual-damage coverage continues to verify post-Tenacity Hits,
lifesteal, and presentation compatibility. `./server/test_combat` passed all
135 scenarios after the change.

### #20 — Raw Hits mutation inventory — completed for confirmed gameplay damage

The complete `Skill.HITS` mutation inventory separates ordinary healing,
boost/restore, NPC scripted regeneration, summon state synchronization, and
test/setup utilities from damage. The only ordinary player-facing raw damage
outside the resolved-damage path was the Army of Obscurity Necronomicon: one
non-lethal scripted self-hit, a damage update, a stat packet, and no hitsplat.
It now uses a `SCRIPT` resolved-damage request with `DAMAGE_ONLY` presentation,
preserving that exact presentation and avoiding invented contribution,
lifesteal, or death policy.

`ResetCrystal`, `Admins.damagePlayer`, `Admins.damageNpc`, Development kill
commands, and the signed `ProjectileEvent` admin hook remain intentional
privileged compatibility operations. In particular, the signed projectile
hook can heal above the ordinary maximum and therefore cannot use the
non-negative damage transaction. Quest NPC resurrection/regeneration and
summon state synchronization are likewise not damage paths. They are recorded
as explicit follow-up candidates only if their individual administrative or
quest semantics are redesigned; this branch makes no generic raw-Hits rewrite.

The compiled `necronomicon_self_damage_uses_resolved_sparse_settlement`
scenario verifies the script-category transaction’s one-Hit settlement,
damage update, and no-hitsplat policy. `./server/test_combat` passed all 136
scenarios; the full server/plugin build remains required before final handoff
to compile the changed plugin artifact.

### #19 — Combat-clock domain reconciliation — completed

`Mob.setCombatTimer` has always written the server `GameClock`, but six active
cooldown consumers compared that value with JVM wall time: player logout,
NPC-talk busy grace, both OpenPK stat-change checks, the wilderness Magical
Pool, and A Bone To Pick eligibility. They now compare the combat timestamp to
the same authoritative `GameClock`. Real-time client activity, exchange,
movement, report, and shot timestamps intentionally remain on wall time; only
the combat-timer clause moved.

The compiled `combat_cooldown_uses_authoritative_game_clock` fixture freezes
wall time implicitly through the deterministic harness, proves a fresh combat
timestamp blocks the cooldown, then advances exactly 10,001 game-clock
milliseconds and proves it opens. `./server/test_combat` passed all 137
scenarios. Plugin-only callers are included in the final full server/plugin
build gate.
