# Tall stone windows — two material bands

## Owner-approved scope

The owner requested taller stone window variants for the tower's2.15-height
walls while keeping the original1.5-height window proportions. Existing stone
doorway header214 already supplies the approved visual precedent for masonry
from native height192 to275. Four additive passive blocking boundary definitions
provide the existing window appearance below192 and existing stone texture2
above it. Existing windows, quest boundaries and doorway headers are unchanged.

No new textures, scenery model, map format, attachment mechanism, server restart
or public deployment is inherent in this source change. Editor discovery and
rendering of optional definition metadata are separate Toolkit work coordinated
under Core; the editor must not contain a special list of these window IDs.
Scenery stacking is explicitly outside this task and remains investigation only.

| Boundary lookup ID | Name | Existing lower reference | Lower texture, both sides | Upper texture, both sides |
| --- | --- | --- | --- | --- |
|216|High stone window|3|5|2|
|217|High stained glass window|6|18|2|
|218|High stone arrowslit|13|7|2|
|219|High barred stone window|34|27|2|

All four: total275, lower192, upper83, doorType1, unknown0, WalkTo/Examine.
They remain blocking boundaries; they are not passable door-surround carriers.
Their new lookup IDs and existing one-based terrain carriers remain below255.
No ID-specific rendering branch was added.

## Generic optional definition contract

The server XML definition owns three optional integer fields:

| Field | Client/server getter | Meaning |
| --- | --- | --- |
|wallLowerHeight|getWallLowerHeight()|Original lower material band's top, relative to endpoint base |
|wallUpperFront|getWallUpperFront()|Upper band's front native material |
|wallUpperBack|getWallUpperBack()|Upper band's back native material |

All absent/default-zero means legacy single-band geometry. Positive lower height
enables two contiguous quads: original modelVar2/3 materials from0 to lowerheight,
upper materials from lowerheight to modelVar1. Texture0 is a valid active-band
material, not an absent-value sentinel. Signed negative RGB555 materials are also
valid;32767 is rejected as the reserved transparent carrier. Values outside
signed16-bit material range are rejected.

Both client construction and server definition loading reject partial declarations
with lowerheight0 but nonzero upper materials, nonpositive/too-large splitheight,
interactive/quest/passable declarations, or a combination with doorOpeningHeight
or doorSurroundFor. Existing Java constructors delegate with zero defaults.
Server loading already invokes validateDoorSurround for all definitions; it now
validates wall bands first, including ordinary definitions without an opening.
A header may not use a banded wall as its source. Legacy metadata remains valid.

## Actual game geometry paths

- `World.addWallSegmentInput`: builds two immutable wall segment quads for
  cached/native terrain products. Endpoint structural elevations independently
  offset both bands; seam coordinates are shared even on a slope.
- `World.emitWallProduct`: consumes those quads through its existing material,
  light, pick/collision/minimap path. No new renderer helper or ID lookup exists.
- `World.insertWallIntoModel`: builds the same two quads for legacy terrain.
- `mudclient.createWallObjectModel`: ordinary directed/network boundary path;
  active bands allocate8 vertices/two faces, legacy stays4/one. The maintained
  RSModel also grows capacity, but explicit allocation avoids needless growth.
- Roof support uses unchanged total `getWallObjectHeight()`275, not the lower
  band's192. Shared endpoints receive the existing single structural marker;
  bands do not double-count support height.
- Renderer-v2 derives actual triangles, bounds and shadow casters from the
  resulting model: two quads/eight two-sided triangles, Y=-275..0 and a275-unit
  shadow height. No special window IDs or metadata-specific mesh fallback.

All216 earlier server definitions were compared field-for-field with the
pre-task baseline `eb89c714ec1d8570b13783cd5b09ea1f5ddfa47a`. Ordinary windows
still produce exactly their original lower quad, material and physical dimensions;
the new lower window band matches that geometry in each path and direction.
Legacy door header dimensions, opening clearance, collision and roof support
retain their existing regression tests.

## Original texture mapping limitation — corrected October 23

The initial implementation used the **same existing83-unit-face texture mapping as
approved doorway header214**. This gives the agreed visual precedent and fills
the extra wall height without stretching the window texture. It does not claim
continuous brick-course UV alignment with an adjacent unsplit275-unit Highwall7:
the legacy face-relative texture mapping visibly changes masonry course scale
between an83-unit cap and the full-height face. No new UV system, cropped texture
or texture-capacity change was introduced to disguise that difference.
The owner subsequently identified the scale mismatch in the actual tower. The
shared partial-wall U mapping described below supersedes that limitation.

Actual maintained software Scene comparisons were rendered using
`mudclient.createWallObjectModel` and its maintained `loadTextureFromPixels`
palette conversion with existing authentic texture exports. For each of the
four window styles, screen left-to-right shows approved header214, new tall
window, original192-height window, Highwall7. The window's shape and192 physical
height are unchanged; new top height275 aligns with Highwall. Core's manager
reviewed those comparisons and confirmed this meets the agreed header approach.
The owner subsequently approved official inclusion on October 23, 2026.

Read-only texture reference PNGs come from the existing Core export folder:
`output/sprite-png-export-20260702-154351/authentic-sprites/textures/`.
No PNG copy is added to source or an editor asset bundle.

| Texture | Export filename | SHA256 |
| --- | --- | --- |
|2|id-03227.png|6dc2c6e2d9792775e6f564571f53677bc656c7dbfdd9e1bd45601e939322ee6c|
|5|id-03230.png|5c3b375de2ee1e6f351552f2cc35a52a5deb88021511124533b0b7e94833f58e|
|7|id-03232.png|2c45c9846fa0c42b38c6c30fc78990fb829c2a55861f9b6e8980410e5f703a94|
|18|id-03243.png|4d4d32ff41512d3a0d454b143ed94443e533ad7b0714b12dabe697a1a31ab766|
|27|id-03252.png|5feb30efd0b541068919023ba1069211b1635a7f022a731b20217df1fd510473|

## Reproducible checks

```sh
./scripts/build-client.sh
python3 tests/myworld/test-tall-stone-windows.py
python3 tests/myworld/test-stone-door-surrounds.py
```

The Java proof runs901 validation/definition/native emission/legacy terrain/
directed boundary/sloped endpoint/material/roof/collision/renderer-v2 checks.
The prior header proof separately runs1389 checks. The additive Python guard
checks all216 earlier boundary definitions are unchanged and all four new rows
declare the agreed materials/split, rather than trusting a hand-built fixture.

Optional actual-texture diagnostics after compiling the Java proof:

```sh
mkdir -p output/tall-window-tests
javac -cp 'Client_Base/build:server/core.jar:server/lib/*' \
  -d output/tall-window-tests server/src/com/openrsc/server/external/DoorDef.java \
  server/test/com/openrsc/server/io/TallStoneWindowTest.java
java -Xmx384m -Djava.awt.headless=true \
  -cp 'output/tall-window-tests:Client_Base/build:server/core.jar:server/lib/*' \
  com.openrsc.server.io.TallStoneWindowTest server/conf/server/defs/DoorDef.xml \
  /path/to/authentic-sprites/textures output/tall-window-textured
```

DoorDef.xml is historically entirely CRLF. Its format is preserved, avoiding a
whole-file normalization diff. Whitespace checking uses only a transient Git
`core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol` setting;
other whitespace checks remain enabled and no user/global Git config changes.

Private test placements and map-package changes belong only in the manager's
isolated review installation, not in production source or the owner's tower.

## Manager integration and local tool installation — October 23

Core main7687c6d26 integrates the exact READY handoff. Independent maintained
client/server builds and the901new/1389header checks passed after rebuilding
the client classes; the first test invocation used stale pre-update classes,
then passed when rerun against fresh builds. Original216definitions remain
unchanged. Native scenery and combat projectile regression checks also passed.

The private comparison package contains a row repeating original1.5window,
new2.15window and plainHighwall for all four styles. Native startup confirmed
983boundaries and27892scenery with valid new definitions. Its original source
package and the owner's tower project were not changed. Owner visual review
of these specific window variants was subsequently accepted on October 23.

Toolkit mainf796232d97431ca603601347fc9484b085adc52c adds generic discovered
band rendering. Full package verification reported1228tests, no failures or
errors,2skipped. The manager independently ran its source probe against Core
main: four active definitions were discovered with matching client/server band
getters, rendered as four triangles and exposed by the existing wall flyout;
the prior header algorithm proof remained accepted. No content roster was added.

The verified extractable Linux package is retained under
`/home/justin/.local/share/tall-windows-integration-G0wypBwt/toolkit-package/`.
It is installed at `/home/justin/Core-Framework/RSC Development Toolkit`.
The previous installation is recoverable in the same evidence directory as
`previous-toolkit-installation`. Package and per-file hashes passed verification.
Existing saved projects need normal View → Refresh content; none was modified.
The public deployment was not updated or restarted.

## Official content acceptance — October 23

The owner requested official inclusion of the extended window walls alongside
the accepted hanging cage. Boundary definitions216–219 and all supporting
client/server geometry are already integrated in maintained Core main. The
installed Toolkit contains the generic discovered material-band support; it
does not identify these variants by a built-in content roster. The maintained
client was rebuilt with the combined accepted content. Existing projects need
View → Refresh content; no owner map placements, public activation or player
release are part of this acceptance.

## Partial-wall stone texture correction — October 23

The stone cap above both window and door openings originally compressed the
whole texture into83units. The corrected generic mapping samples the same
vertical texture range as the complete275unit wall: U192/275 through1, with
horizontal V unchanged. The full original192unit window texture remains0..1.
Existing `doorOpeningHeight`, `wallLowerHeight` and total wall height supply all
parameters; no wall IDs, new textures, map encoding or per-install asset roster
are added. Ordinary walls and unrelated models retain their exact default UVs.

`WallTextureMapping` provides the shared calculation. `RSModel` lazily retains
per-face U start/scale and preserves it through growth, cloning, merging and
sector splitting. Reusing a face resets its range; changing a range invalidates
the resident model version. Legacy/directed/native producers set ranges from
existing definition data. Native cached GPU wall products, resident object GPU
meshes, projected and near-clipped faces, and the software projective raster
all consume the same range. Software rendering changes only virtual texture
basis points, never geometry, picking, clearance, collision, shadows or roof
support. GPU signatures already include actual UV arrays and the focused test
proves a mapping-only change alters them.

Verification: `python3 tests/myworld/test-wall-cap-texture-mapping.py` exercises
actual producers, four wall directions with sloped endpoints, an arbitrary
360unit synthetic wall with123unit split, all consumers, copy/split/growth/reuse
and clipped-source interpolation. A synthetic striped texture compares the
rendered cap against the corresponding pixels of a full wall at three camera
angles. Integer projective rounding yields1.3–2.2% differing samples around
stripe edges, compared with approximately95% for the old squashed texture.
The meaningful visual scale and phase are retained. Existing901window and
1389door surround geometry/clearance/collision/roof checks pass unchanged.

Optional actual-stone before/after diagnostics, using the existing read-only
texture reference folder and a fresh output path:

```sh
python3 tests/myworld/test-wall-cap-texture-mapping.py \
  output/sprite-png-export-20260702-154351/authentic-sprites/textures \
  output/wall-cap-stone-review
```

Four styles each produce before/after PNGs. Screen left-to-right: doorway
header, tall window, original window, full Highwall. Before views explicitly
restore the old0..1 texture mapping without changing geometry. These diagnostics
are game-rendered software evidence; actual private OpenGL owner review remains
the final acceptance gate. The Toolkit must separately recognize the revised
source algorithm, retain old full-range semantics for unchanged targets, and
apply the discovered range in its preview. No owner project or live service is
modified by this Core worker.
