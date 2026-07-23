# Dragon Gear Crafting Plan

Status: in-progress design
Owner: An-actual-duck
Branch: no active branch; assign the next checklist item to a fresh topic branch in a neutral AI slot
Related docs:
- `docs/myworld/in-progress-work-plans/how-to-acquire-dragon-armor.md`
- `docs/myworld/in-progress-work-plans/monster-slayer-guild-plan.md`
- `docs/myworld/in-progress-work-plans/mining-guild-and-smithing-expansion-plan.md`

## Summary

Make tier-11 dragon equipment a full crafting/smithing progression tier instead
of a small collection of special-case NPC conversions and rare direct drops.

Players should still need to do meaningful content to unlock dragon gear, but
the unlock should feel like account progression rather than a reason to skip
dragon gear and rush tier 12. The lava forge becomes the central dragon-tier
unlock: the player completes Dwarf Youth Rescue, repairs the lava forge with
black dragon scales and gold, then uses the repaired forge to smelt dragon bars
and purified rune bars.

## Goals

- Make dragon gear worth pursuing before Exalted Rune.
- Preserve the Dwarf Youth Rescue and lava forge content.
- Preserve dragon hunting as the source of dragon metal.
- Turn dragon bars into normal anvil Smithing inputs.
- Remove the recurring NPC gold fee from dragon armor production.
- Give black dragons, KBD, and other dragons clear material-drop roles.
- Move purified rune production to the repaired lava forge.
- Make this plan the owner for all active dragon equipment acquisition routes.

## Non-Goals

- Do not reintroduce dragon skirts.
- Do not remove existing compatibility-only items unless a separate cleanup
  plan says to.
- Do not make tier-12 Exalted Rune easier.
- Do not keep dragon gear as a direct Elder Green Dragon reward once this route
  is active.

## Current Implementation Findings

### Dwarf Youth Rescue And Lava Forge Access

Relevant files:

- `server/plugins/com/openrsc/server/plugins/custom/minigames/DwarfRescue.java`
- `server/plugins/com/openrsc/server/plugins/authentic/defaults/Ladders.java`
- `server/plugins/com/openrsc/server/plugins/authentic/skills/smithing/Smelting.java`

Current Dwarf Youth Rescue flow:

- The miniquest uses player cache key `miniquest_dwarf_youth_rescue`.
- Gramat starts the rescue flow when the player meets the current Dwarf Cannon
  quest-stage condition in code.
- The player enters the underground construction/lava forge area from the
  dwarven mine ladder.
- The Dwarven Youth asks the player to recover teddy parts.
- The player needs both teddy halves, thread, and 15 Crafting to stitch the
  teddy back together.
- Returning the repaired teddy to Gramat sets
  `miniquest_dwarf_youth_rescue` to `2`.
- On completion, Gramat gives the `Dwarf Smithy Note` and says the player now
  has access to the lava forge's power.

Access caveat:

- The ladder currently allows entry if the cache key exists at all.
- Lava forge production requires
  `miniquest_dwarf_youth_rescue == 2`.
- The repaired-forge state is separate from miniquest completion:
  completion grants access, repair grants production.

### Implemented Lava Forge Use

Implemented lava forge behavior:

- Requires `miniquest_dwarf_youth_rescue == 2`.
- Requires one-time repair with `100 Black dragon scale` and
  `1,000,000 coins`.
- Modern clients open a lava forge production UI.
- `Dragon bar`: `1 Dragon Metal Scrap`, `6 Dragon sulfur`, 80 Smithing,
  `1000` Smithing XP.
- `Purified Rune Bar`: `1 Runite bar`, `14 Dragon sulfur`, 90 Smithing,
  `500` Smithing XP.
- Dragon metal chains are hidden compatibility-only items and are not produced
  by the lava forge.

### Implemented Dragon Material Sources

Relevant file:

- `server/src/com/openrsc/server/constants/NpcDrops.java`

- Black Dragon drops `1 Black dragon scale`.
- King Black Dragon drops `2 Black dragon scale`.
- Dragon metal scrap is a hidden rare dragon-family drop:
  - Elder Green Dragon: `1/128`
  - King Black Dragon: `1/128`
  - Black Dragon: `1/512`
  - Red Dragon: `1/1024`
  - Green Dragon: `1/2048`
  - Blue Dragon: `1/2048`

### Retired Chipped Dragon Scale Use

Relevant file:

- `server/plugins/com/openrsc/server/plugins/authentic/skills/crafting/Crafting.java`

- The KBD scale item ID is now named `Black dragon scale`.
- The chisel recipe has been removed.
- Chipped dragon scales remain in code as compatibility-only items.
- Chipped dragon scales should have no active purpose in the first pass of the
  new dragon crafting route.

### Retired Wayne Dragon Armor Route

Relevant files:

- `server/plugins/com/openrsc/server/plugins/authentic/npcs/falador/WaynesChains.java`
- `server/plugins/com/openrsc/server/plugins/custom/minigames/DwarfRescue.java`
- `server/plugins/com/openrsc/server/plugins/authentic/itemactions/InvAction.java`

Wayne's special armor option has been removed.

- Wayne is no longer the main dragon armor crafter.
- The one-time lava forge repair cost replaces Wayne's recurring `500,000`
  coin fee.
- Dragon equipment is smithed at normal anvils using dragon bars.
- Dragon scale mail body and legs are hidden until a better non-metal-line
  route exists for them.
- Wayne can be repurposed as a hint NPC, recipe explainer, or legacy dialogue
  contact if desired.

### Implemented Anvil Support

Relevant files:

- `server/plugins/com/openrsc/server/plugins/authentic/skills/smithing/Smithing.java`
- `tests/myworld/test-dragon-metal-production-removal.py`

Implemented dragon anvil support:

- Dragon bars are normal Smithing inputs.
- Dragon metal chains do not have a Smithing recipe.
- Dragon platebody, platelegs, large helm, square shield, paladin shield, and
  existing dragon weapons are crafted at anvils.
- Dragon square/paladin shield repair remains available through shield halves.
- `test-dragon-metal-production-removal.py` now validates this route.

## New Core Decisions

### Repair Material

The forge repair material is `Black dragon scale`.

Drop sources:

- Regular black dragon: guaranteed `1 Black dragon scale` per kill.
- King Black Dragon: guaranteed `2 Black dragon scale` per kill.

The repair requires:

- `100 Black dragon scale`
- `1,000,000 coins`

This is a one-time account unlock after Dwarf Youth Rescue. It is not paid per
dragon item.

### Repaired Lava Forge UI

The repaired lava forge should open its own production UI.

Initial lava forge outputs:

| Output | Ingredients | Notes |
| --- | --- | --- |
| `Dragon bar` | `1 Dragon Metal Scrap`, `6 Dragon sulfur` | Dragon gear material |
| `Purified Rune Bar` | `1 Rune bar`, `14 Dragon sulfur` | Moved from regular furnace |

The production UI should only become available once:

- Dwarf Youth Rescue is complete.
- The lava forge has been repaired.
- The player meets the relevant skill level for the selected output.

### Dragon Sulfur Access

Lower the Mining Guild expansion gates so dragon sulfur supports the dragon
crafting route:

- Dragon sulfur Mining requirement: `80 Mining`.
- New Mining Guild area entry requirement: `80 Mining`.

This keeps dragon sulfur as a meaningful material while aligning it with the
dragon tier instead of pushing it too close to tier 12.

### Dragon Bar Recipe

Dragon bars should require:

- `1 Dragon Metal Scrap`
- `6 Dragon sulfur`

Dragon bars should be created only at the repaired lava forge.

### Purified Rune Relocation

Purified Rune bars should be created at the repaired lava forge instead of the
regular furnace.

Current tier-12 recipe direction remains:

- `1 Rune bar`
- `14 Dragon sulfur`

The production location changes; the recipe identity stays the same unless
future balancing changes it.

### Dragon Equipment Smithing

Dragon equipment should be made at normal anvils.

Rules:

- No recurring coin cost to smith dragon equipment.
- No Wayne crafting fee.
- Dragon equipment uses dragon bars.
- Dragon equipment should not require dragon metal chains.
- Dragon equipment should not require chipped dragon scales in the first normal
  Smithing route.
- Dragon equipment should appear in the normal Smithing production UI.
- Dragon equipment should behave like a complete tier-11 metal family where
  item coverage exists.

Open tuning that still needs exact values:

- Smithing level per dragon item.
- Dragon bar cost per dragon item.
- Which compatibility-only dragon items stay hidden.
- Whether dragon shields remain shield-half only or also become bar-smithable.

### Dragon Equipment Coverage

This plan should cover all active dragon equipment routes, not only plate
bodies or plate legs.

Equipment to account for:

- Dragon plate mail body.
- Dragon plate mail legs.
- Dragon helmet, using the current `Dragon Helmet` / `Large Dragon Helmet`
  identity as the main metal-line helm.
- Dragon shields, including square and paladin/kite variants.
- Dragon gloves/gauntlets if a metal dragon hand slot is added or exposed.
- Dragon boots/greaves if a metal dragon foot slot is added or exposed.

Rules:

- Retired dragon skirts stay retired.
- Dragon scale mail body, dragon scale mail legs, and dragon scale mail top are
  variant armor pieces and should stay hidden until they have a better route
  than normal metal-line Smithing.
- Dragon medium Helmet still exists as item `795`, but should stay hidden or
  compatibility-only for this first dragon Smithing pass. Former medium-helm
  reward slots should use the main `Dragon Helmet` / `Large Dragon Helmet`
  instead. Do not add the medium helm as a normal anvil recipe unless the helm
  structure is revisited.
- Compatibility-only tops should stay hidden unless a separate visual or
  equipment migration requires them.
- Existing shield-half behavior can remain if it is intentionally kept as a
  special route, but this plan must decide that explicitly.
- Finished dragon equipment should not be a Monster Slayer reward route unless
  this plan is deliberately changed later.

## Dragon Drop Changes

### Dragon Metal Scrap

Add dragon metal scrap as a rare dragon-family drop.

Target rates:

| Source | Dragon Metal Scrap rate |
| --- | ---: |
| Elder Green Dragon | `1/128` |
| King Black Dragon | `1/128` |
| Black Dragon | `1/512` |
| Red Dragon | `1/1024` |
| Green Dragon | `1/2048` |
| Blue Dragon | `1/2048` |

Implementation should decide whether these are hidden unique-style drops,
normal rare drops, or custom personal drops. The rates above are the intended
player-facing odds.

### Elder Green Dragon Cleanup

Elder Green Dragon should stop dropping direct dragon gear/ammunition rewards.

Replace those direct dragon equipment drops with the `1/128` dragon metal scrap
drop. This keeps the Elder Green Dragon valuable while moving dragon gear
progression into the repaired-forge crafting loop.

Unless separately decided, this does not mean removing biological creature
drops like hides from Elder Green Dragon. The cleanup target is direct dragon
equipment/ammunition rewards.

### Black Dragon Scales

Add guaranteed black dragon scale drops:

| Source | Black dragon scale drop |
| --- | ---: |
| Black Dragon | `1` |
| King Black Dragon | `2` |

These are used for the one-time lava forge repair.

## Dragon Armor And Monster Slayer Conflict Resolution

The earlier Monster Slayer Guild plan proposed dragon plate legs and dragon
scale mail legs as Legends Guild point-shop rewards.

This plan supersedes that reward direction:

- Dragon plate legs should be crafted through the repaired lava forge and
  normal anvil route.
- Dragon scale mail legs should be crafted through the repaired lava forge and
  normal anvil route only if scale mail later becomes part of the dragon
  Smithing family. For the first pass, hide the scale mail pieces instead.
- Monster Slayer shops should receive different unique rewards instead of
  dragon legs.
- Combat Odyssey should still be preserved and migrated into Monster Slayer,
  but no longer needs to be the dragon plate legs route.
- The current Legends Guild task-provider entry into the old dragon-leg path
  can be hidden while Monster Slayer is redesigned around unique rewards.

## Incentive Goals

The route should make dragon gear attractive without invalidating tier 12.

Desired effects:

- Dragon gear becomes a clear, achievable tier-11 crafting milestone.
- Players have a reason to farm dragons before pursuing Exalted Rune.
- Black dragons and KBD gain a clear role in forge repair.
- Normal dragons contribute dragon metal scrap at appropriate rarity.
- The one-time forge repair gives a strong account-progress moment.
- Dragon sulfur bridges the Mining Guild expansion into dragon and Exalted Rune
  progression.
- Tier 12 remains stronger and more expensive through purified rune.

## Implementation Checklist

- [x] Add or rename the repair material as `Black dragon scale`.
- [x] Add guaranteed `Black dragon scale` drops to black dragon and KBD.
- [x] Add a one-time repaired lava forge player state.
- [x] Add Dwarven Smithy or lava forge dialogue explaining the broken forge.
- [x] Add lava forge repair interaction requiring `100 Black dragon scale` and
      `1,000,000 coins`.
- [x] Add repaired lava forge production UI.
- [x] Move purified rune production from the regular furnace to lava forge UI.
- [x] Change dragon bar production to require `1 Dragon Metal Scrap` and
      `6 Dragon sulfur`.
- [x] Remove dragon metal chains from the lava forge production choices.
- [x] Lower dragon sulfur Mining requirement to `80`.
- [x] Lower the new Mining Guild area entry requirement to `80`.
- [x] Remove the KBD scale chisel recipe.
- [x] Keep chipped dragon scales as compatibility-only/inert items.
- [x] Keep dragon metal chains as compatibility-only/inert items.
- [x] Add dragon metal scrap drops to dragons at the decided rates.
- [x] Remove direct dragon equipment/ammunition drops from Elder Green Dragon.
- [x] Add dragon equipment recipes to normal anvil Smithing.
- [x] Define the active acquisition route for every dragon equipment slot.
- [x] Hide dragon scale mail body, dragon scale mail legs, and dragon scale
      mail top from player-facing acquisition routes.
- [x] Keep dragon medium Helmet compatibility-only and replace its live reward
      sources with `Dragon Helmet` / `Large Dragon Helmet`.
- [x] Remove Wayne's direct dragon armor crafting route or convert him to
      guidance/recipe dialogue.
- [x] Hide the current Legends Guild task-provider entry into the old
      dragon-leg route until Monster Slayer replaces it with unique rewards.
- [x] Update Dwarf Smithy Note text.
- [x] Update Smithing guide entries.
- [x] Update `how-to-acquire-dragon-armor.md`.
- [x] Update `mining-guild-and-smithing-expansion-plan.md`.
- [x] Update Monster Slayer plan so dragon legs are no longer its top reward.
- [x] Replace dragon-metal-removal tests with tests for the new route.

## Testing Checklist

- [x] Dwarf Youth Rescue completion grants lava forge access but not repaired
      production.
- [x] Unrepaired lava forge explains the repair requirement.
- [x] Repair consumes exactly `100 Black dragon scale` and `1,000,000 coins`.
- [x] Repair state persists.
- [x] Repaired lava forge opens the new production UI.
- [x] Dragon bar recipe requires `1 Dragon Metal Scrap` and `6 Dragon sulfur`.
- [x] Dragon metal chains are not offered by the new lava forge UI.
- [x] Purified Rune Bar recipe is unavailable at regular furnaces.
- [x] Purified Rune Bar recipe works at repaired lava forge.
- [x] Dragon sulfur mining and area entry require 80 Mining.
- [x] Black dragon drops `1 Black dragon scale`.
- [x] KBD drops `2 Black dragon scale`.
- [x] Dragon metal scrap rates are wired for blue, green, red, black, KBD, and
      elder green dragons.
- [x] Elder Green Dragon no longer drops direct dragon gear/ammunition.
- [x] Chiseling KBD/black dragon scales no longer creates chipped scales.
- [x] Dragon equipment can be made at normal anvils with no coin fee.
- [x] Dragon equipment recipes require bars only, not chains or chipped scales.
- [x] Dragon scale mail body, legs, and top have no player-facing acquisition
      route in the first pass.
- [x] Dragon medium Helmet has no active reward route and is not introduced as
      a normal anvil recipe in the first pass.
- [x] Wayne no longer creates dragon armor directly if that route is retired.

## Open Questions

- Existing `King Black Dragon scale` item ID was renamed in definitions to
  `Black dragon scale`, preserving existing saved items.
- Dragon shields are both bar-smithable and repairable through the legacy
  shield-half route.
- Should metal dragon gloves/gauntlets and boots/greaves be added as part of
  this tier, or should dragonhide hand/foot slots stay separate?
- What future route, if any, should reintroduce dragon scale mail variants?
- Should Dragon metal chains receive a new purpose later, or remain
  compatibility-only indefinitely?
- Should Dragon medium Helmet be permanently retired, kept as compatibility-only,
  or folded into a future helm variant route?
- What should Monster Slayer shops use as their top-end rewards now that dragon
  legs move to crafting?

## Completion Criteria

This plan is complete when:

- The repaired lava forge is the production home for dragon bars and purified
  rune bars.
- Dragon sulfur and the Mining Guild expansion gate align at 80 Mining.
- Dragon bars use dragon metal scrap plus dragon sulfur.
- Dragon equipment is normally smithable at anvils.
- Dragon equipment recipes use bars only; dragon metal chains and chipped
  scales are hidden compatibility-only materials.
- Dragon scale mail variants are hidden, and Dragon medium Helmet is
  compatibility-only until their future roles are decided.
- Every active dragon equipment route is accounted for in this plan.
- Black dragon scales repair the forge as a one-time unlock.
- Dragon-family monsters drop dragon metal scrap at the specified rates.
- Elder Green Dragon no longer directly drops dragon gear.
- Chipped dragon scale production is removed.
- Related player guides, docs, and tests reflect the new route.
