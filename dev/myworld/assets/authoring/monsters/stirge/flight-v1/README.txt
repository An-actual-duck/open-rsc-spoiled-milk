Stirge flight and attack v1 — full candidate for owner review
===========================================================
Owner approved front-v1 and authorized the remaining directions and in-between
frames together. Use the bat's poses and approved Stirge anatomy, with wing
flapping rather than an alternating-leg walk. This is authoring work only: no
NPC IDs, combat definitions, spawns, drops or runtime archives are installed.

Coverage / frame order (same order as the bat)
 00..02 front
 03..05 front-right three-quarter
 06..08 right profile
 09..11 back-right three-quarter
 12..14 back
 15..17 right-facing attack: windup / proboscis thrust / recovery
Left-facing views are mirrored previews, following the bat's directional scheme.

Frames 00..14 are native 150x130. The bat's narrower attack canvases are retained:
frames 15..17 are native 97x130. The Aseprite working project and overview sheet
use 150x130 cells; native attack PNGs retain their narrower canvas on export.
All pixels have binary alpha and use the approved front pose's palette family.
Frame 00 is pixel-identical to the approved starting pose. Front frames 01/02
reuse its core anatomy with a small hover bob; wings follow their reference poses.

Creation / reproduction
The built-in image_gen tool produced one complete directional source sheet from
the approved native Stirge and the existing bat's pose grid. Both inputs and the
generated source are preserved. prompt.txt contains the exact generation prompt.
The returned source was larger than native. export-native.lua exports individual
native frames through Aseprite, quantizes to the same compact palette, aligns
the body motion with bat reference measurements, and assembles the editable
project, native sheet and animated previews. It does not change the source input.
frame-map.json/lua record the source crop boxes and bat anchors. body-alignment
json/lua record the small per-frame translations used to reduce pose jitter.

From the worker root, ensure output/art/stirge-flight-v1/ exists, then run:
 aseprite -b --script-param root=ABSOLUTE_WORKER_ROOT --script dev/myworld/assets/authoring/monsters/stirge/flight-v1/export-native.lua

Preview: output/art/stirge-flight-v1/index.html. The combined GIF cycles through
eight viewing directions, then both attack directions. It has 120 frames at
150ms each. Narrow attack PNGs are padded ONLY for the unified 150x130 previews.
Individual angle GIFs also loop beside the bat pose sheet in the gallery.

Checks recorded in validation.json:
 - all 18 PNGs have the bat's proper native canvas and binary transparency;
 - approved front pixels remain unchanged;
 - all sheet cells equal their individual native exports;
 - all 120 composited GIF frames equal their intended source/mirror/padding,
   with no residual trails between frames;
 - the editable project's 18 frames and six tags round-trip to the same sheet.

Only the original front design is approved. The new poses and motion remain
candidates until the owner reviews the full set. Playback timing is a review
choice; the eventual NPC's gameplay animation driving still needs integration.
