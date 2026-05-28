# Standard Equipment Checklist

This checklist breaks standard non-jewelry equipment into:
- what is already explicitly covered
- what is only partially covered
- what still appears to lean on fallback behavior or missing intentional values

It is ordered by practical implementation priority, not by every item id.

## Status Key

- `[todo]` not started
- `[doing]` in progress
- `[done]` intentionally mapped already
- `[partial]` some coverage exists, but the family is not fully intentional yet

## 1. Core Melee Weapons

- `[done]` Daggers
- `[done]` Shortswords
- `[done]` Longswords
- `[done]` Scimitars
- `[done]` Maces
- `[done]` Axes
- `[done]` Battleaxes
- `[done]` Spears
- `[done]` 2h swords

Notes:
- The main bronze-to-rune core lines are already explicitly overridden in
  [ItemDefsMyWorld.json](/home/justin/Core-Framework/server/conf/server/defs/ItemDefsMyWorld.json).
- Weapon speed identity is also in place for the core melee families.

## 2. Core Metal Armor

- `[done]` Medium helms
- `[done]` Full helms
- `[done]` Chain bodies
- `[done]` Plate bodies
- `[done]` Plate legs
- `[done]` Square shields
- `[done]` Kite shields

Notes:
- The metal armor baseline is already mapped into the new melee/ranged defense
  model.
- The lighter-vs-heavier sidegrade rules are already in place for:
  - medium helm
  - chainbody
  - square shield

## 3. Core Ranged Weapons And Ammo

- `[done]` Shortbows
- `[done]` Longbows
- `[done]` Crossbows
- `[done]` Arrows
- `[done]` Crossbow bolts
- `[done]` Darts
- `[done]` Throwing knives

Notes:
- The first explicit ranged offense pass exists.
- Poison-capable thrown/ammo lines were also given a first pass.

## 4. Core Magic Weapons And Robes

- `[done]` Basic staves
- `[done]` Battlestaves
- `[done]` Iban / god-staff line
- `[partial]` Wizard hats and robe basics
- `[partial]` Wider robe-family coverage

Notes:
- The basic caster weapon line has explicit magic-offense coverage.
- Robe coverage exists, but it is still more piecemeal than melee armor.

## 5. Core Leather Armor

- `[done]` Leather basics and split melee/ranged defense conversion
- `[done]` Explicitly reject a separate ranged-armor path.
- `[todo]` Continue leather, cloth, and metal armor as defensive material
  families rather than combat-style armor lanes.

Why this is next:
- Ranged offense has a usable first pass.
- Armor is not ranged, melee, or magic armor in MyWorld. Armor only contributes
  defensive stats, plus occasional attack-speed modifiers on specific pieces.

## 6. Broader Cloth / Robe Coverage

- `[todo]` Broader cloth and robe coverage remains future production work.
- `[todo]` Existing robe pieces can remain compatibility data until that wider
  production path is designed.

Why this is next:
- Magic has weapon coverage, but cloth/robe defensive coverage is still less
  complete than metal and leather.
- A lot of robe-type items are likely still relying on name-based fallback
  classification rather than explicit values, but the full fix now depends on
  future production planning.

## 7. Standard Special-Case Weapons

- `[partial]` Poison dagger and spear variants
- `[partial]` Specialty melee one-offs already touched during the first pass
- `[todo]` Warhammer-family audit
- `[todo]` Halberd-family audit if present/usable
- `[todo]` Other standard wieldables that do not fit the core families cleanly

Why this is later:
- The core progression lines matter more for baseline combat feel.
- These items should be mapped after the main defensive armor family gaps.

## 8. Standard Special-Case Armor / Rewards

- `[done]` Amulet of accuracy remap
- `[todo]` Audit non-jewelry reward equipables that still assume legacy combat
  stats
- `[todo]` Audit standard equipables with unusual slot/bonus combinations

## 9. Still-Dormant Or Separate Systems

- `[partial]` Old enchanted gem amulet defs remain as dormant compatibility data
- `[partial]` Legacy dragonstone compatibility items remain to be reviewed
- `[todo]` Crowns remain a separate later audit

## Recommended Next Order

1. Finish dormant legacy jewelry cleanup.
2. Continue any remaining caster-gear cleanup in
   [magic-equipment-tasklist.md](/home/justin/Core-Framework/docs/myworld/magic-equipment-tasklist.md),
   but the basic staff ladder and its equip requirements are already in place.
3. Keep armor work framed as material-family defensive progression, not ranged,
   melee, or magic armor.
4. Audit standard special-case weapons like warhammers and other one-offs.
5. Only after that, move into crown redesign.
