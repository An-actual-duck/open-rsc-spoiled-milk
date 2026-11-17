Rebirth goblin art reference
Saved 2026-11-17. Art exploration only; no runtime installation.

START HERE
Read style-notes.txt and the Rebirth concept document:
docs/myworld/rough-drafts/rebirth-visual-overhaul.md (repository-root path).
Open preview.html for the two selected stills at native and enlarged size.

SELECTED REFERENCES
v1/goblin-a.png and goblin-a.aseprite: original front-facing A, selected for
minimalist detail and simple shading. Preserve this as the identity anchor.
away-static-v2/goblin-a-away.png and goblin-a-away-static.aseprite: owner-accepted
rear three-quarter upper-right still, constructed with a reviewed geometry guide.
No complete directional animation set has been accepted.

PROMPTS AND REPRODUCTION
v1/selected-a-prompt.txt: exact submitted prompt for selected A.
v1/prompts.json: exact original prompts for A/B/C; no reference images supplied.
v1/sources/a-generated.png: original generated source.
v1/prepare.lua: Aseprite native preparation; provenance.json records hashes.
v1/selected-a-palette.json: original 19 opaque colors.
away-static-v2/: exact prompt, original source, geometry guide, preparation Lua,
brief, provenance and independent static-review summary.
These records preserve submitted instructions, not undisclosed model internals.
No reproducible generation seed/model build was recorded; prompts alone do not
guarantee identical images. Reuse the approved masters as visual references.

HISTORY, NOT ACCEPTED REFERENCES
v1/goblin-b.aseprite and goblin-c.aseprite: unselected style alternatives.
walk-front-v1/: eight-frame front-walk consistency experiment; useful broad motion,
with forehead seam and stray pixels around the backstepping foot noted by owner.
walk-away-v1/: rejected diagonal-away study. Ears misplaced/joined on rear skull;
torso/arms face a different direction from face/legs; legs appear single-file.
Do not propagate these errors or confuse this study with accepted away-static-v2.

PORTABLE BUNDLE
Run: python3 dev/myworld/assets/authoring/npcs/rebirth-goblin-style-study/package-reference.py
from the repository root. Bundle keeps concept document, all study provenance,
selected art, historical studies with status notes, and checksums together.
If installed locally, multidirectional-sprites skill/checklist are included as
a dated workflow snapshot; game code does not depend on the personal skill path.
