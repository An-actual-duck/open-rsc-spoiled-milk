Selected mythical god crossbows — native cleanup
================================================
Owner selected option 1 for each god from the nine reference-free designs.
Only those actual 48x32 PNGs are inputs here; no old crossbow/mace references.

Saradomin: fill existing string gaps; every original opaque pixel retained.
Zamorak: restore original option 1; fill only right-string transparent gaps
with matching dark red, recolour existing arrow black/charcoal like the horns.
No stock reinforcement, left-string edits, white string or white arrow.
Guthix: thicken brown stock beneath the existing wood/foliage details; every
original opaque pixel retained.

Editable Aseprite layers preserve stock reinforcement, original selected art,
and string/arrow cleanup separately. Inputs and recipe are included.
Rebuild with ROOT set to the absolute worker path:
 aseprite -b --script-param root=ROOT --script dev/myworld/assets/authoring/items/god-crossbows/selected-mythic-v1/refine.lua

All outputs 48x32 RGBA with binary alpha. Receipt records input/output hashes
and changed-pixel counts. No runtime installation or gameplay changes yet.
Preview: output/art/god-crossbow-selected-cleanup/index.html

Guthix cleanup is owner-approved and frozen unchanged.
