# Tower wall decorations — native review batch

## Scope and ownership

The owner requested an in-game look at three original Model Studio candidates:
rusted shackles, tattered banner and smashed mirror. The owner approved all
three after the private OpenGL review on October 23, 2026 and requested their
availability through ordinary editor discovery. No public deployment,
permanent map placements, new attachment mechanic or editor asset roster is
included. These are ordinary game-owned scenery definitions and native models
which the standalone editor discovers from the selected game installation.

Model Studio handoff `e5c826d`, integrated main `2ae30da`, retains the editable
masters, briefs, manifests and inspected exports. Core receives the reviewed
native exports only, not that repository's Git history or Blender dependencies.
The original local candidates are:

`/home/justin/rsc-model-studio/projects/local/<asset>-manager-001/`

The assets are original closed-component flat-colour geometry. Native portraits
and wall shields were read-only scale/mounting references; no extracted geometry,
texture image, external-service upload or AI training is part of these exports.
The shackles use the corrected continuous-chain geometry, not rejected history.

## Definitions and mounting

| ID | Name / model | Vertices / faces | Native Y (negative up) | Native Z depth |
| --- | --- | --- | --- | --- |
|1345|Rusted shackles / sm_rusted_shackles|692 /518|-179..-60|-60..-29|
|1346|Tattered banner / sm_tattered_banner|82 /63|-187..-45|-58..-39|
|1347|Smashed mirror / sm_smashed_mirror|88 /64|-194..-57|-59..-44|

All three are type0 nonblocking, 1×1 decorative scenery, WalkTo/Examine only,
with groundItemVar0. They are transparent to combat projectiles under the normal
scenery policy. There is no prisoner interaction, reflection, animation, shader,
spell effect or new collision rule. Prior definitions retain their IDs/fields.

The original authoring convention is X across the wall, Z up, wall at Y=-.5,
visible face toward +Y into the tile. The existing importer maps coordinates
to native `(128X,-128Z,128Y)` and **does not normalize the hanging mesh to ground**.
Native scenery placement centers a 1×1 object at tile+.5; wall direction0 lies
on the tile's negative-Y edge, native local Z=-64. Back faces therefore retain
4/6/5 native units of clearance rather than fighting the wall depth plane.

Ordinary scenery rotation is `model.addRotation(0,direction*32,0)`. Cardinal
directions0/2/4/6 rotate the mounting edge to the corresponding tile edge.
The actual RSModel fixed-point transform—not just a guessed coordinate matrix—
was exercised for all four, with minimum wall clearance4–7 units after rounding.
They need deliberate placement against the appropriate wall, not automatic wall
search or an editor-specific attachment feature. Rotation changes which side
faces into the room; objects should not be placed centered across a wall.

## Native encoding and preservation

The maintained `scripts/import-native-scenery.py` converts the reviewed OBJ/MTL
using negative RGB555 colours, flat-light flags and identical front/back face
materials. No new texture or renderer fallback is required. Opaque mirror facets
are ordinary pale grey/blue faces, not live reflection or transparency.

All507 original archive entries retain exact ten-byte headers, payload bytes and
ordering, including the approved collapsed wall and five floor props. Their
concatenated header/payload SHA256 is:
`b8a1b3239a644407fad39ee5980899539fa396195a4de60db7e88f535ddddf4c`.

New complete models.orsc SHA256:
`b7ab00fa4e14f347a60515a19f3151dd2b6e4cdb8d28db5820882fc4309533d4`.

| Asset | Source OBJ SHA256 | Native OB3 SHA256 |
| --- | --- | --- |
|rusted-shackles|f0fdbbb9f5614c2fc4b243379a60ade0c172d10191cd0292cab69e5fd973c578|4b80f3f998cd9ca572c5507e4a0c2109af677137535492b85e1f517a1a46b787|
|tattered-banner|97ac744a37e398d054599c8f1545088311b32cb4e3be9f09d4d0f19eaf8fac0f|9996ad35ea7d10c754d707e9c342b8262f04ba9f700a09f303a692367668735f|
|smashed-mirror|a2cb6fb179fe01795507f7655d8a289b2685f665d4209f11ea6d929177ece260|09425d6db7dd2408967718a8b291777b6c94b98d17cbb7c3d860b785b5d58880|

The common source MTL SHA256 is
`fc6aee330e87d74245aac2025f4f52a3b6086d88c60d0e2f15b0c1c33735cf42`.
Each candidate's complete manifest file inventory was verified before conversion.
Reproduce from the pre-batch507-entry archive with the existing importer, one
asset at a time using fresh output paths and model names. It refuses duplicate
name/hash entries and output overwrites. The shackles exceed255 vertices and
therefore use native16-bit indices; the actual decoder reaches index691.

## Verification

Passed:

```sh
python3 tests/myworld/test-native-scenery-import.py
python3 tests/myworld/test-combat-projectile-collision-policy.py
```

All six native regression tests pass, including the previous five unchanged
tests for the approved props. The added wall-prop test proves original507-entry
preservation, new hashes/counts, hanging height, mounting gap, full16-bit indices,
nonzero face area after integer rounding, flat opaque materials and definitions.
All1345 prior server definitions were compared field-for-field and unchanged.

The native wall-context harness uses actual maintained RSModel/Scene classes,
fresh client definition classes and a plain opaque275-unit wall with the same
edge/orientation convention as the real wall geometry:

```sh
mkdir -p output/tower-wall-native/classes
javac -cp /path/to/fresh/Client_Base/build -d output/tower-wall-native/classes \
  tests/myworld/TowerWallPropsNativeHarness.java
java -Xmx384m -Djava.awt.headless=true \
  -cp output/tower-wall-native/classes:/path/to/fresh/Client_Base/build \
  orsc.graphics.three.TowerWallPropsNativeHarness \
  Client_Base/Cache/video/models.orsc output/tower-wall-native
```

Twenty-four context views passed: three assets × four cardinal rotations ×
front/back. All12 front views visibly differ from wall-only renders (1,965–5,452
pixels), while all12 rear views show zero leaked prop pixels through the wall.
Registry model count468 plus24 animation registrations remains below1000.
This is native encoding/rotation/occlusion evidence, not final aesthetic approval
or a claim that the flat harness background duplicates the full OpenGL world.

The manager prepares a separate private native map copy with real stone boundary
walls. Native replacement runtimes ignore legacy placement JSON, so review
objects belong in the existing surface placement set, not a second same-level
set. Startup counts and actual OpenGL inspection remain the final private gate.
No test placements, accounts or launcher configuration are tracked in this task.

## Owner acceptance and editor use

Actual private startup loaded all three models against ten ordinary stone
Highwall sections. The owner accepted the assortment. Final models and matching
client/server definitions are maintained in Core; the editor must discover them
from this installation, never from a bundled custom asset list. Existing saved
projects retain their previous content until View → Refresh content is used.
The standalone editor and the owner's tower project are not modified by this
integration. The private test wall and prop placements are not production data.
