# Underground Pass bridge layer fix

The lever beside the player tile `(711,587,-1)` failed after saying that the
bridge lowered. Oracle's v0.3.6 log shows `Entity legacy Point differs from its
WorldLocation` in `UndergroundPassObstaclesMap1.onOpLoc`, at the bridge's direct
`registerGameObject` call. The bridge exists in the installed map: closed
scenery 726 at `(704,585,-1)`, with lever 733 at `(711,586,-1)`.

The old lever code manufactured detached objects at packed-Y `(704,3417)`.
It did not replace the authored native bridge and failed before crossing.
The subsequent crossing coordinates also used packed Y `3420`.

The lever now finds the existing bridge in its world space and signed level,
then uses the maintained replacement transaction to carry native/authored
identity into lowered scenery 727. The original location retains the identity
needed by the existing ten-second restoration event. An already lowered bridge
is reused without creating another close event. Missing or unrelated scenery
fails safely without manufacturing a bridge or moving the player.

Both lever and lit-arrow crossings use explicit underground coordinates, with
Y 588 and level -1. The arrow's firing-row check also uses geographic Y 585,
which corresponds to the old packed Y 3417. The original ranged requirement,
miss chance, quest progression, crossing steps, and bridge reset delay remain.
The legacy fallback retains its packed object lookup; explicit teleports project
back through the maintained legacy adapter.

## Verification

`UndergroundPassBridgeIntegrationTest` invokes the actual lever and arrow
plugins over the maintained Server, Player, native scenery/collision store,
replacement transaction, and delayed-event implementation. Dialogue waits are
suppressed in the isolated test context; no listeners or player database start.
The original plugin reproduced the exact live exception in this fixture.

The corrected fixture passes 24 assertions covering lowering, native identity,
west/east crossing, ten-second reset rounded to a game tick, repeated pulls,
and absent-bridge refusal. A second bridge at identical XY on the surface
remains the same instance throughout. The fixture preparer also checks that the
installed map still contains the real underground bridge and lever positions.

Reproduce from a worker checkout with a fresh disposable fixture path:

```bash
python3 tests/myworld/prepare-underground-pass-bridge-fixture.py \
  output/tests/underground-pass-bridge/review
ANT_HOME="$PWD/tools/vendor/apache-ant-1.10.5" \
  sh tools/vendor/apache-ant-1.10.5/bin/ant -f server/build.xml \
  test_underground_pass_bridge \
  -Dbridge.fixture.dir="$PWD/output/tests/underground-pass-bridge/review"
```

The existing four Underground Pass swamp-shortcut checks and the compiler
warning gate pass. This focused change does not audit the remaining quest's
obstacles or exercise the complete quest in a legacy runtime. No map, client,
database, or live Oracle deployment changes are included. Public activation
requires a separate authorized server update.
