# Generic double-door passage

The editor can place an unrestricted double doorway using the existing
`doubledoorsclosed` / `doubledoorsopen` models. This is a focused extension of
the existing guild-style **open → pass through → close** interaction, not a
new saved door-state system. Rank checks and nearby guard NPCs are separate
future gameplay work. Ordinary single doors, double bank doors and quest
interactions are unchanged.

The appended server/client literal definitions are paired through the
`DOORS_GENERIC_PASS_CLOSED` switch case in `DoorAction`, whose direct
`doDoubleDoor(player, obj, SceneryId.DOORS_GENERIC_PASS_OPEN.id())` call
identifies the counterpart. The editor discovers this source relationship;
an editor-maintained object ID list is not the contract.

Both definitions have width 1, height 2 and collision type 2. **The temporary
open model deliberately remains blocking**, like a guild door's open frame:
another player cannot follow through and bypass a future access check. This
is not the permanently open double doorway, nor the existing manual doors'
passable type-3 open state.

`Functions.doDoubleDoor` delegates to the focused `DoubleDoorPassage` helper.
It preserves either lane on either side in all four cardinal rotations and
keeps the exact signed level. A failed safety check leaves the player and
object unchanged. It requires actual authored native-package terrain and
identity; it does not treat classic default/upper-level walkability as proof
that a landing exists. Existing classic door handlers remain available.

Before opening, both occupied tiles require real, fully rendered walkable
floors. The raw surrounding support is read to apply the existing renderer's
upper-floor edge cleanup: narrow upstairs hallways with void outside the
walls are supported, but a floor with an invisible half is refused. Collision
checks subtract only this door's exact recorded contribution from a detached
copy, and then reject full blockers or another wall across the passage.
Continuing side walls are allowed and no runtime collision is removed.

The actor passes immediately, as with `doGate`; the temporary visual closes
after two game ticks. An owner-independent, `ALLOW_MULTIPLE` callback closes
only the exact still-current temporary object. Logout/reset does not prevent
closure; removal, replacement or a changed native generation cannot resurrect
or overwrite a door. No player busy flag or pending token is created.

## Verification

Run the maintained server and client builds first. A disposable fixture and
actual World/Player/plugin/helper/scheduler test do not start listeners or
read accounts:

```bash
python3 tests/myworld/prepare-double-door-fixture.py /absolute/new/evidence/fixture
ANT_OPTS='-Xmx512m -XX:+UseSerialGC' bash tools/vendor/apache-ant-1.10.5/bin/ant \
  -f server/build.xml -Ddoor.fixture.dir=/absolute/new/evidence/fixture \
  test_double_door_passage
```

The fixture tests both sides and both lanes in all cardinal directions on
levels 0, 1, 2, 3 and -3; collisions before/during/after passage; duplicate
clicks; continuing side walls; unrelated blocking scenery and overlapping
wall edges; narrow upper corridors and empty rendered half floors; missing
authored support; reset/logout; simultaneous doors; and stale close callbacks.
The paired client definition test cold-loads the maintained client JAR and
matches the discovered server rows, models, dimensions, collision and commands.

No release, deployment, map import, account mutation or public-server restart
is part of this work. Editor preview and private gameplay acceptance remain
separate owner review steps.
