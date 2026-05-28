# Equipment Tasklist

Authoritative prerequisite:

- Check
  [important-game-changes-that-must-be-adhered-to.md](/home/justin/Core-Framework/docs/myworld/important-game-changes-that-must-be-adhered-to.md)
  before changing standard item families.
- Record removals/replacements in
  [retired-replaced-and-repurposed-items.md](/home/justin/Core-Framework/docs/myworld/retired-replaced-and-repurposed-items.md).

This checklist turns the equipment audit into an active implementation tracker.

Status key:
- `[todo]` not started
- `[doing]` currently active
- `[done]` implemented
- `[blocked]` waiting on a design/system dependency

## Phase 1: Standard Equipment Coverage

- `[done]` Build a concrete missing-items checklist for standard melee weapons
  and armor still relying on fallback values.
- `[done]` Build the same checklist for standard ranged weapons and defensive
  armor stats.
- `[done]` Build the same checklist for standard robes, staves, and related
  magic gear.
- `[doing]` Work through the checklist in
  [equipment-standard-checklist.md](/home/justin/Core-Framework/docs/myworld/equipment-standard-checklist.md),
  with the current stock-def ranged slice centered on leather and launcher/ammo tuning.
- `[done]` Break the immediate staff redesign into a dedicated tracker
  in [magic-equipment-tasklist.md](/home/justin/Core-Framework/docs/myworld/magic-equipment-tasklist.md)
  before implementing the next caster-weapon pass.
- `[todo]` Remove remaining reliance on fallback heuristics where explicit item
  values should exist.
- `[todo]` Verify equipment requirements still line up with the new combat and
  skill model.
- `[todo]` Audit special-case equipables outside the normal families that still
  need direct mapping into the new stat model.

## Phase 2: Standard Jewelry Cleanup

- `[doing]` Audit remaining obsolete enchanted jewelry inflows from drops,
  quests, shops, and admin/dev sources.
- `[done]` Remove normal live sources that were still handing out obsolete
  enchanted gem jewelry.
- `[done]` Remove obsolete ring/amulet outputs from the old magic-enchant spell
  flow so only crown enchanting remains on that path.
- `[done]` Add altar-based amulet enchanting framework.
- `[done]` Add altar-based necklace enchanting framework.
- `[done]` Add altar-based elemental ring enchanting.
- `[done]` Add special ring lines for chaos, nature, cosmic, soul, and law.
- `[done]` Add amulet lines for elemental, chaos, death, blood, mind, body,
  nature, cosmic, law, and soul.
- `[done]` Add soul altar support so the soul jewelry line is craftable.
- `[todo]` Audit dormant compatibility item defs and legacy names that are no
  longer meant to enter normal play.
- `[todo]` Add deeper behavior tests for enchanting effects instead of relying
  mostly on structural validation.

## Phase 3: Legacy Specialty Jewelry

- `[done]` Remap [Amulet of accuracy](/home/justin/Core-Framework/server/conf/server/defs/ItemDefs.json)
  into the new water-style high-roll-bias model.
- `[done]` Tune `Amulet of accuracy` so it lands between tier 1 and tier 2
  water-enchanted jewelry.
- `[done]` Neutralize the old charged dragonstone gameplay path.
- `[todo]` Audit any remaining dormant dragonstone compatibility items and
  decide whether to retire or remap them explicitly.
- `[todo]` Check for any other one-off reward jewelry that now needs direct
  remapping into the new combat/enchanting language.

## Phase 4: Opal And Partial-Tier Jewelry

- `[done]` Retire `Opal ring` as active equipment content.
- `[done]` Fold the old dwarven-ring cannonball bonus into standard gameplay.
- `[done]` Remove the old opal enchant path from active gameplay.
- `[todo]` Decide whether `opal` the gem should get a future proper material or
  jewelry role at all.

## Phase 5: Crowns

- `[doing]` Hide crowns from active acquisition and enchanting for now so they do
  not compete with the current enchanting direction.
- `[todo]` Audit all crown sources, enchanting paths, recharge paths, and live
  effect hooks.
- `[todo]` Decide whether crowns remain a separate equipment family at all.
- `[todo]` If retained, decide whether crowns become:
  - altar-enchanted head-slot equivalents to jewelry
  - a charge-based relic system
  - or future skilling artifacts separate from jewelry entirely
- `[doing]` Remove crown enchanting from the old spell system.
- `[todo]` Unify crown durability and recharge behavior.
- `[todo]` Reassign each crown effect into the new itemization language
  intentionally.

## Phase 6: Coverage And Cleanup

- `[todo]` Verify all equipable families have an intentional place in the new
  combat/enchanting model.
- `[todo]` Verify no obsolete equipables are still entering the game through
  hidden sources.
- `[doing]` Audit specialty armor and utility-wear items to separate:
  - true mechanical/quest-use equipment that should stay
  - legacy alternates whose visuals can be repurposed
  - cosmetic or side-path items that can be retired if they no longer fit the
    new equipment language
- `[done]` Hide the god-themed magic-gear inflows that do not fit the current
  enchanting direction:
  - Mage Arena god capes no longer enter normal play
  - normal Zamorak-robe drops and holiday rewards no longer enter normal play
  - quest-only compatibility uses remain for now
  - future direction is to revisit those visuals as prayer-aligned equipment
- `[doing]` Continue removing stale live inflows for retired magic-gear lines:
  - old battlestaff/orb items should no longer leak in through normal drops,
    pickpocket tables, or glassblowing/crafting outputs
  - old wizard-color clothing should be treated as compatibility or quest gear,
    not active progression
  - document every quest-only holdover that still references those legacy items
    so it can be revisited once prayer/magic replacement lines are in place
- `[todo]` Add focused tests or reports for special equipment behavior:
  - enchanting effects
  - special jewelry
  - specialty reward items
  - crowns, if retained
- `[todo]` Document any items intentionally left dormant for compatibility only.

## Current Focus

- `[doing]` Audit dormant compatibility item defs and legacy names for jewelry
  that should no longer enter normal play.
- `[doing]` Build the dedicated leather-armor direction in
  [leather-armor-tasklist.md](/home/justin/Core-Framework/docs/myworld/leather-armor-tasklist.md),
  including Crafting ownership, source-creature-derived hide/carapace
  materials, object-based tanning/curing, visual remapping, and the shared
  defense-per-ingredient budget model that should later extend into the metal
  armor overhaul too.
- `[doing]` Use the specialty-armor audit to decide which alternate armor lines
  should be removed from smithing, drops, and shops once their silhouettes are
  repurposed.
- `[todo]` Review the old body-type item split for plate armor and decide how
  to replace NPC/item swapping with a cleaner body-type-aware visual system.
- `[todo]` Add a dedicated `Tool` equipment slot in the long term so hatchets,
  pickaxes, and similar skilling tools can be equipped without occupying the
  weapon slot.
- `[todo]` Rebalance metal armor around the same ingredient-budget defense model
  being introduced for leather/carapace:
  - `Helm` = `1`
  - `Boots` / `Gloves` = `2`
  - `Shield` / `Legs` = `3`
  - `Body` = `4`
  - defense per piece should then be `tier x ingredient cost`
- `[doing]` Build out the staff redesign plan in
  [magic-equipment-tasklist.md](/home/justin/Core-Framework/docs/myworld/magic-equipment-tasklist.md)
  for wood-tier staves, elemental attunement, staff migration cleanup, and the
  move of base staff shaping into `Crafting`.
- `[todo]` Keep wider armor production planning in the goals list as defensive
  material-family progression, not ranged, melee, or magic armor.

## Recommended Next Order

1. Finish dormant compatibility cleanup for legacy jewelry.
2. Lock the design direction in
   [magic-equipment-tasklist.md](/home/justin/Core-Framework/docs/myworld/magic-equipment-tasklist.md).
3. Then implement the first staff enchanting slice.
4. Only after those are stable, start the crown audit/design pass.
