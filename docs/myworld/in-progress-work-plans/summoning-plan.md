# MyWorld Summoning Plan

This document is the source of truth for the current Summoning implementation
and the next tuning pass.

It is now scoped around the first full summon book: a small level 1-70
catalog, a real Summoning skill, and the already-proven combat/support/utility
runtime.

## Goals

- Maintain a real summoning system instead of one-off scripted companions.
- Keep `Wolf` and `Hellhound` leather set bonuses on the summon runtime.
- Support combat, support, and charge-limited utility summons.
- Keep the first 14-summon catalog spaced from level `1` through level `70`.

## Core Rules

- A summon consumes runes and raw materials.
- Every summon requires the hidden `Life rune`.
- Most creature-based combat and support summons require bones equivalent to the
  creature. Utility summons do not require bones.
- Additional rune costs follow these intended rules:
  - `Body runes` are used for weaker/basic summons.
  - Elemental runes are used when the creature has a matching element.
  - `Nature runes` are used for medium creature summons.
  - `Law runes` are used for utility summons.
  - `Soul runes` are used for high-end summons.
  - Other runes may be mixed in when thematically appropriate.
- Summons may also require physical materials such as hides or other
  creature-themed inputs.
- Only `one` summon may be active per player per source.
- Normal/manual summoning and armor-effect summoning are separate sources, so
  armor summons can coexist with a normal summon if the system supports it.
- Manual summoning has a `5 second` charge time.
- The charge is interrupted if the player takes damage before it completes.
- Resource costs are consumed only after the charge completes successfully.

## Skill Progression

The first 14 summons are ordered below and should be evenly spaced across
levels `1-70`.

| Level | Summon | Role | Cast XP |
| ---: | --- | --- | ---: |
| 1 | Broodling Spider | Combat, melee | 15 |
| 7 | Mischief Imp | Support | 30 |
| 12 | Loot Goblin | Support | 45 |
| 14 | Ironhide Bear | Combat, melee | 55 |
| 20 | Sacred Unicorn | Support | 80 |
| 26 | Duskwind Bat | Combat, ranged | 110 |
| 33 | Pack Rat | Utility | 145 |
| 39 | Bound Battleaxe | Combat, melee | 185 |
| 45 | Mourning Unicorn | Support | 230 |
| 51 | Restless Shade | Combat, ranged plus fear | 280 |
| 55 | Guard Dog | Support | 310 |
| 58 | Delivery Camel | Utility | 335 |
| 64 | Astral Wraith | Combat, magic | 395 |
| 70 | Abyssal Demon | Combat, melee or magic | 460 |

The exact levels can be tuned, but this should remain the baseline progression
until a larger summon catalog exists.

Cast XP is awarded only after a summon charge completes successfully and costs
are consumed.

## Naming Direction

Summons use distinct player-facing names rather than directly copying their NPC
source names. `Pack Rat` is the model: it still uses a rat NPC source visually
and mechanically, but the summon reads as its own ability.

Summon icon files live in `dev/myworld/assets/sprites/UI/summon` and are named
from the summon display name in lowercase with spaces converted to dashes:
`broodling-spider.png`, `mischief-imp.png`, `loot-goblin.png`,
`ironhide-bear.png`, `sacred-unicorn.png`, `duskwind-bat.png`, `pack-rat.png`,
`bound-battleaxe.png`, `mourning-unicorn.png`, `restless-shade.png`,
`delivery-camel.png`, `astral-wraith.png`, and `abyssal-demon.png`. Guard Dog
currently uses the client's `GD` initials fallback because no purpose-made
`guard-dog.png` has been supplied.

## Summon Roles

### Attacker

- Higher damage.
- Lower health.
- Assists the player in combat against the player's current target.

### Tank

- Lower damage.
- Higher health.
- Protects the player either by taking aggro or by absorbing/transferring
  damage that would otherwise hit the player.
- Damage transfer is the preferred first implementation because it is less
  likely to disrupt existing multi-attacker combat.

### Support

- Does not attack.
- Does not take damage.
- Provides passive player effects.
- Lasts `1 minute`.
- If the player has `Life runes`, consumes `1 Life rune` each minute instead
  of despawning.
- Despawns when dismissed, replaced, or the owner logs out/dies.

### Utility

- Does not attack.
- Does not take damage.
- Appears to perform a limited number of successful non-combat services.
- Intended utility actions include:
  - selecting an inventory item and converting all matching inventory items to
    notes
  - depositing a limited number of selected inventory items into the bank
- Failed or cancelled selections do not consume a service.
- Persists until its final successful service, explicit dismissal, replacement,
  owner death, or owner logout.

## Summon Lifetime Rules

### Combat Summons

- Combat summons persist until they die, are replaced, or their owner logs
  out/dies.
- Armor-based combat summons can die and then enter a cooldown before returning.
- They should despawn cleanly when:
  - the player logs out
  - the player dies
  - the summon dies
  - the summon is replaced by a new summon

### Utility Summons

- Utility summons remain through unsuccessful selections and disappear after
  their final successful service.
- They also disappear on explicit dismissal, replacement, owner death, or
  owner logout.
- Pending item selections are cleared when the summon is dismissed or replaced,
  so a stale menu cannot spend a replacement summon's service.

## First Summon Catalog

### Broodling Spider

- level: `1`
- role: combat, melee
- cost: `1 Life rune`, `1 Body rune`
- material: no bones
- health: `2`, plus `1` every `10` Summoning levels beyond requirement
- max damage: `1`, plus `1` every `24` Summoning levels beyond requirement
- damage absorption: `30%`
- trait: none; introductory summon

### Mischief Imp

- level: `7`
- role: support
- cost: `1 Life rune`, `1 Body rune`, `1 Ashes`
- effect: enemies will not initiate attacks against the player
- restriction: if the player attacks an enemy, the imp despawns

### Loot Goblin

- level: `12`
- role: support
- cost: `1 bones`, `1 Life rune`, `1 Body rune`, `1 Mind rune`
- effect: collects owner-bound dropped stackable items into the player's inventory
- restriction: if no slot or matching stack is available, the item remains on the ground

### Ironhide Bear

- level: `14`
- role: combat, melee
- cost: `1 Life rune`, `2 Body runes`, `1 bones`
- health: `14`, plus `1` every `8` Summoning levels beyond requirement
- max damage: `2`, plus `1` every `30` Summoning levels beyond requirement
- damage absorption: `60%`
- trait: `Tank`; absorbs twice the standard summon damage share

### Sacred Unicorn

- level: `20`
- role: support
- cost: `1 Life rune`, `1 Body rune`, `1 Cosmic rune`, `1 bones`
- effect: `+10` prayer points

### Duskwind Bat

- level: `26`
- role: combat, ranged
- cost: `1 Life rune`, `1 Air rune`, `1 Body rune`, `1 Nature rune`,
  `1 Bat bones`
- health: `7`, plus `1` every `9` Summoning levels beyond requirement
- max damage: `3`, plus `1` every `18` Summoning levels beyond requirement
- damage absorption: `30%`
- trait: `Vampirism`; heals the owner for damage dealt

### Pack Rat

- level: `33`
- role: utility
- cost: `1 Life rune`, `2 Law runes`, `1 Body rune`, `1 Nature rune`
- services: `4`
- effect: each successful service converts all matching selected unnoted
  inventory items into certs
- service XP: `75 + 5` per converted item, capped at `150` displayed XP
- maximum: `600` displayed service XP, or `745` including the `145` cast XP
  (`1,800` and `2,235` respectively at the normal `3x` My World rate)

### Bound Battleaxe

- level: `39`
- role: combat, melee
- cost: `1 Life rune`, `2 Cosmic runes`, `1 Iron battleaxe`
- health: `8`, plus `1` every `10` Summoning levels beyond requirement
- max damage: `6`, plus `1` every `16` Summoning levels beyond requirement
- damage absorption: `25%`
- trait: `Relentless`; `15%` chance to make a second hit up to half its max
  hit

### Mourning Unicorn

- level: `45`
- role: support
- cost: `1 Life rune`, `1 Body rune`, `1 Cosmic rune`, `1 bones`
- effect: automatically buries prayer drops such as bones, big bones, bat
  bones, and similar drops
- reward: awards double prayer XP for automatically buried drops

### Restless Shade

- level: `51`
- role: combat, ranged plus passive fear
- cost: `2 Life runes`, `3 Cosmic runes`, `1 Soul rune`, `1 Ashes`
- health: `9`, plus `1` every `9` Summoning levels beyond requirement
- max damage: `4`, plus `1` every `18` Summoning levels beyond requirement
- damage absorption: `20%`
- trait: `Fear`; `20%` chance to prevent an enemy attack against the owner

### Guard Dog

- level: `55`
- role: support
- cast XP: `310`
- cost: `2 Life runes`, `2 Body runes`, `1 Soul rune`, `1 bones`
- effect: reserves one active PvM enemy for the player and safely prevents
  additional NPCs from acquiring or retaining combat with the player
- effect: suppresses player-originated secondary and area damage while leaving
  the selected primary target's normal hit intact
- multicannon: remains locked to one valid enemy while Guard Dog is active;
  cannons never target summoned NPCs
- visual: reuses `GUARD_DOG_SINCLAIR_MANSION` (`748`), which has the existing
  dog sprite without the attackable/aggressive behavior of the wilderness
  guard-dog definition
- artwork: no dedicated summon icon currently exists, so the summon menu uses
  its built-in `GD` initials placeholder instead of inventing replacement art

### Delivery Camel

- level: `58`
- role: utility
- cost: `1 Life rune`, `2 Body runes`, `2 Law runes`, `2 Nature runes`
- services: `2`
- effect: each successful service deposits one selected inventory slot or stack
  into the owner's bank
- service XP: `225` displayed XP
- maximum: `450` displayed service XP, or `785` including the `335` cast XP
  (`1,350` and `2,355` respectively at the normal `3x` My World rate)

### Astral Wraith

- level: `64`
- role: combat, magic
- cost: `2 Life runes`, `4 Cosmic runes`, `1 Soul rune`, `1 bones`
- health: `10`, plus `1` every `8` Summoning levels beyond requirement
- max damage: `7`, plus `1` every `14` Summoning levels beyond requirement
- damage absorption: `25%`
- trait: `Spell Echo`; `15%` chance to deal a second magic hit up to `3`

### Abyssal Demon

- level: `70`
- role: combat, melee or magic
- cost: `3 Life runes`, `1 Blood rune`, `1 Soul rune`, `1 Demon ash`
- health: `18`, plus `1` every `7` Summoning levels beyond requirement
- max damage: `9`, plus `1` every `12` Summoning levels beyond requirement
- damage absorption: `35%`
- trait: `Hellfire`; `10%` chance for attacks to trigger Hell's Fire, max
  hit `8`, with the tier 2 infernal fire debuff
- behavior: uses both melee and magic-style attacks

## Completed Proof Targets

The first proof implementation has already established the core summon runtime.

### Combat Proof Summon

- `Bear`
  - behavior:
    - summons a bear companion
    - the bear attacks whatever the player attacks
    - enemies do not target the bear directly
    - the bear absorbs part of enemy damage dealt to its owner
    - summoned-bear stats are defined by the summon profile, not by editing the
      base bear NPC
    - persists until it dies, is replaced, or its owner logs out/dies

### Utility Proof Summon

- `Rat`
  - cost:
    - `1 Life rune`
    - `2 Law runes`
    - `1 Body rune`
    - `1 Nature rune`
  - behavior:
    - summons a rat companion
    - interacting with the rat asks the player to select an inventory item
    - the rat converts all matching unnoted inventory items into notes
    - the rat reports its remaining services and despawns after its fourth
      successful conversion
    - cancellation and failed conversion attempts do not consume a service

## Current Implementation Status

- The summon tab contains the first 14-summon catalog in the planned order.
- Server casts are driven by shared summon profiles instead of one-off test
  entries.
- Resource costs are enforced for the first 14 summons.
- Summoning has a `5 second` charge.
- Taking damage during the charge interrupts the summon.
- Costs are consumed after a successful charge, not when the summon is clicked.
- Successful casts award Summoning XP after costs are consumed.
- The on-player summoning animation starts when the charge starts, lasts for the
  charge window, and is cleared early if the charge is interrupted.
- Successful summons play a role-specific arrival animation at the summon
  location. Combat and support summons reveal on the `6th` frame; utility
  summons reveal on the `4th` frame.
- Combat summon health and max hit scale from Summoning level, using each
  summon profile's base values and growth intervals.
- Support and utility summons do not absorb damage.
- Support summons last `1 minute` and begin at `1 Life rune` per minute to
  stay active. Their upkeep rises by `1 Life rune` every `3 minutes` active,
  then recovers by `1 Life rune` for each `1 minute` without a support summon.
- Pack Rat and Delivery Camel persist for `4` and `2` successful services
  respectively; failed actions do not consume services.
- `Summoning` is a persisted MyWorld skill with database columns, stat packet
  fields, and client stat array support.
- The first 14 summons are level-gated by Summoning level.
- `Mischief Imp` blocks hostile NPC aggro while active and despawns when the player
  starts attacking.
- `Mourning Unicorn` auto-buries prayer drops, including bones, bat bones, big
  bones, dragon bones, generic ashes, and demon ash, for double prayer XP.
- `Guard Dog` limits PvM engagement to one enemy, suppresses player area
  attacks and procs, and restores normal targeting immediately when inactive.
- Ranged and magic combat summons use projectile attacks with owner kill/XP
  credit preserved.
- `Abyssal Demon` uses a mixed melee/magic attack pattern.

## Existing Codebase Support

The current codebase already has useful pieces:

- hidden summon-related spell entries in
  `server/src/com/openrsc/server/constants/Spells.java`
- timed NPC spawning helpers in
  `server/src/com/openrsc/server/plugins/Functions.java`
- NPC chase behavior in
  `server/src/com/openrsc/server/model/entity/npc/Npc.java`
- owner-linked kill credit through `relatedMob` in
  `server/src/com/openrsc/server/model/entity/npc/Npc.java`
- existing pet-oriented config and temporary-NPC patterns

These are enough to build a first proper system. They are not enough to skip
system design.

## Implementation Plan

## Phase 1: Summon Framework

Build a shared summon framework rather than special-casing each summon.

Required pieces:

- a `SummonDefinition` model:
  - summon id
  - summon role (`attacker`, `tank`, `support`, or `utility`)
  - summon source (`manual`, `armor`, or future source)
  - rune costs
  - item/material costs
  - duration or resolution rule
  - spawned NPC id
- player summon state:
  - active summon id per source
  - active summon NPC reference per source when a visible NPC exists
  - summon expiry or cooldown per source
- single-active-summon enforcement per source
- shared summon spawn/despawn helpers
- cleanup on:
  - logout
  - player death
  - summon death
  - replacement
  - timeout

## Phase 2: Combat Summon Behavior

Implement the first combat companion behavior using `Bear`.

Required pieces:

- owner linkage from summon NPC to player
- summon-specific profiles:
  - base NPC id and visual
  - summon role and source
  - duration
  - custom attack, defense, ranged, strength, and hits values
  - optional damage cap
  - optional owner-damage absorption percent
- target mirroring:
  - if the player attacks a target, the summon should assist that same target
- assist rules:
  - enemies should not target summons directly
  - tank summons should absorb a portion of damage dealt to their owner instead
    of becoming enemy targets
  - summon should not create combat lock issues
  - summon should not break current multi-attacker combat behavior
- reward rules:
  - summon damage should still resolve kill credit and loot ownership properly
  - player ownership should remain explicit

## Phase 3: Utility Summon Behavior

Implement the first utility summon using `Rat`.

Required pieces:

- summon interaction trigger
- inventory item-selection prompt
- note conversion for all matching unnoted inventory items
- validation rules:
  - item exists in inventory
  - item can be noted
  - inventory items are removed before notes are created
- consume one service only after a successful conversion
- report remaining services after each success
- despawn after the final successful service
- clear stale selections on dismissal, replacement, death, and logout

## Phase 4: Content Entry Path

Summoning is cast from the third subtab in the existing magic/prayer side
panel.

Current UI direction:

- keep the `Magic`, `Prayer`, and `Summon` subtabs in the same panel
- display summons in the same icon-grid style as spells and prayers
- show summon level, cost, and role/effect in the tooltip area
- send summon casts through a custom interface packet, not through dev commands

Remaining work after limited release:

- field-test full catalog behavior and tune numbers/effects from observed play
- supply original summon icons if icon art is restored; the current
  charge/arrival animation assets are wired

Implemented work:

- replaced the temporary four-entry hardcoded client list with the first
  14-summon catalog
- added the persisted `Summoning` skill
- enforce summon level requirements
- enforce resource requirements server-side
- start a `5 second` interruptible charge before costs are consumed and the
  summon appears
- consume resources only after the charge succeeds
- award Summoning XP only on successful casts
- implemented Imp enemy safety
- implemented Black Unicorn auto-bury
- implemented Ghost fear
- implemented ranged/magic summon projectile styles
- implemented Greater Demon mixed melee/magic attacks

## Phase 5: Wolf And Hellhound Integration

Implemented as armor-bound spirit companions:

- full `Wolf` set summons a `Spirit Wolf Companion`
  - invulnerable
  - no resource cost
  - does not absorb damage
  - attacks the player's current target for up to `3` damage
- full `Hellhound` set summons a `Spirit Hellhound Companion`
  - invulnerable
  - no resource cost
  - does not absorb damage
  - attacks the player's current target for up to `6` damage
  - has a `5%` chance per attack to proc `Hell's Fire`

These armor companions use a separate summon slot from manual Summoning skill
summons. They appear when all `5` matching pieces are equipped and fade when
the set is broken.

## Technical Guardrails

- Do not implement summon behavior as scattered special-case NPC scripts.
- Do not let summons bypass the shared combat ownership model.
- Do not allow multiple active manual summons per player.
- Armor-bound spirit companions may coexist with one manual summon because
  their source is equipment rather than a cast.
- Do not leave summoned NPCs or pending selections stranded on logout, death,
  replacement, or explicit dismissal.
- Keep new summon additions narrow and testable; avoid adding broad summon
  content before the first catalog has enough field-test data.

The charged utility model deliberately preserves the existing XP paid per
successful service. This raises the maximum service XP per cast to four times
the old Pack Rat maximum and twice the old Delivery Camel maximum. That is a
balance concern to field-test; this change does not silently introduce a new XP
formula or reduce the established reward.

## Open Design Decisions

These remain tuning/design questions after the first catalog implementation:

- utility summons beyond Pack Rat and Camel
- whether combat summons should follow the player when out of combat
- how much direct control the player has over a summon
- the exact chance for `Fear` to negate an enemy attack
- whether Greater Demon alternates attacks randomly or follows a fixed melee /
  magic pattern

## Recommended Next Order

1. Field-test each summon in level order, checking cost consumption, XP, effect,
   dismissal, replacement, and logout cleanup.
2. Tune summon cost, XP, damage, hits, and traits based on field-test results.
3. Add further visual variants only when an identified presentation need
   justifies them.
