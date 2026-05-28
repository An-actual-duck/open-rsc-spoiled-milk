# Altar Enchantment And Conversion Plan

This document is the implementation record and rule reference for the live
altar pass. It covers rune-altar enchanting costs, two-part Enchanting level
gates, and god-aligned knight equipment conversion.

## Live Rules

- Make altar enchanting consistently rune-fueled.
- Remove metal bars from enchanted cloth upgrade costs.
- Keep cloth armor upgrades as a one-tier-at-a-time process.
- Keep staff enchanting as one flat attunement action, with cost based on the
  staff's wood tier.
- Keep jewelry on the current altar-enchanting flow, but add the new matching
  rune cost.
- Enforce two independent Enchanting gates for every altar-enchanted item:
  altar access and item tier.
- Make god-aligned knight conversion free, steel-only, and one-way.

## Shared Level Gates

Every rune-altar enchantment must check two independent requirements.

The altar gate checks whether the player has enough Enchanting level to use the
altar at all.

Current altar gate:

| Altar | Required Enchanting |
| --- | ---: |
| Air | 1 |
| Water | 1 |
| Earth | 1 |
| Fire | 1 |
| Life | 1 |
| Mind | 8 |
| Body | 15 |
| Chaos | 22 |
| Cosmic | 30 |
| Nature | 38 |
| Law | 46 |
| Death | 54 |
| Soul | 62 |
| Blood | 70 |

The item gate checks whether the player has enough Enchanting level for the
specific item tier being created or upgraded.

This means both of these examples must be blocked:

- a low-level player using a high altar, even for a low-tier item
- a player using an entry altar on an item whose tier is above their
  Enchanting level

Example: a player may have enough Crafting level to make a high-end staff, but
still must meet that staff tier's Enchanting requirement before they can attune
it, even at an elemental altar.

Implementation note: the code may still refer to the old internal skill key as
`RUNECRAFT`; the player-facing behavior and messages should treat this as
Enchanting.

## Item Tier Gates

Cloth armor and staves use the ten-step equipment tier curve:

| Tier | Required Enchanting |
| ---: | ---: |
| 1 | 1 |
| 2 | 8 |
| 3 | 15 |
| 4 | 22 |
| 5 | 30 |
| 6 | 38 |
| 7 | 46 |
| 8 | 54 |
| 9 | 62 |
| 10 | 70 |

Jewelry uses the five-step gem curve:

| Jewelry Tier | Base Gem | Required Enchanting |
| ---: | --- | ---: |
| 1 | Sapphire | 8 |
| 2 | Emerald | 18 |
| 3 | Ruby | 32 |
| 4 | Diamond | 48 |
| 5 | Dragonstone | 58 |

## Rune Costs

All normal altar-enchanting costs use the rune matching the altar.

Examples:

- Fire altar costs fire runes.
- Blood altar costs blood runes.
- Life altar costs life runes.

### Cloth Armor

Affected items:

- Wool Hat
- Wool Robe Top
- Wool Robe Bottom
- Wool Gloves
- Wool Boots

Rules:

- Base wool pieces can be enchanted at any rune altar to become tier 1 for that
  altar.
- Existing enchanted cloth can only be upgraded at its matching altar.
- Each action advances the item by exactly one tier.
- A tier cannot be skipped.
- The target tier controls the item level gate and the rune cost.
- Metal bars are no longer part of the upgrade cost.

Cost:

```text
target tier * 100 matching altar runes
```

Examples:

- tier 1 Fire Wool Robe Top: 100 fire runes
- tier 2 Fire Wool Robe Top upgrade: 200 fire runes
- tier 10 Fire Wool Robe Top upgrade: 1000 fire runes

### Staves

Affected items:

- the full wood-tier staff line from tier 1 through tier 10

Rules:

- Staves are attuned in one action.
- Staff enchanting does not upgrade one rank at a time.
- The staff's wood tier controls the item level gate and rune cost.
- The altar controls the output rune identity.
- Cosmic runes are no longer part of normal staff attunement unless a later
  design explicitly adds them back.

Cost:

```text
staff tier * 200 matching altar runes
```

Examples:

- tier 1 staff at Air altar: 200 air runes
- tier 5 staff at Nature altar: 1000 nature runes
- tier 10 staff at Blood altar: 2000 blood runes

### Jewelry

Affected items:

- amulets
- necklaces
- rings

Rules:

- Jewelry keeps the existing altar-enchanting flow.
- The gem tier controls the item level gate and rune cost.
- The altar controls the output enchantment family.
- Law jewelry recharge behavior is not changed by this plan; this cost applies
  to initial altar enchantment unless a separate recharge rule is specified.

Cost:

```text
gem tier * 50 matching altar runes
```

Examples:

- sapphire jewelry at Air altar: 50 air runes
- ruby jewelry at Chaos altar: 150 chaos runes
- dragonstone jewelry at Soul altar: 250 soul runes

## God-Aligned Knight Conversion

Black, white, and grey knight equipment are god-aligned steel counterparts.

God mapping:

- Black Knight equipment: Zamorak
- White Knight equipment: Saradomin
- Grey Knight equipment: Guthix

Conversion rules:

- Conversion has no resource cost.
- Only ordinary steel equipment can be blessed.
- Conversion is one-way.
- God-aligned knight equipment cannot be converted into another god's line.
- The god altar determines the output line.

Examples:

- steel equipment used on a Zamorak altar becomes Black Knight equipment
- steel equipment used on a Saradomin altar becomes White Knight equipment
- steel equipment used on a Guthix altar becomes Grey Knight equipment
- Black Knight equipment cannot be used on a Saradomin altar to become White
  Knight equipment

The prayer bonus and any god-specific benefit on these items should still only
apply while the player is worshipping the matching god.

## Implementation Delta

Implemented in the current first pass:

- Cloth upgrade costs were changed from metal bar plus old rune cost to
  `target tier * 100` altar runes.
- Staff attunement was changed from the old flat rune/cosmic cost to
  `staff tier * 200` altar runes.
- Jewelry enchanting was changed from the old small tier cost to
  `gem tier * 50` altar runes.
- The two-part level gate is guarded for cloth, staves, and jewelry.
- Black Knight, White Knight, and Grey Knight conversion routes are implemented
  as free, steel-only, and one-way.

## Validation Coverage

`tests/myworld/test-magic-enchanting-costs.py` and
`tests/myworld/test-altar-enchantment-conversion.py` guard:

- low Enchanting level cannot use a high altar, even with a low-tier item
- low Enchanting level cannot enchant a high-tier item, even at a level 1 altar
- cloth upgrades consume only matching altar runes and no metal bars
- cloth upgrades advance exactly one tier
- staves consume `tier * 200` matching altar runes and no cosmic runes
- jewelry consumes `tier * 50` matching altar runes
- ordinary steel converts to the matching god knight equipment at the matching
  god altar
- black, white, and grey knight equipment cannot convert into each other
- god-aligned knight prayer bonuses only apply while worshipping the matching
  god
