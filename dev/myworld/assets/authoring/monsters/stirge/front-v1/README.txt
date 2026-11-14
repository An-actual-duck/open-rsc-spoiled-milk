Stirge front pose v1 — unapproved authoring candidate
====================================================
Owner selected Stirge and Sanguinary gland. Work begins with a single camera-
facing hovering pose, using the existing bat's anatomy, posing and native pixel
constraints. Animation must be wing flapping, not a left/right walking cycle.
Do not build other directions or animation frames before this pose is approved.

Reference: Client_Base/Cache/video/Custom_Sprites.osar, npc:bat frame 0.
Bat full canvas: 150x130; visible bounds (2,16)-(148,63), 146x47.
Stirge full canvas: 150x130; visible bounds (4,17)-(146,59), 142x42.
Candidate uses 22 opaque palette colors and binary alpha. No runtime changes.

The built-in image_gen generated the design from the bat reference and prompt.
It returned a larger source image despite the requested native dimensions.
That source is preserved unchanged in inputs/generated-source.png. The included
Aseprite script samples into the native canvas, removes low-alpha export noise
and maps to a compact palette; it introduces no larger canvas or smooth scaling.
The actual native PNG/ASEPRITE files, rather than the large source preview,
are the files for judging game-scale readability.

Reproduce from the worker root:
 aseprite -b --script-param root=ABSOLUTE_WORKER_ROOT --script dev/myworld/assets/authoring/monsters/stirge/front-v1/export-native.lua

Preview: output/art/stirge-front-pose/index.html, native and integer 4x.
No new NPC IDs, drops, gland icon, spawns or combat stats have been installed.
