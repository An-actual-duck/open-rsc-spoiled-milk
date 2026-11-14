Stirge v2 — focused underside and four-leg consistency pass
=========================================================
Owner requested retaining the general design, making the front underside fit
the more insect-like other views, and showing four spindly legs consistently
anchored to the thorax. No new wings, palette, head, outfit or posing redesign.
V1 is preserved unchanged. This revision is an unapproved authoring candidate.

The built-in image_gen tool produced an underside anatomy guide from the native
front pose and the owner's supplied insect-like Stirge reference. The complete
source, exact prompt and user reference inputs are preserved in this directory.
export-front-guide.lua makes its limited-palette native authoring export.
Only the abdomen silhouette from that guide is imported into the three front
frames, excluding its generated legs and preserving the old feeding proboscis.
All actual legs are authored as four explicit native jointed polylines.

refine-legs.lua starts from the existing V1 editable project. It preserves the
existing wings, head, ears, eyes, palette and flight/attack poses. It changes
the front underside, removes old short feet below the abdomen, and adds four
spindly jointed limbs as separate near/far editable layers. Legs attach to two
thoracic pairs, never to the tip of the abdomen. Far legs are occluded by the
body where appropriate; their distal portions are separated for readability.
Foreground legs overlap the abdomen in front views; back views layer them behind.

Landmarks record measured thorax/abdomen positions per frame. The small shared
leg rig follows that body's translation and pitch; each root snaps onto an
actual brown thorax pixel. Only distal joints have a shared one-pixel flutter,
not alternating footfalls. leg-attachments.json records all four roots per frame.
No generated leg remnants are imported with the front abdomen correction.

Export sizes and sequence are unchanged: flight frames 00..14 are 150x130;
attack frames 15..17 are 97x130. Aseprite project/sheet working cells are 150x130.
Five stored directional groups and one attack group, three frames each. Left
views are mirrored previews, as in V1. The combined GIF has 120 frames at 150ms.

Reproduce from the worker root, with output/art/stirge-leg-consistency-v2/ ready:
 aseprite -b --script-param root=ABSOLUTE_WORKER_ROOT --script dev/myworld/assets/authoring/monsters/stirge/flight-v2/export-front-guide.lua
 aseprite -b --script-param root=ABSOLUTE_WORKER_ROOT --script dev/myworld/assets/authoring/monsters/stirge/flight-v2/refine-legs.lua

Review gallery: output/art/stirge-leg-consistency-v2/index.html. It includes the
old/new front, all animated directions/attacks, and an optional thorax-root
overlay for checking attachment consistency without modifying any artwork.

validation.json records native sizes and binary alpha; four distinct occupied
thorax roots for every frame; no changed pixels outside the explicit anatomy
edit masks; an exact editable-project round trip with 18 frames, six tags and
three layers; and all 120 composited GIF frames matching native source pixels,
mirroring and preview padding. The older set remains available for comparison.

No NPC definitions, runtime sprites, game archives, spawns, drops, gland icon,
combat mechanics, public server or release files have been changed.
