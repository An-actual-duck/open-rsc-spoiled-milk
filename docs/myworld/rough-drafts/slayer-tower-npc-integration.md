# Slayer Tower: approved sprites and encounter goals

Status: owner-directed content design draft; documentation only, not implemented.
Updated: 2026-09-17.

This records the owner's tower roster and Slayer gimmick direction. Combat
levels below are initial recommendations, not final stat definitions. Unresolved
mechanics and item ideas are explicitly distinguished from owner decisions.
The [Monster Slayer guild plan](../in-progress-work-plans/monster-slayer-guild-plan.md)
remains authoritative for shipped progression, shops, currencies and kill credit.
This draft is a proposed expansion, not a replacement for that implementation plan.

The related [hide and leather overhaul](slayer-hide-and-leather-overhaul.md)
also covers these new task monsters, existing hide sources, boss assignments,
and grandfathering retired materials/equipment as untradeable.

## Goal and scope

- Convert eight approved NPC sprite sets into working in-game creatures.
- Populate an owner-built Slayer Tower with six floors, one per Slayer tier.
- Include at least one preparation-dependent Slayer gimmick on every floor.
- Sell consumable counters through the Slayer shops. The owner's wording is
  "sold in each shop"; do not silently restrict supplies to a single contact.
- Gimmick counters are nonstackable three-use items, following potion doses.
  `::slayergimmicks` grants administrators one full item per implemented counter,
  not multiple bottles. Add new counters to that test kit as they are implemented.
  Exact stock distribution, prices and payment currencies remain to be decided.
- Without the appropriate counter, affected encounters should be impossible or
  nearly impossible at their intended progression point. With preparation,
  they should become manageable at their ordinary intended combat level.
- Naga is intentionally a conventional encounter, not a special-counter enemy.
- Do not add a new Slayer skill or rewrite existing guild progression implicitly.

## Tower roster and starting combat targets

Tier names follow the existing six challenge tiers, not numerical skill levels.
Floor assignment is owner-selected; combat levels are proposed balancing targets.

| Floor | Challenge tier | NPC | Proposed combat level | Gimmick |
| --- | --- | --- | ---: | --- |
| 1 | Fledgling | Giant frog | 20 | Sticky attack |
| 2 | Adept | Cockatrice | 35 | Paralyzing stony glare |
| 3 | Veteran | Banshee | 50 | Painful scream |
| 3 | Veteran | Naga | 60 | None; normal combat |
| 4 | Elite | Terror dog | 75 | Feeding Frenzy |
| 4 | Elite | Bloodveld | 85 | No counter required; lifesteal and tongue pull |
| 5 | Champion | Dark beast | 105 | Marked lightning / Static discharge wipe |
| 6 | Hero | Abyssal demon | 125 | Sticky Flesh / shared Slime Solvent, eroded by hits |

Levels describe prepared encounters, not the uncountered special's effective
danger. Final stats, health, damage, accuracy, attack intervals and displayed
combat-level calculation need a separate balance pass. In particular, terror
dog's uncountered threat must not redefine its intended baseline as level 200.
Tower coordinates/layout, spawn density, respawns, drops, task eligibility/counts
and rewards have not yet been specified. Floor access follows the rank gates below.

## Owner-designed map and floor gatekeepers

The owner will design the tower in the map editor. Do not generate a replacement
layout or invent final gate/stair coordinates. Integrate NPC placements and access
interactions with the owner's completed layout.

Each floor is gated by a new NPC named **Monster Slayer Associate**. The
associate requires the corresponding Slayer rank **or higher** to enter that
floor. The gatekeeper role is distinct from existing shop associates; do not
repurpose their IDs, shop behavior or current placements.

| Floor | Minimum Slayer rank | Associate armor |
| --- | --- | --- |
| 1 | Fledgling | Bronze |
| 2 | Adept | Iron |
| 3 | Veteran | Steel |
| 4 | Elite | Mithril |
| 5 | Champion | Adamant |
| 6 | Hero | Rune |

Armor follows the guild's established visual tier ladder. Each associate must
be clearly identifiable by that tier's equipment despite sharing the same name.
Create the necessary new NPC definitions/variants with free IDs after an audit;
exact outfits, dialogue and coordinates remain to be chosen.

Access uses authoritative earned guild rank, not point balance, combat level,
equipment worn, or possession of a flavor rank-proof item. Higher ranks retain
access to lower floors. Rank names are player-facing: preserve existing internal
compatibility keys rather than renaming persisted state for these gates.

During integration, select the actual gate interaction (door, stairs, dialogue,
or another mapped passage) with the owner. Enforce access server-side on the
entry route, not just as dialogue flavor; audit alternate entrances and movement
paths. Test below-rank rejection, exact-rank entry and higher-rank entry on every
floor. Provide safe exit/backtracking without trapping a player behind a gate.
New rank requirements do not implicitly authorize map edits or relocation of
existing guild NPCs.

## Owner-selected encounter mechanics

### Giant frog — sticky attack

Ranged spit applies **Slimy Spit**, preventing attacks for ten seconds without
refreshing or repeating its message while active, plus a small poison. **Slime Solvent** protects
against the attack lock only for ten minutes; poison remains unaffected.
Both statuses use the potion HUD.
The frog holds ranged distance without actively retreating. See the
[initial combat test contract](giant-frog-combat-test.md) for tuning and scope.

### Cockatrice — stony glare

Melee-only attacker, level 35, with closely distributed defenses ordered
ranged > melee > magic. Unsuppressed melee swings can apply **Stony Glare**,
including misses: movement is frozen for three server ticks, while attacks are
blocked for ten seconds. Active effects cannot refresh or reannounce, even from
other cockatrices. One hidden immunity tick follows the ten-second effect's
expiry to prevent immediate reapplication. On leg release, say exactly:
"Your legs break free but you can't move your arms". Movement may resume while
the attack lock persists, allowing escape. No facing-direction check.

**Eye Drops** prevent and clear both parts for ten minutes per use. Three-use,
nonstackable bottles use a potion placeholder and the inventory action Apply.
Both named statuses appear in the status HUD. `::slayergimmicks` supplies one
full bottle of each implemented counter. See [combat test contract](cockatrice-combat-test.md).

### Banshee — painful scream

Level 50 positional hybrid: melee when adjacent, otherwise magic at casting
range, holding distance without kiting. Holy projectile and reused side-pose
attack animation. Both attacks carry the wail effect rather than casting a
separate scream. Without **Wax earplugs**, damage is replaced by a uniform roll
from zero through 50% of maximum HP, rounded down, bypassing ordinary defense.
It does not add to normal damage, so one hit cannot kill from full health.
Damaging unprotected hits say: "The Banshee's wail pierces your eardrums and
ripples through your body". Protected attacks use normal melee/magic rolls.

Wax earplugs have three uses, each lasting ten minutes, and use **Insert**.
Examine: "Desolve after 10 minutes". HUD: "Wax earplugs". The test kit grants
one full item. See [combat test contract](banshee-combat-test.md).

### Naga — ordinary encounter

No special Slayer-counter requirement. Approved sword cleave and sword throw
are integrated as separate combat animations. At distance he throws a spinning
iron scimitar (using its inventory/ground icon) and advances between throws.
Adjacent attacks are melee only: simultaneous independent red main-hand and
yellow off-hand rolls, the off-hand maximum halved before defense mitigation.
Very high magic defense, high ranged defense, low melee defense encourage a
choice between safer/slower ranged combat and riskier/faster melee. A shared
cooldown prevents immediate extra swings when changing styles. He shares floor 3
with Banshee. See [Naga combat test contract](naga-combat-test.md).

### Terror dog — Feeding Frenzy

Terror dogs use ordinary melee speed. Each damaging direct hit adds one
independent normal melee roll per other living Terror dog within two tiles of
the attacker, including idle dogs and dogs fighting the player. Extra hits are
yellow, resolve on the same tick, and never trigger another frenzy. Each attacking
dog counts its own neighbors independently, making packs especially dangerous.

Scattering Dog Treats grants ten minutes of player-specific protection from
Feeding Frenzy without changing normal melee damage. Each consumable has three
uses. See [Terror dog combat test contract](terror-dog-combat-test.md).

### Bloodveld — lifesteal and tongue pull

An ordinary encounter requiring no counter item. Level 85, 150 HP, melee offense
80, and 30 in each defense. Melee heals half actual damage dealt, rounded down
and capped at missing HP; zero damage never heals. The five-tile tongue pull
deals no damage and draws the player adjacent through a clear walkable path.
Bloodveld holds range while preparing its pull and always bites when adjacent.
The approved secondary animation uses a thin whip-like tongue.
See [Bloodveld combat test contract](bloodveld-combat-test.md).

### Dark beast — marked lightning

Level 105 melee encounter with a ten-tick lightning charge. Players within four
tiles when charging begins are marked once; leaving does not escape the strike.
One Static discharge wipe removes all current marks. Charging halves incoming
damage and suspends attacks/movement. Lightning uses magic offense 210 and the
`thunder-3` visual. The charge is random with one guaranteed at half health.
See [Dark beast combat test contract](dark-beast-combat-test.md).

### Abyssal demon

Rapid one-tick melee stabs and an immediate radius-one sinking/spike AoE with
four ticks of recovery. Very high ranged/magic defenses favor melee engagement.
Unprotected damaging hits apply **Sticky Flesh**: three seconds unable to move
or perform gameplay actions, then **You manage to break free** and two ticks of
shared reapplication immunity. Traps do not refresh. The existing three-use
**Slime Solvent** protects against this too, but each damaging Abyssal hit
removes five seconds of its remaining timer. It cannot be used while trapped.
See [Abyssal demon combat test contract](abyssal-demon-combat-test.md).

Owner accepted the private Abyssal combat test on 2026-09-20 with no notes.
The next content pass is drops and assembly, not another combat redesign.

## Current drop and icon planning

The [task expansion and pricing comparison](slayer-tower-task-expansion.md)
records the approved addition of all eight monsters to mandatory progression
without removing existing tasks, preservation of every already-completed tier,
draft assignment tips, approved task counts/payouts and unchanged backpack
prices. All six below-backpack, source-tier-only assembly prices are approved.
Neither the task expansion nor the equipment prices are implemented yet.

The [special drops and assembly document](slayer-special-drops-and-assembly.md)
is the reference for the confirmed eight-monster drop roster, material/remains
exceptions, 1–3 residue/feather quantities, rare-component ordering and icon work.
The [initial component pass](slayer-component-implementation.md) now implements
the 16 materials and their drops with temporary icons. Ordinary loot expansion,
equipment assembly and leather production remain pending.
Default hide and bones plus level-appropriate existing ordinary loot are implied
unless explicitly overridden. The [equipment design](slayer-unique-equipment-design.md)
records six rewards, approved recipes and the deferred coating system. Naga's
Serpant's Tail is on hold and must not drop. Eye and gland are fixed at 1/128;
Abyssal Rib is 1/2,000 and single vertebrae are 1/128. The rib is the whip's
intended rare component, and collecting the complete recipe can take longer
than the average 2,000-kill rib wait. Other equipment acquisition targets are
2,000 kills for staff/bow and 1,000 for pendant/dagger. The pendant requires
one Frozen Tear, visually a crystallized tear on a string. Horn is 1/2,000;
tongue, fang and tear are each 1/1,000. Unique rolls are independent and do not
require an active task. Feathers and residue give 1, 2 or 3 with equal chance.
The six equipment assembly currency prices are approved;
"rare" does not automatically mean 1/128, and appropriate parts should reach
1/1,000 or rarer. Do not infer implemented drops from accepted combat tests.

The [equipment requirements](slayer-unique-equipment-design.md#approved-wear-requirements)
are 70 Melee for the whip, 30 Melee for the dagger, 62 Magic for the staff and
54 Ranged for the bow; pendant and shield have no wear requirements. Stat tiers
and assembly-currency tiers remain distinct from these wear requirements.
The [leather audit](slayer-hide-and-leather-overhaul.md) retains tanning/Crafting
and unique set effects, and adds Balrog/Elder Green Dragon only to opt-in
repeatable chances. Neither boss enters mandatory progression.

## Sprite-to-game integration checklist

All eight creatures have owner-approved artwork. This does not establish that
their files have been imported, assigned IDs, or tested by the game runtime.

- Locate exact final exports and preserve them as the approved visual baseline.
- Audit direction/frame ordering, transparency, palette, native scale, anchors,
  ground contact, animation timing and frame bounds before conversion.
- Preserve the frog's three-frame hop instead of imposing a conventional walk.
- Map the cockatrice talon attack, terror dog attack, bloodveld gape/chomp and
  dark beast ram to appropriate runtime sequences; audit banshee attack coverage.
- Preserve naga cleave/throw and abyssal demon stab/sink-spikes as distinct
  attacks. Verify runtime support for selecting second attacks.
- Create or verify separate projectile/effect assets where required; a spit
  wind-up or sword-release frame does not itself supply a missile asset.
- Audit larger attack canvases/offsets without shrinking bodies to fit cells.
- Register NPC definitions, animations, combat behavior and task families in
  the existing systems; reserve IDs only after checking current definitions.
- Validate each direction and attack in motion in the actual client before
  accepting the sprite as runtime-ready. Do not overwrite unrelated NPC art.

## Consumable and encounter integration decisions

Before implementation, settle activation (self-use versus use on NPC), dose or
charge consumption, duration, refresh/stacking, expiry warning, death/logout and
region behavior, tradeability/stacking, shop quantities and typed-currency costs.
Document whether counters affect only their user or the creature for everyone.
Ensure stock/pricing lets players buy protection before attempting its required
encounter; do not create a loop requiring kills of that creature to buy its
first counter. Respect existing multi-currency shop rules unless the owner
explicitly approves a change.

Proposed verification should cover prepared and unprepared fights at each tier,
effect application/expiry, Bloodveld lifesteal and collision-safe pulling,
treat-controlled Feeding Frenzy, prevention/cure of paralysis, scream-counter interaction,
multiplayer/summon ownership, no double consumption, and ordinary Slayer task
credit. Naga and Bloodveld require no special consumable.

## Next design decisions

1. Test the frog's approved slime/solvent rules and tune its initial combat values.
2. Tune Bloodveld lifesteal and pulling; no Bloodveld counter item is required.
3. Dark beast and Abyssal mechanics accepted; component drops are implemented.
   Implement assembly recipes/prices and settle ordinary loot. Component luck
   modifiers now follow the existing rare-table rules.
4. Set shared consumable lifecycle and shop rules, then per-monster values.
5. Audit final sprite exports and runtime integration requirements.
6. Approve combat tuning, tower access/spawns and assignment/reward additions.

No gameplay implementation, asset import, map mutation, database change, release
or live-server action is part of this documentation pass.
