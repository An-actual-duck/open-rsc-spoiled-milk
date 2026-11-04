# Dot assignment: Gem dragon remaining walking directions

Owner handoff, October 26, 2026. Art review task only. The side and diagonal-front
walking cycles have passed owner review. Complete the remaining walking directions
in Aseprite, preserving that approved design and its primitive RSC aesthetic.
Do not implement NPCs, change game archives, assign IDs, edit maps, or deploy.

## Workspace and deliverables

Use Aseprite for the spritework, including editable layered/frame-based sources,
manual corrections, assembly, registration and GIF export. Aseprite Lua is suitable
for repeatable operations. Do not substitute a model render or a newly illustrated
style. This assignment does not authorize uploading the original game assets to
external generation services; the reference inventory carries rights restrictions.
Work locally with the supplied references. Preserve every approved input.

This is a local asset-authoring handoff, not activation of a Core code worker.
Put new candidates under:
`/home/justin/spoiled-milk-next-update/art/gem-dragon-dot/`
Use separate versioned folders per direction. Do not edit the Core manager, an
unallocated Core worker, Toolkit, Sprite Baker, or Model Studio implementation.
If code-repository implementation becomes necessary, report it for Core allocation.
Keep scripts, notes and checks with the candidate. Do not commit large art assets
into Core. Core will review and arrange any later game integration separately.

For each remaining direction produce:

- Three distinct full-canvas transparent PNG poses and an editable three-pose
  `.aseprite` source, with useful layer names where separation is practical.
- A looping GIF in runtime walking order **0, 1, 2, 1**, not just 0, 1, 2.
- Native-scale PNGs and nearest-neighbor enlarged views.
- A browser preview comparing original dragon and gem dragon in the SAME direction,
  synchronized at the same pose, with play/pause, slow playback and frame stepping.
- A contact sheet including approved profile and diagonal-front for consistency.
- A short review report: source frame IDs, offsets/canvas treatment, changes,
  validation, local output paths and remaining concerns. Clearly label candidates
  as awaiting owner approval; do not claim gameplay acceptance.

Create directions sequentially: **front → diagonal away → away**. Finish and inspect
one complete three-pose cycle before proceeding. Owner prefers direction-by-direction
review, so keep each independently reviewable; no need to stop after every export
if there is useful independent work remaining. Attacks and recolored gem variants
are outside this assignment; retain references for a later task.

## Exact original dragon being followed

This is the game's **authentic neutral/recolorable dragon sprite family**, not a new
Blender model, OSRS dragon, or a generic white-dragon illustration.

Read-only family:
`/home/justin/rsc-sprite-trainer/data/local/organized-animation-library-v7-authentic/full/npc/dragon/authentic/`

Read `metadata.json` and inspect `index.html` plus individual files in `frames/`.
Files are named `dragon-id-1323.png` through `dragon-id-1340.png`.
Source archive recorded by metadata: `Authentic_Sprites.orsc`.

| Direction | Original sprite IDs | Family slots | Status |
|---|---|---|---|
| Front | 1323, 1324, 1325 | 0–2 | Create |
| Diagonal front | 1326, 1327, 1328 | 3–5 | Approved gem v4; preserve |
| Side/profile | 1329, 1330, 1331 | 6–8 | Approved gem side v1; preserve |
| Diagonal away | 1332, 1333, 1334 | 9–11 | Create |
| Away/back | 1335, 1336, 1337 | 12–14 | Create |
| Attack reference | 1338, 1339, 1340 | 15–17 | Reference only; not this task |

Confirm orientation by opening the actual sprites before drawing. Walking frames
have a **226 × 163 logical canvas**. The PNGs in `frames/` can be cropped exports:
metadata contains `x_shift`, `y_shift`, `bound_width`, and `bound_height`.
Reconstruct logical canvases using those offsets; do not independently center or
fit each cropped frame. Attack frames have a different logical width (335), so do
not accidentally apply that width to the walking sequence.

Original runtime walk sequence is `{0,1,2,1}` in
`/home/justin/Core-Framework/Client_Base/src/orsc/mudclient.java`.
Review timing of 220 ms per step is illustrative, not a new gameplay-speed decision.

## Approved design and animation references

All paths below are under `/home/justin/spoiled-milk-next-update/art/`.

### Definitive profile design: `gem-dragon-profile-v3/`

- `generated-concept.png`: approved high-resolution profile design (1477 × 1065).
- `gem-dragon-profile.png`: prepared 226 × 163 sprite/scale reference.
- `gem-dragon-profile.aseprite`: editable prepared reference.
- `prompt.txt`: original detailed approved design brief.
- `preview.html`, `comparison.png`: original-versus-gem design comparison.

### Approved side motion: `gem-dragon-side-movement-v1/`

Inspect `preview.html`, `walk-cycle.gif`, `registered-0.png` through
`registered-2.png`, `review-0.png` through `review-2.png`, and
`gem-dragon-side-three-poses.aseprite`.

### Latest approved diagonal-front: `gem-dragon-diagonal-front-v4/`

This is the latest owner-approved result, including the corrected tail sway.
Inspect `preview.html`, `three-poses.png`, `walk-cycle.gif`, the three `registered-*.png`
and `review-*.png` files, and `gem-dragon-diagonal-front-three-poses.aseprite`.
`rebend-tail.lua` documents the final tail correction. V1–V3 and the unselected
experiments are historical iterations, NOT alternative approved targets.

Use the profile for creature identity/material design, the latest approved cycles
for consistency, and each corresponding ORIGINAL direction for pose and motion.
Do not use an unapproved older gem candidate as the design anchor.

## Detailed drawing prompt

Create the requested directional walking cycle of the SAME gem dragon as the
approved profile. It is an original RSC dragon undergoing uncomfortable parasitic
crystallization: its own anatomy is mineralizing, not wearing crystals pasted on
its surface. Match the original dragon's proportions, camera, grounded mass and
rough low-resolution raster aesthetic while carrying the approved deformities
coherently into the new view.

WINGS: the membranes and supports themselves have become brittle, heavy and
incapable of flight. Contiguous irregular faceted plates fracture into missing
chips and jagged, crumbling edges. The wings retain recognizable draconic structure
and shift with walking, despite their damaged stiffness. Do not leave an intact
smooth wing underneath decorative crystal drawings.

JOINTS: a few readable rough uncut gem protrusions erupt through selected knees
and ankles, deforming the limb contours. Preserve the anatomical placement from
the approved design across directions; do not add a neat row of spikes or jewelry.

FACE: the affected eye is sealed shut by mineral growth through and around its
socket. Preserve the original long snout, jaw, recognizably draconic head and
approved asymmetry. Infer occlusion honestly when the affected side faces away;
do not mirror the disease arbitrarily or invent an open eye on its sealed side.

BACK: a large bulbous asymmetric crystalline mass grows uncomfortably out of the
back, crowding the wing root and changing the silhouette. It is a substantial
roughly faceted deformity, not another thin horn, regular dorsal crest, or accessory.

PALETTE/TEXTURE: neutral white/grayscale body and minerals, with fixed surviving
yellow accents matching the approved images. This is a base intended for later
gem-color recoloring. Restrained rough polygonal shading, readable pixel clusters,
slightly uneven primitive edges. No soft airbrush, anti-aliased illustration feel,
glow, glitter, rainbow crystals, glossy modern rendering, or smoothing that erases
the RSC texture. Preserve transparency and avoid edge halos.

MOTION: follow the original dragon's whole-body walk. Animate three genuinely
different poses with coordinated gait, upper-body sway, neck/head movement,
heavy wing response, and a flexible tail swinging back and forth. Maintain a
consistent grounded registration without freezing the upper body to achieve it.

## Critical lesson: do not freeze the upper body or tail

The most common rejected result so far was good-looking legs beneath an almost
stationary head, torso, wings and tail. Compare the original cycle by stepping
frames: it has subtle but deliberate motion throughout its body. The crystalline
wings can be heavier and less flexible, but the creature must still walk dynamically.

A tail correction also failed when it merely moved/rotated the SAME closed curl
up and down. The owner specifically wants lateral tail swinging and changing
curvature: bend/unbend along its length, change the arc and tip direction, and
allow an intermediate open S-shaped sweep. A rigid curled silhouette translated
or rotated as one piece is not sufficient. In approved diagonal-front v4 the
sequence now changes from curl to open S to extended sweep. Use that as evidence
of the required motion quality, not a literal identical tail pose for every view.

Every pose should advance the motion. Do not repeat the exact tail/head/wing
pixels in two unique source poses merely because the feet changed. The repeated
passing step in playback is intentional; duplicated artwork among the three
unique poses is not the intended solution. Avoid excessive flailing or growing/
shrinking anatomy. Growths remain attached to the same moving anatomical sites.

## Scale, registration and review checks

- Keep the full creature and swinging tail in frame with modest transparent padding.
- Use a common canvas, scale and registration for all three poses in a direction;
  no per-frame tight fitting or automatic recentering that produces swimming feet.
- Approved raw concepts use 1477 × 1065 and shared 226 × 163 review projections.
  Preserve that established relationship or document a necessary canvas adjustment;
  do not distort proportions just to fit a box. Inspect at actual intended sprite size.
- Reconstruct the ORIGINAL reference with its recorded offsets before comparison.
- Check `0→1→2→1→0`, including the loop boundary, at normal and slow speeds.
- Inspect head, wing tips, spine lump, joints and tail separately through all poses.
  Look for stationary upper-body regions, unchanged tail curls, detached gems,
  torn warp seams, clipped tips, color drift and altered body scale.
- Validate transparent exports, three unique poses, correct repeat order, and that
  reopened Aseprite composites reproduce exported PNGs. Review visually as well
  as mechanically; unique hashes alone do not prove useful animation.
- Preserve approved files, owner projects and saves. No server/client operations,
  production archive edits, map imports, item/NPC ID changes, or releases.

## Handoff to Core

Provide the review HTML path for each completed direction, source `.aseprite`
paths, frame/offset mapping, a compact validation report and any unresolved visual
limitations. Stop before integration. Core and the owner approve appearance before
these become game content. No usage/billing monitoring or dashboard prerequisite
is part of this assignment; the owner handles that separately.
