# Approved Slayer movement previews

These eight sheets are final owner-approved, reference-guided AI sprite artwork
from the independent RSC Sprite Trainer project. The owner explicitly requested
their Core integration on 2026-09-17. Only the reviewed final PNG exports are
imported, byte-for-byte; no source caches, models, training data or other project
history are included. See `provenance.json` for source-relative export names and
SHA-256 identities. The source project remains unchanged.

## Movement-only runtime contract

Columns 0–4 are front, diagonal front, right-facing side, diagonal rear and rear.
Rows are idle, first step, opposite step. The frog instead has crouch, kick,
landing. Existing opposite camera directions mirror the corresponding columns.
Original additional attack columns are preserved. The subsequent giant frog
combat pass enables its attack column; other attack columns remain disabled.
Banshee has only five columns.

The native pixels, transparency, padding and unequal attack-column widths are
preserved. Presentation starts at 2.4 world units per native pixel, uniformly
across directions; this is a movement-test baseline subject to in-game scale
review. Bloodveld's 120x110 cells preserve its approved 10-pixel walk padding.
The remaining movement cells are 100x100. No recoloring or regenerated art.

The client supports these through both custom and authentic sprite loading,
including PNG resources embedded in the client JAR. Ordinary NPCs keep their
existing animation sequence. These previews use 0,1,0,2; frog uses 0,1,2; a
stationary preview displays row 0. Walking speed remains the server's existing
NPC movement behavior. This does not add hop physics or change tile traversal.

## Spawn for testing

Use an updated matching client and MyWorld server build, on an administrator
account. `::spawnnpc ID 4 10` creates one temporary NPC with a four-tile roaming
radius for ten minutes. Use an open walkable area. Radius zero is useful for
checking idle. No persistent spawns are added and these creatures do not respawn.

| ID | NPC | Command |
| --- | --- | --- |
| 863 | Giant frog | `::spawnnpc 863 4 10` |
| 864 | Cockatrice | `::spawnnpc 864 4 10` |
| 865 | Banshee | `::spawnnpc 865 4 10` |
| 866 | Naga | `::spawnnpc 866 4 10` |
| 867 | Terror dog | `::spawnnpc 867 4 10` |
| 868 | Bloodveld | `::spawnnpc 868 4 10` |
| 869 | Dark beast | `::spawnnpc 869 4 10` |
| 870 | Abyssal demon | `::spawnnpc 870 4 10` |

NPCs 863–869 now have staged combat implementations. Abyssal demon 870 remains
a harmless, non-attackable fixture with placeholder level-one stats. All are
non-aggressive. No tower placements, new drops or access gates are supplied.
Existing NPC IDs, including Gorak 861 and Green Dragon 862, remain unchanged.

Dark beast uses the approved normalized 120×110 base and an added lightning
column, packed by `tools/myworld/PackDarkBeastLightning.java`. Source art and
registration are preserved in `../dark-beast-lightning/` and described in the
[combat contract](../../../../../../docs/myworld/rough-drafts/dark-beast-combat-test.md).

Giant frog now has a first ranged combat implementation; see
[the combat test contract](../../../../../../docs/myworld/rough-drafts/giant-frog-combat-test.md).

Acceptance: watch all directions and camera rotations; check facing, mirrored
directions, ground alignment, size, clipping, leg alternation and the complete
frog hop. Check stationary idle, walking/stopping, client restart and packaged
asset loading. Automated tests cannot certify the final in-world appearance.
This integration does not automatically update the separate World Builder 2
installation or authorize a public-server restart/deployment.

## Verification

```sh
./scripts/build-client.sh
python3 tests/myworld/test-slayer-movement-preview.py
python3 tests/myworld/test-gorak-visual-npc.py
python3 tests/myworld/test-monster-slayer-client-npc-definitions.py
python3 tests/myworld/test-client-external-asset-loader.py
./scripts/build-server.sh
```

Tests cover source hashes, packaged PNG identity, all 120 directional source
frames, the eight-direction mapping/mirroring contract, cadence/idle selection,
client definitions and harmless server metadata. Live visual acceptance remains
pending; no real account, live server or map is altered by the tests.
