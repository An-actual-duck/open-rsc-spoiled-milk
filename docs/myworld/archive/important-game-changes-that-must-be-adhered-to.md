# Important Game Changes That Must Be Adhered To

This file is the authoritative high-level ruleset for `MyWorld`.

Before making progression, shop, drop, equipment, crafting, or cleanup changes,
check this file first. If a lower-level tasklist or audit note conflicts with
this file, this file wins until it is intentionally updated.

## Core Tier Rules

- Standard families now follow a `10`-tier baseline.
- `Dragon` and similar explicit capstone lines are tier `11` exceptions.
- `Rune` is now tier `10`, not the old tier `6`.
- If a source previously sold or awarded the highest normal tier, it should
  still reach the highest normal tier now.
- For standard shop support, normal shops should usually cap at tier `6`.

## Non-Standard Legacy Materials

- `Black` is not a standard progression tier.
- `White` is not a standard progression tier.
- `Black` and `White` should not appear in standard shops or standard tier
  progression.
- `Black` and `White` may be reused later for specialty or god-aligned content.

## Removed Or Retired Standard Equipment Families

- Standard `Chain` equipment is gone from the game.
- Standard `Medium helms` are gone from the game.
- Standard `Square shields` are removed from active progression and should stay
  dormant unless intentionally repurposed later.
- `Square shield` and `Medium helm` counterparts may still exist for explicit
  special-case exceptions such as some tier `11` dragon content.
- `Metal skirts` are going away from standard progression.
- Where male/female visual differences are needed, use sprite/body-type swaps
  rather than separate standard progression items.

## Shop Rules

- Standard armor and weapon shops should sell standard progression only.
- Standard shops should not treat `Black`, `White`, `Chain`, `Medium helm`,
  `Square shield`, or `Metal skirt` lines as part of the active normal ladder.
- Old rune-premium shops should remain rune-cap shops where appropriate.
- Shops that were not previously top-end shops should usually cap at tier `6`.
- Only magic shops should sell clothing-type magic gear:
  - hats
  - robe tops
  - robe bottoms
- Tailor-class shops should sell:
  - `cloth`
  - base `leather`
  - `needle`
  - `thread`
  - a small authored set of early leather gear
- Maintain explicit quest functionality when a shop is also a quest compatibility
  source.
- Generic clothing is going away.
- Do not expand or redesign clothing shops casually; treat that as an authored
  pass, not incidental cleanup.

## Material-Family Rules

- Leather is not a ranged-armor lane.
- Cloth is not a magic-armor lane by default.
- Armor is defensive gear, not style-specific combat armor.
- Leather gear belongs to `Crafting` and material progression.
- Cloth gear belongs to `Crafting` plus later magic/prayer specialization work.

## Magic Gear Rules

- Standard staff progression is the wood-tier staff ladder.
- Elemental identity comes from enchanting/attunement, not from the old staff
  progression shape.
- Battlestaff/orb progression is retired from active standard progression.
- Named specialty staves are handled as special cases, not as the normal staff
  ladder.

## Documentation Rules

- When an item family is removed, replaced, or repurposed, record it in
  [retired-replaced-and-repurposed-items.md](/home/justin/Core-Framework/docs/myworld/retired-replaced-and-repurposed-items.md).
- When a cleanup or distribution pass is planned, align it with this file
  first, then update the relevant tasklist/audit docs.
- If an implementation pass discovers that this file is missing a rule, update
  this file instead of burying the decision only in a tasklist.
