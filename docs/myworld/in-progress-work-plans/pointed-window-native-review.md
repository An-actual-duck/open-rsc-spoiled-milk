# Pointed window — private native pilot

The owner requested a scenery cutout after the surrounding stone of tall window
walls failed to match ordinary walls. This additive pilot puts a shallow pointed
stone frame and opaque dark pane on an existing wall, like the accepted hanging
props. It does not cut a hole in the wall. Owner in-game approval is pending;
this document does not authorize a public release or map placement.

## Provenance and binding

The Model Studio relief-002 candidate is a derivative of local authentic RSC
texture27, `authentic-sprites/textures/id-03252.png`, SHA256
`5feb30efd0b541068919023ba1069211b1635a7f022a731b20217df1fd510473`.
It is not original artwork. Only the pointed frame, dark pane and coarse central
pane tones are adapted; surrounding bricks are excluded. The reviewed source
is kept outside Git in the separate Model Studio workspace at
`/home/justin/rsc-model-studio-ai-1/projects/local/pointed-window-relief-002/`.
Authoring exact READY commit: pending final authoring handoff.

Scenery1349, **Pointed window**, uses model `sm_pointed_window`, type0,
1×1, groundItemVar0, WalkTo/Examine. Server XML and client definitions match.
All1349 preceding server definitions retain their field values and IDs.
It has no transport, opening action, collision, actual wall hole or interaction.
The standalone editor must discover this game-owned asset normally; no editor
model roster, bundled asset, texture allocation or shader change is included.

## Native geometry and preservation

The existing reviewed flat-colour OBJ importer maps `(X,Y,Z)` to native
`(128X,-128Z,128Y)`. Native bounds are X[-34,34], Y[-189,-34], Z[-60,-51].
The back sits4native units ahead of the standard wall plane Z=-64; the frame is
9native units deep. Authored height above the ground is preserved. Ordinary
cardinal scenery rotations mount it on the corresponding wall side.

The closed relief contains216vertices and189faces, with one-byte indices,
identical negative RGB555 colours on both sides and serialized flat-light flags.
Normal scenery lighting applies Gouraud shading at runtime. Twelve beveled
stone strips, a sill, a closed dark pane and coarse pane plates provide readable
shading without a rectangular brick patch. The native payload is3,180bytes,
compared with the rejected planar candidate's2,472vertices/618faces. Face count
is comparable to the198-face hanging cage and lower than the518-face shackles;
repeated placement still costs ordinary scenery draw/lighting work.

All511 previous model entries, stored headers, payloads and ordering remain
byte-exact. Their concatenated SHA256 is
`e22b5941ebd1ee4ab781851c5965d5a08f1391582fdc46ae23a20175fbc1f387`.
The512-entry archive was built from the preserved511-entry base, not by appending
a second copy of an earlier pilot model.

- OBJ: `33011013430846d7a4db7256927d25cebdde35c72236bc1292467ef0e65345be`
- MTL: `7cbdcc1ae3c6fc4282d41eecc6f142ccec69fca84673fd9474c061bbe55e57ef`
- Native OB3: `bc393caddb63366f019ef341ccea7bfdc8e03d92b4299fe3c61561108d985006`
- Complete models.orsc: `bf221f746d9f60b5e5a67dbb04e8aefd6a45bf7e4245f1a3a954a9a52c6c30c5`

## Rejected planar attempt

The earlier source-cell cutout successfully preserved the source colours but
failed native appearance: a nearly black silhouette and221rear-view pixels
leaking through the wall. Tiny planar quads interact poorly with the software
painter and the standard squared flat-colour shading. The original output and
failed images remain under `output/pointed-window-native/`; they are evidence,
not the integrated asset. The replacement is coarse closed geometry using
unchanged native runtime rules. No engine workaround or hidden texture slot
was used.

## Native verification and remaining acceptance

The maintained RSModel decoder, current client registry and actual Scene
software rasterizer rendered the candidate against three ordinary128-unit
stone wall spans in four cardinal orientations, front and back. It uses the
authentic stone texture through the maintained client texture loader.

All four fronts show2,919–2,968changed pixels, with maximum gray brightness
129–182; frame and dark pane were visually inspected. All four rear views
match the wall-only render exactly: zero leaking pixels. Rotated mounting
clearance is4–5native units. Registry470models plus24animations remains below
1000. Evidence is in `output/pointed-window-native-relief-002/`.

Reproduce the harness with a fresh output folder:

```sh
javac -cp /home/justin/collapsed-wall-private-8vInKUKb/Client_Base/build \
  -d output/pointed-window-native/classes \
  Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java \
  tests/myworld/PointedWindowNativeHarness.java
java -Xmx384m -Djava.awt.headless=true \
  -cp output/pointed-window-native/classes:/home/justin/collapsed-wall-private-8vInKUKb/Client_Base/build \
  orsc.graphics.three.PointedWindowNativeHarness \
  Client_Base/Cache/video/models.orsc output/pointed-window-native-relief-002 \
  /home/justin/Core-Framework/output/sprite-png-export-20260702-154351/authentic-sprites/textures/id-03227.png
```

Bounded regression checks cover all prior model entries and server definitions,
new binding, exact native hash/bounds, colour parity, index widths and
nondegenerate polygons. Final private OpenGL world review remains necessary;
software proof does not replace owner approval. The worker changes no private
installation, account, server process, owner map, toolkit or live deployment.
