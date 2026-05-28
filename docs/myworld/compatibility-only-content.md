# Compatibility-Only Content

This ledger tracks content that may still exist in item definitions, quest
checks, old banks, or shared handlers, but is not part of active MyWorld
progression.

Use this to distinguish real leaks from intentional holdovers. If one of these
items starts appearing through normal shops, drops, crafting, or skilling, treat
that as a bug unless the source is explicitly listed here.

## Inert Item Families

- `Battlestaff`, enchanted battlestaves, and orb equipment
  - kept as compatibility records
  - old orb charging and battlestaff crafting are retired
  - live staff progression is wood-tier staffs enchanted at rune altars
- old vanilla elemental staff names such as `Staff of air` and `Staff of fire`
  - replaced by rune-first staff products such as `Air Staff`
  - old names should not be normal progression rewards
- `Opal ring` and old partial-gem jewelry paths
  - retired from live crafting and enchanting
  - standard altar jewelry starts at sapphire
- generic legacy leather armor and gloves
  - replaced by creature-specific leather and carapace armor
  - old world/drop sources should map to the new hide families
- standard `Chain` armor, `Medium helm`, `Square shield`, and metal-skirt lines
  - retired from normal standard progression
  - may remain as data or visual sources
  - dragon or specialty exceptions must be explicit
- old `Black` and `White` equipment as normal metal tiers
  - retired from the standard ladder
  - repurposed as live god-aligned knight equipment, alongside the new Grey
    Knight line, rather than generic progression
- crowns and the crown mould
  - scrubbed from active jewelry progression
  - should not re-enter standard shops, drops, or crafting

## Quest And Utility Exceptions

- quest-required fishing tools
  - normal fishing uses equipped wood-tier rods
  - `Fishing Contest`, `Dragon Slayer`, and lava-eel compatibility paths may
    still reference old tools where the quest specifically needs them
- `Pink skirt`, `Priest robe`, and `Priest gown`
  - kept for quest/cosmetic compatibility
  - not part of the retired wizard-gear line
- `Dramen staff` and `Wizard staff`
  - specialty utility staves, not part of the standard staff ladder
- god staves and Iban staff
  - named special-case combat staves
  - not produced by normal wood-tier staff crafting
- Firemaking cape
  - item may remain, but normal NPC acquisition is retired
  - future acquisition needs a deliberate special route
- Fletching cape
  - compatibility item only after Fletching folded into Crafting
  - no normal active acquisition path

## Retired System Items

- rune talismans and tiaras
  - retired from the live Enchanting/runecrafting path
  - rune production uses stone and overworld altars
- old Tanner NPC processing and hammer/fat/fire leather processing
  - replaced by tanning racks and Crafting
- old fishing bait, feathers-as-bait, harpoons, nets, cages, and similar tools
  - retired from normal fishing progression
  - quest exceptions remain case-by-case
- old direct jewelry enchant spell outputs
  - replaced by altar-based jewelry enchanting
- old combat-stat potion inflows
  - retired from the main Herblaw direction unless a quest/special source
    explicitly needs the item

## Maintenance Rule

When a dormant item gains a live purpose, record the new source and role here
before removing it from compatibility-only status. When a dormant item appears
through an unlisted live source, remove or remap that source rather than
treating the item as active by default.
