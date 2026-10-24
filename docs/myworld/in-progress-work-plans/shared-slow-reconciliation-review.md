# Shared Slow reconciliation review

Worker: `feat/shared-slow-reconciliation`, `/home/justin/Core-Framework-ai-2`.
Prepared October 24, 2026. Core owns integration and gameplay acceptance.

## Provenance and boundaries

- Actual starting published main/worker HEAD: `27e202d8e3d0361241ef28f1d1b2148a9a6876b9`.
  The assignment's earlier game baseline was `b6f1c7dea5fbfe8713b0668a0614ac252d5c29ff`.
- Fixed source: `5e79e8f069c5c6f2cb7a4ed37bd50ce5e39d6e34`.
- Resolved preserved branch reference before reading:
  `spoiled-milk/art/slayer-item-sprite-candidates` =
  `85ed0e8a05c13143d318336f680e786849a0b75a`.
- First implementation checkpoint: `2a734e11404366f01f2339902c930ccc222a7652`.
  The final pushed commit is recorded by the worker checkpoint/READY record and
  final task response; this report does not create a self-referential commit ID.

Only focused Slow changes were ported. The preceding antidote commit
`4d98e17833135ae3f150f7ad953099b184fe91d1` was inspected, not imported.
Its `antidote_cleanse` HUD expectation accidentally present in the historical
Slow diff was removed during reconciliation. There are no new poison APIs,
items, assets, map/package changes, editor changes, coating implementations,
server operations, main integration or main publication.

## Old-to-current dependency map

| Area | Current integration and adaptation |
| --- | --- |
| Pool | New `content/Slow.java`; existing Mob transient attributes, current tick and `CombatParticipantSnapshot` |
| Decay | Production `GameTickEvent`/handler; one unowned callback per live pool, stopped immediately on clear; due pulses settle before reads/applications |
| Lifecycle | Current `Mob.advanceCombatLifecycle` clears pool/HUD; player death/logout/session ID and NPC removal/respawn retain current lifecycle authority |
| Melee | Current actor-owned `PvmMeleeEvent`; modifier calculation first, integer Slow tier second; replacement cannot bypass captured deadline |
| Bow/thrown | `RangeUtils`, `RangeEvent`, `ThrowingEvent`; fixed deadlines survive retarget truncation of the separate legacy timer |
| NPC projectiles | Existing `RangeEventNpc` shared delay helper; profile `NpcBehavior` gains lifetime-qualified committed tick deadline |
| Combat magic | Player's ordinary cast timer retained; separate current-game-clock deadline checked by packet, queued and autocast paths; only offensive finalization commits it |
| Specials | Banshee/Naga/Bloodveld shared deadlines gain Slow once and reject old lifetimes; Abyssal recovery and Dark Beast charge remain fixed |
| HUD | Existing `ActiveStatusEntry`, Player collector, packet decoder/HUD model; append kind 2 identities 4/5 with Earth rune 33 |
| Definitions | Current item-generator input, generated server/client definitions, existing client spell descriptions and skill-guide entry |
| Tests | Current combat harness and Ant compilation; restored `test_slow`, expanded deterministic fixtures, retained golden source characterizations |

The historical implementation needed stricter input validation (only source caps
12/22), overflow-safe addition/delay, callback-order-independent boundary decay,
immediate abandoned-event cleanup, raw-state HUD clearance after `killed=true`,
and lifetime-qualified special cooldowns. Slowed actor deadlines are bounded to
one melee and one ranged record per Mob, independent of the pool; clearing power
cannot shorten a committed action. Lifetimes invalidate those records.

### Producers

All approved mappings are in [shared Slow foundation](shared-slow-foundation.md).
Earth ranks keep the published rank-times-three projectile envelope and map at
positive-damage impact to 4/6/8/10 power with caps 12/12/22/22. Withering remains
positive-damage/survivor gated and applies 8/12 or 12/22. Sword, moss, Earth Dragon
and KBD adapters replace only Slow application, preserving original eligibility,
proc decisions, damage callbacks and roll order. Grandfathered equipment stays
retired and untradable; no crafting or drops are restored.

Real legacy NPC ranged execution exposed two pre-existing null-player calls:
its damage formula received `getPlayerOwner()` despite NPC ownership, and ground
item recovery passed a null spatial observer. These now pass the actual NPC
actor/observer through the existing formula and spatial authority. The same
weapon/ammo IDs, recovery roll, per-player fallback recovery loop and projectile
ownership remain. This is a small prerequisite adaptation, not ranged balancing.

### Legacy API audit

Production searches across `server/src` and `server/plugins` found no remaining
callers of Mob `applyEarthAttackSpeedDebuff`,
`applyDragonEarthAttackSpeedDebuff`, `applyBearIntimidateDebuff` or
`applyWaterSlow`. These compatibility APIs remain defined; their former Earth
percentage getter has no live cadence consumers. The similarly named
`ProjectileLaunchSpecification.getEarthAttackSpeedDebuffPercent` is an envelope
accessor used by ProjectileEvent; `SpellClassification` still constructs its
rank-times-three payload. These two are intentionally retained contracts.

### Separate effects and limitations

Startle/Ogre attack cancellation, Frostbite reflection, Whip delay/immunity,
Sticky Skin's pending next attack, and frog/cockatrice/demon immobilization remain
separate. Mobility Shield coverage does not expand to Slow. All eight Slayer
cadences are exercised: frog profile ranged, cockatrice melee (its telegraph remains unchanged), Banshee
melee/magic, Naga melee/throw, Terror Dog melee/frenzy, Bloodveld melee/pull, Dark
Beast melee/fixed charge, and Abyssal one-tick stab/fixed spike recovery.

PvP stays disabled. Reciprocal CombatEvent does not consume shared Slow: its
alternating shared event would delay both actors. Future PvP needs actor-owned
scheduling review. Coating herb, level/yield, poison/Slow amounts, cap and
lifetime/charges remain undecided. Keep the broader post-Slayer review active.

## Validation and baseline evidence

All commands run offline from this worker. Java 8:
`JAVA_HOME=/home/justin/jdk8u482-b08`, `ANT_OPTS=-Xmx768m`;
fixture heap 512 MB; additional independent regression batch used
`JAVA_TOOL_OPTIONS=-Xmx512m`. No gameplay server/client was launched.

Current broad gate: `ant test_combat_strict` **PASS 144/144 scenarios**. This is
its current reported total, not a historical count imposed on the suite.
Final focused and independent regression confirmation is recorded below.

Baseline `ant test_combat_strict` failed before edits at
`current_projectile_impact_lifecycle_policy_is_characterized`: ordinary NPC Point
movement attempted to leave immutable native terrain. The preserved fixtures
also assumed writes to rollback `TileValue`s changed native collision authority.
Current native gates were not disabled to get a pass.

Fixture adaptations retain all assertions: explicit signed-domain transitions
use `setWorldLocation`; collision tests register/unregister package-qualified
native boundaries on actually open native terrain; the three-target shuriken
fixture searches for three genuinely visible native targets, and spell resource
fixtures select actually adjacent native tiles, before replaying their
unchanged sibling/cost/RNG assertions. No immutable package is edited.

Failed intermediate checks included those baseline fixture assumptions, an
invalid shortbow/bronze-arrow test setup (fixed to current tin arrows), a test
using player auto-retaliation when isolating NPC cadence (disabled for that test),
and an attempt to load two native worlds in one 512 MB fixture (now one world).
These are not final pass claims or removed regression coverage.

The ordinary no-Slow cast and profile-NPC timers remain their existing authority:
the additional fixed deadline is installed only for an actually slowed actor.
This avoids introducing a second ordinary timer in unslowed combat. The focused
tests check that resetting the ordinary cast timer still works without Slow.

## Final checks

| Command | Result |
| --- | --- |
| `cd server && sh ../tools/vendor/apache-ant-1.10.5/bin/ant test_slow` | PASS; 103 added boundary/interaction assertions plus the restored foundation fixture, direct producer adapters, and retained Earth Dragon/KBD/sword/Ogre damage/RNG characterizations |
| `ant test_combat_strict` with the same bundled launcher | PASS; all current 144 scenarios, including projectile/resource/spatial/lifecycle and exact damage/RNG checks |
| `ant test_slayer_leather_effects test_slayer_rewards test_slayer_mobility_shield test_abyssal_demon_combat test_dark_beast_combat test_bloodveld_combat test_terror_dog_combat test_naga_combat test_thunder_spell_casting` | PASS; all nine independent targets, run sequentially with the bundled launcher |
| `bash scripts/build-client.sh` | PASS; maintained client compiled/packaged, never launched |
| `python3 tests/myworld/test-cleric-status-hud.py` | PASS; status identities/decoding and unrelated status preservation |
| `python3 tests/myworld/test-leather-set-bonuses.py` | PASS |
| `python3 tests/myworld/test-generator-scripts.py` | PASS; item and NPC generator regressions |
| `python3 tests/myworld/test-retired-leather-items.py` | PASS; 35 retired definitions and note forms preserved |
| `python3 tests/myworld/test-slayer-component-items.py` | PASS; 17 component identities/materials, inert ingredient scope |
| `python3 tools/generators/run-generators.py --only item --check` | PASS; 2049 current overrides and generated client/server parity |
| `git diff --check` | PASS |

The focused count is an assertion count, not a fabricated combat-scenario total.
The final independent Ant batch also includes `test_slow` and succeeds as a
single sequential invocation. The final full combat gate is then run separately.
Baseline/intermediate failures above were resolved; no current failed gate is
being waived and no assertion was removed merely to obtain green.

Not run: private/public gameplay acceptance, release packaging, deployment,
server restarts or unrelated repository-wide suites. These are not claimed as
covered by offline tests. Core should review the legacy NPC admin-range owner
adaptation and native fixture changes alongside the Slow diff. The existing
legacy recovery-per-player policy is intentionally retained, not redesigned.

The work is ready for exact-commit Core review after the worker handoff script
confirms its pushed commit. There are no remaining implementation or validation
blockers. PvP cadence redesign, coating design/implementation, and the broader
post-Slayer effect audit remain deferred.
