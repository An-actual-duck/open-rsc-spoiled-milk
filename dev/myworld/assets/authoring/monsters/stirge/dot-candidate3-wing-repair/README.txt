Dot candidate 3 — Core wing attachment repair
=============================================
Owner asked Core to take over this Stirge art and use Aseprite to correct the
front/right frames: the old body position left empty membrane, and the wings
were still attached too low on the repositioned body. The body is NOT moved.

Frozen input: /home/justin/stirge-bat-test-20261114/revisions/candidate-3/.
Its editable master, native exports and anchor metadata are copied in inputs/.
The original Dot folder and all prior Core Stirge candidates remain untouched.
No image generation was used for this repair. Artwork is edited through native
Aseprite image/cel operations by repair-wings.lua.

Scope
 - Only Rust bat wings layer in frames 03, 04 and 05 is changed.
 - Extend missing root membrane into the former body cutout, with the existing
   three rust membrane shades and texture sampled from that same frame's wing.
 - Add a narrow root rib and close residual shoulder gaps bounded by membrane.
 - Move the repaired wing pose +3px headward and -5px upward. Its projected
   shoulder is consistently 1px right and 8px above the unchanged thorax center.
 - Retain wing flap shapes, outer tips, scalloped edges, and the existing palette.
 - Head, body, abdomen, all four legs, feeding needle and their positions remain
   pixel-identical in every source layer/frame. Hidden guides remain hidden.

Rebuild from a Core worker root, with output/art/stirge-dot-wing-repair ready:
 aseprite -b --script-param root=ABSOLUTE_WORKER_ROOT --script dev/myworld/assets/authoring/monsters/stirge/dot-candidate3-wing-repair/repair-wings.lua

Outputs
 stirge-wing-repaired.aseprite: full editable 18-frame, seven-layer project.
 frame-00.png..frame-17.png: 150x130 flight / 97x130 attack native exports.
 stirge-wing-repaired-sheet.png: all 18 native frames in 150x130 working cells.
 output/art/stirge-dot-wing-repair/index.html: focused before/after playback,
 individual phases, and the full updated sheet.

Validation
 Aseprite verifies 123 untouched layer/frame combinations, all original wing
 tips remain within native bounds after translation, and each new shoulder
 meets an opaque upper-thorax pixel. PNG comparisons prove only 03..05 changed;
 the other 15 frames are identical. Editable round-trip sheet equals the native
 exports, with all 18 frames, six tags, seven source layers/five visible layers.
 Native PNG alpha remains binary. validation.json records hashes and scope.

This remains a candidate for owner visual review. No game assets, archives,
NPC definitions, maps, combat values or public server files are installed.
