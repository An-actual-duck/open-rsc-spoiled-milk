Stirge expanded plunge attack — owner review candidate

Approved flight source: near-wing-correction at 63d82348d. Input snapshots
preserve that master and the rejected narrow profile attack at edc050493.
Original Dot profile landmarks are preserved as profile-anatomy.json.

Owner requested a pinball-plunger motion, with an enlarged canvas rather than
shortening the wings: recoil/coil, forward and slightly downward stab, then
a downward wing stroke to restore balance. Attack exports are now 210x160;
flight exports remain their approved 150x130. The full editable master uses
the expanded canvas but preserves all fifteen flight cels at original positions.

Frame 15: body pulls back, head compresses slightly into collar, legs fold
into small angular coils anchored at their four thoracic roots. Wings retain
the approved full native span, raised/folded inward. 240 ms.
Frame 16: torso translates 45px forward and 13px down, with modest stretch;
head extends along the body line. Legs straighten back alongside the body.
Both full-length profile wings sweep back individually, without span scaling.
The feeding needle stays at 14 degrees down and 45px projected length across
all three frames; its tip travels 57.5px forward and 23.2px down into the stab.
120 ms.
Frame 17: body recenters, head returns to its normal profile orientation,
legs dangle; near wing strokes down in front of the body, covering 307 of 617
body-layer pixels while the head remains in front. Far wing stays behind.
240 ms.

Source profile wing stencils contained orange bat-leg remnants below row 41;
those are excluded from the new attack plates. Approved flight is untouched.
All edits use native Aseprite Lua image/cel operations. No image generation.

Rebuild from a Core worker root (create output/art/stirge-plunge-attack first):
aseprite -b --script-param root=ABSOLUTE_WORKER_ROOT --script dev/myworld/assets/authoring/monsters/stirge/plunge-attack/rebuild.lua
aseprite -b --script-param base=ABSOLUTE_WORKER_ROOT/dev/myworld/assets/authoring/monsters/stirge/plunge-attack --script dev/myworld/assets/authoring/monsters/stirge/plunge-attack/validate.lua
python3 dev/myworld/assets/authoring/monsters/stirge/plunge-attack/build-review.py

Validation checks 135 unchanged flight layer/frame combinations, fifteen
unchanged flattened flight frames, four leg roots per attack pose, fixed
needle angle, positive forward/downward travel, binary alpha, dimensions,
recovery layering and overlap. Transform source bounds are asserted before
sampling. Master retains eighteen frames and six tags, plus a tenth layer
for the foreground recovery wing.

Review: output/art/stirge-plunge-attack/index.html. Enlarged and native animated
views, pause/step, slow speeds, three still poses and approved profile reference.
Owner visual review pending; no game/runtime installation or public deployment.

Final owner corrections: frame 16's near-wing plate is moved to the foreground
layer with no pixel or position changes; far wing stays behind. Frame 15's two
far-side coiled legs extend past separate silhouette edges so all four legs
are visible, with all original root positions retained. Frame 17 is unchanged.
inputs/plunge-before-final-fixes.aseprite preserves the reviewed base.
