# Next update map — offline runtime acceptance, November 1, 2026

The latest frozen export is **not accepted yet**. The maintained runtime found
inaccessible Ichor shores and four unsafe gem-cave ladder return approaches.
No map, owner save, gameplay code, account or public server was changed.

## Inputs and reproduction

The manager exported the frozen generation4748 save with the installed toolkit.
This worker used only the explicit exported server package:

- Package fingerprint: `1143ba2555abde10c888e2d2ddb7f73764cec984651e55a0e4450ca8c25420d3`.
- Manifest SHA256: `385eaaf52aa519d33b42777263841a12d2c614a3ee14ffd49364154f15e8d590`.
- Source runtime: published `1e43ed17761d25290af158e88e1b82abd7c32f69`.
- 1,787 sectors; 4,081 NPC, 926 ground-item, 28,612 scenery and 979 boundary
  placements, totaling 34,598.

Run [the focused runner](../../../scripts/test-next-update-map.sh) from a Core
worker with the explicit package, manifest hash and a new external evidence
directory:

```sh
./scripts/test-next-update-map.sh "$candidate_package" \
  385eaaf52aa519d33b42777263841a12d2c614a3ee14ffd49364154f15e8d590 \
  "$new_evidence_directory"
```

The runner serializes heavy work with the Core lock, builds with JDK8 and a
512MiB/SerialGC budget, and copies only versioned static definitions, sample
configuration and SQL query XML into an isolated fixture. It installs a JDBC
guard, selects `native-replacement`, invokes the maintained whole-catalog
material/placement/collision-footprint/capacity preflight, then populates the real
World. It never calls `Server.init()`, starts listeners, reads accounts, uses
installed owner-map selectors or includes owner/synthetic maps in Git.

The original focused run exits 1: **389 checks, 9 failures**. The
external package fingerprint is unchanged after the run. Build and runtime logs:
`/home/justin/.local/share/next-update-map-acceptance-20261101/runtime-acceptance-02/`.

## Passing checks

| Scope | Runtime result |
| --- | --- |
| Replacement map | Exact manifest; one replacement package; all material, placement, footprint and NPC-capacity preflights pass; full population succeeds |
| Tower entrance | Actual DoorAction passes both lanes inward and outward, preserves the exact rank-binding/free-exit distinction and auto-closes |
| Tower stairs | All five exact upward bindings use actual Ladders transport; all five downward counterparts return freely; all ten destinations have runtime footing and an adjacent movement exit |
| Associates871–876 | Exactly one each, at the reviewed positions, exact stationary bounds, nonattackable/nonaggressive effective definitions; registered and reachable from their passage approach |
| Gem dragons877–881 | Counts10/6/5/4/3 (28 total); every spawn has footing and movement access; all28 connect through actual PathValidation to the cave exit in a1,005-tile component |
| Antidote eggs3400/3401 | 11 Giant Spider Eggs and19 Jungle Spider Eggs; actual spawns and pickup footing; all30 remain absent until the authored60-second delay and respawn as fresh GroundItem instances through the real event scheduler |
| Divine wellspring1355 | All11 markers admit at least one land-side ClericWellInteraction with a movement exit |

## Ichor access blocker

All five Ichor well1354 markers on L-1 fail the real land-side admission:
`(105,670)`, `(103,673)`, `(104,672)`, `(104,671)`, `(105,669)`.
Every surrounding tile is blocked: overlay27 Ichor, overlay255 void, or overlay8
(effective TileDef7: invisible colour12345678, unknown5, objectType1). The log
records each marker's3×3 neighborhood, actual traversal masks, terrain-blocked
flags and scenery-blocker counts. This is not a missing-definition or scenery
collision problem; neighboring terrain is blocked before adding scenery.

A nearby existing seven-tile land component is `(109,674)`, `(109,675)`,
`(110,675)`, and `(109,676)` through `(109,679)`, all L-1. It connects to neither
the markers nor the local ladder5 at `(108,681,L-1)`. That ladder and all eight
neighbors are overlay255/blocked terrain, so it also has no safe local approach.
This provides a bounded authoring reference, not proof that a one-tile fix is
sufficient. Any repair is a separate manager/owner-reviewed map operation.

The subsequently requested ladder-safety extension preserves the original
`BASELINE_RESULT` and adds both-direction contact/round-trip/shore connectivity
checks. The original map returns **422 checks,23 failures** overall: the same
nine baseline failures, plus14 checks exposing the Ichor access problem. The
pair is confirmed as ladder5 at `(108,681,L-1)` and ladder6 at `(108,681,L0)`.
No underground land contact is valid. All eight surface land contacts descend
to blocked overlay255: `(107,680–682)`, `(108,680)`, `(108,682)`,
`(109,680–682)`, all L-1. A preview repair must support every ordinary contact,
return safely, and connect each underground arrival component to all five
Ichor markers. Original extended evidence: `runtime-acceptance-02/original-extended.log`.

A mismatched manifest hash is refused by the maintained runtime contract before
definition loading/population; the refusal smoke exits1 and records
`Selected native package does not match the configured manifest SHA-256` in
`runtime-acceptance-02/manifest-mismatch-refusal.log`.

## Gem-cave return blocker

The cave exit is ladder5 at `(339,612,L-2)`, paired with ladder6 at the same XY
on L-1. Its five clear approaches are admitted by the actual WalkToObjectAction.
Ladders preserves the player's XY for this one-tile ladder, causing four
approaches to land on blocked L-1 terrain:

| Approach on L-2 | Actual destination on L-1 |
| --- | --- |
|339,611|339,611|
|340,611|340,611|
|340,612|340,612|
|340,613|340,613|

All four destinations have traversal mask64, overlay24, terrainBlocked=true,
zero scenery blockers and no raw walls. The fifth approach `(339,613,L-2)`
returns safely to `(339,613,L-1)`. That safe landing belongs to a seven-tile
corridor connected to stairs41 at `(338,614,L-1)`. Actual upward travel reaches
`(341,614,L0)`; stairs42 returns to `(337,614,L-1)`; the reciprocal ladder then
reaches the gem cave. Thus a functioning surface round trip exists, while
ordinary admitted exit approaches remain unsafe.

## Limits

These are real offline World/definition/collision/action/scheduler checks, not
client rendering or owner gameplay acceptance. They do not prove visual
placement, private rank playthrough, combat, drops, sigil resource conversion,
saving or database persistence. Existing synthetic regression fixtures remain
separate. A repaired explicit export must be checked again before map acceptance.
