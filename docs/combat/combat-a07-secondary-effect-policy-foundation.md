# A07.1 Secondary-Effect Policy Foundation

## Scope and result

A07.1 establishes one typed source for the stable identities already emitted by
current secondary-damage transactions. It does not add an effect registry,
select targets, execute procs, change damage, reorder callbacks, or move death
authority. Existing event/content owners continue to provide every gameplay
decision.

`SecondaryEffectPolicy` contains 39 current settlement policies in five
families:

| Family | Count | Existing owners |
| --- | ---: | --- |
| Auxiliary | 6 | Reciprocal melee, PvM melee, and projectile Magic/"true" helpers |
| Reflection | 10 | Frostbite, Cleric Thorns, jewelry recoil, Divine Retribution |
| Player child | 11 | Chain, Splinter, robe splashes, Scythe, Death Amulet/Ring |
| Owned content | 9 | Balrog, Elder Green Dragon, Elder armor, summon bonus damage |
| Delayed spell | 3 | God-spell area, Iban area, Salarin second strike |

These are settlement identities, not yet semantic proc descriptors. For
example, Bear Maul, dragon weapon breath, elemental sword, armor procs, and
Hell's Inferno reuse the event owner's auxiliary settlement policy. A future
on-hit catalog must give those semantic effects separate identities without
changing the stable damage observations introduced by A05.

The catalog is immutable, rejects duplicate/blank keys at class initialization,
supports exact lookup, and derives its size from its declarations. The current
total inventory alone exceeds 32, so Classic-Scape's number cannot be reused as
a total registration/catalog cap. That fact does not prove how much work can
occur in one phase. A07 must therefore distinguish:

- registration capacity, which should grow with the immutable reviewed
  catalog and must not silently reject a newly declared effect; and
- per-hit execution work, which must be bounded from the maximum eligible
  effects in each characterized phase, including explicitly named planned
  effects and reviewed headroom.

A07.1 intentionally chose no execution budget because no shared executor or
complete semantic descriptor inventory existed yet. A07.2 is now complete and
records the 71 semantic identities, per-phase counts, and descriptive planning
budgets in
[`combat-a07-secondary-effect-descriptor-inventory.md`](combat-a07-secondary-effect-descriptor-inventory.md).

## Current policy matrix

`View` below means the caller enumerates its existing view-area collection.
That collection currently supplies world/layer membership. Most secondary
radius checks then compare legacy `Point` coordinates and do **not** perform an
independent wall/line-of-effect check. A later selector must preserve that fact
unless a separately approved safety change explicitly tightens it.

| Effect family | Eligibility and geometry | Zero-hit aggro | Lifesteal/procs | Ordering and death |
| --- | --- | --- | --- | --- |
| Auxiliary Magic/"true" | Existing primary target only; living-target guard; mitigation remains helper-specific | None locally | No recursive equipment/reflection/lifesteal chain | Event adapter immediately handles terminal target; helper return value remains caller-specific |
| Frostbite reflection | Consumed Frostbite source; positive pending hit; reflects half rounded up and reduces the pending primary hit | None | No reflected-hit lifesteal or recursive reflection | Runs before True Defense and Cleric reduction; event-specific melee death adapter or projectile direct death |
| Cleric Thorns | Typed Cleric result after established lifesteal; positive reflected amount | None | No Thorns/recoil/lifesteal recursion | Can kill source before later procs; simultaneous target death still settles through the primary event adapter |
| Jewelry recoil | Equipped defender, successful chance roll, positive derived reflection | None | No recursive reflection/lifesteal | Melee uses event death adapters; projectile preserves ranged reset, Ring of Life, then direct death |
| Divine Retribution | Player defender, positive actual incoming damage, prayer/equipment rule and chance | None | No recursive proc chain | Caller owns reflected-source death order; Elder dragon and ordinary paths remain distinct |
| Chaos chain | Player-to-NPC root; Guard Dog suppression; View; radius 4 around current anchor; random target; at most 3 hops; no independent wall check; previous targets may currently be revisited | None | No per-hop lifesteal or ordinary on-hit chain | One chance draw and one child settle per hop; anchor/projectile advance before next hop; current event/direct death rules differ |
| Splinter | Positive projectile hit; configured chance; player-to-NPC; Guard Dog suppression; View; one random NPC within radius 2 of primary; no independent wall check | On applied child damage only when `shouldChase` and target is idle | No child lifesteal or further proc chain | Runs before primary terminal branch; direct child death |
| Blood Robe splash | Positive Magic actual damage; blood spell; player-to-NPC; Guard Dog suppression; View; every eligible NPC within radius 2; no wall check | None | No local lifesteal; outer primary Magic path remains separate | Runs before Blood Amulet and Cleric post-hit processing; view iteration order; direct Magic child death |
| Death Robe overkill | Positive primary overkill; target terminal; Guard Dog suppression; View; every eligible NPC within radius 2; no wall check | None | No local lifesteal; summon-owner assist contribution retained | Runs immediately before primary death adapter; style follows owning melee/projectile path; direct child deaths occur in view order |
| Scythe cleave | Equipped Scythe; Guard Dog suppression; View; every living attackable non-summon NPC in the eight tiles around the player; no independent spatial/line check in the local coordinate predicate | Yes, including an accurate zero | Per-child Divine Grace, Blood Amulet, summon lifesteal, Death Ring, Death Robe; Bear/dragon/elemental/poison descendants remain allowed in their current order | View order; each child fully settles before the next; child death and party update remain local |
| Death Amulet burst | Charged qualifying kill; Guard Dog suppression; View; configured player-centered radius; no wall check | None | No local lifesteal or summon-assist contribution | Charge is gained/consumed before child loop; direct child death in view order |
| Death Ring hit | Living non-summon NPC primary; positive stored charge damage | None | Contribution and summon-owner assist; no local lifesteal | Returns lethal fact; caller owns death and later ordering |
| Balrog splash | Positive Balrog Magic primary; View; every eligible player within radius 2; no player-area suppression or wall check | None | No Cleric, party, Ring of Life, reflection, or lifesteal chain | Style mitigation then child settlement/tracking/stat; direct child death in view order |
| Elder dragon attacks/burn | Effect-specific player selection and radius; summon attackability; style-specific mitigation; player area suppression does not apply | NPC attack path owns attempt/launch behavior | Corrosive Aura then Divine Retribution; no player contribution/lifesteal | Per-child stat/party, reflected dragon death, player death, timer/Ring of Life order remains content-owned |
| Elder armor proc/burn | Valid player source/target; primary plus Guard-Dog-suppressible player-owned radius-2 splash; refreshable five-pulse burn | None | No secondary proc/reflection/lifesteal chain | Primary settles before splash; each positive surviving target receives/refreshed burn; direct child death |
| Summon bonus | Trait-owned single target; positive bonus; style-specific player mitigation | None locally | Summon contribution only; no local lifesteal | Returns lethal fact to caller; caller owns death |
| God/Iban area | Scheduled one tick after cast; Guard Dog suppression; View; living attackable non-summon NPCs within radius 2; no independent wall check | Surviving child starts chase | God spell aggregates all damage for one later lifesteal; no per-child proc chain | Primary projectile precedes area event; each child contribution/chase/effect order remains handler-owned; lethal helper boundary remains active |
| Salarin second strike | Scheduled one tick after primary; original target; potion Magic reduction for player target | None | No XP, lifesteal, aggro, or further proc chain | Damage update without hitsplat/stat; contribution then direct death |

## Duplicated on-hit chains that remain active

The three central event paths are similar but not interchangeable:

1. Reciprocal melee and PvM melee settle primary damage, summon lifesteal,
   contribution/Divine Grace/Death Ring where applicable, Blood Amulet,
   Cleric post-hit/Thorns, player-facing packets, Corrosive Aura and Divine
   Retribution, scripts/Ring of Life, then surviving-target effects or terminal
   Death Robe/death.
2. PvM melee additionally owns combat timers, summon on-hit effects,
   auto-retaliation, Scythe descendants, and NPC-only special procs.
3. Projectile impact owns Balrog/Blood splash placement, projectile-style
   contribution, deferred Cleric Rally, source-terminal stop, Death Ring,
   Splinter, terminal Death Robe, summon/poison/leather/dragon effects,
   dual-element effects, and chase in a different order.

Even the duplicated `applyLeatherSetOnHitEffects` methods differ in random
source, PvP splash handling, style eligibility, and debug labels. A07.1 leaves
them unchanged. Consolidation must begin with one exactly matching effect
family and stop if it requires a common chain that changes any of those facts.

## Bounded follow-up branches

### A07.2 — semantic descriptor inventory and phase budget

Status: complete. `SecondaryEffectDescriptor` is descriptive only; the current
maximum phase count is 30 and the reviewed planning budget is 34 for that
phase. No shared executor or runtime cap was introduced.

- Give each proc/debuff/heal/child semantic a stable identity separate from its
  A05 damage settlement identity.
- Record phase, style/source/target gate, zero-damage rule, RNG draw timing,
  charge ownership, recursion, presentation, and existing owner.
- Derive per-phase current counts and list concrete planned effects from active
  plans before selecting a work budget.
- Keep the descriptor catalog descriptive; do not execute it yet.

Stop if an effect's phase or RNG position cannot be proven by an executable
fixture.

### A07.3 — player-owned radius selection, split by compatible policy

Status: complete on published main. The typed selector and
its two explicit live/snapshot timing contracts are recorded in
[`combat-a07-player-owned-npc-radius-selection.md`](combat-a07-player-owned-npc-radius-selection.md).

Only target enumeration for the compatible ordinary player-owned NPC splash
and Death Amulet terminal-burst groups moves. Current view ordering, Guard Dog
suppression, spatial-domain behavior, primary exclusion, summon exclusion,
wall behavior, moving/fixed center choices, and revalidation timing remain
unchanged.

Do not combine Scythe, random Splinter, chain traversal, boss-player areas, or
delayed spell areas merely because each is called AoE.

### A07.4 — chain and random-single traversal

Status: complete on published main. Chain lightning and
Splinter remain separate typed policies, as recorded in
[`combat-a07-chain-random-traversal-policy.md`](combat-a07-chain-random-traversal-policy.md).

Executable cycle, repeated-target, RNG order, layer, source/target removal,
respawn, cap, wall, and child-death tests establish current target revisits and
missing line-of-effect checks as maintained compatibility. Changing either is
a separately approved behavior change, not part of this refactor.

### A07.5 — one duplicated proc family at a time

Status: the first six bounded families are complete on published main;
later families remain separately characterized follow-ups.
`OgreStaggeringBlowProc` centralizes only the identical complete-set gate,
single draw, and one-attack debuff application. The contract and excluded
nearby families are recorded in
[`combat-a07-ogre-stagger-proc.md`](combat-a07-ogre-stagger-proc.md).
`BabyDragonSmokeProc` centralizes only the positive equipment-effect gate,
single draw, smoke projectile, and five-attack accuracy-debuff refresh unit.
Its separate contract is recorded in
[`combat-a07-baby-dragon-smoke-proc.md`](combat-a07-baby-dragon-smoke-proc.md).
`BlueDragonWaterProc` centralizes only the complete-set gate, chance and damage
draws, event-owned auxiliary true-damage callback, and five-attack max-hit
debuff refresh. Its separate contract is recorded in
[`combat-a07-blue-dragon-water-proc.md`](combat-a07-blue-dragon-water-proc.md).
`EarthDragonSlowProc` centralizes only the complete-set gate, chance and damage
draws, event-owned auxiliary true-damage callback, and five-attack attack-speed
debuff refresh. Its separate contract is recorded in
[`combat-a07-earth-dragon-slow-proc.md`](combat-a07-earth-dragon-slow-proc.md).
`RedDragonFireProc` centralizes only the complete-set gate, chance and damage
draws, event-owned auxiliary true-damage callback, and five-attack defense
debuff refresh. Its separate contract is recorded in
[`combat-a07-red-dragon-fire-proc.md`](combat-a07-red-dragon-fire-proc.md).
`BlackDragonBreathFollowup` centralizes only the complete-set and exact-marker
gate, inclusive payload draw, and event-owned auxiliary true-damage callback.
Poison chance/state, marker ownership, and shared Black/KBD presentation remain
event-owned. Its separate contract is recorded in
[`combat-a07-black-dragon-breath-followup.md`](combat-a07-black-dragon-breath-followup.md).
A07.5 remains open for separately characterized one-family branches.

Move only an effect whose melee/projectile policies are proven identical into
a shared executor. Preserve RNG call count/order, zero-hit eligibility,
mitigation, presentation, contribution, lifesteal, descendants, and death
short-circuiting. Re-run the full gate after each family and revert/stop on any
behavior delta.

Owned boss/summon effects and delayed spells remain content-owned until a
separate branch proves a common contract. DoT ownership remains A08.

## Verification and acceptance

- The A07.1 compiled catalog fixture asserts all 39 exact keys, five family counts,
  lookup round trips, immutability, and explicit rejection of 32 as a total
  catalog capacity. The A07.2 fixture separately asserts 71 semantic keys,
  complete immutable metadata, exact counts for all nine phases, and planning
  budgets with no runtime authority.
- Existing A05 fixtures continue to execute the production call sites and
  assert unchanged stable keys, styles, contribution, mitigation, ordering,
  death, and callback behavior.
- The authoritative combat gate passes 94 scenarios, including the compiled
  A07.3 view/filter/revalidation, A07.4 traversal/RNG, and first six A07.5
  shared proc contracts for Ogre Stagger, Baby Dragon smoke, Blue Dragon water,
  Earth Dragon slow, Red Dragon fire, and the Black Dragon breath follow-up.
- No test class enters production artifacts; core/plugin builds and changed-code
  analysis remain required before handoff.
- This server-only identity refactor changes no packet, visual, animation,
  timing, or gameplay behavior, so no private visual acceptance is required.
