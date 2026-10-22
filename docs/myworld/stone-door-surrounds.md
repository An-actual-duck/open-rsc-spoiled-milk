# Server-owned stone doorway headers

October 22, 2026. Initial implementation, pending matching private-game and owner
visual acceptance. No public deployment or owner-map rewrite is included.

## Representation and ownership

Two ordinary native boundary definitions are appended to the game-owned
`DoorDef.xml` and matching client definitions. They explicitly match Highwall
(lookup 7) and Ancient Wall (lookup 212). Both use the existing stone texture 2
on both faces, total structural height 275, and opening height 192. They are
visible, passable and noninteractive (`WalkTo` / `Examine`). Single and double
doors share the same per-edge carrier; doors retain their independent normal
visuals, collision and interactions. Existing definitions are unchanged.

`doorOpeningHeight` is optional, default zero. A positive value declares a
geometric opening: the native wall quad's lower two vertices are raised by this
height, while its upper vertices and structural support remain at the full
height. `doorSurroundFor` identifies the exact source wall lookup, and is only
meaningful with a positive opening. Both properties have pure public getters on
server and client definitions. The server rejects malformed dimensions, blocking
headers, missing sources and mismatched source height/material at definition load.

Both cached/static and dynamic native wall geometry honor the opening. Roof and
upper-story support continue using the full height, including the midpoint of a
two-edge doorway. Existing software/OpenGL wall geometry and material pathways
remain shared. No new map format, texture slot, archive asset, glyph allocation,
or global texture-coordinate mechanism is introduced.

The ordinary native texture projection maps the existing stone image across the
shorter masonry band. Thus the band has denser bricks than a full-height wall;
this is an explicit visual review point, not a claim of seamless brick alignment.
Any future artwork refinement belongs in game content, not an editor asset pack.

## Editor contract

The independent toolkit discovers the selected installation's matching server
and client metadata and validates the reviewed runtime implementation. It must
not bake these IDs, names, dimensions, textures or associations into its catalog.
Refresh content exposes additions; importing a map does not install game assets
or upgrade an old client. Install matching maintained server/client code before
using a header in-game. Unsupported targets keep ordinary doors available.

## Verification

Build the maintained client and server, then run:

```sh
mkdir -p output/stone-surround-test
javac -cp Client_Base/Open_RSC_Client.jar:server/core.jar \
  -d output/stone-surround-test \
  server/test/com/openrsc/server/io/StoneDoorSurroundTest.java
java -Xmx256m -XX:+UseSerialGC -Djava.awt.headless=true \
  -cp Client_Base/Open_RSC_Client.jar:server/core.jar:output/stone-surround-test \
  com.openrsc.server.io.StoneDoorSurroundTest server/conf/server/defs/DoorDef.xml
python3 tests/myworld/test-stone-door-surrounds.py
python3 tests/myworld/test-client-world-model-product-split.py
```

The offline test opens no landscapes, listeners, accounts or database. It loads
actual built definitions, compares all server/client rows, exercises both native
geometry paths, four wall kinds, passability and two-edge structural support,
and rejects malformed header metadata. It does not substitute for private-game
visual acceptance: inspect both faces, axes, higher stories, manual and passage
doors, and real walking through the unchanged door opening before release.
