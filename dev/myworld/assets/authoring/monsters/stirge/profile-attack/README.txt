Stirge profile attack — review candidate

Approved base: near-wing-correction/stirge-near-wing-corrected.aseprite,
checkpoint 63d82348decab4d201c1684857824e2fc7460a8e. Frozen in inputs/.
Only attack frames 15–17 are replaced; all fifteen flight frames stay intact.

Native Aseprite articulation uses true-profile frame 07 for body/head/legs,
profile wing poses 06 and 08 for the stroke. No front/right anatomy is used.
1. Wind up: torso pulls backward and pitches up, head dips back toward the
   body, wings drawn up. 220 ms.
2. Plunge: torso drives forward and levels, head pivots at the collar to
   point the feeding needle nearly horizontally into the target. 120 ms.
3. Stabilize: torso recenters, head lowers, wings sweep up to catch balance.
   200 ms.

Leg layers follow the same rigid thorax transform, preserving all four leg
roots and joint geometry. Head pivots at the collar separately. Wing spans
are foreshortened for the narrow 97x130 attack canvas; profile source outlines
and rust palette are retained. Feeding needle is redrawn as a constant 33px
projected shaft across all three attack poses. Original flight artwork is
unchanged. The layered master retains 18 frames and all nine existing layers;
attack-only.aseprite is also editable. No image generation was used.

Rebuild from worker root:
aseprite -b --script-param root=ABSOLUTE_WORKER_ROOT --script dev/myworld/assets/authoring/monsters/stirge/profile-attack/rebuild-attack.lua
python3 dev/myworld/assets/authoring/monsters/stirge/profile-attack/build-review.py

The Aseprite output enforces right-hand attack bounds. Native export validation
checks all fifteen flight frames, attack canvas sizes and binary alpha. The
review page has before/after playback, pause/step, half speed, individual poses
and the approved profile reference. validation.json records pixel scope/hashes.

Owner review pending. No runtime installation or public deployment.
