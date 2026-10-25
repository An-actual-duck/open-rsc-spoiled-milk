# Stone rubble — private native review

The owner requested a pile of stone rubble on October 24, 2026. This is a new
compact 1×1 low mound of irregular angular neutral-grey fragments, related to
the approved collapsed wall's stone palette but without its laid-brick pattern.
The owner approved the private in-game appearance on October 24, 2026:
"looks good, that can be added". The accepted model and definitions are the
exact reviewed candidate; this worker does not install content or change maps.

## Original authoring and native binding

Model Studio authoring READY: `93b563c82002a70cb9265723562fc3738de7a47c`.
Reviewed editable/export source remains outside Git at
`/home/justin/rsc-model-studio-ai-1/projects/local/stone-rubble-001/`.
This is original geometry:12 closed irregular fragments and small ground chips.
Neutral palette 686868/777777/565656 is consistent with previously approved
original collapsed-wall/pillar work. No third-party reference geometry or
texture was copied. Model Studio’s 46 tests and independent saved-Blender inspection
passed, including integer-grid convexity, closure, winding, nondegeneracy,
no positive-area coplanar face overlap and support beneath upper fragments.

Scenery **1353**, **Stone rubble**, model `sm_stone_rubble`, type 1, 1×1,
groundItemVar 0, WalkTo/Examine. Matching client and server definitions are
appended after approved 1352. It blocks ground movement using ordinary scenery
collision. Existing ordinary-scenery policy already adds no combat-projectile cover for
either player-allied or enemy ranged/magic attacks. The name/model requires
no structural-wall exception or special whitelist. No new mechanic, interaction or loot is added.

No server runtime source is changed; `CombatProjectileCollision.java` remains
byte-identical to the starting main revision. Core stages the XML, client
definition and model archive for private gameplay checks without a new
collision implementation or server-code rebuild. No editor model roster or bundled asset
is introduced: the editor should discover finished game-owned content normally.

## Native conversion, dimensions and preservation

The existing reviewed `scripts/import-native-scenery.py` maps Z-up OBJ coordinates
to native `(128X,-128Z,128Y)` and appends the unique name to a fresh archive.
It does not replace any earlier model. Integer native bounds are X[-63,62],
Y[-55,0],Z[-60,45]. This fits the128×128 native footprint, rests at ground 0 and
has a 55-unit peak, lower than approved collapsed wall 1339’s 69-unit height.

There are96 vertices and 120 faces, with one-byte vertex indices, identical
negative RGB555 colours on both sides and serialized flat-light flags. The
native payload is 1,684 bytes. The
normal scenery path applies its existing Gouraud lighting; no renderer,
shader, texture slot or runtime model-generation change is made. The budget
is comparable to the 91-face collapsed wall and 135-face destroyed barrel;
dense repeated placement still incurs ordinary scenery drawing work.

- OBJ: `2f62d68689a7085018a1539e473cddc3135968e2d82af6065e13a1795138db73`
- MTL: `feadf6839b8b7e8310a2bcd76269b2f2031ec7cc926090c03feb57e82eaf14ac`
- Native OB3: `a0b8e74f45b552b5e854a92323f10e7aa5d07e8052559615f94aa75e35e2895b`
- Complete 516-entry archive: `0423d6d274c634c472809c008b9596a883210fbd97ae16e551a510ddba6db6a2`
- Preserved 515-entry header/payload prefix: `64bbb510bfafc1b7d61f91c65ed2e050393a9d2e77c080c5a8ec26fb7799885e`

All 515 previous stored archive headers/payloads and 1353 previous server definitions
retain exact values/IDs, including the owner-approved four window styles.
No item, NPC, asset mapping, account, configuration, owner project, toolkit,
server process or live deployment is changed by this worker.

## Verification and remaining owner review

Ten native/archive tests pass: previous content anchors, new native hash,
dimensions, ground alignment, index width, material parity, nondegenerate
native faces and matching definition binding. The existing combat projectile
policy regression also passes with scenery 1353 classified NONE, type 1 / 1×1 binding,
ground traversal blocked, and both allied/enemy projectile masks transparent.
Existing structural walls/doors and asymmetric fence policy remain covered.

The maintained RSModel decoder and current EntityHandler registry were exercised
in the actual Scene software rasterizer with standard scenery lighting. Eight
camera angles produce 2,997–3,382 visible pixels. All views were inspected for
readable angular stones and ground chips. Registry 474 models plus 24 animations
remains below 1000. Evidence is under `output/stone-rubble-native/`, including
individual native PNGs and the eight-angle contact sheet.

Reproduction from this worker, using existing private build classes solely as
unchanged dependencies:

```sh
mkdir -p output/stone-rubble-native/classes
javac -cp /home/justin/collapsed-wall-private-8vInKUKb/Client_Base/build \
  -d output/stone-rubble-native/classes \
  Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java \
  tests/myworld/StoneRubbleNativeHarness.java
java -Xmx384m -Djava.awt.headless=true \
  -cp output/stone-rubble-native/classes:/home/justin/collapsed-wall-private-8vInKUKb/Client_Base/build \
  orsc.graphics.three.StoneRubbleNativeHarness \
  Client_Base/Cache/video/models.orsc output/stone-rubble-native
```

Native checks did not replace private OpenGL review. Core staged a separate
fixture and the owner approved the model on October 24, 2026. The manager may
now integrate the exact accepted game-owned content and verify ordinary editor
discovery. Existing editor projects can use **View → Refresh content** to find
newly integrated scenery. No editor-specific asset bundle, ID roster or special
implementation is needed.

Private comparison placements remain separate from the owner's tower/map and
are not copied to the live map automatically. This acceptance does not authorize
a public release, deployment, shutdown or restart. Normal integration/release
checks and the public-server shutdown gate remain applicable.
