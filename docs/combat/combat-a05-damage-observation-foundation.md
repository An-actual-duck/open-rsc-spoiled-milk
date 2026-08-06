# A05.1 Damage Request, Result, and Observation Foundation

This is the historical A05.1 foundation record. A05.2's bounded primary-melee
authority continuation is documented in
`docs/combat/combat-a05-direct-melee-damage-transaction.md`.

## Outcome and authority boundary

A05.1 adds immutable `DamageRequest` and `DamageResult` facts plus an optional
read-only `CombatDamageObserver`. Production construction installs
`CombatDamageObserver.NONE`. The only instrumented production boundary is the
primary mutation inside `PvmMeleeEvent.inflictDamage`, and its observation is
disabled by default.

This branch does **not** add a damage pipeline. Existing callers still own
formula resolution, mitigation, Hits mutation, hitsplats, contribution,
lifesteal, effects, packets, death, XP, and drops. `DamageResult.Status` is
therefore `OBSERVED_CURRENT_PATH`, not `APPLIED`: the result reports what the
current path already did and cannot authorize it.

When observation is disabled, no request UUID or participant snapshot is
created. When a diagnostic observer is enabled, setup happens immediately
before the existing Hits subtraction and publication happens immediately after
the existing damage update and hitsplat. Failures from observer enablement,
request customization, or result publication are logged with only the stable
effect key and cannot prevent, roll back, or duplicate damage.

## Contract

`DamageRequest` records:

- request, optional parent, event, and encounter identities;
- the server combat tick;
- source and target references paired with A04 lifecycle/session snapshots;
- broad provenance (`ACTOR`, `OWNED_EFFECT`, `DOT`, `ENVIRONMENT`, or
  `SCRIPT`), optional attack style, and a stable effect key;
- the already-resolved non-negative legacy damage and hitsplat type; and
- the sole supported input stage, `RESOLVED_LEGACY`.

It intentionally does not claim raw-roll, accuracy, mitigation, contribution,
XP, lifesteal, reflection, deduplication, or death policy. Those facts cannot be
filled honestly for every current path until each source-family branch owns its
complete order.

`DamageResult` validates the observed Hits transition against the request and
records Hits before/after, actual damage, overkill, and whether the observed
transition reached zero. “Hits after” is the immediate logical damage outcome,
not the later post-death/respawn value.

## Current damage and death inventory

The inventory below is the migration map. A path appearing here is not
permission to consolidate it in A05.1.

### Direct melee primary paths

`PvmMeleeEvent.inflictDamage` and `CombatEvent.inflictDamage` currently order
their main mutation approximately as follows, with important differences
retained in each class:

1. summon attack eligibility, hit count, and summon outgoing adjustment;
2. combat side-effect scripts, NPC combat scripts, and Paralyze Monster
   compatibility;
3. robe/potion/summon absorption, Frostbite, True Defense, then Cleric
   Ward/Aegis mitigation and blocked-damage tracking;
4. direct Hits subtraction, damage update, and hitsplat;
5. summon lifesteal, NPC contribution, summon-owner contribution, Divine
   Grace, and Death Ring terminal checks;
6. Blood Amulet, Cleric Rally, and Cleric Thorns/reflection;
7. stat/sound/party packets, Corrosive Aura, and Divine Retribution;
8. Ring of Life and combat scripts for survivors;
9. summon/leather on-hit effects and PvM auto-retaliation where applicable; or
10. Death Robe overkill splash and the event-specific death adapter.

A05.1 observes only step 4 in `PvmMeleeEvent`; all surrounding order remains
where it was. A05.2 must cover both melee classes, zero hits, overkill,
simultaneous reflection death, player/NPC directionality, and every listed hook
before moving this mutation.

### Primary projectile path

`ProjectileEvent.projectileDamage` owns bow, thrown, magic, NPC projectile,
summon projectile, and related compatibility types. It performs removal/range
handling, outgoing buffs and suppression, style-specific mitigation,
Frostbite, True Defense, Cleric mitigation, Hits/hitsplat/impact effect,
tracking and contribution, summon/Blood/Cleric lifesteal, reflection, Death
Ring, Splinter, poison, leather/dragon/elder-dragon effects, elemental debuffs,
chasing, and death settlement. A05.3 must retain its type distinctions and must
not pre-empt A06 launch/impact settlement.

### Secondary, reflection, and area paths

Direct Hits mutations also exist in:

- `CombatEvent`: Chaos chain, Frostbite, Death Robe splash, auxiliary magic,
  and auxiliary true damage;
- `PvmMeleeEvent`: the same families plus scythe cleave and jewelry damage;
- `ProjectileEvent`: Chaos chain, jewelry recoil, Frostbite, Splinter, Blood
  Robe, Death Robe, Balrog splash, auxiliary magic/true damage, and Cleric
  Thorns;
- `Player`: Death Amulet and Death Ring damage;
- `Summoning.inflictSummonBonusDamage`, `DivineRetribution.apply`, and
  `ElderGreenDragonSpecialAttacks.inflictPlayerDamage`; and
- delayed Salarin and god-spell secondary damage in `SpellHandler`.

These paths differ on mitigation, contribution style, tracking, stat packets,
aggro, lifesteal, terminal adapters, and whether a zero hitsplat is emitted.
They remain A05.4/A07 work rather than being forced through one premature
policy.

### DoT, environment, script, and plugin paths

`Mob.damage` / `damageAndGetActualDamage` is the broad compatibility helper
used by poison, burn, desert heat, dragon/fire scripts, NPC behavior,
`Functions`, spells, and many authentic/custom plugins (agility obstacles,
quests, minigames, random events, consumables, and commands). It currently:

1. records player damage time and applies Goblin Tenacity;
2. caps the returned actual damage to current Hits;
3. calls `killedBy(getOpponent())` before stat/hitsplat publication on a lethal
   request, otherwise writes Hits directly;
4. sends the player stat; then
5. publishes the legacy damage update and hitsplat.

Poison depends on the returned actual damage for Blood Necklace Leach. Direct
plugin/admin mutations also exist and do not all use this helper. These remain
A05.4/A05.6 and A08 characterization targets; changing the helper now could
alter unattributed deaths, packet order, zero damage, overkill, or plugin
compatibility.

### Death authorities

`Npc.killedBy` resolves player/summon ownership, poison cleanup, plugin kill
hooks, range timer, blocked-damage messaging, contribution XP, pending summon
XP, kill-credit fallback, achievements/kill counters, jewelry effects,
logging, personal drops, death listeners, and removal. It has distinct null,
already-killed, plugin-owned-removal, and owner-unavailable exits.

`Player.killedBy` guards login/exactly-once state, cancels attack ownership and
advances the A04 lifecycle, sends death packets, preserves a tutorial special
case, clears transient effects, records PvP state, drops bones/inventory,
handles duels and Hardcore status, clears combat/summons/range/magic, teleports,
normalizes stats, refreshes party/equipment/inventory state, and delays removal
of the killed guard. A05.5 must preserve these phase orders and simultaneous
death behavior; A05.1 does not call either authority.

## Provenance and adaptation

Classic-Scape commit `e00c154b4650dc0a80f9cca91fde99cde77d01fa`
demonstrated request/result provenance and an observation callback. Its
`DamagePipeline` also mutated Hits, contribution, and hitsplats in the first
slice. Spoiled Milk adopts only the small immutable-fact and inert-observer
concept here. It deliberately omits that pipeline's policy flags, deduplication,
rejection decisions, contribution authority, and raw-stage promise because the
current project has substantially more Cleric, summoning, jewelry, dragon,
area-effect, and compatibility ordering to preserve.

The source-category names and request/result separation retain attribution to
Classic-Scape author aicovergod. The implementation was independently reduced
and adapted to the current A04 participant snapshots and current server
constructor seams.

## Executable verification

A05.1 grows the authoritative combat gate from 32 to 35 scenarios. The new
fixtures verify:

1. immutable resolved-stage fields, lifecycle-aware snapshots, non-negative
   validation, actual-damage capping, overkill, and terminal facts;
2. one exact observation for one existing nonlethal PvM melee mutation,
   including HP, damage update, hitsplat, event identity, style, and stable
   effect key; and
3. failures during observer enablement and publication cannot suppress,
   repeat, or roll back current damage or hitsplats.

Production artifacts must still exclude all combat fixture classes. Required
branch gates are the 35-scenario combat suite, focused Cleric/summoning/poison/
projectile/dragon/layered tests, the authoritative core/plugin build and
artifact audit, and changed-code analysis. No client packet or visible combat
behavior changes, so A05.1 has no private visual acceptance surface.

## Next bounded branches and stop conditions

1. **A05.2 direct melee primary:** implemented on its focused branch with both
   main melee mutation blocks and exact parity fixtures; manager integration
   remains the acceptance boundary.
2. **A05.3 projectile primary:** migrate ranged/magic primary impacts without
   changing launch or settlement timing.
3. **A05.4 secondary/reflection/environmental:** define explicit source policy
   for the inventoried paths and compatibility helper.
4. **A05.5 death lifecycle:** introduce atomic death results and plugin adapters
   only after NPC/player phase parity and simultaneous-death coverage.
5. **A05.6 pruning:** remove direct mutations only when runtime coverage proves
   them obsolete.

Stop any branch on a formula, mitigation, damage display, contribution, XP,
lifesteal, proc, aggro, overkill, death, drop, plugin, packet, RNG draw, or hook
cardinality delta. Do not make the observer a required startup service, and do
not enable collection or persistence without a separate redaction, capacity,
and operational review.
