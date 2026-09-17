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
| 4 | Elite | Terror dog | 75 | Rip apart |
| 4 | Elite | Bloodveld | 85 | Lifeforce drain |
| 5 | Champion | Dark beast | 105 | Undecided |
| 6 | Hero | Abyssal demon | 125 | Undecided |

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

Ranged spit applies **Slimy Spit**, preventing attacks for ten seconds and
refreshing on reapplication, plus a small poison. **Slime Solvent** protects
against both frog effects for ten minutes. Both statuses use the potion HUD.
The frog holds ranged distance without actively retreating. See the
[initial combat test contract](giant-frog-combat-test.md) for tuning and scope.

### Cockatrice — stony glare

Stony glare paralyzes the player. Eye drops are the consumable solution.
Protection duration, whether drops prevent or cure paralysis (or both), which
actions paralysis blocks, and the attack trigger/cadence remain undecided.
Do not assume a facing-direction check or invent a permanent immunity item.

### Banshee — painful scream

The scream inflicts massive damage that ordinary prevention cannot solve,
analogous to fighting a dragon without anti-fire protection. Its intended
exception is the dedicated consumable: ear drops or temporary wax ear plugs.
Choose the item later. Exact protection duration, residual damage, scream
frequency, and interaction with each defensive mechanic need explicit rules;
"unpreventable" must not accidentally bypass its own Slayer counter.

### Naga — ordinary encounter

No special Slayer-counter requirement. Approved sword cleave and sword throw
remain available attacks; projectile integration is separate from sprite approval.
It shares floor 3 with the gimmick-bearing banshee.

### Terror dog — rip apart

Without dog treats, attacks deal high damage at the fastest supported attack
tick rate, making the creature comparable in danger to level 200 or higher.
Using dog treats brings its combat threat back in line with the intended level.
Determine the actual minimum supported interval from the combat system rather
than assuming a number of milliseconds or allowing multiple attacks per tick.
Treat delivery, effect duration, damage/stat reduction, restoration of ordinary
attack speed, and per-player versus per-NPC scope remain to be settled.

### Bloodveld — lifeforce drain

The bloodveld has overwhelming lifesteal, with an intended tuning range of
200–500% and a positive minimum heal even on a zero-damage hit. This should make
an unprepared fight nearly impossible rather than merely a longer normal fight.
The final multiplier, minimum, healing trigger, eligible attacks, damage basis,
health cap and counter suppression amount remain undecided.

A possible formula for later approval is:
`heal = max(minimumHeal, multiplier * eligibleDamage)` on a qualifying attack,
with rounding and maximum-health behavior explicitly defined. This is not an
approved implementation formula; a miss versus a resolved zero hit also needs
to be distinguished deliberately.

The counter should retain the player's lifeforce. The owner's initial idea is
a smudging stick; the item is not finalized. Suggested fiction: a consumable
**binding incense stick** temporarily anchors the user's lifeforce, preventing
the bloodveld from drawing sustenance. "Smudging stick" can remain the name if
preferred. Alternative: a **soul-sealing draught**, if self-use and a timed buff
read more clearly than burning incense. These are proposals, not new stock.

### Dark beast and abyssal demon

Both need an owner-selected gimmick and shop consumable. Leave these open.
The dark beast's horn ram and the abyssal demon's blade stab and sinking/spike
AoE are approved animation concepts, not automatically their Slayer gimmicks.
Floors 5 and 6 are not design-complete until their counter mechanics are chosen.

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
effect application/expiry, zero-hit bloodveld healing, treat-controlled damage
and attack rate, prevention/cure of paralysis, scream-counter interaction,
multiplayer/summon ownership, no double consumption, and ordinary Slayer task
credit. Naga must remain playable without any special consumable.

## Next design decisions

1. Test the frog's approved slime/solvent rules and tune its initial combat values.
2. Choose banshee item and bloodveld retainer item.
3. Design dark beast and abyssal demon gimmicks/counters.
4. Set shared consumable lifecycle and shop rules, then per-monster values.
5. Audit final sprite exports and runtime integration requirements.
6. Approve combat tuning, tower access/spawns and assignment/reward additions.

No gameplay implementation, asset import, map mutation, database change, release
or live-server action is part of this documentation pass.
