# Slayer Tower ruined props — native review batch

## Scope and approval

On October 23, 2026 the owner requested an in-game look at five Model Studio
candidates: the larger 2×1 broken table, destroyed barrel, fallen pillar,
dilapidated bookshelf and ruined books. The owner subsequently approved the
four unaffected props and confirmed the corrected barrel works better. The
five assets are accepted for use; this is not a public deployment or permanent
world placement.
The proposed statue remains deferred. No standalone editor changes or bundled
editor assets are required: content is defined in the selected game installation.

The original recipes were authored in independent Model Studio, handoff
`31f651aea2c0e8a44adc000a3ab8b65bc65dae59`, integrated as `e6d3032`.
No Git history, Blender application or editable source mesh is imported into
Core. Original masters, manifests and source OBJ/MTL remain preserved locally:

`/home/justin/rsc-model-studio/projects/local/<asset>-manager-001/`

The `<asset>` names below use hyphens rather than the native model underscores.
Those are original closed-component meshes using flat colours, not copied
native models or texture images. Existing game renders were read-only style
references. The game owns the reviewed native exports.

The owner approved the table, pillar, bookshelf and book pile in game. After
reporting bottom-plank clipping and camera-dependent flicker, the owner tested
and accepted the corrected barrel on October 23, 2026 before requesting the
next scenery batch. Its corrected original export is preserved in
`/home/justin/rsc-model-studio/projects/local/destroyed-barrel-manager-002/`,
Model Studio handoff `4367492`, integrated `7b5b320`. The other four reviewed
meshes are unchanged. This continuation edits asset geometry only, not game
draw ordering, lighting or collision systems.

## Additive game definitions

| ID | Name / native model | Footprint | Movement | Vertices / faces | Height, native units |
| --- | --- | --- | --- | --- | --- |
|1340|Broken table / sm_broken_table|2×1|Blocked|140 /100|90|
|1341|Destroyed barrel / sm_destroyed_barrel|1×1|Blocked|190 /135|89|
|1342|Fallen pillar / sm_fallen_pillar|2×1|Blocked|62 /41|69|
|1343|Dilapidated bookshelf / sm_dilapidated_bookshelf|2×1|Blocked|202 /149|183|
|1344|Ruined books / sm_ruined_books|1×1|Passable|170 /127|43|

The table's 2×1 size is owner requested; other footprints and collision choices
are explicit initial integration assumptions. The small book pile is passable
floor decoration; the four larger props use ordinary type1 collision. All five
are projectile-transparent under the existing normal scenery policy, verified
without adding a new classifier exception. WalkTo/Examine only, no new plugins.
All earlier definitions and native models retain their IDs and bytes.

## Native encoding and provenance

The maintained `scripts/import-native-scenery.py` importer converts authoring
Z-up coordinates to native `(128X,-128Z,128Y)` and quantizes linear MTL diffuse
colours through sRGB to native negative RGB555 materials. Native ground is Y=0.
Both face sides use identical materials; no textures, UV mapping, shaders,
new animations or generated-model fallback. Ordinary game scenery lighting is
unchanged. Neither collision nor final appearance is inferred from Blender alone.

All 502 previously stored archive headers, payloads and ordering—including the
approved collapsed wall—remain exact. Their concatenated header/payload SHA256:
`fb29bcd37aaf2b61d8f5cdc70bc564b921ff533e7c2f911f61a1d2112f7d890e`.

New complete models.orsc SHA256 after the barrel correction:
`d88245d27c95d4f4aaf15cf6a8a640a7850ec8b40d84b588915625cc47fdddb6`.

| Asset | Source OBJ SHA256 | Native OB3 SHA256 |
| --- | --- | --- |
|broken-table|0728fc352ea2fd5f9344076eb39b31e48f350770f460f0c072044e3429abff1e|fe75c68fca1fccb61aa0a1d88e5f1ef77570d0037082b79959c45add8b4abaf2|
|destroyed-barrel|c67a02e321f4efa90a2e0dd1697b026814ceb237ffa872dbc0dc1a74fb8554fe|99614bd48f28e1743ef348022715cdaf7c91cf5fa8cb32f30dafdd9418573f29|
|fallen-pillar|c26a9d21f866b03d0dd3493a0122b449d32214e14c4e6c2380f6e08905d0bfd2|6ab8f1eba991a0c32fe90c38e503f6bb640dbc17ddfa9a61a745915465b83595|
|dilapidated-bookshelf|afedc274a0f8131c393bcb348cd66dbbbcbfa71700771198a17c7952c57b429b|6a928e193e5a2ce09be3cef974cc44c5a0216507c212e2d51000337153f0d42a|
|ruined-books|440662c601013572a722478a16bad26f65782adcd531b0086d53c91a9e1b1377|2e46927d3f72883816a279cb696d7965842d42c0fb7c516e32131015dd5400a1|

All five source MTL files have SHA256:
`ad8ce350ef2dd4d03455a34e07085018b08b94bb7af775620350c741b0aa37b4`.
The source manifest's complete file inventory was checked before conversion.
Reproduction: run the maintained importer sequentially against the pre-batch
archive, using unique `sm_<asset>` model names and fresh output archives. The
importer refuses name/hash collisions and existing output paths. Do not run it
against an archive that already contains these models expecting replacement.

## Verification and owner retest

Passed converter/archive regression tests and the full projectile cover policy:

```sh
python3 tests/myworld/test-native-scenery-import.py
python3 tests/myworld/test-combat-projectile-collision-policy.py
```

The prior collapsed-wall regression now anchors the original prefix and its
fixed entry rather than assuming it is forever the final archive entry. The
five-prop regression validates the original502 prefix, new model hashes,
counts, signed native ground/heights, flat negative-colour materials, vertex
indices, and server definitions. Future additive entries need not break it.

Actual maintained client `RSModel`, `EntityHandler` and software `Scene` were
exercised with the freshly built client classes in the isolated private install:

```sh
mkdir -p output/tower-ruins-native/classes
javac -cp /path/to/Client_Base/build -d output/tower-ruins-native/classes \
  tests/myworld/TowerRuinsNativeHarness.java
java -Xmx384m -Djava.awt.headless=true \
  -cp output/tower-ruins-native/classes:/path/to/Client_Base/build \
  orsc.graphics.three.TowerRuinsNativeHarness \
  Client_Base/Cache/video/models.orsc output/tower-ruins-native
```

All five registries, native mesh counts, ground/heights and all20 renders
passed, with 3,024–17,572 visible pixels; current registry model count465
(plus24 animation registrations) remains below the existing1000 cache bound.
Native previews and the actual private OpenGL client were reviewed; owner
in-game acceptance is recorded above.

### Barrel base correction

The original base and fallen stave top both rounded to native height 7 while
their horizontal footprints overlapped. The original Model Studio geometry was
corrected by moving the fallen stave and splinter completely outside the base,
with no scale, height, material, face-count or vertex-count change. The exported
barrel remains 190 vertices/135 faces, height89, within its assumed1×1 footprint.

Core rebuilt the five-entry addition from the preserved pre-batch502-entry
archive with the reviewed importer. Comparing both507-entry archives confirmed
that **only entry503 (the barrel) changed**; the other506 stored headers and
payloads are byte-exact. The previous candidate remains recoverable at commit
`0b0c501f9164acd141cbda842a691a283620460e` and in ignored local evidence.

Five native regression tests pass. The added regression decodes native integer
X/Z coordinates and applies convex-hull separating-axis checks to the base,
fallen stave and splinter; all three are strictly pairwise separated after
native rounding. The actual Scene harness now renders16 barrel camera angles
(step64/1024) plus the existing four angles for each other prop:32 renders pass,
with3,103–5,046 visible barrel pixels. This is geometry/render evidence, not a
substitute for the owner's in-game camera-motion retest, which the owner then
completed successfully. No client rebuild
or server restart is necessary for this cache-only geometry replacement.

The manager stages a private native map copy and explicitly selects its path,
manifest hash and runtime profile. Native replacement profiles ignore legacy
MyWorldSceneryLocs: test placements must be in the native surface placement
set, not a second same-level set. No test placement is tracked here. Existing
developer command `::addobject ID X Y` is also available in the private game.
Check actual game-scale readability, camera angles and footprint behaviour
before approving. Public servers, accounts, maps and editor projects remain
untouched by this task.
