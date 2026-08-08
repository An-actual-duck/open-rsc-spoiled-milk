# A08.1 Poison and Burn Provenance/Lifecycle Audit

## Status and boundary

This is the investigation and planning record for A08.1. It was prepared
against published `main` commit `6dbce559551ff097bf8359fb5ac70fb8a30f0e6b`
on 2026-08-07. No combat behavior, formula, persistence value, event, or
player-facing message changes in this milestone.

The audit covers every active or restorable poison and burn implementation,
including application, ownership, stacking, refresh, ticking, persistence,
death/respawn, logout, offline sources, kill credit, corrupt state, and the
future executable test matrix. It continues the boundary deliberately left by
A05 and A07:

- A05 characterized generic poison/burn without moving them through the shared
  damage transaction because their lifecycle and attribution were unresolved.
- A07 assigned semantic identities to Elder Dragon burn application and pulse
  paths, but left their clocks content-owned.
- A08 must settle provenance and lifecycle policy before changing generic
  `Mob.damage`, merging burn implementations, or adding a generic DoT executor.

## Executive result

There is no single poison/burn system today. There are four distinct runtime
contracts:

| Runtime | State owner | Source retained | Refresh/stacking | Persistence | Lethal attribution |
| --- | --- | --- | --- | --- | --- |
| Generic poison | Target `Mob` fields, attribute, and event | Latest player UUID only | Add current power up to the highest observed ceiling; do not reset tick countdown | Player current/maximum power only | Target's current opponent, not poison owner |
| Generic burn | Target `Mob` fields, attribute, and event | None | Replace damage and pulse count; restart countdown | Player damage/pulses | Target's current opponent |
| Elder Green Dragon boss burn | Player attributes and player-owned event | Live `Npc` object | Extend wall-clock end time; latest dragon becomes source | None | Explicit dragon source |
| Elder Green Dragon armor burn | Target attribute and target-owned event | Live `Player` object | Reset five pulses/countdown; latest player becomes source | None | Explicit player source |

The highest-risk finding is that generic poison stores a player UUID for
Leach, but the actual poison damage still calls the compatibility helper. A
lethal poison pulse therefore calls `killedBy(target.getOpponent())`. The
poison owner neither owns the lethal hit nor receives poison contribution.
The current opponent may be null, may be the right attacker by coincidence, or
may be a different actor who engaged after poison was applied.

Other important findings are:

1. Player poison/burn pauses offline and resumes from cache after login or a
   restart. Poison ownership is not persisted, so resumed poison cannot Leach
   and has no durable source identity.
2. Successful logout stops all player-owned tick events before player removal.
   This prevents live duplicate poison/burn events across ordinary relog. It
   must remain an explicit invariant in the replacement design.
3. Generic poison accepts only a player as an owner. NPC and environmental
   applications become indistinguishable `null` ownership even when the caller
   has the attacking NPC available.
4. A valid poison reapplication replaces the player owner before checking
   whether current power actually increases. An application at the ceiling can
   therefore transfer all later Leach ownership without adding poison.
5. Generic burn has no production application call site. It remains a
   compatibility/restoration path for cached state and tests. The two active
   burn families are separate content-owned implementations.
6. Login restores poison/burn cache values without schema, bounds, or type
   validation. Malformed integer rows can abort loading; negative poison can
   throw every time its due event runs; extreme values are not normalized.
7. `curePoison()` removes player cache keys only when a `poisonEvent`
   attribute exists. Orphaned cached poison survives a cure and can return on
   next login. `extinguish()` does not have this particular defect.
8. Authoritative NPC removal clears generic poison but does not explicitly
   clear generic burn or the Elder armor burn. Active content burn normally
   self-clears on its next event pass, but cleanup is distributed rather than
   guaranteed at the lifecycle boundary.

These are architecture findings, not authorization to change balance or
gameplay policy.

## Terminology

- **Target owner** means the entity whose status/event contains the DoT. It
  does not mean the attacker.
- **Source** is the actor or environment that applied the status.
- **Effect owner** is the source to which contribution, Leach, and kill credit
  should belong under an approved policy.
- **Runtime identity** is an entity-lifetime UUID. Player UUIDs are stable
  `new UUID(0, usernameHash)` values and can resolve after that player relogs;
  NPC UUIDs identify only one NPC runtime object/lifetime.
- **Persistence** means player-cache state saved in the external database.
  Runtime attributes and event objects are not persistent.
- **Accepted application** means `applyPoison` passed its positive-value and
  live-NPC gates. It does not necessarily mean current poison power increased.

## Authoritative implementation inventory

### Shared state and event construction

Generic state lives in
`server/src/com/openrsc/server/model/entity/Mob.java`:

- poison: `poisonDamage`, `poisonMaxPower`, `poisonOwnerId`, and attribute
  `poisonEvent`;
- burn: `burnDamage`, `burnPulseCount`, and attribute `burnEvent`;
- application: `applyPoison`, `startPoisonEvent`, `applyBurn`, and
  `startBurnEvent`;
- cleanup: `curePoison`, `extinguish`, and `cure`.

Tick behavior lives in:

- `server/src/com/openrsc/server/event/rsc/impl/PoisonEvent.java`;
- `server/src/com/openrsc/server/event/rsc/impl/BurnEvent.java`.

Player restoration is performed directly inside `Player.setLoggedIn(true)`.
Cache serialization is the generic `Cache`/`PlayerCache` path in
`GameDatabase` and `PlayerService`; the database stores a type discriminator,
key, and string value.

The two active burn families live in:

- `server/src/com/openrsc/server/content/ElderGreenDragonArmorEffect.java`;
- `server/src/com/openrsc/server/event/rsc/impl/combat/ElderGreenDragonSpecialAttacks.java`.

### Generic poison application paths

| Producer | Gate/phase | Application | Recorded owner today | Notes |
| --- | --- | --- | --- | --- |
| `CombatEvent.applyWeaponPoison` | Positive melee damage; adaptive weapon/armor/dragon set proc | Strongest applied component for the hit; combined maximum | Player | PvP/general melee event |
| `PvmMeleeEvent.applyWeaponPoison` | Positive melee/Scythe child damage; same proc model | Same | Player | PvM melee path |
| `ProjectileEvent.applyWeaponPoison` | Positive surviving ranged, thrown, or magic impact | Strongest applied component for the hit; combined maximum | Player | Central My World projectile path |
| `ProjectileEvent.applyDualElementOnHitEffects` | Positive surviving Acid/Corrode impact and effect roll | 20/30/40 applied and fixed ceiling by spell tier | Caster when player | Uses the shared poison event |
| `SpellHandler.applyGuthixGodSpellPoison` | Positive surviving primary or successful secondary Guthix hit | Primary 20/40 into 40/80; secondary 10/20 into 20/40 | Caster | Secondary has its own 25%/50% draw |
| `CorrosiveAura.apply` | Positive incoming damage while the prayer/equipment gate is active | 10–50 applied; maximum is current power plus application | Defender | This deliberately ratchets the ceiling on each proc |
| `NpcPoisonPlayerScript` | Eligible poison NPC hits and 10% roll | Configured NPC power, default 38 | None | Attacking NPC is available but not passed; generic state could not retain it anyway |
| Compatibility `PlayerPoisonScript` | Non-My-World PvP only, poisoned equipped item and 25% roll | Flat 48 | None | Attacker is available but is not passed; disabled by `WANT_MYWORLD` |
| `SinisterChest` | Successful chest opening | Flat 68 | None/environment | Environmental/scripted poison |
| Admin poison-self command | Explicit administrator test command | Requested positive power after cure | None/self-test | Diagnostic path |

`RangeUtils.applyPoison` contains an older poisoned ammunition chance path
(1-in-8 against players, 1-in-50 against NPCs), but the repository has no
caller. It is dormant compatibility code, not an additional active poison roll.
Weapon-poison item conversion in `InvItemPoisoning` creates poisoned equipment;
it does not apply a status to a `Mob` and is only an upstream acquisition path.

### Generic burn application paths

No production code calls `Mob.applyBurn`, and no production code constructs a
generic `BurnEvent` except `Mob.startBurnEvent`. Its live entry points are
therefore:

- restoration of existing `burn_damage` plus `burn_pulses` player-cache keys;
- direct test/fixture construction; and
- a future caller that might use the public `Mob.applyBurn` method.

This dormant state matters for compatibility, but it must not be mistaken for
the active Elder Dragon burns or used as the model for them without a separate
retirement/migration decision.

### Active burn application paths

| Producer | Target selection | Application/refresh | Source requirement | Persistence |
| --- | --- | --- | --- | --- |
| Elder Green Dragon boss burn | Eligible online players in radius 6 with hostile-projectile line of effect | Five-second wall-clock duration; reapply moves end time to now + 5 seconds and replaces source; existing tick countdown continues | Live Elder Green Dragon `Npc` | None |
| Elder Green Dragon armor proc burn | Primary and eligible radius-2 secondary targets after positive breath damage | Five one-damage pulses; reapply resets pulses and countdown and replaces source | Logged-in, present, living player | None |

Both are already represented by A07 semantic application/pulse descriptors and
A05 `SecondaryEffectPolicy` damage keys. Those catalogs describe current
behavior; they do not own status lifecycle.

## Current generic poison contract

### Application, stacking, and refresh

`Mob.applyPoison(applied, maximum, source)` rejects nonpositive inputs and
poison against an NPC that is killed, removed, or respawning. It then:

1. stores the source UUID only if the source is a player; otherwise clears the
   current owner;
2. sets the ceiling to `max(existing ceiling, incoming maximum)`;
3. sets current power to
   `min(new ceiling, current event power + incoming applied power)`; and
4. updates the existing event or creates a new one.

The existing event's countdown is not reset. Current power is additive, while
the maximum is strongest-observed/monotonic for the life of that poison. The
ceiling is reset only by cure/lifecycle cleanup. Weapon and armor maximums are
combined before one central application, and only the strongest successfully
rolled applied component from that one attack is added.

Owner replacement is unconditional after the input/live-NPC gates. This gives
the latest accepted player application future Leach ownership even when the
target was already at its ceiling. A later unattributed or NPC application
clears player ownership.

`CorrosiveAura` is a special use of the same API: it passes `current + applied`
as the incoming maximum, so repeated aura procs can continue increasing the
ceiling instead of being constrained by a fixed effect profile.

### Tick and presentation

`PoisonEvent` is target-owned, scheduled every 8 server ticks, and registered
with `DuplicationStrategy.ALLOW_MULTIPLE`. Normal code relies on the single
`poisonEvent` attribute to reuse one event; the scheduler itself does not
prevent duplicates.

On a due pulse it:

1. cures an NPC whose lifecycle no longer accepts poison;
2. cures power below `PoisonPowerReduction.CURE_THRESHOLD` (10);
3. calculates damage using integer division `power / 10`;
4. drains 3 power plus the target player's currently equipped Nature Cleansing
   decay bonus;
5. writes target fields and, for a player, the `poisoned` cache value and
   message;
6. applies the requested damage with a poison hitsplat through
   `damageAndGetActualDamage`; and
7. after settlement, applies Blood Necklace Leach to the recorded online,
   living player owner using factual damage actually removed.

The current target equipment is evaluated for poison decay at pulse time. The
current source equipment is evaluated for Leach at pulse time. Neither is an
application-time snapshot. Leach does not use nominal overkill and does not
invoke ordinary Siphoning/lifesteal.

The compatibility damage helper applies Goblin Tenacity to player targets, but
does not apply robe, potion, prayer, True Defense, Cleric, summon absorption,
contribution, aggro, or a typed DoT transaction request.

### Persistence, logout, and restart

For player targets, current power is cached as `poisoned` and the ceiling as
`poisoned_max`. The owner UUID, countdown position, application identity, and
application time are not cached.

On login, `Player.setLoggedIn(true)` creates the event first, then loads cached
current power into it, and finally restores the ceiling (falling back to the
current power for old data). The newly constructed event gets a full 8-tick
countdown. The source is null. A server restart has the same semantic result.

On successful logout save,
`PlayerSaveRequest.logoutSaveSuccess()` stops all tick events indexed to that
player before removing the player. Poison does not tick while the target is
offline. Relog constructs one new event from the saved remaining power; there
is no wall-clock catch-up. This stop-before-remove behavior prevents ordinary
relog from accumulating live old events and is a required future invariant.

Player UUIDs are stable by username hash. If the poisoned target remains
online while a poison owner logs out and back in, the event's in-memory owner
UUID can resolve to the new player instance and Leach may resume. If the target
logs out, its unpersisted owner is lost regardless of the source's state.

NPC poison is process-local and target-lifetime-local. It is not persistent.

### Death, respawn, and explicit cleanup

Player death calls `cure()` after the death drop/teleport/reset sequence,
clearing generic poison and burn before the player is normalized. NPC death
processing calls `cure()`, and authoritative NPC removal plus respawn reentry
explicitly call `curePoison()`. `canReceivePoison()` blocks application during
the dead/removed/respawning portion of the NPC lifecycle.

Antidotes, cure-poison consumables, the admin test command, and Cleric support
use `curePoison()` or bounded `reduceCurrentPoisonPower()`. Partial reduction
preserves source and ceiling; falling below 10 cures.

`curePoison()` stops/removes the event, then clears fields and owner. It removes
`poisoned`/`poisoned_max` only inside the branch where the event attribute was
present. Consequently, a missing/wrongly removed event plus surviving cache
keys is not repaired by cure and can resurrect on login.

### Offline source and ownership behavior

Poison damage does not require a source to remain present, logged in, or alive.
An absent owner only suppresses Leach. If an owner is online but has removed
the Blood Necklace, Leach is also suppressed; equipping it before a later tick
enables Leach.

NPC and environmental poison never has an owner in the state. Player poison
uses one latest-owner UUID rather than per-contributor provenance. Reapplication
by another player transfers all future Leach, including damage derived from
power previously contributed by someone else.

### Contribution and kill credit

Generic poison pulse damage calls `damageAndGetActualDamage`, whose lethal
branch calls `target.killedBy(target.getOpponent())`. The stored
`poisonOwnerId` is consulted only after damage for Leach. It is not passed to
death settlement and the pulse itself adds no combat/magic/ranged/summon
contribution.

Consequences:

- a poisoned NPC can credit a later/current opponent instead of the poison
  owner;
- a poisoned NPC with no opponent does not complete normal death processing:
  `Npc.killedBy(null)` cures and returns without ordinary removal/reward work,
  while the compatibility helper has already reported factual lethal damage;
- a poisoned player can credit the current opponent even if that actor did not
  apply the poison;
- an environmental poison death may still be attributed to an unrelated
  current opponent;
- a player-owner poison tick can Leach factual damage while a different actor
  receives lethal credit; and
- resumed poison has no owner even for Leach, while lethal attribution still
  follows whatever opponent happens to be present.

An opponent-less player death generally follows the unattributed player-death
path, but the Tutorial Island special-case dereferences the null killer before
that fallback. This is another reason the DoT adapter must carry an explicit
source/cause rather than passing an incidental opponent through current death
code.

NPC reward selection may later consider previously recorded direct-damage
contributions, but poison pulses add none. That existing reward fallback does
not make poison provenance correct.

## Current generic burn contract

### Application, replacement, and tick

`Mob.applyBurn(damage, pulses)` overwrites both target fields and calls
`startBurnEvent`. If an attribute event exists, `startBurnEvent` extinguishes
it and creates a new `BurnEvent` with `ONE_PER_MOB`. This is replacement, not
addition, strongest-wins, or duration extension, and it restarts the full
8-tick countdown.

Each pulse uses fixed damage, decrements pulse count before damage, updates or
removes player cache state, and calls the standard compatibility damage helper.
It uses the standard hitsplat, not poison. There is no source, Leach,
contribution, typed policy, or explicit death adapter. Lethal credit therefore
uses the target's current opponent exactly as generic poison does.

### Persistence and lifecycle

Player targets cache `burn_damage` and `burn_pulses`. Both keys are required to
restore; a partial pair is ignored and not repaired. Login/restart creates a
new event with a full countdown. Burn pauses while the player is offline and
does not catch up by wall clock.

Player death clears generic burn through `cure()`. NPC death through the normal
`killedBy` path also calls `cure()`, but authoritative `Npc.remove()` and
respawn reentry only name `curePoison()`. A generic burn on a scripted-removed
NPC therefore lacks the same explicit lifecycle cleanup guarantee and depends
on later event behavior/removal. This is currently low exposure because no
production generic-burn producer was found.

`extinguish()` always removes both player cache keys whether or not an event
attribute exists, so it repairs orphan generic-burn cache more reliably than
`curePoison()` repairs poison.

## Active Elder burn contracts

### Elder Green Dragon boss burn

The boss burn stores three player attributes: active, absolute end time, and a
live source `Npc`. It schedules one player-owned event with a one-tick cadence.
Reapplication always writes `now + 5000 ms` and the latest dragon source; when
already active it does not create another event or reset its immediate
countdown.

Every pulse validates an online, present, living player and a present source
NPC. It rolls 1–3 damage, sets the dragon's kill type to Magic, and uses the
owned-effect resolved-damage transaction with stable key
`elder-green-dragon-burn-pulse`. Fire robe mitigation, Magic potion reduction,
and summon absorption apply; True Defense intentionally does not. It retains
the Elder attack follow-up sequence: Corrosive Aura, Divine Retribution,
explicit dragon/player death, combat timer, Ring of Life, player stat, and
party update.

The burn ends when time expires, source disappears, target dies/logs out, or
the event next observes invalid state. It is not persisted. On successful
logout the event is also stopped with the player's other tick events; a new
player object has none of the old attributes.

This path has explicit live source and kill credit, but no durable source
identity. Reapplication by another Elder Dragon transfers later ticks to that
dragon. Attribute state is not atomic: `active=true` with a missing event would
cause later applications to update source/end time without scheduling a new
event.

### Elder Green Dragon armor burn

The armor burn stores the event itself under a target attribute and schedules
it as `ONE_PER_MOB`. A valid application requires a logged-in, present, living
player source and a living/present target. Reapplication of a running event
replaces the source, resets five pulses, and resets the countdown.

Each one-tick pulse deals one owned-effect damage using stable key
`elder-green-dragon-armor-burn`, explicitly records player contribution for an
NPC target, updates a player target's stat, and explicitly settles death to the
player source. It does not invoke generic Leach, Siphoning, reflection, or a
recursive proc chain.

The effect is not persistent. It ends if the source logs out, is removed, or
dies; if the target is removed or dies; or after five pulses. Reapplication by
another player transfers all later pulse ownership and kill credit. Guard Dog
can suppress initial secondary-target selection, but does not cancel a burn
that was already applied.

For an NPC target, authoritative removal does not call an armor-burn-specific
cleanup method. The target-owned non-player event normally observes the removed
target on its next pass, clears the attribute, and stops. That eventual cleanup
should become an explicit lifecycle assertion rather than an assumption.

## Persistence and corruption audit

### Current cache format

The generic cache supports Integer, String, Boolean, and Long values. Save
serializes each value using a type discriminator and `toString`; load parses
the declared type before login restoration. There is no DoT schema/version,
atomic grouped record, bounds metadata, source identity, or checksum.

### Failure matrix

| Corrupt/incomplete state | Current result | Required future handling |
| --- | --- | --- |
| `poisoned` exists, `poisoned_max` absent | Current power is also used as maximum | Accept as an explicit legacy migration, then rewrite normalized state |
| `poisoned_max` exists, `poisoned` absent | No poison event; orphan maximum remains | Remove/quarantine orphan key |
| Poison cache exists, event attribute missing, then cure | Fields clear but cache can remain | Cure must clear persistent state independently of event presence |
| Negative `poisoned` | Negative event is scheduled; cure-threshold validation throws | Reject, clear/quarantine, log bounded diagnostic; do not schedule |
| Zero `poisoned` | Event is scheduled and cures on first due run | Normalize to no state before scheduling |
| Current poison exceeds maximum | Restored event current is not clamped to restored maximum | Clamp only under an approved migration rule, record repair, rewrite |
| Extreme poison values | Long-lived/extreme damage; later addition can overflow `int` | Enforce effect-specific bounded arithmetic before registration |
| Non-numeric declared numeric value | Integer parsing can abort player-cache load | Fail the individual optional status closed without blocking the account |
| Boolean/unsupported type under a poison key | `getInt` throws during login restoration | Same typed validation/quarantine rule |
| Only one burn key exists | Burn is ignored and stale key remains | Treat pair atomically; remove/quarantine incomplete record |
| Nonpositive burn damage/pulses | Event is scheduled, then extinguishes on first due run | Normalize to no state before scheduling |
| Extreme burn damage/pulses | Potential lethal spike or unbounded lifetime | Bound by effect profile/schema |
| Poison attribute contains wrong object type | Unchecked generic attribute cast can throw | Typed state container/registry; reject mismatched legacy attribute |
| Elder boss `active=true`, event missing | Refresh never schedules a replacement | Registry/event reconciliation must be atomic and self-healing |
| Elder boss source attribute wrong type | Unchecked cast can fail on tick | Typed state with source-kind validation |
| Elder armor stale stopped event | New application replaces it | Preserve replacement, but make cleanup/registration atomic |
| Duplicate poison scheduler event outside attribute | `ALLOW_MULTIPLE` permits it | One authoritative target/effect registration enforced by scheduler/state owner |

Corrupt-state diagnostics must not print player credentials, cache contents,
network addresses, or other private data. A bounded effect key, repair reason,
and nonreversible account diagnostic identifier are sufficient.

## Policy decisions required before implementation

> Decision update (2026-08-07): the project owner approved
> state-changing-only capped ownership transfer, factual poison contribution,
> no synthesized rewards for an offline poison owner and no unrelated-opponent
> fallback credit, and retirement/migration of dormant generic burn. The exact
> accepted wording and executable-evidence boundary are recorded in
> [`combat-a08-dot-lifecycle-characterization.md`](combat-a08-dot-lifecycle-characterization.md).

The following recommendations preserve current gameplay where it is coherent
and make currently accidental behavior explicit. Rows marked **decision** can
change rewards or PvP outcomes and must be approved before A08.2+ changes them.

| Question | Current behavior | Recommended A08 policy |
| --- | --- | --- |
| Does player-target poison survive logout/restart? | Yes; pauses offline | Preserve. Persist normalized remaining state and cadence policy, stop runtime event on logout, restore exactly once. |
| Does generic burn survive logout/restart? | Yes if both legacy keys exist | **Decision:** either preserve as a compatibility family with validated state or migrate/retire old rows. Do not silently map it to either Elder burn. |
| Does poison continue if its source logs out/dies/disappears? | Yes; only Leach stops | Preserve damage continuation. Source availability should gate live equipment benefits, not erase target status. |
| Does Elder armor burn continue if player source goes offline/dies? | No | Preserve unless its item design is deliberately changed. |
| Does Elder boss burn continue if dragon disappears? | No | Preserve. |
| How is poison replacement owned? | Latest accepted player call, even at ceiling; NPC/environment clears owner | **Decision:** retain documented latest-source behavior, but define whether a zero-increase capped application is ownership-changing. Recommended: only an application that changes state can transfer ownership. |
| How is mixed-source accumulated poison credited? | One latest player owns all Leach; lethal uses opponent | **Decision:** simplest compatible model is one latest effective source for all future ticks. Per-contributor power is more exact but is a larger balance/data design. |
| What if the poison owner is offline when an NPC dies? | Owner is ignored; opponent/reward fallback decides | **Decision:** retain durable source identity for audit. Do not synthesize offline loot/XP without a separate reward design; if source is online and eligible, explicit source should own death. |
| Should NPC/environment poison receive kill identity? | No explicit identity | Store source kind and runtime NPC identity or scripted/environment key. Player deaths should report/settle that explicit cause when valid, never a coincidental opponent. |
| Does Leach snapshot equipment? | No; checked each pulse | Preserve current live equipment check. Persist source identity, not a Leach percentage snapshot. |
| Do poison ticks contribute NPC damage? | No | **Decision:** recommended yes, using factual tick damage and the approved latest-source rule, so reward and kill ownership agree. |
| Do generic DoTs trigger other procs? | No ordinary proc chain; poison Leach only | Preserve effect-specific no-recursion. Do not route ticks back through root attack processing. |
| Do effects tick offline? | No | Preserve pause semantics. Wall-clock catch-up would be a balance change. |
| What clears on target death/respawn? | All generic statuses; active burns stop through validation | Preserve and centralize as an atomic target-lifecycle operation. |

## Recommended provenance architecture

### Typed target-owned state

Introduce a small target-owned periodic-damage registry or equivalent typed
slot. Do not continue splitting one effect between fields, stringly typed
attributes, cache keys, and an independently registered event. Each state must
carry at least:

- schema version and stable effect key;
- target lifecycle identity;
- source kind (`PLAYER`, `NPC`, `ENVIRONMENT`, or `SCRIPT`) and stable source
  identity appropriate to that kind;
- source runtime generation where actor-lifetime validity matters;
- current power/damage, ceiling or pulses, and bounded profile limits;
- explicit stacking/replacement policy;
- cadence/remaining timing policy;
- persistence and offline-target policy;
- source-availability policy;
- contribution, kill-credit, Leach, and recursion policies; and
- application identity/tick for diagnostics and parent-child observation.

Do not persist live `Mob`, `Player`, `Npc`, or event object references. Player
source identity can use the existing stable username-derived UUID internally;
NPC source identity requires both the runtime UUID and an explicit rule for
what happens after that lifecycle ends. Environment/script sources need stable
non-player effect keys.

### Atomic application and registration

One operation should validate an application, compute bounded next state,
apply the effect's approved stack/refresh policy, update provenance, and ensure
exactly one target/effect event. Event registration failure must not leave an
active marker without a clock, and a stale marker must be repairable without a
second damage stream.

Generic poison's tick countdown must continue across ordinary reapplication to
preserve current cadence. Elder armor burn must retain countdown reset. Elder
boss burn must retain end-time extension. These are policy fields, not one
universal refresh behavior.

### Typed tick settlement

DoT pulses should use `DamageRequest.SourceCategory.DOT` with a stable
effect-specific key and explicit source resolution. The adapter must preserve
each family's currently approved mitigation, presentation, packet, follow-up,
and recursion contract. Migrating to the transaction is not permission to make
generic poison use Elder burn mitigation or proc chains.

The tick result should atomically:

1. validate target lifecycle and effect state;
2. resolve source according to the effect's offline/lifetime policy;
3. calculate bounded requested damage;
4. settle one typed damage request;
5. record factual contribution if approved;
6. apply only explicitly allowed post-tick benefits such as poison Leach;
7. settle lethal credit from provenance rather than `getOpponent()`; and
8. decrement/clear/persist state exactly once.

The existing death-before-presentation behavior of the compatibility helper is
observable. A08 implementation tests must explicitly approve or preserve any
ordering delta rather than assuming equal final Hits is enough.

### Persistence and session lifecycle

Use one versioned logical record per persistent effect, even if compatibility
requires temporarily reading old cache keys. On login:

1. parse without allowing one optional effect to fail the whole account;
2. validate types, ranges, pair completeness, and source kind;
3. migrate recognized legacy values;
4. clear/quarantine impossible state with a bounded diagnostic;
5. construct target state; and
6. register exactly one event only after valid state exists.

On logout, atomically persist approved target state and stop/unregister its
runtime event before removal. Preserve the existing no-offline-ticks behavior.
On death, respawn, NPC removal, or lifecycle replacement, clear all
target-lifetime-only periodic state at the lifecycle authority rather than
waiting for an event to notice later.

## Existing regression evidence and gaps

> A08.2 update: the first executable lifecycle slice is recorded in
> [`combat-a08-dot-lifecycle-characterization.md`](combat-a08-dot-lifecycle-characterization.md).
> It corrects this audit's generic-burn replacement description: active
> reapplication clears the newly written state and leaves an unscheduled
> zero-state marker because the prior `ONE_PER_MOB` event still awaits cleanup.
> It also proves generic burn can tick after NPC removal begins.

The current suite proves useful fragments, but not the provenance lifecycle as
a whole:

- `CurrentCombatSecondaryDamageCharacterization` executes one generic poison
  pulse and one generic burn pulse, including power/pulse mutation, hitsplat,
  and compatibility-helper presentation.
- `CurrentCombatCharacterizationTest` proves generic NPC poison is cleared on
  ordinary death/removal and cannot cross respawn.
- `CurrentCombatDeathLifecycleCharacterization` proves player death clears
  generic poison. It does not independently seed/assert generic burn.
- `CurrentCombatOwnedDamageCharacterization` proves Elder armor burn refresh,
  five pulses, cleanup, and primary/secondary behavior, plus representative
  Elder boss burn mitigation, presentation, and transaction identity.
- the A07 descriptor and policy fixtures pin the stable Elder burn semantic and
  settlement keys;
- `test-poison-model.py`, `test-poison-balance.py`, and related My World tests
  guard configuration and source structure; and
- `test-npc-poison-death-lifecycle.py` supplements the compiled NPC lifecycle
  case.

No current fixture proves target logout/login, server restart, persisted owner,
source logout/death, multiple source replacement, capped ownership transfer,
poison contribution, poison kill credit, opponent mismatch, generic-burn NPC
removal, malformed cache repair, partial cache pairs, wrong attribute types,
scheduler duplication, or failure atomicity. Passing today's combat gate does
not resolve those A08 policies.

## Phased implementation plan

### A08.2 — executable characterization and decisions

- Add runtime fixtures for every row in the future matrix below before moving
  authority.
- Capture current ordering: power/pulse mutation, cache write, damage, death,
  presentation, contribution, and Leach.
- Obtain explicit decisions for capped ownership transfer, poison contribution,
  offline-owner NPC kills, and generic-burn compatibility.
- Add no-op observer evidence for current poison/burn where useful without
  changing settlement.

Stop if a test cannot distinguish poison source from current opponent or if a
proposed assertion silently chooses a reward policy.

### A08.3 — typed state/provenance foundation

Status: implemented for generic poison application and tick settlement. The
runtime now uses atomic typed target state, one scheduler stream, bounded
application, typed DOT settlement, factual contribution and Leach, and
explicit lethal attribution without unrelated-opponent fallback.

- Add immutable/bounded provenance and periodic-state value types.
- Add atomic target registry/application and one-event enforcement.
- Migrate generic poison state/application first while preserving power,
  cadence, Nature decay, message, poison hitsplat, and Leach formulas.
- Add typed DoT settlement and explicit lethal adapter only after the approved
  source/contribution rules are executable.

Stop on formula, RNG, application phase, countdown, hitsplat, packet,
lifesteal, contribution, drop, XP, or callback-cardinality drift.

### A08.4 — persistence and lifecycle migration

Status: next bounded implementation phase.

- Read current `poisoned`, `poisoned_max`, `burn_damage`, and `burn_pulses` as
  legacy inputs under a versioned migration.
- Persist approved player-target provenance without live object references.
- Validate, normalize, and rewrite; make cure/extinguish remove state even when
  the event/attribute is absent.
- Prove successful and failed logout saves, restart, repeated relog, death,
  NPC removal, and respawn.

### A08.5 — burn disposition

- Decide whether generic burn remains a compatibility family or is retired
  after its persisted rows are migrated/cleared.
- Move the two Elder burn clocks behind the typed lifecycle boundary one at a
  time without forcing them into a shared refresh or source-validity policy.
- Retain existing A05 settlement keys and A07 semantic keys unless an explicit
  migration maps them.

## Future regression matrix

Every scenario must assert final Hits, displayed damage, hitsplat type/count,
event count/running state, state values, cache values, source identity,
contribution, Leach, kill callback/cardinality, drops/rewards where applicable,
and observer effect key. Tests must use deterministic clocks and random
sources; sleeps and live servers are not acceptable assertions.

### Application and stacking

| Scenario | Required assertions |
| --- | --- |
| First generic poison application | One event; current/ceiling/source exact; full initial countdown |
| Reapply below ceiling | Power adds; ceiling follows max rule; existing countdown unchanged |
| Reapply above ceiling | Power caps; no overflow; exact owner-transfer policy |
| Reapply while already at ceiling | No state inflation; approved ownership result explicit |
| Lower/higher incoming maximum | Ceiling never accidentally shrinks; cure resets it |
| Weapon + armor + dragon poison on one hit | One application, strongest applied component, additive maximum, exact RNG calls |
| Corrosive Aura repeated application | Current and ratcheting maximum match approved existing formula |
| Player then player poison | Latest/effective source policy and Leach owner exact |
| Player then NPC/environment poison | Approved source replacement and later credit exact |
| NPC/environment then player poison | Source becomes explicit and no prior damage is duplicated |
| Generic burn apply/reapply | Replacement damage/pulses and countdown policy exact |
| Elder boss burn reapply | End time extends, one event, latest source, countdown not duplicated |
| Elder armor burn reapply | Five pulses restored, countdown reset, latest source |
| Poison and each burn simultaneously | Independent keys/events; cleanup of one does not clear another |

### Ticking, formulas, and recursion

| Scenario | Required assertions |
| --- | --- |
| Poison powers 9, 10, 19, 20, and ceiling edge | Cure boundary and integer damage exact |
| Nature Cleansing changed between pulses | Current equipment changes decay only; source unchanged |
| Blood Leach equipped/removed between pulses | Live source equipment controls only later Leach |
| Poison overkill and Goblin Tenacity | Factual damage drives Leach/contribution; approved presentation/death order |
| Zero factual poison damage | No heal; state still decays under approved formula |
| Generic burn final pulse | Cache/state/event clear once after one damage settlement |
| Elder boss damage 1/2/3 | Fire/Magic mitigation, summon absorption, no True Defense, exact follow-up order |
| Elder armor burn pulses | Five one-damage typed settlements; no ordinary lifesteal/reflection/proc recursion |
| Divine Retribution/Corrosive Aura interaction | Boss burn retains current one-way follow-up without recursive burn/poison loops |
| Duplicate scheduler registration attempt | Exactly one authoritative stream; deterministic refusal/repair |

### Persistence, logout, and restart

| Scenario | Required assertions |
| --- | --- |
| Player target logout/login with poison | Remaining power/ceiling/source restored once; full approved cadence; no offline ticks |
| Source player logout/login while target remains | Damage continues; stable source resolves; Leach stops/resumes only per live equipment policy |
| Player target logout/login with generic burn | Approved compatibility result and one event |
| Repeated target relogs | No scheduler/event/reference accumulation |
| Server restart | Same normalized state and attribution as logout/login |
| Logout save failure | Player/runtime state is not prematurely lost or duplicated on retry |
| Partial legacy burn pair | Deterministic repair and rewrite |
| Legacy poison without maximum | Explicit migration to normalized record |
| Source identity missing from legacy poison | Explicit unattributed legacy source; never guessed from opponent |

### Death, respawn, source availability, and credit

| Scenario | Required assertions |
| --- | --- |
| Poison kills NPC while owner is online | Explicit approved owner, contribution, XP/drop/plugin callbacks exactly once |
| Poison kills NPC after owner logs out | Approved no-offline-reward policy; durable audit source; no unrelated opponent credit |
| Poison kills NPC after another player engages | No kill steal through current opponent |
| Poison kills player with original source present | Explicit source/kill type and PvP updates under approved policy |
| Poison kills player after source leaves | Explicit offline/environment result; no coincidental opponent credit |
| Environmental poison kills player | Script/environment cause, not current opponent |
| NPC poison kills player after NPC dies/despawns | Approved lasting-poison credit/fallback, no stale object dereference |
| Target player death/respawn | All target-lifetime poison/burn state and cache clear once |
| Target NPC death/removal/respawn | No generic poison, generic burn, or armor burn crosses lifecycle |
| Elder armor source logout/death | Burn stops immediately on next authoritative boundary; no further credit |
| Elder boss source removal/death | Burn clears without later pulse |
| Simultaneous lethal pulse/reflection | One death owner/callback; no recursive second death |

### Corruption and failure injection

| Scenario | Required assertions |
| --- | --- |
| Negative/zero/overflow poison values | No event scheduled; bounded repair; account can log in |
| Current greater than maximum | Approved clamp/quarantine and normalized rewrite |
| Wrong cache type/non-numeric value | Effect rejected independently; account load survives |
| Orphan poison maximum | Removed/quarantined |
| Cure with missing event attribute | Fields, cache, provenance, and registry all clear |
| Active marker with missing event | Exactly one repaired event or fail-closed clear |
| Wrong runtime attribute type | No unchecked-cast crash; bounded repair |
| Event add refusal/exception | No active state without clock and no clock without state |
| Tick settlement exception | No double decrement/damage on retry; explicit failure policy |
| Death callback exception | Lifecycle finalizes according to A05 death authority; DoT cannot repeat lethal settlement |

### Producer parity

At minimum, execute one deterministic successful and failed application from
each active producer family: general melee, PvM melee, projectile ranged,
projectile magic, dual-element Acid, primary/secondary Guthix, Corrosive Aura,
NPC poison, legacy non-My-World PvP poison, Sinister Chest, admin self-test,
Elder boss burn, and Elder armor primary/secondary burn. Keep a source scan test
so a future producer cannot bypass the typed application boundary unnoticed.

## Validation for this audit

The inventory was produced with repository-wide Java searches for
`applyPoison`, `applyBurn`, `BurnEvent`, `curePoison`, `extinguish`, and both
Elder burn owners, followed by direct inspection of:

- `Mob`, `Player`, `Npc`, `Cache`, `PlayerService`, and `GameDatabase`;
- `GameTickEvent`, `GameTickEventStore`, and `GameEventHandler`;
- every active producer listed above;
- A05 damage transaction/characterization documents and implementation; and
- A07 semantic descriptors and current combat regression references.

Before any A08 implementation branch, rerun the producer/source inventory.
The combat system is actively evolving, so this document is authoritative only
for the stated baseline until refreshed.

Audit-branch verification:

- `./server/test_combat` — PASS, 91 scenarios;
- `python3 tests/myworld/test-poison-balance.py` — PASS;
- `python3 tests/myworld/test-npc-poison-death-lifecycle.py` — PASS;
- `python3 tests/myworld/test-jewelry-runtime-effects.py` — PASS; and
- `python3 tests/myworld/test-poison-model.py` — existing baseline failure. Its
  source-text assertion still expects the old `RangeEvent` constructor tail
  `true, ammoId, 0, 0, 0, 0, DuplicationStrategy.ONE_PER_MOB`, which is absent
  on the audited published baseline. This document-only branch did not repair
  that unrelated brittle assertion.

## A08.1 handoff boundary

A08.1 is complete when this document is reviewed. The next branch should be
A08.2 characterization and policy decisions, not an immediate generic DoT
rewrite. In particular, do not implement poison kill/contribution ownership,
offline-owner rewards, capped reapplication transfer, or generic-burn
retirement until those decision rows are explicitly resolved.
