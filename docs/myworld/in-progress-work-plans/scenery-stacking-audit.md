# Same-position scenery stacking audit

Owner requested investigation after the tall-window update on October23,2026,
specifically whether individual crates can be placed vertically on one another.
This is a read-only finding, not authorization to implement a new stack system.

## Result

The current maintained game does **not** support arbitrary vertical stacks of
separate scenery placements at the same world-space/X/Y/level anchor. The
editor's refusal is therefore justified for this example. No restriction was
removed and no map, runtime, collision or network behavior was changed.

This is not a blanket assertion that every visual/footprint overlap is forbidden:
scenery and wall boundaries can coexist, larger models can extend beyond their
anchors, and levels/world spaces have independent slots. Those cases do not
provide a per-object vertical offset or two crates in the same scenery slot.

## Evidence chain

- `server/src/com/openrsc/server/io/NativeLayeredWorldPackage.java`: scenery
  schemas accept identity, definition, position and direction (plus optional
  agility metadata in v5), not a vertical offset. The decoder rejects a duplicate
  scenery slot at the same WorldLocation even with another placement ID.
- `server/src/com/openrsc/server/model/world/NativeLayeredGameObjectRegistry.java`:
  runtime slots contain location/type/direction; scenery normalizes direction to
  zero, and registration rejects an already occupied slot. Different rotations
  cannot create a second scenery slot there.
- `server/src/com/openrsc/server/model/world/World.java`: legacy registration
  finds the current scenery at the point and applies a replacement transaction,
  rather than accumulating independently raised crates.
- `server/src/com/openrsc/server/external/GameObjectLoc.java` and
  `server/src/com/openrsc/server/io/NativeLayeredSceneryPlacement.java`: placement
  state has no generic editable vertical offset.
- `server/src/com/openrsc/server/net/rsc/generators/impl/PayloadCustomGenerator.java`:
  ordinary scenery updates carry ID, X, Y and direction. Layer/context authority
  does not add a per-object vertical displacement.
- `Client_Base/src/orsc/mudclient.java`: ordinary scenery materialization and
  static presentation translate models to terrain elevation. Hardcoded special
  offsets are not an arbitrary placement-height facility.
- `refreshGroundItemSupportHeights` uses `getGroundItemVar` for ground items on
  scenery. It does not raise another scenery model; this is not stack support.
- Toolkit `ModernEditor.guardFootprint`, placement records and native save
  representation do not offer separate scenery heights. Simply lifting the
  editor guard would produce data the current target cannot use correctly.

Current active crate definitions all bind the native model named `crate`,
including the generic storage-crate variants. No separate pre-stacked crate
choice was found in the active scenery catalog. The native model itself has
10vertices/6faces; it is not an authored multi-crate stack.

## Verification and limits

The existing native-package refusal test, including its duplicate-scenery-slot
case, passed independently:

```sh
python3 tests/myworld/test-layered-native-package-foundation.py \
  LayeredNativePackageFoundationTest.test_package_refuses_changed_payload_undeclared_level_and_bad_chunk_size
```

An initial broader15-test run passed13 but failed two historical archive-based
checks because `server/conf/server/data/Custom_Landscape.orsc` is absent. Neither
the window patch nor this audit changes that path. Those unrelated historical
fixture failures were not bypassed, repaired or counted as passes. The focused
duplicate-slot test passed. Logs are preserved in the tall-window integration
evidence directory, including `stacking-duplicate-refusal.log` and the broader
`stacking-map-validation.log`.

## Recommended next choice

For tower decoration, make server-owned **pre-stacked crate scenery**—for
example two-high, three-high and an uneven pile. One placement can contain
multiple crate forms in a single native model, just like other composite props.
The current game/editor can already represent that, with normal discovery and
one coherent collision/picking definition. Actual model/footprint choices still
need a separate approved asset task and in-game review.

Freely stacking independent arbitrary scenery would be a larger coordinated
feature: map schema, runtime identity/storage, transport, model-height offsets,
collision/removal/picking and editor save/import all need explicit support.
No implementation of either option was performed by this audit.
