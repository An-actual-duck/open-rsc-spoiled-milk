# Equipment Transition Audit

This audit focuses on getting all equipables aligned with the new MyWorld
combat and enchanting model.

The goal is not just "make items work", but to identify the cleanest migration
order from low-friction remaps to high-friction redesign cases.

## Migration Order

### Tier 1: Straight Data Remap

These are the easiest items to transition because the framework already exists
and they mostly need intentional values, not new mechanics.

- Standard melee weapons and armor
- Standard ranged weapons and armor
- Standard magic robes and staves
- Base gem jewelry that now cleanly maps onto altar enchanting

Why they are easy:

- The offense/defense runtime is already in place.
- The item override layer already exists.
- Most of these items do not need unique code paths, only tuned numbers and
  requirement cleanup.

Primary work:

- Finish explicit offense/defense values
- Remove reliance on fallback heuristics where still present
- Verify every straightforward equipable family is intentionally mapped

### Tier 2: Straight Jewelry Cleanup

These are jewelry items that mostly fit the new enchanting framework, but need
coverage/audit to make sure no legacy path still hands out obsolete variants.

- Standard gem amulets
- Standard gem rings
- Standard gem necklaces
- Soul/law jewelry lines already added under the new framework

Why they are medium-low friction:

- The altar-based framework is already live.
- The remaining work is mostly completeness and legacy cleanup.

Primary work:

- Audit any remaining old enchanted jewelry inflow
- Verify item sources, shops, quests, and drops no longer give obsolete
  enchant outputs
- Check that all intended gem tiers and altar variants actually exist

### Tier 3: Legacy Specialty Jewelry

These items are not hard because of deep code, but because their legacy role no
longer matches the new model and they need intentional reassignment.

- [Amulet of accuracy](/home/justin/Core-Framework/server/conf/server/defs/ItemDefs.json)
- Legacy enchanted amulet lines that predate the new altar rules
- Legacy dragonstone amulet variants and compatibility leftovers

Why they are medium friction:

- They are conceptually obsolete under always-hit combat and the new jewelry
  identity split.
- They need design decisions more than systems work.

Current notes:

- `Amulet of accuracy` is not a deep code problem; it is a legacy reward item
  that now needs to be remapped into the new always-hit / high-roll-bias model.
  It currently comes from
  [ImpCatcher.java](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/free/ImpCatcher.java).
- Current direction for `Amulet of accuracy`:
  - make it function like the existing water-aligned high-roll / "accuracy"
    effects
  - tune it between tier 1 and tier 2 strength so it lands between sapphire and
    emerald water-enchanted jewelry
  - keep it as a special standalone reward item rather than forcing it into the
    normal altar-tier ladder
- The old charged dragonstone path is already mostly neutralized in
  [DragonstoneAmulet.java](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/misc/DragonstoneAmulet.java),
  but dormant compatibility/data cleanup still remains.

Primary work:

- Decide which remaining legacy specialty items get remapped, replaced, or retired
- Remove remaining obsolete inflows
- Make sure quest rewards and admin/dev kits hand out the new intended forms

### Tier 4: Opal And Partial-Tier Jewelry

This was a special bucket because it was neither a full legacy line nor a clean
part of the current new system.

- [Opal ring](/home/justin/Core-Framework/server/conf/server/defs/ItemDefsCustom.json)

Why it needed separate attention:

- It existed as a real equipable crafted item.
- It historically enchanted through the old spell path into `Dwarven ring`.
- It did not fit the current five-tier gem ladder cleanly.

Current notes:

- I did not find an `opal necklace` or `opal amulet` line in defs.
- Current direction:
  - retire the `opal ring` as active equipment content
  - fold the old `Dwarven ring` cannonball bonus into standard gameplay
  - keep `opal` itself as a gem/material for now unless a stronger future use is defined

Current state:

- `Opal ring` crafting and the old `opal ring -> Dwarven ring` enchant path have
  been removed from active gameplay.
- The cannonball bonus has been folded into standard cannonball production in
  [Smelting.java](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/skills/smithing/Smelting.java).
- `Opal ring` is no longer forced equipable in
  [EntityHandler.java](/home/justin/Core-Framework/server/src/com/openrsc/server/external/EntityHandler.java).

Primary work:

- Decide whether `opal` the gem should get a future proper jewelry/material role
  at all

### Tier 5: Crowns

Crowns are the highest-friction equipment audit bucket because they are a full
parallel enchanting subsystem, not just a few mismatched items.

Relevant items:

- [Crown of dew](/home/justin/Core-Framework/server/conf/server/defs/ItemDefsCustom.json)
- [Crown of mimicry](/home/justin/Core-Framework/server/conf/server/defs/ItemDefsCustom.json)
- [Crown of the artisan](/home/justin/Core-Framework/server/conf/server/defs/ItemDefsCustom.json)
- [Crown of the items](/home/justin/Core-Framework/server/conf/server/defs/ItemDefsCustom.json)
- [Crown of the herbalist](/home/justin/Core-Framework/server/conf/server/defs/ItemDefsCustom.json)
- [Crown of the occult](/home/justin/Core-Framework/server/conf/server/defs/ItemDefsCustom.json)

Why they are hardest:

- They have their own effect manager in
  [EnchantedCrowns.java](/home/justin/Core-Framework/server/src/com/openrsc/server/content/EnchantedCrowns.java).
- They still use the old magic-enchant flow in
  [SpellHandler.java](/home/justin/Core-Framework/server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java#L931).
- Several crown effects have live hooks in skilling, loot, NPC death, and item
  interactions.
- Some use shatter logic, some use recharge logic, and they are not aligned to
  the new altar-jewelry rules.

Current live crown effect summary:

- `Crown of dew`
  - flour -> dough conversion in
    [PotFlour.java](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/itemactions/PotFlour.java)
  - softens clay during mining in
    [Mining.java](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/skills/mining/Mining.java)
  - shatters after a charge pool
- `Crown of mimicry`
  - NPC mimic/avoidance hook in
    [NpcBehavior.java](/home/justin/Core-Framework/server/src/com/openrsc/server/model/entity/npc/NpcBehavior.java)
- `Crown of the artisan`
  - artisan/crafting-style proc in
    [Player.java](/home/justin/Core-Framework/server/src/com/openrsc/server/model/entity/player/Player.java)
- `Crown of the items`
  - extra item/resource procs in gathering systems:
    [Woodcutting.java](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/skills/woodcutting/Woodcutting.java),
    [Mining.java](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/skills/mining/Mining.java),
    [GemMining.java](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/skills/mining/GemMining.java),
    [Fishing.java](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/skills/fishing/Fishing.java),
    [Harvesting.java](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/custom/skills/harvesting/Harvesting.java)
- `Crown of the herbalist`
  - herb-identification / herb-XP behavior in
    [EnchantedCrowns.java](/home/justin/Core-Framework/server/src/com/openrsc/server/content/EnchantedCrowns.java)
  - recharge handled through
    [BrotherJered.java](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/npcs/edgeville/BrotherJered.java)
- `Crown of the occult`
  - bone cremation / prayer-XP style behavior in
    [EnchantedCrowns.java](/home/justin/Core-Framework/server/src/com/openrsc/server/content/EnchantedCrowns.java)
  - recharge handled through
    [SpiritOfScorpius.java](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/npcs/ardougne/west/SpiritOfScorpius.java)

Primary work:

- Hide crowns from active acquisition/enchanting until they have a clear place
  in the new enchanting/equipment model
- Decide whether crowns remain a separate equipment family at all
- If retained, decide whether they become:
  - altar-enchanted head-slot equivalents to jewelry
  - a charge-based relic system
  - or future skilling artifacts separate from jewelry entirely
- Remove crown enchanting from the old spell system
- Unify durability/recharge behavior
- Reassign each crown effect to the new itemization language intentionally

## Recommended Working Order

1. Finish straightforward standard equipment coverage.
2. Finish standard jewelry cleanup under the new enchanting framework.
3. Resolve legacy specialty jewelry like `Amulet of accuracy` and remaining
   dragonstone leftovers.
4. Decide what `opal` is supposed to be before expanding or deleting it.
5. Audit and redesign crowns last, because they are a separate subsystem and
   will take the most design work.

## Immediate Actionable Follow-Up

- Build a concrete missing-items checklist for standard equipables still leaning
  on fallback values.
- Build a short legacy-jewelry checklist:
  - `Amulet of accuracy`
  - any obsolete enchanted gem outputs
  - dormant dragonstone items
- Build a separate crown-design note before making crown code changes.

## Additional Dormant Magic-Wear Notes

- God-themed magic wear like the Mage Arena capes and the old Zamorak robe set
  no longer fit the current robe/enchanting direction.
- Current direction:
  - hide those items from normal acquisition for now
  - leave quest-only compatibility cases in place where they still gate old
    content
  - revisit their visuals later as possible prayer-aligned equipment rather
    than active magic-gear progression
- Known compatibility holdovers to revisit later:
  - Underground Pass still uses `Robe of Zamorak top` and `Robe of Zamorak
    bottom` as active quest disguise items and local quest-area spawns
  - Watchtower still accepts the legacy `Wizard's robe` in its old false-evidence
    path
  - Betty's quest scripting still recognizes the old blue and black wizard hats
    alongside the new wool hat for `Mice to Meet You`
