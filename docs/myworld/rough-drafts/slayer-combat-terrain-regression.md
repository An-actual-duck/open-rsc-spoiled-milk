# Combat gate: native-terrain fixture correction

Resolved 2026-09-24. Test-only changes; no gameplay, terrain package, provider,
configuration, server restart or deployment changes.

## Root causes

The default `ant test_combat_strict` loads the installed native map. Several
older fixtures assumed mutable legacy `TileValue` edits controlled all terrain:

1. Two projectile-domain scenarios changed NPC floors with
   `setLocation(packedPoint, true)`. For NPCs that boolean controls presentation;
   it does not authorize ordinary movement to leave native terrain. The fixture
   now uses the production typed `teleport(WorldLocation)` API, including its
   explicit transition validation. Assertions still require no impact damage
   and `LAUNCH_DOMAIN_DEPARTURE`, plus new signed-level and immutable-launch
   location checks.
2. The Heroes Guild test attempted to remove a real native railing by changing
   a legacy tile. Native collision is derived from the authoritative package.
   The test keeps the actual railing and launches on its clear side, then moves
   the target behind it during flight. Player-friendly/enemy-blocking launch
   checks, settled player damage and `IMPACT_PATH_BLOCKED` remain asserted.
   The legacy-only fallback still constructs its synthetic railing when no
   native terrain owns the location.
3. Four generic hard-cover/fence cases modified legacy collision directly.
   They now use a verified clear corridor and real registered structural-wall
   or railing boundary objects, carrying native package/generation identity
   when applicable. Each object is removed after its case. Existing native
   boundaries cannot be replaced. All damage, producer compatibility and
   collision-reason assertions remain in place.
4. Shuriken resource and magic rune/XP fixtures used fixed locations that
   contained native cover. They now use read-only clear-area selection while
   preserving the 2x2 three-target shuriken layout and adjacent spell-target
   pairs. No sibling counts, rune costs, XP, preservation or replay assertions
   were weakened.

`CurrentCombatHarness.clearCombatRectangle` searches a bounded real-terrain
area, checks ordinary and enemy-projectile paths for every ordered tile pair,
and avoids earlier fixture actors. It fails explicitly if no suitable area
exists; it does not edit or suppress terrain, ignore failed path checks, or
change a loaded package.

## Verification

Run from `server` without `JAVA_TOOL_OPTIONS`/alternate map-discovery roots:

```sh
bash ../tools/vendor/apache-ant-1.10.5/bin/ant test_combat_strict
```

All **144 scenarios pass** against installed native terrain. The default gate
was rerun for repeatability, followed by `test_slayer_rewards` and
`test_thunder_spell_casting`. Deliberate observer-failure log messages are
expected characterization scenarios, not gate failures.

These changes fix test setup, not combat rules. No new authority to walk off
native terrain, shoot through cover or escape projectile-domain checks was
introduced.
