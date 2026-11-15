Approved anti-coagulant scimitar artwork — style B
================================================
Owner selected B (dripping across the blade), November 14, 2026.
approved-style-b.aseprite/png preserve the exact native 48x32 selected sample.
Inputs/inventory were exported from the existing game renderer. Inputs/held
are the raw scimitar frames from the game-owned Custom_Sprites.osar. This is
existing RSC artwork with an approved burgundy coating, not external imported art.

apply-style-b.lua applies the exact selected inventory overlay to all fourteen
materials. The eighteen held frames use attached short inward rivulets on
original neutral metal only. Alpha, hilt colours, crop, shift and canvas remain
unchanged. Raw frame crops can extend beyond their declared display canvas;
these legacy offsets are intentionally preserved, including negative offsets.
Editable Aseprite layers preserve original art separately from the coating.

Regenerate native exports with Aseprite --script-param root=REPO --script
apply-style-b.lua, then run scripts/generate-scimitar-coating-art.py.
The packer reads source-owned PNGs, appends one native family and verifies
all unrelated custom and authentic archive entries against their baseline.
Client aliases are 1176..1189 (one-based); authentic records are 4300..4317.
Existing published animation indices stay fixed in both modes. Authentic-mode
padding refers to the existing sword family and creates no new sprite range.

Inventory/ground icons are runtime PNGs; held assets use the native archives.
The older account-avatar exporter safely resolves these sparse appearances
to its existing uncoated scimitar pose; in-game clients use the coated family.
No combat stats, bleed rules or recipes are changed by this artwork.
