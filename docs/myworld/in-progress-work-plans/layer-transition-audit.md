# Native layer movement audit

Manager update, November 12, 2026: integrated at
`2a6a77a7880302522029c5ad71b6b08613b43047` and staged as server-only `v0.3.20`.
The current-code and Oracle ARM64/JDK17 replays both pass 51 cases / 342 checks;
the running baseline fails 39 cases. The fixes subsequently shipped with the satchel dialogue update in `v0.3.21`,
source `977a5c310b232b4184ca5b87f7ef440665b72b50`; installed callbacks pass 51/342
with class-loading proof. The `v0.3.20` candidate was never activated.
See [staging and maintenance handoff](layer-transition-release-staging.txt).
The worker's original scope and evidence below are preserved as history.

Status: prepared for manager review in `fix/layer-transition-audit`, based on published
`18d57f9ec6ea1ff8a8d4b1226e1c675ed5e81920`. The owner requested an independent
worker audit after the Draynor Manor basement ladder fix. No deployment is part
of this assignment; Oracle remains on the manager's server-only v0.3.17.

## Contract and boundaries

`Player.teleport(x, y, bubble)` intentionally prefers the native origin level
when terrain exists there. When that native destination is absent it can fall
back to checked legacy-packed interpretation. Native X/Y alone therefore must
not be treated as a universal surface teleport.
Hard cross-level destinations must use an explicit signed level or the checked
legacy-packed destination API. Existing relative native ladder movement, source
ownership, gameplay checks, cache resets and legacy behavior must be preserved.
This audit must not globally reinterpret ordinary movement or every teleport.

The already-fixed Draynor quest override is the reference failure: reaching the
right X/Y on level -1 is not a return to overworld level 0. Similar errors can
also occur when an override compares native object/player Y against a legacy
packed value, preventing the branch from executing.

## Audit sequence

1. Inventory direct teleports and suspicious packed-Y predicates in maintained
   gameplay plugins and core travel code, retaining exact file/line/arguments.
2. Review generic ladder special cases and quest-specific object overrides
   first. Correlate source objects and destinations with the installed canonical
   map, existing telepoint data and earlier signed-layer fixes.
3. Separate confirmed defects from valid same-level movement, legacy-only
   fallback, inactive content, and cases requiring map-design decisions.
4. Make small fixes only where source, destination and behavior are supported.
   Use offline production plugin/Player/map fixtures to reproduce representative
   failures and validate signed destination, landing safety and existing side
   effects. Preserve the original source/map and all evidence.
5. Finish with an exact pushed READY handoff, a reviewed inventory, results and
   explicit coverage limits. The manager will collect it later.

No GUI, browser, listener, public server, player database or private browser
fixture is used. Long local builds/tests run serially at reduced priority.
Generated evidence belongs under `output/tests/layer-transition-audit/` and
`output/reviews/layer-transition-audit/`; ignored prior evidence is preserved.

## Results

The audit fixes 28 hard surface-return call sites in ten plugins, plus the
Observatory underground keep gate in an eleventh plugin. Each surface return
now specifies level `0` without changing its coordinates, timing, animation,
quest requirements or original `bubble=false` behavior. The keep gate retains
normal doorway walking; its packed-coordinate guards and destinations are
converted through the existing compatibility helpers.

| Handler | Confirmed repair |
| --- | --- |
| Magic Guild portals | Wizards Tower return from floor 2 lands at `(212,695,0)`; other portals remain unchanged. |
| Dragon Slayer | Four installed ship ladders use the cached Crandor/Sarim destination on level 0. |
| Dwarf Cannon | Tower descent and cave mudpile return to the surface. |
| Merlin's Crystal | Ship cutscene ends outside Keep Le Faye on level 0. |
| Plague City | Mudpile and both permitted sewer-pipe exit paths return to the surface; gasmask denial remains. |
| Holy Grail | Whistle returns from the castle's upper floors reach the surface destination. Both island-return branches are explicit. |
| Waterfall Quest | Cave ledge and the missing-amulet flood return to the surface, retaining damage and dialogue. |
| Gnome Glider | Four ordinary flights and the quest crash return to outdoor destinations after the underground flight scene. |
| Grand Tree | Declining the quest after the roots visit returns upstairs to `(415,163,0)` without starting the quest. |
| Watchtower | Three special cave exits and six web exits return to level 0. |
| Observatory | Installed keep gate `(689,681,-1)` is recognized; permitted passage walks to `(690,681/682,-1)` from either side; locked passage stays blocked. |

No core teleport API, source map, map placement, database, client or deployment
was changed. The compiled core contains the same 16,554 class bytes as the
preserved baseline. The compiled plugins retain the same class roster and only
the eleven listed plugin classes differ.

## Inventory and review record

The read-only baseline inventory covers 1,659 maintained Java files in
`server/plugins` and `server/src`: 833 lexical movement call sites, including
701 qualified plugin calls across 102 files, plus 73 packed-Y comparisons in
19 files. These are finite candidate inventories, not 833 executed routes or
73 proven defects. Comments and strings are masked; receiver types, control
flow and reachability are not inferred by the lexical scan.

Evidence under `output/tests/layer-transition-audit/` includes
`baseline-source-inventory.json`, its `inventory.py` generator, complete
scenery/boundary placement indexes, preserved baseline JARs, callback traces,
class comparisons and successive validation runs. The installed package is
`7e04367b1de143053f66d67a6e5c9b7ff02c00cedb4c917fc249e3902626c8cc`,
with manifest SHA-256
`42e20c36f19bd7c5a2ce7cfd2d65c91809c8671ff4bdf7b4e8b45014e8290c2b`.
All 4,081 NPC, 926 ground-item, 28,612 scenery and 979 boundary placements
were populated read-only for the callback fixture.

The targeted review included generic `Ladders`, the quest overrides above,
packed doorway conditions, legacy rune-altar exits, normal boats, spirit trees,
Lady of the Waves, Lava Forge, core spell teleports and change-level helpers.
Prior Underground Pass, agility and layered-location fixes were treated as
controls rather than replaced. Important exclusions:

- Ordinary two-coordinate same-level movement is intentional. Many destinations
  with packed Y above 944 still work through checked fallback, including the
  untouched Magic Guild upper-floor portals and Cannon tower ascent, which
  pass the real callback tests.
- Generic ladders already use native relative transitions or qualified
  telepoint lookup. Several stale coordinate branches are bypassed by a valid
  lookup or an equivalent relative route. They do not justify changing global
  teleport semantics.
- Ordinary placed boat/spirit-tree/Lady of the Waves sources are surface routes;
  no reproduced cross-level defect was found there. Dynamic movement and NPC
  movement calls are not hard surface destinations.
- RuneScript level-change helpers have legacy assumptions, but the maintained
  Java `LostCity` caller starts on the surface and its packed transition still
  resolves. This does not certify uncalled helper combinations.

## Validation

The maintained runner is `tests/myworld/test-layer-transition-audit.py`; it
builds serially at reduced priority, prepares an isolated fixture, compiles
`LayerTransitionAuditIntegrationTest`, then invokes actual production callbacks
with real `Player` transitions and unmodified installed terrain/placements.
It never calls `Server.init`, opens a listener, initializes accounts or copies
an owner database. Log4j 2.17 dependencies precede the fat core JAR, matching
production. The final replay command is:

```sh
nice -n 10 python3 tests/myworld/test-layer-transition-audit.py \
  --out output/tests/layer-transition-audit/final-review \
  --baseline-plugins output/tests/layer-transition-audit/baseline-plugins.jar
```

The candidate passes **51 cases / 342 checks**. The preserved pre-fix plugins
fail **39 of those cases** on the same unchanged core and installed map. The
other twelve cases are working movement, denial, inspection or decline
controls. The four Observatory baseline failures include failure to own the
installed gate even for locked denial; they are not four separate wrong-layer
landings. The report records exact starts, destinations, outcomes and traces.

Every changed-route destination exercised by the fixture has native terrain,
a non-void authored tile and at least one legal outward walking step. Upper
floor control destinations also have a floor. Controls which intentionally do
not move only prove unchanged scope, not the walkability of an arbitrary
fixture starting tile. Dialogue menus are scripted and waits elided; Merlin's
ship test uses the existing branch where Arhein is temporarily occupied.
Damage, denial behavior, quest-stage preservation and intermediate transitions
remain in the traces.

Additional checks passed: full server build and its generated contracts,
`test-layered-location-followups.py`, `test-quest-shortcuts.py`, workspace layout
validation and `git diff --check`. Final replay metadata binds the tested Java
source digest, fixture source, baseline JAR, candidate JARs and source map.
Earlier `review-3` evidence also passes 51/342; its `sourceCommit` names the
checkpoint before the final two plugin edits, so use the final replay/source
digest when binding results to the handed-off source.

## Remaining candidates and limits

These are explicit follow-up candidates, not certified defects fixed here:

- Plague City's underground gate 457 still contains packed player-Y and packed
  doorway coordinates. Review its keyed/dynamic gate lifecycle from both sides.
- Tourist Trap mining carts compare source Y with 3631/3639. Native placement
  Y is 799/807; reproduce the forced-roll cart success path with Ana/barrel
  state before changing it.
- Legends Quest retains packed coordinate conditions outside prior native
  shortcut handlers. Distinguish bypassed fallback from active quest branches.
- Vampire Slayer coffin/staircase predicates and Fight Arena prison predicates
  merit separate quest-progression review; callback reachability was not proved.
- The old Cosmic altar miniroom exit targets an obsolete packed destination.
  A current route into that legacy miniroom was not established. The physical
  altar, talisman and jewelry flows must not be moved based on this alone.
- Disk of Returning/black-hole classification and unused RuneScript helper
  combinations need a focused lifecycle audit if those routes are re-enabled.
- A few legacy-coordinate bubble checks can suppress a cosmetic bubble while
  preserving the correct movement; this pass did not change those visuals.

This is a bounded route audit, not full-world movement certification. Fixtures
call the real owning plugin directly and prove its block guard, destination
and landing terrain; they do not test network packet dispatch, all approach
paths, full quest walkthroughs, dialogue timing, client rendering, or live
play. Legacy-authority-off projection uses the unchanged explicit-level API,
but no complete legacy-server replay was run. No ambiguous map-design case was
silently repaired and no map edits were made. Manager review, integration and
any deployment remain separate.
