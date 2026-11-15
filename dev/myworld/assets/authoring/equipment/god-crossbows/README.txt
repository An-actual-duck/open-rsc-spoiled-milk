God crossbow equipped visuals — review in progress, November 15, 2026

Owner direction: build on the basic equipped crossbow, emphasize the approved
inventory icons' palettes, simplify fine details but retain visual identity.
First palette-only study was rejected as too similar. The ornamented detail-v2 study was rejected as messy. Current held review
is silhouette-v3/, restarted directly from unchanged inputs/: Saradomin has
continuous crescent gold limbs; Zamorak has black horned limbs extending forward
from the original string attachments; Guthix uses brown stock and limbs with
small ivy patches across both. No stars, extra spikes or rays.
All fifteen full 64x102 native frames preserve the underlying stock/grip positions.
Slight limb ornaments extend beyond the original crop, inside its full canvas.

inputs/ contains unchanged game-owned custom crossbow frames, extracted from
Client_Base/Cache/video/Custom_Sprites.osar equipment:crossbow. Their original
frame geometry and SHA256 values are recorded in frame-layout.json.
author.lua creates the initial palette study in saradomin/, zamorak/, guthix/.
details.lua preserves the rejected ornament study in detail-v2/.
silhouette-v3.lua creates the current baseline restart.
Each export has an editable native Aseprite master; no resampling is performed.
Approved inventory icons are referenced from authoring/items/god-crossbows/
selected-mythic-v1 and remain unchanged.

Reproduce using aseprite -b --script-param root=ABSOLUTE_THIS_DIRECTORY
--script author.lua, then the same invocation with silhouette-v3.lua for the current revision.
scripts/preview-god-crossbow-visuals.py opens no server and writes the current
held-only review under output/art/god-crossbow-equipped/silhouette-v3/index.html.

Shot visuals are separate draft work. The owner clarified there should be no
prayer activation animation, then asked to postpone shot review until held
weapons are satisfactory. effects.lua drafts four flight and six impact frames
per god. These remain unapproved. Further shot design work is deferred
until the held review is resolved. Original runtime numbers and mechanics are
unchanged. Runtime archives currently contain only the initial palette study;
silhouette-v3 is a review candidate and is not installed yet.

Classic aliases retain appearance IDs1170..1172 and use free sprite ranges
4200..4214,4227..4241,4254..4268. The ordinary crossbow family is preserved.
Draft shot IDs43..45 require client10061; per-viewer server fallback sends20 to
older clients. The public server/client, latest user map and Dot's ai-1 work
are not modified by this task. Worker remains ACTIVE pending visual acceptance.

Current revision: anchored-v4 (owner correction)
----------------------------------------------
Silhouette-v3 was close, but had string/limb registration problems from scaling
landmarks to each frame's bounding box. Anchored-v4 starts from the unchanged
baseline again and uses explicit anchors for all fifteen frames, recorded in
frame-anchors-v4.json. No bbox scaling remains. Original stock pixels and grip
positions are retained; clean strings terminate at the same anchors as limbs.
The Saradomin crescent is thicker, the hidden-side Zamorak horn is omitted in
rear frames12–14, and Guthix has denser ivy on brown wood. Disconnected remnants
from replaced original limbs are removed in native Aseprite. Editable masters
and exports are in anchored-v4/. This revision remains a review candidate.
Current preview: output/art/god-crossbow-equipped/anchored-v4/index.html.
Shot visuals remain deferred. No runtime installation of this candidate yet.
