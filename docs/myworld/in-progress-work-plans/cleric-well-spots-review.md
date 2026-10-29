# Cleric wells — placeable interaction spots, Core review

Owner scope on October29,2026: **“Prepare the areas now; charging later.”**
The previously approved floors/walls are already installed. This task adds the
missing buildable shoreline scenery and its real invisible interaction path.
The owner also approved the native Thorn sigil visual; artwork status is now
recorded as approved, with no new sigil item IDs or economy changes.

## Game content and generic infrastructure

- Ichor well1354 and Divine wellspring1355 are ordinary game-owned1×1 type0
  nonblocking scenery definitions, paired in serverXML and client source.
  Commands are **Dip sigils** and **Examine**, with the approved flavor text.
- Both use the ordinary game model registry selector `interaction_spot`. Its
  native OB3 is one inset1×1, double-sided transparent quad at nativeY−4. No
  missing-model fallback, special model factory, renderer content-name switch
  or custom ID roster in the toolkit is used. Existing516 model archive entries'
  headers/stored payloads are byte-exact; only one38-byte model is appended.
- Optional `GameObjectDef.isInteractionOnly()` defaultsfalse in all existing
  definitions. Old client constructors delegate to the extra boolean argument;
  serverXML's absent flag remainsfalse. Only explicitly opted-in placements
  receive `RSModel` interaction metadata; the shared model cache is unchanged.
- The setter invalidates the resident geometry version only on a flag change.
  Clone/copy/split retain the flag. Flagged meshes emit no resident GL triangles,
  shadows or glow, even if another definition uses the same opaque native mesh.
  Scene's projected export and software raster treat flagged faces as invisible.
- Scene admits flagged transparent faces into its normal native polygon mouse
  path. It keeps ordinary scenery's picking behavior unchanged. Occlusion is
  filtered only for invisible interactions: actual polygon coverage, camera-ray
  face depth, the selected side's material, repeating texture alpha and existing
  partial-wall UV ranges. Expanded ordinary pick padding is not an occluder.
  Transparent terrain and cutout holes do not hide a nearer eligible interaction.
  The existing special `-2` material differs between backends: legacy raster
  skips it; the GL mesh renders it. The filter follows that actual mode behavior.
- The extra occlusion path activates only during a mouse query over a projected,
  frustum-visible, pickable interaction footprint. Its eligibility check is
  folded into the existing model-rotation traversal. No extra full-mesh pass or
  per-frame native proxy generation is introduced. This is not a zero-cost or
  overall FPS speedup claim.

## Authoring-stage behavior

The standalone toolkit worker discovers the checked client/server metadata and
native transparent geometry to show an **editor-only** footprint guide. It owns
no well assets or ID list; the game remains the custom-content owner. Its exact
READY/integration/capture evidence is coordinated separately by the manager.

Search Scenery for **Ichor well** or **Divine wellspring** after accepting a
content refresh. Place spots on liquid-edge tiles along the entire shoreline,
adjacent to walkable land. Placement does not automatically paint liquids or
fill the perimeter. Floor values27/28 and regular/tall wall IDs220–227 from the
previous approved materials remain available and unchanged.

`SigilWells` is a real registered location plugin. It revalidates world,
world-space/level, current registered object identity, nonblocked standing land
and ordinary object reach. Removed/replaced/stale/wrong-level/out-of-range/walled
targets cannot run the action. During this approved building stage it gives
matching Ichor/Blight or Divine-water/Grace guidance explicitly stating that
infusion is not available yet. It grants/consumes no items, XP or Devotion.
There is no invented infusion balancing and no claim of finished Cleric casting.

## Commands and results

From the activated Core worker, maintained artifacts were built sequentially:

```sh
ANT_OPTS='-Xmx512m -XX:+UseSerialGC' ROOT_DIR="$PWD" bash scripts/build-client.sh
ANT_OPTS='-Xmx512m -XX:+UseSerialGC' ANT_HOME="$PWD/tools/vendor/apache-ant-1.10.5" sh tools/vendor/apache-ant-1.10.5/bin/ant -f server/build.xml compile
python3 tests/myworld/test-cleric-well-spots.py
python3 tests/myworld/test-native-scenery-import.py
python3 tests/myworld/test-client-scene-model-removal.py
python3 tests/myworld/test-renderer-v2-shadow-inventory-cache.py
python3 tests/myworld/test-resident-object-chunk-grid.py
python3 tests/myworld/test-wall-cap-texture-mapping.py
python3 tests/myworld/test-client-scene-instance-store.py
python3 scripts/install-cleric-well-assets.py --check
python3 scripts/audit-cleric-well-archives.py
```

All passed. The combined driver runs sequential256/512MiB JVMs and creates an
isolated immutable native package in a fresh temporary folder. It copies static
configuration/schema inputs with unused isolated DB settings, never accounts,
player databases, owner maps or credentials. No server/game loop/listener starts.

-1,420 actual software client assertions;1,428 projected GL-resource assertions.
 Native decoder/paired definitions, eight facings, exact invisibility, mouse
 hits/misses, nearer/rear opaque geometry, transparent geometry, texture cutouts,
 expanded padding, off-frustum targets, clone/copy/split, geometry cache versions,
 no proxy shadows/glow, and shared-model ordinary-definition isolation.
-364 actual native-replacement runtime assertions. Fresh48×48 authored terrain,
 two blocked liquids,17 authored spots covering every facing, actual raw walls,
 signed levels0/2/−3, eight adjacent land approaches, normal packet→walking
 action→PluginTickEvent→registered Dip plugin→outgoing message delivery, native
 replacement/stale identity, standing-water/range refusal, raw sigil quantity,
 inventory size and Blessing XP preserved. World-space equality is explicitly
 checked by production admission; this does not claim implementation/testing of
 a new instancing system.
- A1,024-footprint Scene mouse-query fixture remains local to the hovered area
 and renders no proxy pixels. The final sample averaged1.09ms software and1.46ms
 capture per complete Scene query on this machine; no general FPS claim.
-10 additive native import tests;718 existing wall texture mapping checks;
 12,000-model Scene removal equivalence, resident grid, shadow inventory cache
 and scene-instance storage regressions passed.
- Existing2277 classic sprite records and1003 custom serialized entries, plus
 original wall/floor definition rows, remain exact under the previous materials
 archive audit. This task changes only the model archive and the two scenery rows.

Game source is unchanged after tested checkpoint
`6e8a0cdca8c983673fd3007bb1dca52b8967b89b`; subsequent work adds tests/docs only.
The final maintained server build includes its fail-closed admission guard.
Evidence and artifact hashes are adjacent in `cleric-well-spots-evidence/`.

Early fixture attempts incorrectly pre-initialized an object before ordered
registration and then attempted to mutate snapshots from the owner's immutable
native package. Those assumptions were removed; the final fixture authors its
own native terrain. The first packet-message fixture also needed the normal
PluginTickEvent drain. These were fixture corrections, not runtime bypasses or
changes to map ownership/immutability.

## Remaining acceptance and boundaries

Owner editor placement/guide inspection and private gameplay are still required.
Software picking and hardware-facing GL data/export were tested offline; no live
hardware OpenGL context or game login was launched. Sigil charging, resources,
recipes, XP and Devotion policy remain part of the Cleric-overhaul follow-up.

No public/private server operations, map imports, owner-save changes, stage-folder
edits, integration, publication or release occurred in this worker.
