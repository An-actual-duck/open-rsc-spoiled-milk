# MyWorld Prayer Rework

This document pins the accepted prayer direction before runtime conversion.

## Core Model

- Prayer changes from a drain-over-time pool to an allocation pool.
- Activating a prayer reserves its point cost while the prayer is active.
- Turning a prayer off refunds its reserved points.
- There are no prayer level thresholds; point availability is the gate.
- Equipment adds prayer allocation points while worn instead of slowing drain.
- If worn equipment changes and the player drops below the active allocation
  requirement, prayers deactivate from the highest tier downward until the
  remaining active set fits.
- Switching god lines at an altar deactivates all prayers and persists the new
  line through logout.
- The current server XP multiplier should eventually be removed and replaced by
  in-game XP multipliers such as skilling prayers.

## God Lines

Each god line has 15 prayers: 5 offense, 5 defense, and 5 skilling.

- Zamorak: melee offense, ranged defense, smithing XP.
- Saradomin: magic offense, melee defense, enchanting XP.
- Guthix: ranged offense, magic defense, crafting XP.

Combat prayers use these five tiers:

| Tier | Prefix | Cost | Effect |
| --- | --- | ---: | ---: |
| 1 | Weak | 3 | 5% |
| 2 | Lesser | 6 | 10% |
| 3 | Plain | 10 | 15% |
| 4 | Strong | 15 | 20% |
| 5 | Greater | 21 | 25% |

All combat tiers in a set reserve 55 points. Their raw additive value is 75%,
but the final combat offense or defense contribution is capped at 60%.

Combat prayer names follow:

- Offense: `Weak Melee Power`, `Strong Magic Power`, etc.
- Defense: `Weak Ranged Protection`, `Strong Melee Protection`, etc.

Skilling prayers use these five tiers:

| Tier | Prefix | Cost | Effect |
| --- | --- | ---: | ---: |
| 1 | Weak | 2 | 10% |
| 2 | Lesser | 8 | 15% |
| 3 | Plain | 20 | 20% |
| 4 | Strong | 35 | 25% |
| 5 | Greater | 55 | 30% |

All skilling tiers in a set reserve 120 points and stack to a 100% XP bonus.

## Altar Mapping

Logical altar identities are preferred first. The world data should use explicit
god altar objects rather than hiding god identity behind coordinate checks.

- Saradomin: monk altar `200` and the standard altar object `19`.
- Zamorak: altar ids `144`, `296`, `625`, and `939`.
- Guthix: altar id `235`.

Each god should retain at least one convenient reachable altar. The first
runtime pass should use `PrayerCatalog.getGodLineForAltar(...)` rather than
duplicating altar ids in plugins.

## Runtime Wiring

The new catalog contains 45 prayers. The live UI should expose a 15-slot current-book UI and swap that visible book when the player changes god lines at
an altar.

Initial implementation should wire systems to the catalog in this order:

1. Persist selected god line on the player. Done via player cache key
   `myworld_prayer_book`.
2. Replace altar recharge behavior with god-line switching. Done for prayer
   altars.
3. Replace prayer drain with allocation checks. Done for activation and
   equipment overflow pruning.
4. Convert combat effects to post-roll offense and defense modifiers.
5. Add skilling XP multiplier hooks for smithing, enchanting, and crafting.
6. Convert prayer equipment bonuses into allocation capacity. Done for capacity
   calculations.
7. Expand or remap the prayer UI to represent the selected 15-prayer line. Done
   for the custom client placeholder-icon grid and book-swap packet.

## Prayer Gear Track

Prayer-aligned equipment is now its own authored content track and should not be
mixed into the normal magic-robe line.

Current accepted direction:

1. Make robe sets for each god.
2. Add a third head-slot piece for robe-style prayer gear later if it still
   feels necessary.
3. Use equipment lines, not unique prayer attacks, for prayer combat identity.
4. Convert god capes into prayer gear and remove magic bonuses from them. Done
   on the first live pass, with capes restored as Mage Arena rewards.
5. Keep monk, brother, abbot, and Monk of Entrana items neutral.

### God Robes

The three prayer robe lines should be:

- Zamorak: red
- Saradomin: blue
- Guthix: green

These are prayer-only robe families. They should not carry the normal magic
bonuses associated with magic robes.

They should be acquired from authored content such as drops or shops, not from
the standard magic-equipment crafting flow.

### Existing Bases

Useful existing robe-family bases already in game:

- `Druids robe top` / `Druids robe bottom`
- `Monks robe top` / `Monks robe bottom`
- `Priest robe` / `Priest gown`
- wool wizard robe pieces and colored variants
- `Zamorak` robe pieces that currently also carry magic identity

Current leaning:

- `Druid` is the strongest thematic base for Guthix because druids are already
  explicitly tied to Guthix in `Druidic Ritual`.
- `Monk` and `Priest` items are more likely to become factionized church gear
  later rather than remaining generic.
- current first-pass factionization should be:
  - `Robe of Guthix` from the old druid robe set
  - `Robe of Saradomin` from the old priest robe/gown set
  - `Monks robe` remains generic prayer gear for now
  - `Robe of Zamorak` remains the Zamorak prayer set, but without magic bonus
- priest NPC presentation should be moved toward Saradomin blue
- druid NPC presentation should be moved toward Guthix green
- monk, brother, abbot, and Monk of Entrana variants stay neutral for now

### First Live Distribution

The first live prayer-robe distribution pass should stay faction-aligned and
avoid inventing generic prayer clothing shops too early.

- `Robe of Guthix`
  - dropped by `Druid`
- `Robe of Saradomin`
  - dropped by `Priest`
  - still retains the existing item ids used by disguise/quest content
- `Robe of Zamorak`
  - dropped by `Monk of Zamorak`
  - shared across the normal, mace, and aggressive Zamorak monk variants

This gives all three god robe lines a real live source now, while leaving later
specialty church/shop distribution for a separate pass.

### Future Affiliation Bonus

Prayer gear should eventually support an affiliation bonus when all of these are
true:

- the player is worshipping a god
- the player is wearing that god's robe set
- the player is wielding that god's staff

The exact bonus is intentionally deferred, but the gear and faction setup
should keep that future check in mind.

### Prayer Equipment Lines

Prayer combat identity should come from equipment families, not from a separate
offensive prayer attack system.

Current accepted lines:

- `Staffs`
  - plain staffs keep their normal magic identity and magic bonus
  - the magic/prayer split happens only at an altar
  - enchanted staffs remain the rune-altar magic path
  - blessed staffs become the prayer-altar prayer path
  - blessing uses the staff directly on a prayer altar
  - blessed staffs are generic by god naming:
    - `Blessed Oak Staff`
    - `Blessed Magic Staff`
    - `Blessed Blood Staff`
  - blessed staff prayer bonus scales by staff tier, up to `+10`
  - the existing named god staffs remain separate tier-11 special items with
    alternate acquisition
- `Maces`
  - should provide `+1 prayer` per tier, up to `+11`
  - if earlier equipment reworks removed that identity, it should be restored
- `Crossbows`
  - should become the ranged prayer-compatible line
  - should provide `+1 prayer` per tier, up to `+11`
- `Paladin shields`
  - the currently unused shield family should become the new standard shield
  - the current kite shield family should be repurposed into `Paladin shield`
  - paladin shields should provide `+1 prayer` per tier
  - their defensive stats should be adjusted slightly to pay for the added
    prayer value

This gives each combat style a prayer-supporting equipment path:

- magic: staffs
- melee: maces and paladin shields
- ranged: crossbows

### Staff Direction

The accepted staff flow is now:

- plain staff crafted
- staff taken to an altar
- altar choice decides whether the staff is enchanted for magic or blessed for
  prayer

This means the base staff should not pre-commit to being a magic-only or
prayer-only item before altar interaction.

The named god staffs remain special high-end items rather than the normal
output of the blessing flow.

### God Capes

God capes are no longer hidden content.

- `Zamorak Cape`
- `Saradomin Cape`
- `Guthix Cape`

Current live direction:

- no magic bonus
- prayer-only stat line
- acquired through the existing Mage Arena god-choice flow
- still mutually restricted against the other gods' named staffs and capes
  unless a later design pass intentionally loosens that rule

### Head-Slot Audit

There is no obvious pre-existing generic hood/cowl prayer item in the current
item set.

Best current head-slot candidates to audit for repurpose:

- `Coif` families
  - many hide-based coifs exist and the silhouette is hood-like
  - drawback: these currently belong to the leather/hide armor families
- `Wool wizard hat` variants
  - many color and rune variants already exist
  - drawback: the silhouette is still explicitly wizard/magic-coded
- `Large helmet` / `hat` / `crown` / `mask` families
  - these are poor thematic fits for neutral robe-style prayer gear

Current conclusion:

- if a clean hood-like prayer silhouette is needed, a new or pallet-swapped
  hood/cowl asset is probably better than reusing monk/priest/wizard hats
- if reuse is required, `coif` is the closest hood-like base visually, but it
  creates overlap with the leather family and should be treated carefully
- this head-slot work is now shelved until the robe/staff/cape/shield direction
  is further along

### Open Design Questions

- whether god-aligned late-game prayers should unlock extra bonuses when the
  player is worshipping the matching god and using the matching gear
- what exact stat tradeoff paladin shields should take for their prayer bonus
- whether the named tier-11 god staffs should gain unique identities later
