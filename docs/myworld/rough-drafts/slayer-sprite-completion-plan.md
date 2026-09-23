# Slayer sprite completion plan

Owner direction: 2026-09-22. This is an artwork plan, not an implementation or
deployment claim. Approved inventory candidates are recorded in
[the art manifest](../../../dev/myworld/art-candidates/slayer-items/README.md).

## Work order

1. Finish inventory/ground icons: remaining NPC drops, then the five gimmick
   solutions. Complete simple equipment variants/recolors in this inventory
   pass; the six unique reward base icons are already approved.
2. Complete the easier worn/held equipment, reusing existing sprite geometry
   and animation through palette swaps wherever possible.
3. Build the Abyssal Whip's new wielded sprites last as a dedicated project.
   It does not fit an existing weapon silhouette; do not force it into a sword
   recolor. Use its approved inventory design as the material/design anchor.

No new Serpant's Tail, Naga unique weapon, Banshee armor, Cockatrice armor or
Abyssal Demon armor is added by this plan. Ectoplasm remains an active drop
with no armor use yet. Review hides/leathers for reuse/recolor, not automatic
new designs. Existing demon ash/bones can retain their established artwork.

## Inventory equipment finish

- Poisoned Dagger of Terror: approved bone dagger with the conventional green
  poison tip, matching existing poisoned daggers. Do not recolor the entire
  dagger or redesign its silhouette.
- Leather armor: existing armor icon shapes, palette swaps only, all usual
  five slots. Use the same family palette for the worn sprites.

| Family | Approved color |
| --- | --- |
| Giant Frog | Vibrant blue |
| Naga | Green |
| Terror Dog | Red |
| Bloodveld | Fleshy beige |
| Dark Beast | Black, retaining readable shaded form |
| Ugthanki | Yellow |

These choices supersede the temporary armor tints. Preserve highlights and
shadows when recoloring rather than flattening every pixel to one color.

## Worn and held equipment

Before selecting sources, inspect the existing custom family spritework and
a verified working item. The owner reported newer equipment inheriting legacy
held sprites (Tin dagger using sword visuals). Apply the source-selection rule
now; the broader [equipment assignment audit](../in-progress-work-plans/custom-equipment-sprite-assignment-audit.md)
is documented as a separate follow-up, not a completed fix.

- Leaching Bow: standard longbow geometry and animations; make the drawstring
  red, with no major other visual changes. The approved inventory bow remains
  separate; this is not a request to regenerate it.
- Sullen Pendant: inspect existing white worn amulet visuals and recolor the
  ornament blue; retain existing wearable geometry/animation.
- Dagger of Terror: recolor a suitable existing held dagger to the approved
  inventory bone palette. No bespoke geometry required if this reads well.
- Shield of Mobility: recolor the wooden shield with the cockatrice palette.
  Matching the inventory icon's circular feather silhouette is not required.
- Leather sets: palette swaps using the table above, not new modeled sets.
- Thunder Spire Staff: inventory art is approved. Specific worn source and
  treatment have not yet been chosen; inspect existing staff options during
  the simple-equipment pass rather than assume a new complex sprite set.
- Abyssal Whip: deliberate new wielded art/animation work, last in sequence.

## Remaining inventory subjects

NPC parts: Sticky Saliva Gland, Cockatrice Eye, Cockatrice Feathers, Frozen
Tear, Terror Fang, Leach Tongue, Lightning Horn, Slimey residue, Abyssal Rib,
Abyssal Vertibrae, and Ectoplasm. Hides/leathers should be reviewed for reuse.

Gimmick items: Slime Solvent, Eye Drops, Wax earplugs, Dog Treats, Static
discharge wipe. Account for charge variants without inventing separate designs
unless a visual distinction is needed. Solvent is shared by frog and demon.

## Review and integration

Use the reference library and inspect source icons at native canvas size.
Show actual 48x32 exports and equal integer pixel zoom, not only large
generated concepts. Preserve approved files, use versioned candidates, and
request approval before enlarging the canvas. Art approval does not mean the
assets have been installed in the client or deployed.

The tier correction (bow 8/Ebony Logs, staff 9/Magic Logs, whip 10) is tracked
separately in the equipment implementation ledger and still requires a gameplay
patch; artwork choices must not silently restore the superseded tiers.
