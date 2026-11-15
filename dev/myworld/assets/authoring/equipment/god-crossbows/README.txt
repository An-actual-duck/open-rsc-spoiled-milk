God crossbow equipped visuals — review in progress, November 15, 2026

Owner direction: build on the basic equipped crossbow, emphasize the approved
inventory icons' palettes, simplify fine details but retain visual identity.
First palette-only study was rejected as too similar. The current held review
is detail-v2/: gold star/ray limbs, curled black horn limbs, living bramble limbs.
All fifteen full 64x102 native frames preserve the underlying stock/grip positions.
Slight limb ornaments extend beyond the original crop, inside its full canvas.

inputs/ contains unchanged game-owned custom crossbow frames, extracted from
Client_Base/Cache/video/Custom_Sprites.osar equipment:crossbow. Their original
frame geometry and SHA256 values are recorded in frame-layout.json.
author.lua creates the initial palette study in saradomin/, zamorak/, guthix/.
details.lua adds the current icon-derived ornaments to detail-v2/.
Each export has an editable native Aseprite master; no resampling is performed.
Approved inventory icons are referenced from authoring/items/god-crossbows/
selected-mythic-v1 and remain unchanged.

Reproduce using aseprite -b --script-param root=ABSOLUTE_THIS_DIRECTORY
--script author.lua, then the same invocation with details.lua.
scripts/preview-god-crossbow-visuals.py opens no server and writes the current
held-only review under output/art/god-crossbow-equipped/detail-v2/index.html.

Shot visuals are separate draft work. The owner clarified there should be no
prayer activation animation, then asked to postpone shot review until held
weapons are satisfactory. effects.lua drafts four flight and six impact frames
per god. These remain unapproved. Further shot design work is deferred
until the held review is resolved. Original runtime numbers and mechanics are
unchanged. Runtime archives currently contain only the initial palette study;
detail-v2 is a review candidate and is not installed yet.

Classic aliases retain appearance IDs1170..1172 and use free sprite ranges
4200..4214,4227..4241,4254..4268. The ordinary crossbow family is preserved.
Draft shot IDs43..45 require client10061; per-viewer server fallback sends20 to
older clients. The public server/client, latest user map and Dot's ai-1 work
are not modified by this task. Worker remains ACTIVE pending visual acceptance.
