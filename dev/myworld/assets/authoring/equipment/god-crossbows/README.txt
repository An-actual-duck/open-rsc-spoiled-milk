God crossbow held visuals and prayer-shot studies — November 15, 2026

Approved held frames
--------------------
The owner approved bolt-v5/ after review of every direction, limb shape,
string attachment, loaded bolt and front-diagonal nose position. These are now
installed in both candidate sprite archives and runtime/sprites/equipment/
god-crossbows. approved-held.json pins all45 accepted PNG hashes. Inventory
icons under authoring/items/god-crossbows/selected-mythic-v1 are unchanged.

inputs/ preserves the unchanged game-owned equipment:crossbow frames extracted
from Custom_Sprites.osar; frame-layout.json records their native framing/hash.
All held exports retain the original full64x102 coordinate space and grip.
Accepted limb silhouettes need new cropped bounds/offsets, which the packer
calculates without moving the full-canvas pixels. The ordinary crossbow family
and unrelated archive payloads are preserved byte-for-byte.
The exact source loaded bolt geometry is restored as a separate native Aseprite
layer, with gold/charcoal/wood colors. Shafts are never inferred from a changing
bounding box. Source193-grey highlights and adjacent148-grey shading are retained.

History is preserved: author.lua creates the palette-only study; details.lua
creates rejected detail-v2 ornaments; silhouette-v3.lua restarts limb shapes;
anchored-v4.lua fixes per-frame string/limb anchors and rear occlusion; bolt-v5.lua
fixes missing bolts and front-diagonal mounts. Run the selected Lua recipe with:
 aseprite -b --script-param root=ABSOLUTE_THIS_DIRECTORY --script RECIPE.lua
Do not regenerate accepted PNGs without checking approved-held.json afterwards.

Current prayer-shot study
--------------------------
shots-v3/ is the current unapproved study. shots-v2/ preserves the first subtle
version, and effects.lua plus the root god folders preserve the older discarded
procedural-bolt/impact draft. The current study borrows the existing runtime
projectile-moving/bolt-basic/bolt-basic.png (unchanged copy in shots-v3/inputs).
The static19x16 bolt shape/alpha is identical in each phase. Six32x32 phases add
small fading trailing pixels; no bolt deformation, activation animation or
impact burst. The owner requested stronger trails and greater palette separation:
bright yellow Saradomin, charcoal/red Zamorak, dark brown/green Guthix.
Rebuild with aseprite -b --script-param root=ABSOLUTE_SHOTS_V3 --script author.lua.
Only the three current flight/trail PNGs are packed; impact drafts stay authoring.

Preview and packaging
----------------------
 scripts/preview-god-crossbow-visuals.py -> art/god-crossbow-equipped/bolt-v5
 scripts/preview-god-crossbow-shots.py -> art/god-crossbow-prayer-shots/study-v3
 scripts/build-god-crossbow-visuals.py [--check] -> approved held archives/runtime,
 current prayer-shot runtime PNGs, preserving unrelated archive content.
 All output paths are under the declared output categories. Firefox previews
 require no server, accounts, network or gameplay edits.

Compatibility and checks
-------------------------
Appearance IDs1170..1172 stay fixed. The classic families use4200..4214,
4227..4241,4254..4268. Classic aliases replace padding, leaving other IDs intact.
Candidate client10061 adds shot IDs43..45, captured with the prayer identity at
launch. Per-observer server fallback sends normal bolt20 to older clients,
including observers of NPC attacks and PvP. Prayer-off bolts retain normal
presentation, poison, resources, power and damage. No combat tuning changes.

The client visual fixture exercises actual custom/classic archive allocation,
sprite selection and packaged sheets. PNG checks prove unchanged approved held
bytes and a static original projectile shape across every flight phase. God
crossbow runtime checks cover real primary launches and older/newer observers.
Evidence: output/tests/god-crossbow-visuals and output/tests/god-crossbows.

This worker remains ACTIVE while the shot visuals are reviewed. No public
release/deployment, map import, live database change or Dot ai-1 change occurred.
Desktop/browser player packages will be rebuilt separately for release.
