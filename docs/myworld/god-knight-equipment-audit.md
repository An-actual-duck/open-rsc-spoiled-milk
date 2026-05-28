# God Knight Equipment Audit

This audit covers the implemented black, white, and grey god-aligned
steel-equivalent equipment and the remaining NPC placement review.

## Intended Mapping

- Black Knight equipment: Zamorak
- White Knight equipment: Saradomin
- Grey Knight equipment: Guthix

All three equipment lines should use steel-equivalent core combat stats. Their
identity comes from prayer/god alignment, not from being a higher metal tier.

Any prayer bonus or god-specific benefit from these items should only apply
while the player is worshipping the matching god.

## Current NPC State

Relevant knight NPCs already present:

- `Black Knight` `66`: level 46, non-aggressive.
- `Black Knight` `189`: level 46, aggressive, described as a follower of
  Zamorak.
- `Black Knight` `108`: fortress/quest variant.
- `Renegade knight` `277`: level 51, aggressive, uses the black-knight drop
  table.
- `White Knight` `102`: level 56, non-aggressive.
- `Grey Knight` `836`: level 56, non-aggressive MyWorld NPC and Guthix
  equipment source.
- `Knight` `322`: Ardougne knight, level 56.
- `Paladin` `323`, `632`, `633`: level 71 paladin variants.
- `Black Knight titan` `401`: quest boss; should not be used as the normal
  Zamorak equipment source.

White Knights already have normal map placement in the loaded NPC location
files, primarily around Falador. The current pass leaves that coverage in
place. Four Grey Knights are added through `MyWorldNpcLocs.json` in the
Taverley druid/white-wolf combat region as the first Guthix-aligned source.

## Current Drop State

Black Knight style drops:

- `Black Knight` `66`, aggressive `Black Knight` `189`, `Lord Darquarius`, and
  `Renegade knight` share a table.
- The shared table currently drops steel gear such as `Steel short sword`,
  `Large steel helmet`, and `Steel mace`.
- Legacy black chain/medium/square/skirt drops are already stripped by the
  drop-table cleanup.
- This Black Knight table is not a direct source of god-aligned Zamorak
  equipment; `Dark Warrior` `199` already supplies direct black weapon/armor
  drops in addition to altar conversion.

White Knight drops:

- `White Knight` `102` has its own table.
- It already drops white weapons and armor, including long sword, short sword,
  dagger, mace, kite shield, plate body, plate legs, and large helm.
- The current pass also adds white two-handed sword, scimitar, and battle axe
  outcomes so the direct equipment source covers the supported weapon line.
- These items are treated as Saradomin-gated prayer equipment.

Grey Knight drops:

- `Grey Knight` `836` now has a dedicated table mirroring the White Knight
  source shape, with grey dagger, mace, short sword, long sword, two-handed
  sword, scimitar, battle axe, helm, kite shield, plate body, and plate legs.
- It is implemented as a new Guthix-aligned steel-like knight identity rather
  than repurposing Ardougne `Knight` or `Paladin` globally.

## Current Equipment State

Current god equipment restrictions already exist for god capes and god staves.
Those items use the active prayer book to prevent mismatched equipment.

Black, white, and grey knight equipment is wired into that god-line gate. It
uses the same principle:

- Zamorak worship enables black knight bonuses.
- Saradomin worship enables white knight bonuses.
- Guthix worship enables grey knight bonuses.

Core armor stats should stay steel-equivalent even when the prayer bonus is not
active.

## Altar Conversion

Current rules:

- Use ordinary steel equipment on the matching god altar.
- Saradomin altar converts steel into White Knight equipment.
- Zamorak altar converts steel into Black Knight equipment.
- Guthix altar converts steel into Grey Knight equipment.
- Conversion has no resource cost.
- Conversion is one-way: only ordinary steel can be blessed.
- Existing black, white, or grey knight equipment cannot be converted into
  another god's equipment line.

The broader altar-enchantment cost and gate plan is tracked in
`altar-enchantment-and-conversion-plan.md`.

Current implementation:

- Steel-to-White Knight conversion is live for existing White Knight item
  definitions.
- Steel-to-Black Knight conversion is live for existing Black Knight item
  definitions.
- Steel-to-Grey Knight conversion is live for the Guthix altar.
- Black, White, and Grey Knight equipment now use the same active-prayer-book
  equip gate as god capes and god staves.

## Placement Review And Settled Sourcing

1. Review whether the Grey Knight source should remain inside the existing
   Taverley druid/white-wolf combat pocket or be separated for easier access.
2. Keep Black Knight titan separate from normal Zamorak equipment sourcing.
3. Standard Black Knights retain their ordinary steel/basic drop table. Direct
   Zamorak-equipment sourcing remains with Dark Warriors and altar conversion,
   avoiding a substantially broader black equipment supply than the white and
   grey lines.

The altar conversion and active-prayer equipment-gate implementation work is
complete for the current pass. White and Grey direct sourcing is now
implemented; further rate and placement tuning is deferred for limited-release
review unless a concrete issue is identified first.
