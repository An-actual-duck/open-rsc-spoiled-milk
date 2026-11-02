# Cleric well assets — Core review

Owner approved the six texture previews and **Dip sigils** wording on October29,
2026. This task installs those game assets, not the full Cleric redesign. The
owner also selected **A — Thorn** for Blight; its native sprite translation is
included as a review candidate, not a production sigil or approved final sprite.

## Installed game content

- Texture67 deep-purple ichor;68 pale-blue Divine Water, both exact native ripple
  recolors, two blocked liquid floors27/28 using existing liquid-edge rules.
- Textures69–72 stone/dirt with ichor/divine seepage. Paired server/client walls
  IDs220–227 provide192 and275 heights. No existing wall/material was changed.
- Six native palettes installed in both Custom_Sprites.osar and Authentic ZIP.
  They are game content, never a toolkit roster or toolkit asset dependency.

### Safe native slots and existing mode mismatch

Original classic registry exposed55 textures, while the selected custom OSAR
already contained67. Extending the old3225+index sprite allocation would collide
with GUI sprites at3284+, and overwrite existing custom materials if the new
art were assigned55. New materials therefore append67–72. Twelve existing
custom materials55–66 gain exact native RGB equivalents, without changing their
OSAR entries or introducing new art. Generic `TextureDef.nativeSpriteId` supports
noncontiguous native archive records4100–4117; old two-argument definitions
still use their unchanged contiguous3225+index mapping. Both modes now load73.
No PNG fallback, content-name switch, custom item roster or new archive decoder
was added. Existing native Sprite.unpack and custom Unpacker are used directly.

Existing external glyph allocation remains after base textures, now73 in both
modes. It previously differed55classic/67custom, while the capability packet
used classic registry count. No shipped TileDef/DoorDef used55/67 as a glyph;
`createMyWorldAirGlyphModel` still returns an empty model. No map was converted
and no undocumented owner raw-material reference was interpreted as a glyph.

### Blight candidate

`dev/myworld/blight-sigil-candidate/review/index.html` shows existing Grace and
Zamorak with the carved/infused Thorn at1×/6×. It preserves the owner-authored
28×25 stone outside44 symbol pixels and alpha everywhere. Full48×32 canvases
use13,4 placement matching the native blank rune reference. Editable Aseprite
layers preserve substrate separately; both round-trip exactly. The symbol uses
a crooked central stem with downward thorn hooks, no skull/drop/upward horns.
Production IDs, Blight carving/infusion economics and spells remain undecided.

## Validation

Commands ran from activated ai2, sequential Java heaps bounded512MiB/SerialGC:

```sh
python3 scripts/install-cleric-well-assets.py --check
python3 scripts/audit-cleric-well-archives.py
ANT_OPTS='-Xmx512m -XX:+UseSerialGC' ROOT_DIR="$PWD" bash scripts/build-client.sh
ANT_OPTS='-Xmx512m -XX:+UseSerialGC' ANT_HOME="$PWD/tools/vendor/apache-ant-1.10.5" sh tools/vendor/apache-ant-1.10.5/bin/ant -f server/build.xml compile
mkdir -p output/cleric-well-tests
javac -cp Client_Base/build -d output/cleric-well-tests server/test/com/openrsc/server/io/ClericWellAssetsTest.java
java -Xmx256m -XX:+UseSerialGC -Djava.awt.headless=true -cp output/cleric-well-tests:Client_Base/build com.openrsc.server.io.ClericWellAssetsTest .
javac -cp Client_Base/build:server/core.jar -d output/cleric-well-tests server/test/com/openrsc/server/io/TallStoneWindowTest.java
java -Xmx256m -XX:+UseSerialGC -Djava.awt.headless=true -cp output/cleric-well-tests:Client_Base/build:server/core.jar com.openrsc.server.io.TallStoneWindowTest server/conf/server/defs/DoorDef.xml
```

- Client/source and server/core+plugins builds passed. No server/client launched.
- 293 focused assertions: actual classic/custom loaders, full73 resources, old55
  mapping plus supplemental safe slots, all18 extension quantized palettes
  identical across modes, all six actual software rasters, GL texture resource
  construction, paired XML/client floor/wall semantics, liquid collision flags.
  No claim of a live OpenGL context or player gameplay acceptance.
- 917 existing tall-window checks passed across native/legacy/directed geometry,
  collision, support, renderer-v2 and validation. The test now accepts later wall
  rows while retaining all original window assertions. Independent baseline
  audit proves original220 wall/26 floor rows including metadata unchanged.
- All2277 original ZIP payloads, ZipInfo, local headers and compressed bytes
  preserved. All1003 original OSAR serialized entries preserved. Exactly18
  native and six custom records added. Generator check is read-only/idempotent.
- Two layered Aseprite candidate round trips passed, same44-pixel glyph mask and
  substrate/alpha invariants. Existing item sprites were only read.

The initial existing-window javac invocation incorrectly used the deleted
post-build `server/build` directory; corrected classpath uses maintained
`server/core.jar` and passes. This was a fixture command correction, not a game
code failure. Evidence logs are adjacent in `cleric-well-assets-evidence/`.

## Remaining coordinated work / risks

The standalone toolkit must prove selected-source explicit native mappings;
its implementation is in its own worker/repository. No custom IDs or assets may
be bundled there. Parent coordinates its independent end-to-end discovery gate.

No invisible shoreline objects were added. A missing/transparent model is not a
proven clickable object: Scene's transparent picking exception currently covers
terrain pick indices200000–300000, not scenery. Correct follow-up requires a
generic interaction-only scenery property and invisible pick proxy in software
and OpenGL, generic editor-only helper/pick footprint, adjacent-land pathing,
full shoreline/corner/layer admission tests and actual infusion rules. Do not
ship a no-op “Dip sigils” object or invent infusion recipes/Devotion behavior.
Approved planned names/examines: Ichor well — “A dark pool of ichor seeps through
the earth.” Divine wellspring — “Divine water gathers here, pale blue and still.”
Line the entire shoreline with nonblocking interactions, not occasional spots.

Private-game texture acceptance remains necessary. Owner maps, saved projects,
accounts, staged release folder and public deployment were not modified. No
server operations, publication, map import or main integration occurred here.
