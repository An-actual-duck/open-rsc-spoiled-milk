# Soul Altar layered-map visual investigation

Status: approved implementation complete; private visual validation pending

Branch: `fix/soul-altar-layered-map-gaps`

Baseline: `e820fcffe9ea56631ff79a059ef3c02f10d136cf`

## Result

The Soul Altar terrain and scenery are not missing from the layered package.
The reported missing center glyph and incomplete surrounding altar visuals come
from the client's fixed altar-visual coordinate tables. Those tables still use
legacy packed Y coordinates, while a native layered underground scene uses
logical Y plus a signed level.

The Soul Altar is authored at legacy `(611,3599)`, which decodes to layered
`global (611,767,-1)`. The native client scene contains the altar at the latter
location. The client nevertheless compares the loaded owner against
`(611,3599)` and, if that comparison were to pass, would calculate the sprite's
local Z from `3599`. Both operations fail:

- owner reconstruction produces logical `(611,767)`, so it does not equal the
  packed table entry;
- the Soul scene base is `(528,672)`, making the stale glyph local tile
  `(83,2927)` instead of the correct `(83,95)`. The stale position is outside
  the 144-tile active window.

The four floating orb visuals around the altar fail identically. Their scenery
owners load at logical Y `765` or `770`, but the table expects packed Y `3597`
or `3602`. This makes the altar surroundings look incomplete even though the
obelisk scenery and terrain are present.

No terrain archive, package, transition-coverage, or residency change is
recommended for this incident.

## Exact affected records

All coordinates below are in world space `global`, signed level `-1`.

| Role | Definition | Layered position | Legacy source position | Layered sector |
| --- | ---: | --- | --- | --- |
| Soul Altar owner | `1296` | `(611,767,-1)` | `(611,3599)` | `(12,15)` |
| Southwest obelisk owner | `1306` | `(609,765,-1)` | `(609,3597)` | `(12,15)` |
| Southeast obelisk owner | `1306` | `(614,765,-1)` | `(614,3597)` | `(12,15)` |
| Northwest obelisk owner | `1306` | `(609,770,-1)` | `(609,3602)` | `(12,16)` |
| Northeast obelisk owner | `1306` | `(614,770,-1)` | `(614,3602)` | `(12,16)` |

The central visual uses the tracked
`dev/myworld/assets/sprites/world/rune-altars/glyphs/soul-glyph.png` asset.
The four obelisk orbs use the established procedural fallback because this
repository intentionally has no element-specific orb PNGs. Neither asset path
is the failure.

The same table defect applies to all three underground fixed altar-visual
families, not only Soul:

| Family | Packed anchor | Layered anchor | Suppressed visuals |
| --- | --- | --- | ---: |
| Cosmic | `(104,3556)` | `(104,724,-1)` | center glyph plus four orbs |
| Death | `(392,3540)` | `(392,708,-1)` | center glyph plus four orbs |
| Soul | `(611,3599)` | `(611,767,-1)` | center glyph plus four orbs |

Surface altar coordinates are numerically unchanged by legacy decoding and do
not expose this defect.

## Terrain comparison

The authored Soul Altar addition changes 470 tiles relative to
`Authentic_Landscape.orsc`. Its exact bounds are
`x=599..622`, `y=748..775`, level `-1`:

| Layered sector | Legacy archive entry | Changed tiles |
| --- | --- | ---: |
| `(12,15,-1)` | `h3x60y52` | 285 |
| `(12,16,-1)` | `h3x60y53` | 185 |

The field-level change counts are 211 elevations, 127 floor colors
(`groundTexture`), 342 floor textures (`groundOverlay`), 46 legacy horizontal
walls, and 52 legacy vertical walls. Roof and diagonal-wall fields are
unchanged in this footprint. The main connected altar component contains 444
of the 470 tiles; the remaining tiles form the authored approach details.

The footprint crosses the storage-sector seam at Y `767/768`, but it does not
touch an active-scene edge. For a center sector of `(12,15)`, the client owns
the complete 3x3 authoritative window `x=528..671`, `y=672..815`. The entire
altar footprint occupies local `x=71..94`, `y=76..103`, safely inside that
window.

The following sources are byte-identical for the relevant terrain:

- server and client `Custom_Landscape.orsc` (whole-archive SHA-256
  `c48f9734f8faf027b9128c28dfcece468d3e84a5c1ed4b9a4452c2481392b6ee`);
- the standalone World Builder source, working, and builder-runtime copies;
- active package sectors `xp12-yp15.raw` and `xp12-yp16.raw`;
- the package mounted by the detached public server, inspected read-only.

The package sector hashes are respectively
`35571858394a321c0d27ab94e8261f1decdb6662d8bb066e64f4c34aa9e6d9ba`
and
`9cd1b174a16bb0bbfa7f014791001d73b30cb22e00485c9128de62cc2a8ad86b`.
Reversing the generator's intentional horizontal/vertical wall-byte swap
reproduces both legacy archive entries exactly.

## Package, transition, residency, and seam audit

- Package generation decodes legacy plane 3 to signed level `-1`, converts
  `h3x60y52` and `h3x60y53` to `(12,15,-1)` and `(12,16,-1)`, and performs an
  exact reverse-transform check before writing the package.
- `placements/global/lm1.json` contains the altar and all four obelisks at the
  logical coordinates listed above. Private startup populated the package with
  27,887 scenery records and did not report a rejected Soul record.
- The native initial terrain receipt is a radius-one 3x3 sector window. Center
  `(12,15)` therefore includes Y sectors 14, 15, and 16. The later symmetric
  radius-two visual/structural stages extend residency but are not required to
  make either Soul sector available.
- Client terrain application iterates all 144 by 144 local tiles and sources
  both available sectors. The internal Y `767/768` storage seam maps to local
  Y `95/96`; it is neither filtered nor cropped.
- Static-scene authority uses the same 3x3 half-open window. The two obelisks
  on each side of the seam are included. The outer presentation ring is not
  involved in their ownership.
- There is no independent projectile, minimap, or renderer-v2-only placement
  path for these visuals. Both classic and OpenGL ultimately receive the same
  owner-gated world sprites from `mudclient`.

## Why existing tests missed it

`test-altar-obelisk-symmetry.py` currently compares the client tables directly
to the legacy placement JSON. That proves packed-source symmetry, but it also
enshrines the stale representation. It does not decode both sides to
`WorldLocation` or exercise a native layered level.

The package, native residency, atomic activation, exact scene visibility, and
client/server landscape-sync suites all pass. That is useful negative evidence:
they show the map product is complete, but none exercises altar visual anchors
after the client switches from packed to logical runtime coordinates.

## Recommended implementation

1. Replace the altar visual tables' untyped coordinate pairs with anchors that
   carry world space, signed level, and logical X/Y.
2. Decode the existing authoritative placement coordinates once using the same
   checked legacy mapping used by package generation. Do not use an unqualified
   `% 944` at draw time, because that would permit cross-level owner matches.
3. Match owners only when world space and signed level agree with the active
   layered scene. Resolve those logical anchors into the current client runtime
   projection for drawing. Preserve the packed-coordinate projection for a
   legacy/non-layered scene.
4. Apply the family-level correction to Cosmic, Death, and Soul rather than a
   Soul-only exception.
5. Convert `test-altar-obelisk-symmetry.py` to compare decoded logical anchors,
   then add a small client harness proving:
   - surface legacy and native anchors still match;
   - Cosmic, Death, and Soul owners match in native level `-1`;
   - the center glyph and four orbs resolve inside their 3x3 windows;
   - a same-X/Y owner on the wrong signed level does not match.
6. Add a Soul-specific terrain characterization that checks all 470 changed
   tiles across sectors `(12,15)` and `(12,16)` and confirms the client active
   window applies both. This protects the observed seam without changing
   correct terrain data.

After implementation, visually enter the Soul Altar on a private server in
both classic and OpenGL modes. Confirm one center glyph, four orb visuals, all
four obelisk owners, and continuous terrain across Y `767/768`. Repeat Cosmic
and Death because they share the same latent defect.

## Approved implementation

The approved family-level correction is implemented in
`AltarVisualAnchor`. Each fixed visual anchor is decoded once from its
authoritative legacy placement into a typed global-world location containing
logical X/Y and signed level while retaining its checked legacy packed-Y
projection.

The client now:

- selects logical coordinates only while native layered terrain is active;
- retains packed coordinates for the legacy loader;
- requires world space, signed level, and projected coordinates to match an
  altar or obelisk owner;
- includes world space, level, and projection in the scene-revision cache key;
- uses the same selected projection when positioning glyph and orb sprites.

This corrects Cosmic, Death, and Soul together. Surface altars retain identical
logical and legacy coordinates. A same-X/Y object on another level cannot own
an altar visual.

`test-altar-obelisk-symmetry.py` now compares decoded logical coordinates and
runs a Java harness over both projections. It covers surface compatibility,
all three underground families, wrong-level and wrong-world-space rejection,
and native-window placement. `test-soul-altar-layered-seam.py` characterizes
all 470 reviewed terrain changes, both storage sectors, the Y `767/768` seam,
the generated raw-sector hashes, and the complete footprint inside one native
active window.

## Local verification performed

- Launched a private production-layered server on `127.0.0.1:43615`; startup
  validated package `rsc-remastered.spoiled-milk-layered-world@0.5.0` and its
  complete placement population.
- Launched the current desktop client with boundary diagnostics; no public
  service was changed or restarted.
- `python3 tests/myworld/test-altar-obelisk-symmetry.py` — PASS.
- `python3 tests/myworld/test-altar-visual-fallback.py` — PASS.
- `python3 tests/myworld/test-soul-altar-layered-seam.py` — PASS.
- `python3 tests/myworld/test-layered-native-package-foundation.py` — 15 PASS.
- `python3 tests/myworld/test-layered-scene-visibility-rings.py` — PASS.
- `python3 tests/myworld/test-native-terrain-symmetric-residency.py` — 4 PASS.
- `python3 tests/myworld/test-native-terrain-atomic-activation.py` — 4 PASS.
- `python3 tests/myworld/test-landscape-client-server-sync.py` — PASS.
- `python3 tests/myworld/test-renderer-v2-world-geometry.py` — PASS.
- `./scripts/build-client.sh` — BUILD SUCCESSFUL.

Private visual confirmation remains required before handoff.
