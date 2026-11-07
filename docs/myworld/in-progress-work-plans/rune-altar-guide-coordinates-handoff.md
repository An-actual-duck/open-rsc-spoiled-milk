# Rune altar guide coordinates and Law Bangle destinations

Ready for Core review on `fix/rune-altar-guide-coordinates`, based on published
`5d805c233aabe3fe9f9041c65ff1e1ccc7e59633`. The exact pushed review commit is
recorded by the worker handoff command.

## Changes

The Enchanting guide's Altars tab (also exposed through the Magecraft and
Runecraft aliases) now uses the same logical `(x,y,Llevel)` convention as the
player's Tile HUD. All 14 runes retain their names, levels, order and icons.
Only the three formerly packed underground coordinates change their X/Y values;
all surface entries gain an explicit `L0` label.

| Rune | Previous guide coordinate | Correct guide coordinate |
|---|---|---|
| Air | 306,593 | 306,593,L0 |
| Water | 147,684 | 147,684,L0 |
| Earth | 62,464 | 62,464,L0 |
| Fire | 50,633 | 50,633,L0 |
| Life | 283,694 | 283,694,L0 |
| Mind | 297,438 | 297,438,L0 |
| Body | 259,503 | 259,503,L0 |
| Chaos | 232,375 | 232,375,L0 |
| Cosmic | 104,3556 | 104,724,L10 |
| Nature | 392,804 | 392,804,L0 |
| Law | 409,534 | 409,534,L0 |
| Death | 395,3541 | 392,708,L-1 |
| Soul | 611,3599 | 611,767,L-1 |
| Blood | 247,102 | 247,102,L0 |

The related teleport correction was explicitly approved during this task:

| Law Bangle destination | Previous landing (signed coordinates) | Correct landing |
|---|---|---|
| Cosmic | 104,724,L-1 | 103,724,L10 |
| Death | 150,212,L0 | 391,708,L-1 |

These landings follow the existing west-adjacent convention used by the other
altar destinations. Cosmic previously targeted the altar footprint on the old
level; the corrected landing is beside the altar on Zanaris's actual level.
No charge costs, requirements, teleport restrictions, eligibility, object
placements, map files or other destinations change.

## Authoritative map and interaction evidence

`server/myworld.conf` selects the installed layered package with fingerprint
`7e04367b1de143053f66d67a6e5c9b7ff02c00cedb4c917fc249e3902626c8cc`
and manifest SHA-256
`42e20c36f19bd7c5a2ce7cfd2d65c91809c8671ff4bdf7b4e8b45014e8290c2b`.
The server primary profile and paired client package agree. The manager also
confirmed that Oracle's deployed map has this exact fingerprint and manifest;
no live access was performed by this worker.

The regression reads every placement set referenced by that selected manifest,
verifies its digest and paired client bytes, and resolves altar identity through
`ObjectRunecraft.xml` and the `Runecraft.getAltarDef` direct-ID/alias rules. It
checks guide rune IDs, names, required levels and exact placement coordinates.
It does not use the old `SceneryLocsRunecraft.json` as placement authority.

Key installed placement identities:

- Cosmic: `spoiled-milk.scenery.scenerylocsrunecraft-json.000175`, object1203,
  `placements/global/lp10.json`, at104,724,L10. This agrees with
  `ZanarisLocation.LEVEL=10` and the signed-location Zanaris entry/exit routing.
- Death: `spoiled-milk.scenery.scenerylocsrunecraft-json.000176`, object1211,
  `placements/global/lm1.json`, at392,708,L-1. The old guide was wrong even after
  subtracting the legacy `3*944` packed-Y offset.
- Soul: `spoiled-milk.scenery.myworldscenerylocs-json.000002`, object1296,
  `placements/global/lm1.json`, at611,767,L-1.

There are also **11** additional legacy altar-interior placements at
x738–985,y19–71 on level0. They are preserved and excluded from the guide's
existing primary overworld-site list. No new location hints or entry promises
are added. The guide lists the interactive altar object's coordinate, while
the Bangle lands adjacent to it.

## Verification

Passed:

- Java8 maintained client build under the shared heavy-build lock.
- Java8 maintained core/plugins build and server artifact/classpath audit under
  the same lock.
- `python3 tests/myworld/test-rune-altar-guide-locations.py --report output/tests/rune-altar-guide/installed-placement-evidence.json --render output/tests/rune-altar-guide/render`
- `python3 tests/myworld/test-enchanting-data.py`
- `python3 tests/myworld/test-layered-spell-teleport-destinations.py` (3 tests).
- `python3 tests/myworld/test-zanaris-layer-relocation.py` (2 tests).
- Python compilation, `git diff --check`, canonical workspace-layout check.

The new fixture renders the actual guide, native font and rune icons at
512x334 and960x528 for all three aliases. Every line fits: longest203px of335px
available. First, middle and final scroll windows fit vertically; final native
scroll position8 exposes the last six rows including Blood. The PNGs were
visually inspected. The offscreen allocation uses the repository's established
test-only Unsafe pattern and emits four Java compiler warnings about that API.

Generated local evidence, not committed or packaged:

- `output/tests/rune-altar-guide/installed-placement-evidence.json`: all14 guide
  placements, all14 Bangle landings and the11 unlisted duplicate placements.
- `output/tests/rune-altar-guide/render/`: six native guide PNGs and text widths.
- `output/tests/rune-altar-guide/*-build.log`, `native-render.log`,
  `enchanting-data.log`, `teleport-destinations.log`, `zanaris-relocation.log`.

## Coverage limits

All14 Bangle landings are checked against installed terrain overlays, raw wall
fields and blocking type1 scenery footprints. The two revised landings also
have no nearby installed boundaries or type2 scenery walls. The Cosmic landing
tile is transparent walkable platform overlay26; Death is ordinary open cave
terrain. This is **static map/collision-input validation**, not a booted-world
pathfinding or logged-in teleport simulation; dynamic NPCs, temporary scenery,
combat state, costs and access gating are not exercised by this new fixture.
No public server interruption, installation, release or deployment occurred.
