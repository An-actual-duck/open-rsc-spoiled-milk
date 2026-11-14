Death wing — owner-approved dark-wing native sprite family

The owner approved a front comparison with dark gray/near-black membranes and
subdued dark supports, then requested this palette for every pose. Giant bats
must retain their original appearance, so this is a separate client family
for NPC 768. Server stats, spawns, camera size and combat cadence are unchanged.

Sources: eighteen read-only authentic bat exports already preserved by the
Stirge authoring work, copied from its isolated input folder. Native cropped
pixels and their original offsets are retained. The approved front comparison
is frozen in inputs/approved-front-dark.png. Original bat family 138 is never
edited, tinted, remapped or overwritten.

recolor.lua builds death-wing.aseprite, six three-frame direction exports,
frame-layout.json and a native atlas. It maps three gray membrane colors to
22/23/27, 43/45/51 and 76/79/87, and maps neighboring outer brown supports to
muted darker browns. Conservative central guards protect the head, torso and
leg roots; only membrane and adjacent outer supports are selected. The master
separates original nonwing pixels and recolored wing pixels into two layers.
Alpha, positions and every unselected pixel are asserted unchanged per frame.

Runtime: assets/runtime/sprites/npcs/death-wing/death-wing.png. Five flight
columns are 150 pixels wide; the attack column is 97. Three rows, 130px each.
The client installs only on NPC 768 in both sprite modes, reserving a new
animation after all published aliases. NPC 43 and original bat assets remain
unchanged. The existing camera presentation remains 225x195.

Rebuild: aseprite -b --script-param root=ABSOLUTE_ROOT --script dev/myworld/assets/authoring/monsters/death-wing/recolor.lua
Verification: tests/myworld/test-bat-npc-wing-colors.py; both actual graphics
installers, disk/JAR loading, all native poses, body guards, alpha and exact
approved-front matching. No live-server or map changes.
