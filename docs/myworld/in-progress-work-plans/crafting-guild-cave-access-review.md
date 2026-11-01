# Crafting Guild cave access

Branch: `fix/crafting-guild-cave-access`, based on `0670db45d68be5bcab9d3a4146f9ea3e59c900c7`.

The Master Crafter previously refused every use of the extension stairs. The surface
stairs now require **permanent Crafting level 70**. A boosted level 69 remains
ineligible; a drained level 70 remains eligible. The existing outer guild door's
level-40/apron rules and the Master's Talk-to/cape purchase dialogue are unchanged.

The exact trigger is scenery 42, `go down`, at global `(338,614,level 0)`, with a
player in the same world space and level. The native signed location prevents
same-coordinate stairs on other levels from acquiring this gate. Master lookup
also stays in the surface guild's domain and existing roam rectangle.

Underlevel players hear these two lines from the Master, or receive the same
information as system messages when no suitable Master is present:

- “You need level 70 Crafting to enter the caves below the guild”
- “Gem dragons lurk down there. Be prepared for a dangerous fight”

Eligible players immediately receive the danger warning and enter through one
call to the maintained `Ladders.onObjectAction` handler. There is no confirmation
menu, second teleport, new destination calculation, persistent flag, item cost or
additional restriction. Ascent and unrelated stairs retain the normal default
handler, so players below level 70 can always leave.

## Validation

Java 8, `ANT_OPTS='-Xmx512m -XX:+UseSerialGC'`, and the shared heavy-build lock:

```sh
flock /home/justin/.local/share/core-heavy-build.lock \
  sh tools/vendor/apache-ant-1.10.5/bin/ant -f server/build.xml test_crafting_guild_cave_access
python3 tests/myworld/test-crafting-guild-extension.py
python3 tests/myworld/test-brown-apron-crafting-guild.py
python3 tests/myworld/test-heroes-guild-stairs.py
git diff --check
```

The target successfully builds server core/plugins and passes **71 assertions**.
It uses actual `PluginHandler` dispatch, checks that exactly one callback is
queued, and executes that selected production callback with the existing offline
fixture. Only dialogue waiting is suppressed. The fixture checks levels 1/69/70/71,
boosted 69, drained 70, exact object/action/coordinates, signed negative/positive
level aliases, normal native descent once, unrestricted default ascent, the
missing-Master warning, a Master on another level being ignored, and both actual
NPC dialogue lines. A different world space is checked through the production
location predicate; this is not a claim that arbitrary runtime instances are
already supported. Packet capture verifies the immediate system messages.

The updated extension source/placement guard and unchanged brown-apron and Heroes'
stairs guards pass. All production changes are confined to `MasterCrafter.java`.
No combat/Slow/client behavior changed, so those broader gates were not rerun.

Evidence retained under the worker's ignored
`output/crafting-guild-cave-evidence/`:

- `runtime-final.log`: final successful builds and 71 assertions.
- `python-checks.json`: exact adjacent passing commands and results.
- `baseline-tanner.json` / `baseline-tanner.log`: inherited adjacent failure.

The unchanged `test-crafting-guild-master-tanner.py` still fails with
`Master tanner shop should stock GIANT_LEATHER`. This was reproduced from a
separate tree containing its exact seven versioned inputs from the task's base
commit. All seven current inputs are byte-identical to that baseline; their
SHA-256 receipts are recorded in `baseline-tanner.json`. No tanner stock or test
assertion was changed to conceal that unrelated retired-item expectation.

## Remaining acceptance

Core must integrate the exact READY handoff and run the focused gate plus the
latest prepared map's runtime acceptance. Private visual/gameplay acceptance of
this revised dialogue is not performed here. No server was launched, stopped or
restarted; no listener, account database, owner map, map selector, asset, local
configuration, toolkit package or live deployment was changed.
