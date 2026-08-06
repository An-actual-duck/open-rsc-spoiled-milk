# Classic-Scape Combat Refactor Audit — 2026-08-05

Status: AUDIT COMPLETE; implementation requires separate focused branches and
manager approval.

## Executive Decision

Do **not** merge, cherry-pick, or reproduce the complete Classic-Scape combat
refactor as one change. It is a substantial and often well-structured redesign,
but its final behavior is not a drop-in replacement for current Spoiled Milk.
The useful result is a set of architectural source materials to adapt behind a
Spoiled-Milk-first characterization suite.

The strongest reusable ideas are:

1. a non-vacuous, deterministic Ant combat-test gate;
2. injected combat clock and random sources;
3. typed tick, request, result, reason, and lifecycle values;
4. one damage/death boundary;
5. launch/impact/settlement separation for projectiles;
6. explicit ownership for engagement, offensive events, contributions, and
   secondary targets; and
7. bounded, correlated combat diagnostics.

The final Classic-Scape behavior also contains confirmed incompatibilities:
ordinary NPC disengagement can fully heal and wipe contribution state; melee
PvM XP becomes flat damage-times-16; dragon mechanics are replaced; ranged and
magic PvP can bypass reattack immunity; poison/DoT stacking changes; and several
multi-target proc and engagement rules differ. These are not acceptable
incidental consequences of a refactor.

The recommended first branch is therefore **A01 — current combat
characterization and non-vacuous Ant gate**, not a production pipeline port.
It should make today's intended behavior executable before any authority moves.

This audit changed no combat code, merged no upstream commit, and did not touch
or start the public server.

## Evidence Anchors And Method

### Revisions

- Spoiled Milk baseline: `307d7a91ff05654f850b34df9b79aa6507c1b39d`
  (`v0.2.66`, 2026-08-05).
- Classic-Scape source: [aicovergod/Classic-Scape](https://github.com/aicovergod/Classic-Scape).
- Classic-Scape final reviewed revision:
  [`f6def6ffb4608f45a493d2c9e5160d4bc7627bb3`](https://github.com/aicovergod/Classic-Scape/commit/f6def6ffb4608f45a493d2c9e5160d4bc7627bb3).
- First refactor implementation revision:
  [`1f79a15ee1e6bc9e7e54266f797cb62cc1fc4250`](https://github.com/aicovergod/Classic-Scape/commit/1f79a15ee1e6bc9e7e54266f797cb62cc1fc4250).
- Last numbered architecture revision before fixes:
  [`afdc503918241325482917166341be6c26581ccf`](https://github.com/aicovergod/Classic-Scape/commit/afdc503918241325482917166341be6c26581ccf).
- Post-sequence corrective revision:
  [`d48213983b89d817c496419c93f55f548e56bba8`](https://github.com/aicovergod/Classic-Scape/commit/d48213983b89d817c496419c93f55f548e56bba8).

The remote branch named `npc-combat-master-refactor` points only to Task 01 at
`1f79a15e`. Tasks 02–36, follow-up fixes, review documents, and Task 37 were
committed on Classic-Scape `main`. Treating the named branch as the complete
refactor would omit almost all of the work.

### Inspection performed

- Read the upstream commit graph, numbered task records, policy documents,
  production code, tests, build files, definitions, and upstream self-review.
- Compared upstream sources with current Spoiled Milk combat handlers, entity
  state, formulas, plugins, configuration, tests, layered-world boundaries,
  Cleric effects, summons, dragons, poison, and post-v0.2.64 changes.
- Counted the selected implementation/fix/test commits independently of
  unrelated commits interleaved on upstream `main`.
- Compiled the Classic-Scape final tree through its bundled Ant combat target
  and ran the deterministic suite. Result: `PASS=113`, `XFAIL=0`,
  `SCENARIOS=113`, `BUILD SUCCESSFUL`.
- Verified the final gate fails closed when required profile/config/database
  inputs are absent in a deliberately incomplete sparse checkout, then passes
  when the shipped inputs are present.

The upstream
[`classic-scape-combat-review.md`](https://github.com/aicovergod/Classic-Scape/blob/f6def6ffb4608f45a493d2c9e5160d4bc7627bb3/tasks/Npc%20Player%20Combat%20System%20Tasks/classic-scape-combat-review.md)
was used as evidence, not as acceptance authority. Its findings were checked
against the final source and current Spoiled Milk surfaces.

### Scale

The selected 43 implementation, support, corrective, and Task-37 commits total
71,317 added and 13,218 removed lines when commit deltas are summed. They touch
503 unique paths, including 290 server-production paths, 27 Java combat-test
paths, 43 Python-test paths, 26 combat policy documents, 57 numbered-task
documents, and 13 configuration/build-profile paths. Summed commit deltas count
a path more than once and describe review scale, not final net size.

Classic-Scape ends with 137 Java files under
`server/src/com/openrsc/server/model/combat`. Current Spoiled Milk has no files
in that package. This is a replacement architecture, not a small isolated
patch.

## Exact Upstream Commit Inventory

The table identifies the implementation commits by task. Short labels link to
the exact full commit. Task 09 was deliberately delivered as five commits;
Tasks 10 and 11 share one large implementation revision, so the movement of a
task document is not a reliable code boundary.

| Task | Exact implementation commit(s) | Primary system |
| ---: | --- | --- |
| 01 | [`1f79a15e`](https://github.com/aicovergod/Classic-Scape/commit/1f79a15ee1e6bc9e7e54266f797cb62cc1fc4250) | Deterministic combat harness |
| 02 | [`1f6bc8e4`](https://github.com/aicovergod/Classic-Scape/commit/1f6bc8e4173d2686b30aaa0568164a473340c168) | Eligibility and liveness |
| 03 | [`0ec7af10`](https://github.com/aicovergod/Classic-Scape/commit/0ec7af10f45ff53fc8a83619486cb47a1fd74082) | Engagement aggregate |
| 04 | [`e1f10922`](https://github.com/aicovergod/Classic-Scape/commit/e1f10922a16f77d20808b5b912efcc9dd6f820af) | Encounter-safe teardown |
| 05 | [`cc2a8bf3`](https://github.com/aicovergod/Classic-Scape/commit/cc2a8bf30c14963b39cbb551ca91572597b777ab) | Passive retaliation ownership |
| 06 | [`3665a52d`](https://github.com/aicovergod/Classic-Scape/commit/3665a52dda1f130637e1cbc729ae4c303234258c) | Attack-start transactions |
| 07 | [`593368bc`](https://github.com/aicovergod/Classic-Scape/commit/593368bc6dfd71c8609ebe44427a05db05add45c) | Offensive event registry |
| 08 | [`d9a6929c`](https://github.com/aicovergod/Classic-Scape/commit/d9a6929c9709af2ad1cb39b0a443f73f0ac11dde) | Fractional cadence |
| 09 | [`e00c154b`](https://github.com/aicovergod/Classic-Scape/commit/e00c154b4650dc0a80f9cca91fde99cde77d01fa), [`7b8be8e3`](https://github.com/aicovergod/Classic-Scape/commit/7b8be8e3a433d5088642b321f818200188bf6a63), [`38c646c0`](https://github.com/aicovergod/Classic-Scape/commit/38c646c0943a430e225f705e2ebacc96aa322bef), [`a749d8bc`](https://github.com/aicovergod/Classic-Scape/commit/a749d8bc5171e7ed84179df668b0c96a7ea89aab), [`1523c3d7`](https://github.com/aicovergod/Classic-Scape/commit/1523c3d7d5471a3192b8b17ecae43bc158957abd) | Shared damage pipeline migration |
| 10–11 | [`e66ec1fa`](https://github.com/aicovergod/Classic-Scape/commit/e66ec1fa76a759682bdab443c264b4440f5ee08f) | Post-mortem sequencing and idempotent death lifecycle |
| 12 | [`0bb9bf26`](https://github.com/aicovergod/Classic-Scape/commit/0bb9bf2628d768e8a21c25520ad246f941cf86cb) | Environmental damage |
| 13 | [`652b0b0a`](https://github.com/aicovergod/Classic-Scape/commit/652b0b0ae576448876782a23568460e2d94b8a20) | Typed DoT provenance |
| 14 | [`b548f34b`](https://github.com/aicovergod/Classic-Scape/commit/b548f34b5e0dd321cd47bccd1b4cac5967201fd2) | Contribution ledger |
| 15 | [`51d670df`](https://github.com/aicovergod/Classic-Scape/commit/51d670df33bd2f9f95d5e6c367b812d8d6646178) | Kill-credit policy |
| 16 | [`7406d186`](https://github.com/aicovergod/Classic-Scape/commit/7406d186fca34b95df6ac7d6b23c5451919222d4) | Death plugin contracts |
| 17 | [`e95dd157`](https://github.com/aicovergod/Classic-Scape/commit/e95dd157c3e083496bda80da4b26ba3a9f97756d) | Combat-start hooks |
| 18 | [`d5795245`](https://github.com/aicovergod/Classic-Scape/commit/d5795245dcb1bdf40393c6dda6d66982df4bb54f) | Combat extension contracts |
| 19 | [`be020ffa`](https://github.com/aicovergod/Classic-Scape/commit/be020ffa851f06bc35b2cf7eb95cea8e7e20693c) | Projectile impact validation |
| 20 | [`98df7e42`](https://github.com/aicovergod/Classic-Scape/commit/98df7e42978a84a40a76d711d0b682b462220a81) | Projectile settlement |
| 21 | [`d2004ec8`](https://github.com/aicovergod/Classic-Scape/commit/d2004ec8bacfc3a60c933c1b2d7ca6853aa890da) | Survival/reflection ordering |
| 22 | [`d9b85d80`](https://github.com/aicovergod/Classic-Scape/commit/d9b85d80a87f10b6436643c99a0352ce4b1c96b4) | Overkill accounting |
| 23 | [`d150115a`](https://github.com/aicovergod/Classic-Scape/commit/d150115a39800b6a6665b380b23dbc0c1871c9f9) | Secondary-target eligibility |
| 24 | [`c8613e96`](https://github.com/aicovergod/Classic-Scape/commit/c8613e96c840cf7068be1bd6c2279124f1c8dc77) | Chain traversal |
| 25 | [`e03c1fc2`](https://github.com/aicovergod/Classic-Scape/commit/e03c1fc2b2655047bdcb352b536a7925bf659f76) | Spell AoE impact |
| 26 | [`24c83cab`](https://github.com/aicovergod/Classic-Scape/commit/24c83cab2c18c83484c31eddba82c3cf2a733bef) | On-hit phases |
| 27 | [`a50da336`](https://github.com/aicovergod/Classic-Scape/commit/a50da33652b930783cb245711eafa5271e1cfa9c) | Final-damage telemetry |
| 28 | [`ac5101af`](https://github.com/aicovergod/Classic-Scape/commit/ac5101afe7b1e7f5c1ad09c33f48dee84db441bb) | Formula modes |
| 29 | [`f59daf85`](https://github.com/aicovergod/Classic-Scape/commit/f59daf8567f390ed3537191bef723e9c9a184abf) | Data-driven NPC profiles |
| 30 | [`d3c1d969`](https://github.com/aicovergod/Classic-Scape/commit/d3c1d969127f58509e862be117ec5634bf229eb5) | NPC aggression/leash state |
| 31 | [`9d0a6c05`](https://github.com/aicovergod/Classic-Scape/commit/9d0a6c05cbb0828817c5ddfd6f0eee5cfe2ad5df) | `RangeEventNpc` retirement |
| 32 | [`bb1e4279`](https://github.com/aicovergod/Classic-Scape/commit/bb1e42796ab9af0172b61f1636191464d12ee043) | Special NPC loot |
| 33 | [`06c5977a`](https://github.com/aicovergod/Classic-Scape/commit/06c5977a575322c912352c0d449f92aff7f0e310) | Participant-exit cleanup |
| 34 | [`48c9bf3d`](https://github.com/aicovergod/Classic-Scape/commit/48c9bf3de6ba1c0a83280d8919aadf22fea29279) | Protected-ranged loops |
| 35 | [`5968ec47`](https://github.com/aicovergod/Classic-Scape/commit/5968ec4788993969fcf02a953457fbe5008ede98) | Configuration profiles |
| 36 | [`afdc5039`](https://github.com/aicovergod/Classic-Scape/commit/afdc503918241325482917166341be6c26581ccf) | Structured observability |
| 37 | [`f6def6ff`](https://github.com/aicovergod/Classic-Scape/commit/f6def6ffb4608f45a493d2c9e5160d4bc7627bb3) | Clean-checkout-safe test gate |

Associated refactor commits:

- [`aa8e909d`](https://github.com/aicovergod/Classic-Scape/commit/aa8e909d656ba5c5e5219d4507f50bec5cd96875)
  fixes melee starts invoked by plugin callbacks.
- [`dce0e49f`](https://github.com/aicovergod/Classic-Scape/commit/dce0e49f9c48a75dfb493605d622fc54450135ff)
  extends cadence boundary coverage.
- [`d4821398`](https://github.com/aicovergod/Classic-Scape/commit/d48213983b89d817c496419c93f55f548e56bba8)
  is the post-sequence runtime/test fix bundle.
- [`946b4798`](https://github.com/aicovergod/Classic-Scape/commit/946b47985b7f2798f1aba7d4b3ce43523a202f46)
  only archives the Task-29 document.
- [`f543c676`](https://github.com/aicovergod/Classic-Scape/commit/f543c676617f1e0fd242b88fae3e6afba3dee4a5)
  adds post-review Tasks 38–52 and changes no runtime code.

Commits for Cleric work, sprites, maps, light systems, upstream synchronization,
and other content are interleaved in the same history. Examples include the
Graardor/K'ril sprite work, Cleric implementation commits, world/light commits,
and Spoiled Milk synchronization commits. They are explicitly excluded from
the refactor inventory and must not be swept into a future port.

## Current Spoiled Milk Combat Architecture

### Concentrated active owners

| Current file | Lines | Active responsibilities relevant to this audit |
| --- | ---: | --- |
| `server/src/com/openrsc/server/model/entity/Mob.java` | 2,367 | Shared combat pointers, event fields, damage/healing, status and spatial-domain state |
| `server/src/com/openrsc/server/model/entity/npc/Npc.java` | 1,965 | Damage maps, threat selection, XP, kill credit, death, drops, summons, personal loot |
| `server/src/com/openrsc/server/model/entity/npc/NpcBehavior.java` | 819 | Roaming, aggression, chase, retreat, projectile selection, layered reachability |
| `server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java` | 899 | Player melee/PvP cycles, mitigation, direct and secondary effects, death |
| `server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java` | 1,027 | PvM melee, summoning, Cleric effects, dragon gear, poison, scythe AoE |
| `server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java` | 1,055 | Projectile impact, reflection, chains/AoE, poison, dragon, summon, Cleric effects |
| `server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java` | 2,499 | Spell packet policy, costs, target rules, casts, content-specific effects |
| `server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java` | 953 | Melee/ranged/magic offense, defense, accuracy, mitigation, custom balance |

The current design still projects combat through nullable `combatWith`,
`combatEvent`, `pvmMeleeEvent`, range/throwing event, hostile-target, sprite,
and timer state. `Mob.checkAttack`, `Mob.damageAndGetActualDamage`,
`Mob.killedBy`, and broad `resetCombatEvent` calls remain active. There are 34
direct `HITS` subtraction sites in active server/core and plugin sources, and
58 direct RNG/wall-clock references in the central combat/projectile/NPC
behavior surfaces inspected here.

That concentration makes the upstream ownership goals relevant. It also makes
a wholesale replacement dangerous: current and Classic versions of nine main
handlers differ by hundreds of lines apiece. For example, the two
`ProjectileEvent.java` files differ by 446 added and 909 removed lines in a
direct file comparison.

### Current behavior that a refactor must preserve

- **Layered space:** `WorldLocation`, `Mob.sharesSpatialDomain`, layered
  collision, and semantic hostile projectile line-of-effect are active. Combat
  cannot fall back to X/Y-only identity or packed-map assumptions.
- **Cleric:** `ClericDirectCombatRuntime.beforeDirectDamage` and
  `afterExistingLifesteal` participate in melee and projectile paths. Ward,
  Aegis, Fervor, Zeal, Rally, and lethal Thorns ordering have accepted behavior.
- **Summoning:** `Summoning.canSummonAttack`, owner contribution, damage
  scaling/absorption, lifesteal, AoE suppression, utility-summon exclusions,
  and summon XP are integrated at multiple phases.
- **Current NPC poison lifecycle:** commit `ad02e5aec` clears poison on NPC
  death and spawn reset. A new DoT owner must retain that exact lifecycle.
- **Dragons and equipment:** dragon armor/weapon effects, KBD behavior, Elder
  Green attacks, and current defense/partial mitigation are gameplay, not
  implementation detail.
- **Custom AoE:** scythes, Exalted Rune scythe recognition, shuriken, chain and
  elemental effects, dragon sweeps, and summon exclusions use distinct rules.
- **Threat, XP, loot, and credit:** damage-share XP, melee Hits-focus,
  contribution-scaled personal loot, pending summoning XP, hidden drops, and
  automatic burial are current contracts.
- **Movement/projectiles:** current attack radii, approach radii, signed map
  levels, line-of-effect, and NPC projectile styles have focused regression
  coverage and private acceptance history.
- **Maintained projectile compatibility/debug path:** `RangeEventNpc` is still
  owned by `Mob`, constructed by two `Admins` command paths, and named by three
  focused projectile tests. Classic Task 31's replacement service is a useful
  model, but the current class is not removable until those callers and their
  collision/command contracts migrate.
- **Compatibility modes:** MyWorld ships with PvP and OSRS formula toggles off,
  but authentic-client, classic-profile, plugin, and configuration surfaces
  have not been authorized for deletion.

The upstream self-review compared against Spoiled Milk v0.2.64 at `d936eecf`.
Current v0.2.66 adds the NPC poison lifecycle correction and Foundry Dragon
support summon after that baseline (17 server/test files, 655 additions, 70
deletions). Those post-review changes require explicit fixtures in any future
pipeline branch.

### Current test shape

Current authoritative `server/build.xml` compiles `core.jar` and
`plugins.jar`; it has no Java combat-test target. Only three unrelated Java
test files exist under `server/test`. The active MyWorld suite has many useful
Python checks—59 filenames match combat, NPC, poison, projectile, Cleric, or
summoning themes—but many assert source shape or independently reproduce math.
For example, `test-combat-runtime-invariants.py` explicitly says it is
source-backed because no Java integration harness exists.

This is the central reason to start with characterization: current tests are
valuable but cannot prove event ordering, duplicated callbacks, exactly-once
damage/death, or state cleanup under the real Java runtime.

## Subsystem Comparison

| Area | Current Spoiled Milk | Classic-Scape result | Assessment |
| --- | --- | --- | --- |
| Melee | `CombatEvent` and `PvmMeleeEvent` own separate hit/effect chains; PvM XP is contribution-scaled | Shared request/result pipeline, engagement/event ownership, fractional cadence | Architecture is valuable; flat `damage * 16` melee XP and cadence change are not implicit refactors |
| Ranged/thrown | `RangeEvent`, `ThrowingEvent`, and `ProjectileEvent` share some code but settle ammo, XP, impact, procs, and death across callbacks | Typed launch snapshot, impact validation, and exactly-once settlement ledger | Adapt after current ammo/recovery, shuriken, layered-line-of-effect, prayer, and PvP behavior is characterized |
| Magic | `SpellHandler`, `MagicCombatEvent`, and `ProjectileEvent` retain current spell IDs, costs, effects, god spells, autocast, Cleric hooks, and custom AoE | Moves shared impact/damage/AoE into typed policies | Do not copy handlers; adapt only the impact/settlement boundary and preserve spell/plugin identities |
| Accuracy | `CombatFormula` combines legacy/MyWorld formulas, Cleric Fervor, item/set biases, elemental and NPC profile behavior | Routes randomness through `GameRandom` and central formula policy | RNG seam is reusable; formulas and probability distributions must remain current unless separately balanced |
| Damage | 34 direct Hits mutations and several custom secondary/reflection paths | `DamagePipeline.applyOnce` with request UUID, final eligible damage, and bounded dedupe | High-value adaptation, but current Cleric, summon, jewelry, dragon, AoE, poison, and death order must be inventoried first |
| Defense/mitigation | Defense rolls, protection prayers, dragon mitigation, equipment effects, Cleric Ward/Aegis, and summon absorption are distributed | Central `CombatEffectCoordinator` phases | Phase concept is useful; Classic drops some current dragon defenses and changes blocked-hit/proc behavior |
| Timing | Tick events mix integer delays, combat timers, and wall-clock decisions; attack-speed modifiers can floor | `CombatTick`, injected clock, and fixed-point `CombatCadenceAccumulator` | Types/seams are good; enabling fractional carry changes DPS and needs explicit balance acceptance |
| Movement/range | Current layered domain, semantic collision/line-of-effect, custom approach radii, retreat, and NPC ranges | Validate/approach/commit attack transaction plus NPC state controller | Adapt to current `WorldLocation` authority; Classic's ordinary disengage/watchdog flow is unacceptable as shipped |
| Aggression | `NpcBehavior`, threat maps, devotion/summon gates, mixed projectile profiles, current retreat/re-engage | Ten-state NPC controller plus JSON profiles | Data ownership is promising; exact state machine and 293-profile data need redesign against current NPCs and behavior |
| Status effects | Poison owner/event plus Cleric typed transient effects and many equipment debuffs | Typed DoT service with owner/lifecycle persistence and on-hit registry | DoT provenance is useful; Classic poison stacking/feedback and some proc ordering differ materially |
| AoE/secondary | Separate scythe, shuriken, chain, spell, dragon, and splash loops | Shared selector, policy, chain traversal, child damage requests | Strong concept; shipped policy changes aggro-on-zero, lifesteal, and per-child proc frequency |
| Death/kill credit | `killedBy`, three damage maps plus summon map, top-damage/fallback owner, plugin/drop/XP sequencing | ALIVE→DYING lifecycle, immutable death context, frozen contribution ledger, explicit credit roles | Adapt in stages; preserve current XP, personal loot, summons, automatic burial, ties, and plugin signatures |
| Plugins | Separately compiled/dynamically loaded plugin jar; numerous combat/death trigger interfaces and direct compatibility calls | Typed extension phases, failure isolation, circuit breaker, revised hooks | Add adapters; do not break existing plugin source/binary contracts or skip hooks during reload |
| Configuration | Ant-authoritative profiles with many compatibility switches; Gradle secondary | Validated named combat profiles; OSRS modes rejected; drift can abort boot | Later Server-R2-aligned concept, not an early port; do not remove retained modes by implication |
| Diagnostics | Existing server and layered diagnostics, but no unified combat correlation | Bounded correlation IDs, reason codes, outcomes, redaction | Adapt late, after ownership is stable; avoid high-cardinality/player-private telemetry |
| Tests | Broad Python/source/data fixtures, little executable Java combat integration | 27 Java files, 113 deterministic scenarios, isolated non-vacuous Ant gate | Test gate design is the best first adoption candidate; upstream scenarios cannot compile against current architecture unchanged |

## Confirmed Upstream Risks And Regressions

These findings are in the final `f6def6ff` source, not only intermediate
commits. They explain why passing 113 scenarios is necessary but insufficient.

### High priority

1. **Ordinary disengagement can become a full leash/reset.** The teardown,
   `NpcBehavior.recoverInvariantViolation`, and `requestLeash` flow can send a
   normally disengaged NPC home, normalize its stats, cure effects, and clear
   contributions. The old run-away re-engage branch remains but is unreachable
   on this path. No scenario covers ordinary run-away and re-engagement.
2. **Melee PvM XP changes silently.** Classic `Npc.MELEE_XP_PER_DAMAGE` awards
   flat `damage * 16`, while current Spoiled Milk derives damage-share XP from
   NPC combat XP/max Hits and retains the configured group multiplier and Hits
   focus. Ranged/magic do not make the same switch.
3. **Dragon combat is replaced rather than refactored.** Partial protection
   from dragon armor/Defense cape, KBD ranged drain, first-projectile
   retaliation, breath weighting, burn chance, and max hits differ. The new
   `dragon` package has no dedicated tests.
4. **PvP reattack immunity is melee-only.** `CombatEligibility` contains
   request flags for source/target reattack readiness, but ranged/thrown/magic
   callers do not set them. Current MyWorld PvP is off, but compatibility
   profiles are still maintained.
5. **Fractional cadence changes effective DPS.** It corrects permanent flooring
   of fractional attack speeds, but players with speed weapons/potions will
   observe a different cadence. This needs a balance decision, not a refactor
   assumption.

### Medium priority

- Goblin Tenacity can roll at both the damage and raw skill-subtraction layers,
  creating double-proc and telemetry inconsistencies on compatibility paths.
- Poison and burn replacement/stacking semantics differ from current behavior;
  ignored poison can still report success, and same-owner multi-proc poison can
  be stronger while cross-player poison is weaker.
- `SecondaryTargetPolicy.AGGRO_ON_ATTEMPT` is declared but not consumed;
  zero-damage secondary hits, cleave lifesteal, and per-child proc behavior
  differ from current AoE.
- Engagement membership replaces older combat-sprite semantics for some
  busy/logout/action restrictions, changing player-visible eligibility.
- Death hooks can be skipped during plugin reload because the snapshot fails
  empty.
- `::shootme` bypasses the new protected-ranged policy and has an argument path
  that can throw.
- Attack intents have no expiry, scripted retreat duration is ignored on one
  compatibility path, and the 32-entry on-hit phase cap is already close to
  the shipped descriptor count.
- Projectile settlement/dedupe, contribution freeze, and combat tracing add
  bounded stores and UUID allocations whose long-session behavior needs load
  evidence.

The upstream review created Tasks 38–52 for many of these items, but those are
documents only at the assessed revision. They do not make the defects fixed.

## Portability Classification

### Direct-source candidates

“Direct” means the small class can be copied with attribution and compiled with
minor package/style adjustment. It does **not** mean enabling it changes no
behavior.

| Candidate | Upstream source | Why it is bounded | Required adaptation/gate |
| --- | --- | --- | --- |
| Clock contracts | `runtime/GameClock.java`, `SystemGameClock.java` | Two-method interface and production adapter | Introduce only where tests need it; prove production still uses the same time source |
| Random contracts | `runtime/GameRandom.java`, `ProductionGameRandom.java` | Narrow adapter over existing `DataConversions` RNG | Current code also uses `Math.random` and helpers; migrate one characterized family at a time |
| Typed tick | `model/combat/CombatTick.java` | Pure immutable value with unit/overflow checks | Do not convert scheduler or persistence units implicitly |
| Deterministic test sources | `MutableGameClock.java`, `SeededGameRandom.java` | Test-only implementations | Keep them out of production packaging and report seeds on failure |
| Cadence arithmetic class | `CombatCadenceAccumulator.java` | Pure fixed-point accumulator | Source can be reused; runtime activation is behavior-changing and requires cadence/DPS approval |

No numbered implementation commit is a safe direct cherry-pick. Even Task 01
touches build/config/server fixtures that have drifted, and later commits layer
on architecture absent from current Spoiled Milk.

### Concepts requiring Spoiled Milk adaptation

1. **Clean, non-vacuous combat gate.** Reuse the isolated Ant output,
   zero-scenario rejection, summary receipt, deliberate-failure fixture, and
   Gradle-delegation concept. Write current-state scenarios rather than copying
   architecture-dependent upstream fixtures.
2. **Central eligibility with reason codes.** Preserve current message text,
   plugin gates, summons, quest/guild restrictions, PvP compatibility, layered
   domains, and attack-style-specific movement.
3. **Validate/approach/commit attacks.** Keep current packet and plugin order;
   add expiry and rollback from the beginning.
4. **Engagement and event ownership.** Introduce as an authority with audited
   compatibility projections, not a second unsynchronized state graph.
5. **Damage/death requests and results.** Build the phase inventory from current
   code first, including Cleric and summon terminal effects, then migrate one
   damage family per branch.
6. **Projectile snapshots and settlement.** Use current `WorldLocation` and
   semantic projectile policy; define cost, XP, recovery, reflection, and death
   settlement for each projectile family.
7. **Secondary/AoE policies.** Author explicit current policies per scythe,
   shuriken, spell, chain, dragon, and summon family. Do not assume one global
   proc/aggro/lifesteal rule.
8. **DoT provenance.** Preserve current poison strength, feedback, death clear,
   logout behavior, and owner credit before consolidating callbacks.
9. **Contribution and kill credit.** Include current personal loot, summoning,
   offline/disconnected contributors, tie-breaking, eligibility, XP, and drop
   roles in the contract.
10. **NPC profile data and state.** Generate/validate against current NPC
    definitions and current hand-authored `NpcAttackStyleProfile`; retain
    layered, devotion, summon, imp, boss, and retreat behavior.
11. **Plugin extensions, config profiles, and observability.** Align with
    Server R2 and retain compatibility adapters. These are late phases, after
    core ownership is stable.

### Unsuitable as shipped

- The complete 36-task source tree or any “ours/theirs” directory replacement.
- Classic's final NPC watchdog/leash behavior.
- Flat melee PvM XP and changed kill-credit tie policy.
- The exact dragon package and its removal of current protection/drain/retaliation.
- The exact 293-entry NPC profile JSON as current authoritative content.
- Classic poison/burn stacking and success-message behavior.
- Melee-only PvP reattack protection.
- A 32-entry effect registry with almost no expansion headroom.
- Global retirement/rejection of retained combat configuration modes before a
  separate compatibility decision.
- Any change that treats Classic X/Y geometry or older layered state as newer
  than current `WorldLocation` and native-layer authority.
- Upstream client/assets/content commits interleaved with the server refactor.

## Compatibility And Integration Risk

### Build and packaging

Spoiled Milk's production authority is the bundled Ant path documented in
`docs/myworld/info/server-build-source-of-truth.md`. Gradle is secondary and
non-authoritative. A future combat gate must be an Ant target and must not make
a vacuous Gradle/JUnit pass look authoritative. It should compile into isolated
output and must not overwrite `core.jar` used by a private or live process.

`core.jar` and the separately compiled/dynamically loaded `plugins.jar` must
both build after every runtime phase. New test-only classes and deterministic
fixtures must not enter either artifact. No player package should change for a
server-only foundation unless a protocol/client presentation change is
separately approved.

### Runtime and protocol

The refactor is mostly server-side, but packet sequencing, sprites, hit splats,
messages, projectile timing, and stat updates are observable by authentic and
enhanced clients. “No protocol schema change” is not enough: each phase must
prove legacy packet prefixes, ordering, and client-visible cadence remain
compatible.

### Plugins

Current plugins compile against core interfaces and are loaded from
`plugins.jar`. Direct signature replacement would be source- and potentially
binary-incompatible. New typed contexts should sit behind adapters until all
active call sites and reload behavior are covered. Plugin exceptions must be
isolated without silently dropping mandatory death/quest hooks.

### Persistence

Damage/engagement state should normally be transient. DoT persistence, player
cache keys, logout/relogin, offline owner identity, and corrupt-state behavior
are exceptions requiring explicit versioning and fail-closed fixtures. No
database schema migration is justified merely by the upstream architecture.

### Licensing and provenance

Both repositories carry byte-identical GNU AGPLv3 root `LICENSE` files (Git
blob `be3f7b28e564e7dd05eaf59d64adba1a4065ac0e`). The assessed refactor commits
are authored by `aicovergod <lewisshuffle136@gmail.com>`. This makes code reuse
license-compatible with the current project, subject to AGPL obligations; this
is a provenance assessment, not legal advice.

For a future branch:

- cite the exact upstream commit(s) in the commit message and branch report;
- preserve the repository license and source-availability obligations;
- use a `Co-authored-by` trailer when substantial copied code genuinely retains
  that authorship, not for a merely inspired design;
- distinguish copied code from independently adapted contracts in review; and
- do not import unrelated assets/content merely because they share history.

## Prioritized Findings

Effort uses S (up to roughly one day), M (several focused days), L (roughly one
to two weeks), and XL (a multi-branch program). Change risk estimates the risk
of the recommended work, not the severity of leaving current code unchanged.

| Rank | Finding/recommendation | Impact | Confidence | Change risk | Effort | Classification |
| ---: | --- | --- | --- | --- | --- | --- |
| 1 | Establish an executable current-behavior combat harness and non-vacuous Ant gate before moving authority | High | High | Low–Medium | M | Adapt test foundation |
| 2 | Introduce bounded clock/RNG/tick seams so combat fixtures can reproduce failures | High | High | Medium | M | Direct primitives, adapted integration |
| 3 | Inventory and centralize damage/death phases one family at a time | Very High | High | Very High | XL | Adapt architecture |
| 4 | Separate projectile launch, impact validation, and settlement using current layered policy | High | High | High | L–XL | Adapt architecture |
| 5 | Centralize eligibility and validate/approach/commit without changing packet/plugin order | High | High | High | L | Adapt architecture |
| 6 | Replace split engagement/event fields with one authority plus compatibility projections | High | High | Very High | XL | Adapt architecture |
| 7 | Define per-family secondary/AoE/on-hit policies before consolidating duplicated procs | High | High | High | L–XL | Adapt architecture |
| 8 | Build typed DoT provenance while preserving current poison semantics and lifecycle | Medium–High | High | High | L | Adapt architecture |
| 9 | Unify contribution/kill-credit ownership without changing XP, loot, summon, or tie policy | Medium–High | High | High | L | Adapt architecture |
| 10 | Data-drive NPC combat profiles and aggression only after current retreat/layer/boss behavior is fully characterized | High | High | Very High | XL | Adapt architecture |
| 11 | Add typed plugin extensions, config profiles, and observability after core ownership stabilizes | Medium | High | Medium–High | L | Later adaptation/Server R2 |
| 12 | Reject wholesale port, exact Classic balance/content, and unreviewed interleaved commits | Very High | High | Low | S | Stop condition |

## Ordered Follow-Up Branches

### A01 — `test/current-combat-characterization-harness` (recommended next)

Implementation record: this branch now supplies the isolated Ant gate,
cross-platform launchers, 11-scenario executable current-state fixture, CI
entry point, artifact-exclusion check, and A02 seam inventory. Integration and
the exact READY commit remain subject to manager review.

Scope:

- Add an isolated authoritative Ant combat-test target and Linux/Windows
  launchers modeled on Classic Task 37.
- Require a written summary receipt, more than zero executed scenarios, and a
  deliberate-failure mode that every advertised entry point rejects.
- Keep Gradle secondary; if retained as an entry point, delegate to Ant before
  supplemental JUnit discovery.
- Add a minimal real-Java current-state harness. Initial scenarios must pin:
  current melee/ranged/magic damage-share XP; attack eligibility and plugin
  callback ordering; layered-domain/line-of-effect rejection; ordinary NPC
  retreat and re-engagement; current poison death/respawn clearing; Cleric
  Ward/Aegis/Rally/Thorns order; summon owner contribution; representative
  scythe/shuriken/AoE behavior; and exactly-once death/drop callbacks.
- Do not move combat authority or change formulas in this branch.

Verification:

- exercise both normal and deliberate-failure test targets from a clean
  checkout;
- prove zero scenarios cannot pass;
- `./scripts/build-server.sh` and artifact/plugin-discovery checks;
- existing relevant Python combat, poison, projectile, NPC, Cleric, summoning,
  dragon, and layered-world suites;
- changed-code static analysis; and
- no production artifact contains test fixtures.

Stop if meaningful current behavior cannot be exercised without a production
seam. Record the missing seam and move only that seam to A02; do not fake a
green source-text assertion as runtime coverage.

### A02 — `refactor/combat-deterministic-runtime-seams`

Implementation record: the focused worker branch now provides the bounded
`GameClock`, `GameRandom`, production adapters, typed `CombatTick`, test-only
mutable/seeded adapters, and a one-whole-tick driver through the existing event
handler. The A01 gate has grown from 11 original scenarios to 20 executable
scenarios without changing formulas, balance, cadence, scheduling authority, or
production artifacts. Integration remains subject to manager review of the
exact READY handoff.

Scope:

- Add `GameClock`, `GameRandom`, production adapters, `CombatTick`, and
  test-only mutable/seeded implementations.
- Migrate only the RNG/time sites required by A01 scenarios. Preserve the
  production generator, distributions, call count/order, and scheduler units.
- Do not enable fractional cadence yet.

Verification:

- seeded replay is byte-identical and reports seed/state on failure;
- production adapter parity against current RNG/time behavior;
- current cadence boundaries unchanged;
- full A01 gate, server/plugin build, and changed-code analysis.

Stop on any changed roll distribution, extra/missing draw, scheduler delay, or
player-visible cadence.

### A03 — `refactor/combat-eligibility-transactions`

Implementation record: the focused worker branch now provides the
side-effect-free reason-coded eligibility service, exact per-player attack
intent serialization, 100-tick stale-intent lease, participant generation
guards, and validate/approach/commit integration for melee, bow, throwing,
targeted magic, autocast, and player retaliation. Compatibility entry points
and stateful content/plugin gates remain in place. The A01/A02 gate has grown
from 20 to 26 executable scenarios. This work is integrated into published
main at merge commit `d410d973c`.

Scope central eligibility/reason codes and validate/approach/commit attack
starts while preserving current movement and plugin ordering. Cover every
style, summons, PvP compatibility, quest/guild restrictions, layered domains,
and stale-intent expiry. Retain old entry points as audited facades.

Verification includes denial side-effect tests, retargeting, plugin callbacks,
walk cancellation, logout/death, melee/ranged/magic, and private packet/message
checks. Stop on any current permission, message, approach radius, or trigger
order change.

### A04 — `refactor/combat-engagement-event-ownership`

Implementation record: the focused worker branch now provides one
`CombatEngagementAuthority` per mob, directional one-outgoing/many-incoming
relationships, exact typed event slots, lifecycle snapshots, compatibility
projections, reasoned teardown, and an explicit non-routine audit/repair path.
Legacy opponent/event fields no longer form a second mutable graph. The
A01–A03 gate has grown from 26 to 32 executable scenarios while preserving the
narrow historical `inCombat()` boundary. This work is integrated into
published main at merge commit `4124e2bd1`.

Create one directional engagement/event authority and project legacy fields
from it. Cover one outgoing/many incoming relationships, passive retaliation,
retargeting, stale callbacks, logout, teleport, death, and audit repair. Do not
change the meaning of busy/logout/PvP restrictions without a separate decision.

Stop if both new and legacy state can mutate independently or if repair hides a
routine path rather than exposing a defect.

### A05 family — damage and death, split by source

A05.1 completion record: published main at merge commit `63a0bbcc0` provides immutable
resolved-legacy damage request/result facts, lifecycle-aware participant
snapshots, an inert production observer, safe observer-failure isolation, a
complete current damage/death migration inventory, and one observation-only
PvM melee boundary. It does not add a damage pipeline or move HP, contribution,
effect, packet, or death authority. The A01–A04 gate grew from 32 to 35
executable scenarios.

A05.2 completion record: published main at merge commit `a52968027` adds exact zero,
nonlethal, lethal/overkill, contribution, lifesteal, XP, terminal-hook,
directionality, and shared-Hits compatibility fixtures for both primary melee
classes. Only their adjacent Hits subtraction, damage update, and hitsplat move
through a server-owned resolved-damage transaction. All formulas, mitigation,
post-hit effects, packets, death, XP, drops, and plugin authority remain in
their original event order. The gate grows from 35 to 38 scenarios.

A05.3 completion record: published main at merge commit `06580f6e0` adds executable
settlement and transaction-result parity for player bow/crossbow, thrown,
player magic, NPC magic/ranged, summon magic/ranged, Iban, and cannon primary
impacts. Only the adjacent primary Hits subtraction, damage update, and
hitsplat move through the same resolved-damage transaction. Unknown
compatibility types and every secondary/reflection/AoE path remain local. The
gate grows from 38 to 42 scenarios. The complete boundary is recorded in
[`docs/combat/combat-a05-primary-projectile-damage-transaction.md`](../../combat/combat-a05-primary-projectile-damage-transaction.md).

A05.4 completion record: published main at merge commit `7c8be55b3` changes no
production damage or death authority. It identifies every remaining direct
Hits mutation and `Mob.damage` compatibility caller, records mitigation,
attribution, presentation, contribution, lifesteal, aggro, death, packet, and
hook-order policy by family, and divides later work into bounded branches. Four
representative runtime scenarios grow the gate from 42 to 46. The exact
inventory is recorded in
[`docs/combat/combat-a05-secondary-damage-characterization.md`](../../combat/combat-a05-secondary-damage-characterization.md).

A05.4A completion record: published main at merge commit `b2dc18979` moves only
the six event-local auxiliary Magic/true Hits/update/hitsplat blocks in `CombatEvent`,
`PvmMeleeEvent`, and `ProjectileEvent` through the resolved-damage transaction.
Six event/contribution-specific keys preserve ownership; all mitigation,
contribution, return, stat-packet, and death adapters remain local and ordered.
Two scenarios grow the gate from 46 to 48. The exact boundary is recorded in
[`docs/combat/combat-a05-auxiliary-damage-transaction.md`](../../combat/combat-a05-auxiliary-damage-transaction.md).

A05.4B implementation record: the focused worker branch moves only the
HP/update/hitsplat blocks for Frostbite, Cleric Thorns, melee and projectile
jewelry recoil, and Divine Retribution through ten effect-specific resolved
damage identities. It preserves pending-hit reduction, attribution and
contribution style, post-lifesteal order, recursion exclusions, player stat
packets, Ring of Life, ranged reset, helper results, caller-owned death, and
simultaneous death. Five policy-specific scenarios replace one combined
scenario, growing the gate from 48 to 52. The exact boundary is recorded in
[`docs/combat/combat-a05-reflection-damage-transaction.md`](../../combat/combat-a05-reflection-damage-transaction.md);
manager review of the exact READY handoff remains the integration boundary.

Use several branches rather than one migration commit:

1. request/result types plus no-op observation;
2. direct melee primary hits;
3. ranged/magic primary projectile hits;
4. secondary/reflection/environmental damage;
5. atomic death lifecycle and plugin adapters; and
6. removal of proved-obsolete direct Hits mutations.

Each branch must inventory phase order and preserve current Cleric, summoning,
jewelry, dragon, prayer, equipment, XP, drops, messages, stat packets,
overkill, and simultaneous-death behavior. Stop on any balance delta or hook
cardinality change.

### A06 — projectile launch/impact/settlement

Introduce current-specific snapshots and ledgers for bow, thrown, magic, NPC,
summon, chain, shuriken, and debug projectiles. Use current `WorldLocation` and
semantic collision authority. Cover teleport/layer changes, source/target
death, protected launches, ammunition/cost/recovery/XP exactly once, reflection,
and duplicate callbacks.

Stop if the branch changes projectile speed/range, spell/ammo costs, prayer,
recovery, damage, animation, or authentic-client packet order.

### A07 — current-specific secondary, AoE, and on-hit policies

Define explicit policies per effect family, then consolidate duplicated proc
chains. Include eligibility, world/layer, range, line-of-effect, zero-hit aggro,
lifesteal, proc eligibility, child ordering, death, and caps. Size registry
capacity from current and planned effects rather than importing Classic's cap.

Stop if unrelated effect families must be changed together or if order cannot
be characterized.

### A08 — DoT provenance and lifecycle

Move poison/burn ownership behind typed state only after exact current
strength, stacking, replacement, feedback, persistence, death/respawn, logout,
offline owner, and kill-credit rules are fixtures. Preserve `ad02e5aec` and
fail closed on corrupt restored state.

### A09 — contribution and kill-credit roles

Create one ledger and explicit final/primary/drop/XP/summon roles. Preserve
current damage-share XP, Hits focus, personal loot, contribution scaling,
hidden drops, pending summon XP, tie policy, offline eligibility, and cleanup.
Do not adopt Classic's XP formula or tie policy by default.

### A10 — NPC profile/state modernization

Reconcile `NpcAttackStyleProfile`, definitions, boss scripts, devotion/summon
gates, projectile visuals, current layered movement, and every special NPC
before generating profile data. Characterize ordinary fight-end, run-away,
re-engagement, leash, healing, poison, contribution retention, target death,
and exact home behavior. Explicitly reject the Classic watchdog reset as a
default.

### A11 — extensions, configuration, observability

Coordinate with Server R2. Add compatible plugin adapters, transactional
reload, bounded reason-coded traces, redaction, and opt-in validated profiles.
Do not make missing custom content or retained compatibility modes abort
startup without a reviewed distribution contract.

## Program Acceptance Gates

Every implementation branch must satisfy all applicable gates:

1. authoritative Ant core and plugin builds;
2. the non-vacuous Java combat suite plus relevant Python fixtures;
3. changed-code javac/Checkstyle/PMD/SpotBugs gates without broad baseline churn;
4. no test classes in production artifacts and intact plugin discovery;
5. exact current formula/XP/cost/drop/credit parity unless a branch is
   explicitly authorized as balance-changing;
6. layered world-space and signed-level coverage;
7. Cleric, summons, poison death reset, dragons, custom AoE, and post-v0.2.64
   regression fixtures where the changed phase can reach them;
8. normal logout, disconnect, teleport, target/source removal, death, respawn,
   and repeated cleanup;
9. authentic-client-compatible packets and private visual/gameplay checks for
   player-observable timing, movement, effects, messages, or animations; and
10. no public-server, live-database, release, or deployment action.

A branch must stop for an owner decision if it changes XP, damage, accuracy,
defense, prayer, attack cadence, aggro/leash, PvP eligibility, poison/DoT,
dragon mechanics, AoE proc/lifesteal, loot, kill-credit ties, plugin order,
protocol order, persistence, or startup compatibility. “Matches
Classic-Scape” is evidence, not authorization.

## Audit-Branch Verification

- Classic-Scape `f6def6ff`: `./server/test_combat` compiled 1,134 production
  sources and 27 combat-test sources, then passed 113/113 scenarios with zero
  expected failures.
- Classic-Scape deliberate-failure fixture: `./server/test_combat
  -Dcombat.injectFailure=true` failed at the advertised Ant preflight and was
  accepted by the audit wrapper only because failure was expected.
- Current Spoiled Milk: `./scripts/build-server.sh` built authoritative
  `core.jar` (957 sources) and `plugins.jar` (492 sources), and the shipped Ant
  dependency/classpath inventory passed.
- Current focused fixtures passed:
  `test-combat-scenarios.py`, `test-combat-runtime-invariants.py`,
  `test-npc-poison-death-lifecycle.py`,
  `test-cleric-c10-direct-combat-effects.py`,
  `test-summoning-combat-assist.py`, and
  `test-hostile-projectile-collision-policy.py`.
- Offline changed-code analysis found no changed Java, Python, or shell files,
  observed the existing 394 SpotBugs findings, and reported no new finding.
- `git diff --check` passed, all 45 exact upstream commit links in this report
  resolved to commits in the inspected clone, and the two root `LICENSE` files
  were byte-identical.

## Final Recommendation

Approve A01 only. Treat Classic-Scape as a well-documented reference
implementation and test-design source, not as a patch series to merge. After
A01 proves current behavior, use the audit's ordered branches to adopt small
ownership boundaries while current Spoiled Milk remains the behavior authority.

Reassess the sequence after A02 and after each damage-family migration. If the
executable characterization cost proves higher than the duplicated-code risk,
stopping with the improved harness and deterministic seams is a valid outcome;
the project does not need to complete the entire upstream architecture to gain
its safest benefits.
