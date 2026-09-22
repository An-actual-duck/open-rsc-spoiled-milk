# Inventory / ground sprite reference library

For AI creating new RSC-style icons. This is an art-reference collection, not
a gameplay database, runtime asset pack, or browsable application.

## Before creating an icon

1. Find the requested object family below, read its short notes, and **open its
   contact sheet visually**. Do not rely on labels alone.
2. Open the closest individual PNG. This is the full reference canvas, not a
   tightly cropped object. Read its canvas and visible-bounds measurements.
3. Use that reference as the scale, orientation, placement and shading anchor.
   Describe the user's desired changes while keeping the unchanged qualities
   explicit. For example: use the ordinary dagger as the scale/pose anchor,
   change its blade into a Terror Fang, and retain native-size readability.
4. Compare the result on equal-size canvases at 1x and integer nearest-neighbour
   zoom. Do not auto-fit each object to fill a thumbnail or enlarge the source
   canvas to accommodate a new design without asking the user first.

## Reference rules

- Original exported artwork is unchanged: no painting, smoothing, recoloring,
  upscaling, sharpening or alpha cleanup was applied to individual sprites.
  The export's cropped pixels were placed at their recorded offsets on a
  transparent canvas. Offsets are part of the reference, not padding to remove.
- Most declared canvases are **48x32**; 13 are **47x33**. Do not normalize those
  exceptions. Match the selected reference and the destination's real contract.
- Eight exports have crop/offset data extending past their declared bounds.
  Their reference PNGs include explicitly flagged extra transparent extent to
  preserve every source pixel. Orange sheet borders show declared bounds. These
  are archival anomalies, NOT permission to enlarge a new item canvas.
- Contact sheets use uniform **3x nearest-neighbour** zoom and a neutral
  checkerboard. Never copy the checkerboard, frame border, labels or sheet
  spacing into sprite artwork. Only the individual transparent PNG is an input
  art reference.
- Favor readable stepped silhouettes and compact interior shading. Preserve
  the selected example's highlights and texture; avoid smooth resampling or
  inventing bright edge halos. Family notes refine this general guidance.
- Many sprites are neutral recolor bases. White/grey regions may be runtime
  color masks, not the item's intended final metal/material color. No speculative
  recolors are included here. Choose the new item's palette explicitly.
- Labels describe what the image looks like where historical client names
  conflict. `catalog.json` keeps source identity, hashes and geometry for audit;
  its name aliases are search hints only. Sprite IDs and `items:N` indices are
  not gameplay item IDs. Shared forms are cross-linked instead of duplicated.
- This covers the supplied Authentic_Sprites export only. It does not claim to
  include every later custom item or approved newly authored sprite.

## Object families

- [ammunition/arrows](ammunition/arrows/README.md) — 6 sprites
- [ammunition/bolts](ammunition/bolts/README.md) — 2 sprites
- [ammunition/cannonballs](ammunition/cannonballs/README.md) — 1 sprites
- [ammunition/components](ammunition/components/README.md) — 6 sprites
- [ammunition/thrown](ammunition/thrown/README.md) — 2 sprites
- [armor/capes](armor/capes/README.md) — 3 sprites
- [armor/damaged](armor/damaged/README.md) — 2 sprites
- [armor/hands-and-feet](armor/hands-and-feet/README.md) — 6 sprites
- [armor/head](armor/head/README.md) — 25 sprites
- [armor/legs](armor/legs/README.md) — 3 sprites
- [armor/shields](armor/shields/README.md) — 9 sprites
- [armor/torso](armor/torso/README.md) — 12 sprites
- [consumables/drinks](consumables/drinks/README.md) — 10 sprites
- [consumables/fish](consumables/fish/README.md) — 13 sprites
- [consumables/food](consumables/food/README.md) — 56 sprites
- [consumables/potions-and-bottles](consumables/potions-and-bottles/README.md) — 8 sprites
- [consumables/produce-and-ingredients](consumables/produce-and-ingredients/README.md) — 25 sprites
- [jewelry/necklaces-and-amulets](jewelry/necklaces-and-amulets/README.md) — 10 sprites
- [jewelry/rings](jewelry/rings/README.md) — 1 sprites
- [jewelry/trinkets-and-badges](jewelry/trinkets-and-badges/README.md) — 13 sprites
- [magic/orbs-and-talismans](magic/orbs-and-talismans/README.md) — 8 sprites
- [magic/runes](magic/runes/README.md) — 15 sprites
- [materials/bones-and-monster-parts](materials/bones-and-monster-parts/README.md) — 14 sprites
- [materials/gems-and-crystals](materials/gems-and-crystals/README.md) — 14 sprites
- [materials/herbs-and-plants](materials/herbs-and-plants/README.md) — 11 sprites
- [materials/hides-and-fur](materials/hides-and-fur/README.md) — 3 sprites
- [materials/ores-bars-and-rocks](materials/ores-bars-and-rocks/README.md) — 16 sprites
- [materials/powders-and-residues](materials/powders-and-residues/README.md) — 6 sprites
- [materials/thread-and-cloth](materials/thread-and-cloth/README.md) — 5 sprites
- [materials/wood](materials/wood/README.md) — 6 sprites
- [miscellaneous/books-scrolls-and-maps](miscellaneous/books-scrolls-and-maps/README.md) — 23 sprites
- [miscellaneous/containers](miscellaneous/containers/README.md) — 26 sprites
- [miscellaneous/creatures](miscellaneous/creatures/README.md) — 5 sprites
- [miscellaneous/currency-and-tickets](miscellaneous/currency-and-tickets/README.md) — 11 sprites
- [miscellaneous/keys](miscellaneous/keys/README.md) — 4 sprites
- [miscellaneous/quest-and-festive-objects](miscellaneous/quest-and-festive-objects/README.md) — 13 sprites
- [tools/crafting](tools/crafting/README.md) — 16 sprites
- [tools/gathering](tools/gathering/README.md) — 17 sprites
- [tools/utility](tools/utility/README.md) — 31 sprites
- [weapons/axes](weapons/axes/README.md) — 3 sprites
- [weapons/bows](weapons/bows/README.md) — 4 sprites
- [weapons/crossbows](weapons/crossbows/README.md) — 1 sprites
- [weapons/daggers](weapons/daggers/README.md) — 4 sprites
- [weapons/maces](weapons/maces/README.md) — 1 sprites
- [weapons/spears-and-polearms](weapons/spears-and-polearms/README.md) — 3 sprites
- [weapons/staves](weapons/staves/README.md) — 5 sprites
- [weapons/swords](weapons/swords/README.md) — 8 sprites

## Rebuild / verify

The editable grouping and family-specific notes live in `classification.json`.
The exported originals remain untouched in their original output directory.
From the repository root (Pillow 12.2 used for the initial build):

```sh
python3 scripts/build-item-reference-library.py --source output/sprite-png-export-20260702-154351
python3 scripts/build-item-reference-library.py --check
```

Generated PNGs, contact sheets, catalog and subgroup notes are checked in so
future AI sessions do not need the original ignored export to use the library.
Edit the classification source and rebuild, not the generated reference images.

Coverage: 486 sprites, 47 subgroups. Export-overflow sprite IDs: [2588, 2589, 2590, 2591, 2619, 2626, 2627, 2628].
