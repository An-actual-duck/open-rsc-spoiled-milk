# Item visual consistency repair

The showcase review exposed incorrect worn appearance references and colour masks
that did not target the coloured pixels in the source sprites. This repair keeps
gameplay data and appearance numbering stable while correcting those visuals.

## Scope

- Audited all 3,444 effective item definitions, including 1,839 equipped appearances.
  Repaired 328 stale one-based references: 144 wool gloves, 144 wool boots,
  17 gauntlets, three greaves, eight shields, nine god robe pieces, two Guthix
  symbols and one hood. An explicit paired client/server contract records each
  old reference, corrected reference, expected family and palette.
- Corrected 15 coated battleaxes: their authoring manifest uses zero-based client
  indices, whereas server equipment references are one-based. No animation IDs
  or existing archive entries move. NPC animation references remain zero-based.
- Converted shared staff icon bases into independent shaft/enchantment masks.
  All ten woods and seventeen enchantments now recolour correctly, including
  the Saradomin/Guthix definitions previously overwritten with a ring icon.
- Equipped staves use palette-mask copies of the native frames. Every directional
  and attack frame retains its silhouette, offsets, canvas and hand registration.
  Original archive families remain untouched. Existing appearance IDs are retained.
- Crossbow icons recolour brown wood pixels only. String/metal source pixels are
  preserved. Native log body hues guide the wood colours; the ordinary crossbow
  no longer inherits the unrelated green string tint. Dragon crossbow art stays
  unchanged. Equipped crossbows continue using their existing shared native model;
  this patch changes their inventory/ground wood colours, not that model.
- Bolt inventory/ground icons use their existing canonical metal palettes,
  with a separate green-tipped poisoned base. Dragon bolt art stays unchanged.
- Pine, Palm, Ebony and Blood log icons now accept their existing colour masks,
  rather than rendering as identical ordinary logs. Native Oak/Willow/Maple/Yew/
  Magic log art is preserved. Staff palettes retain their established hues;
  in particular, Magic staves remain purple while native Magic log wood is gold.
- Moonsilver uses an explicit 39x16 target, matching the Silver bar's rendered
  bounds exactly. Its approved artwork is unchanged.

The six small shared icon mask bases have editable Aseprite projects, a replayable
Lua authoring script, original reference identities and runtime hashes. The
original references are unchanged. The final provider exports cover every item.

## Verification

- Client and server builds passed. Static analysis passes with no new gated
  compiler warnings, changed-line findings or SpotBugs findings. Existing baselines
  were not expanded.
- Real server loading verifies all 328 corrections, mode isolation, all-item
  gameplay field invariance, and every coated weapon's one-based appearance.
- Before/after effective exports differ in exactly 343 appearances; all other
  exported fields remain unchanged. All-item field invariance is also checked
  against the real server definitions rather than just the exported subset.
- Packaged-only client loading verifies the 328 expected families/palettes,
  all 180 staff icons/equipped variants and all 18 native frames per held base.
- Crossbow non-wood pixels, staff orb pixels, native alpha and frame geometry
  are checked independently of the runtime colour selection.
- Existing held-family, Slayer art, authentic catalog, stable appearance ID,
  coating generator and provider regressions passed. Combat: 145/145 scenarios.
- Approved current-update icon bytes match their packaged copies and release
  allowlist. Provider exports are deterministic and validate their asset bundle.
- The refreshed 12 showcase GIFs contain 145 frames with exact source colours,
  alpha, durations and infinite loops. Separate contacts show all 180 staff
  combinations and logs beside their crossbow counterparts.

Evidence: worker `output/reviews/item-visual-consistency/`. Updated showcase:
manager `output/art/showcase/item-variety/v002/`. These generated previews are
not tracked game assets. `v001` remains the released-state comparison.

## Limits

This is an item appearance/material repair, not a redesign of legacy equipment
or layering. The broad name/family check accounts for historical names such as
`leathergloves`, `armorskirt` and the existing Dragon Square Shield mapping; it
does not automatically rename or replace such legacy families. Runtime appearance
indices have not been shifted globally. No combat balance, recipes, map files,
public server process, release tag or live database is changed.
