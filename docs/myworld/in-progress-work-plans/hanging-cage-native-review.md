# Hanging cage — private native review

The owner accepted Dot's hanging-cage authoring preview on October 23, 2026 and
requested an in-game look. This is a separately reviewed Core asset integration,
not a Model Studio history merge, game deployment or permanent map placement.
The owner accepted the actual private OpenGL preview on October 23, 2026.

## Source and game definitions

Source authoring handoff: `1e33e6e00afd50bcf2d896f8d4ce9f88e3b5ef81`.
Reviewed originals remain at
`/home/justin/rsc-model-studio-ai-1/projects/local/hanging-cage-dot-002/`.
The source manifest's complete file inventory was verified before conversion.
Original flat-colour geometry; no reference mesh/texture or Blender shader is
embedded in the game. Only the reviewed native export enters Core.

Scenery **1348**, **Hanging cage**, model `sm_hanging_cage`: type0 nonblocking,
1×1 footprint, groundItemVar0, WalkTo/Examine. Matching server XML and client
EntityHandler definitions are appended; all1348 earlier definitions retain
field values and IDs. There is no occupant, door action, prisoner interaction,
animation, ceiling attachment, new collision rule or editor registration.

The maintained importer maps authoring Z-up to native `(128X,-128Z,128Y)`.
Authored ground gap .55 tiles is retained: native bounds X[-61,61], Y[-289,-70],
Z[-56,56]. A manually chosen placement must supply suitable visual context;
no automatic ceiling attachment or height snapping is implemented. The chain
reaches2.26 authoring tiles high. Ordinary scenery rotation uses existing rules.

## Native bytes and preservation

The reviewed native mesh has264 vertices and198 faces; it requires16-bit vertex
indices, with maximum index263. Both sides use identical negative RGB555 face
colours, with flat-light flags. All510 pre-existing archive entry headers,
payloads and ordering remain byte-exact, including the accepted tower props.
Their concatenated header/payload SHA256 is:
`e487ab178f3eb96740f402212852482af31fa2fde9fe816f302b1627cf24263e`.

- Source OBJ: `14b59a1457549e79776545a40f8fecc16b81d050e41d454086ea84176edfda29`
- Source MTL: `31ab6d82797e110b9dfeaa0f30381a3da06817cb4c2f69a29eed7138f5d9255b`
- Native OB3: `4424069c4295b4f3cd4b10932674741581a18185e9cb95e6ec1040dc71aa675a`
- Complete511-entry models.orsc: `824d9bb766d939b924eab83c6de7a04bec8771486558ea358cd126c59055b1dc`

Reproduce with `scripts/import-native-scenery.py`, the source OBJ, pre-addition
510-entry archive, a fresh output path, and `--name sm_hanging_cage`. The importer
refuses duplicate names/hash collisions and existing output overwrites. The
source/native archive is not generated at runtime or bundled into the toolkit.

## Verification

Passed seven native converter/archive regression tests, including prefix and
native hash, quantized bounds, flat materials, full16-bit indices, nondegenerate
faces and definitions. Existing combat projectile collision policy passes.
All1348 prior server definitions were independently compared field-for-field.

The actual maintained RSModel decoder, refreshed EntityHandler registry and
Scene rasterizer were exercised offline using private installation build
classes as unchanged dependencies:

```sh
mkdir -p output/hanging-cage-native/classes
javac -cp /home/justin/collapsed-wall-private-8vInKUKb/Client_Base/build \
  -d output/hanging-cage-native/classes \
  Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java \
  tests/myworld/HangingCageNativeHarness.java
java -Xmx384m -Djava.awt.headless=true \
  -cp output/hanging-cage-native/classes:/home/justin/collapsed-wall-private-8vInKUKb/Client_Base/build \
  orsc.graphics.three.HangingCageNativeHarness \
  Client_Base/Cache/video/models.orsc output/hanging-cage-native
```

Eight camera directions passed with10,089–12,621 visible pixels. The native
render was visually inspected: bar gaps, base, rust colours and short suspension
remain readable. Registry modelCount469 plus24 animation registrations remains
below1000. This software Scene check does not replace the final private OpenGL
world inspection. No owner map, live data, account, toolkit or server process
was modified by this worker. The manager stages private placements separately.

## Owner acceptance and maintained availability

The manager independently reran all seven native tests and projectile policy,
rebuilt the private client, and passed the eight-angle harness against that
fresh build. Private startup loaded the separate hanging-cage review map with
two rotations at (120,648,0) and (123,648,0). The owner inspected it in game and
reported "looks good, no complaints", then requested official inclusion.

The native asset and matching definitions are integrated in maintained Core
main d5d203b12. Authoring source is preserved in independent Model Studio main
d882c4a and `/home/justin/rsc-model-studio/projects/local/hanging-cage-dot-reviewed-001/`.
The maintained client was rebuilt after integration. The installed Toolkit's
ordinary source discovery resolved scenery1348 as RAW_ARCHIVE with264vertices
and198faces, with matching server/client bindings. The same probe resolved all
four accepted high window definitions with matching material bands. Evidence:
`/home/justin/.local/share/cage-official-acceptance/discovery.log`.
No editor registration or bundled cage is added. Existing editor projects need
View → Refresh content to see new content.
Private comparison placements are not copied into the owner's tower or live map.
This acceptance does not publish a player release or activate the public server.
