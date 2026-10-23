# Collapsed wall native pilot

## Approval and ownership

On October 22, 2026 the owner approved Model Studio's visual candidate and
requested an in-game aesthetic check. This is an original stone-brick rubble
prop, not a copied game model. The owner approved its actual in-game appearance
on October 22, 2026 after reviewing the private OpenGL client.
Nothing in this task deploys or restarts the public server, places persistent
objects in its map, or changes the standalone editor.

Core receives the reviewed native export, not Model Studio's Git history or
authoring application. The editable Blender file and OBJ/MTL stay preserved in
the independent local authoring workspace:

`/home/justin/rsc-model-studio/projects/local/collapsed-wall-manager-001/`

The authoring recipe is Model Studio main `eb3fd6e`. Original procedural
geometry uses ten chipped rectangular masonry blocks and three fragments.
The read-only existing stone texture was a palette/scale reference only; no
texture image, existing mesh or third-party model is embedded in this export.

## Native content contract

- Additive scenery ID **1339**, `Collapsed wall`, model `sm_collapsed_wall`.
- Type 1, two by one tile movement-blocking footprint, ground item offset zero.
- Ordinary low rubble: transparent to both player-allied and enemy projectiles.
  An exact model-key exception prevents the word “wall” from incorrectly making
  this fallen pile structural cover. Other wall classifications are unchanged.
- Native geometry: 130 vertices, 91 convex polygon faces; material colours on
  both sides. No new textures, animations, generated model fallback or renderer
  special case. The ordinary client scenery lighting is retained.
- Authoring `(X,Y,Z)` maps to native `(128X,-128Z,128Y)`, rounded to integer
  native coordinates; origin centered in X/Z, minimum height zero.
  This positive-determinant transform retains winding. Ground is native Y=0,
  maximum height is native Y=-69, approximately half a tile.
- Linear MTL diffuse colours are converted back to sRGB, then native negative
  RGB555 face values. Palette corresponds to authored `#454545`, `#565656`,
  `#686868`, `#777777`, quantized to five bits per channel.
- Current client definition registry uses 460 models; animation registrations
  leave comfortable headroom below its existing 1,000 model cache capacity.
- The editor discovers the normal game definition and model archive. No editor
  asset, numeric roster, catalogue entry or discovery override is added.

## Provenance and reproducibility

Reviewed source OBJ SHA256:
`170c5b09c59ef5fde23c329ddf24d12769f0aa6996b1736d55a285c56b6fd312`

Source MTL SHA256:
`ea8a2158829a819ad87eb8ba0cf1b78b9dcaa488d349470ecd5b627d29f8c888`

Native OB3 SHA256:
`8aeeef3577b9367a7bbcbbdac78045b4364d1b9a995081fb3a4da54c49bc9f03`

Resulting `Client_Base/Cache/video/models.orsc` SHA256:
`aaed7b4ad8861a051121d381017ae6e07e90efb93ef90d12598e4eac3ab32068`

The original 501 archive entries retain their exact stored ten-byte headers,
payload bytes and ordering. One uncompressed `sm_collapsed_wall.ob3` entry is
appended. Concatenated original `(header,payload)` pairs hash to
`463ef68aa0496473ebbad6e8fac0bf9297ae6f4eee9840c84b6f9c8d24028ae9`.
This is verified by the regression test; existing IDs are not shifted.

To reproduce, use the **pre-integration** models.orsc, not the updated archive:

```sh
python3 scripts/import-native-scenery.py \
  /home/justin/rsc-model-studio/projects/local/collapsed-wall-manager-001/collapsed-wall.obj \
  /path/to/original/models.orsc /fresh/output/models.orsc \
  --name sm_collapsed_wall
```

The converter refuses name/hash collisions and overwriting its output. It is
a bounded offline importer for trusted reviewed flat-colour OBJ exports, not
a general untrusted-file sandbox or universal RSC asset upgrader.

## Verification and private review

Passed:

```sh
python3 tests/myworld/test-native-scenery-import.py
python3 tests/myworld/test-combat-projectile-collision-policy.py
```

The maintained Java client decoder and software Scene renderer were exercised
without login/networking/database access, using the rebuilt client classes:

```sh
mkdir -p output/collapsed-wall-native
javac -cp Client_Base/build -d output/collapsed-wall-native \
  tests/myworld/CollapsedWallNativeHarness.java
java -Xmx512m -Djava.awt.headless=true \
  -cp output/collapsed-wall-native:Client_Base/build \
  orsc.graphics.three.CollapsedWallNativeHarness \
  Client_Base/Cache/video/models.orsc output/collapsed-wall-native
```

All four camera angles produced visible native geometry (9,310–10,752 pixels),
with registry, native counts, ground placement and capacity assertions passing.
This proves actual client decoding and rendering, **not final owner aesthetic
approval or a full interactive collision acceptance**.

Private developer command: `::addobject 1339 X Y`. This uses the existing
scenery placement command and saves only in the selected private environment.
Inspect the low pile against existing stone walls, rotate the camera, walk
around its two-by-one footprint, and test ranged/magic across it. Review dark
stone shading under actual client lighting before making palette changes.
No fixed world placement is included in source control.

### Owner acceptance and private placement correction

The full client and server builds passed in the isolated review installation
`/home/justin/collapsed-wall-private-8vInKUKb`. The owner confirmed the model
appeared correctly and approved its aesthetic. This approves the visual asset,
not an assertion that every interactive collision scenario was manually tested.

Two local-only review props were placed at global level 0, (122,647), direction
0 and (126,649), direction 2. The initial legacy JSON test placements did not
load because the native replacement runtime intentionally skips legacy base
population. The corrected fixture uses a separate copy of the native package,
with both props merged into its existing surface placement set and updated
payload/manifest hashes. The private launcher explicitly selects that package;
installed-primary discovery otherwise takes precedence over configuration-only
paths. Startup confirmed 27,894 scenery placements (27,892 original plus two).
The original installed package and public deployment remained unchanged.

Native fixture manifest SHA256:
`d6829db3fa923d0951b0c7a9cb286b9bf88d02ff2878dc301f2460653810f8fa`.
These test placements, private account operations and launch configuration are
not production changes. Normal editor content discovery should find scenery
1339 from the maintained game definitions and native archive, without an
editor-specific asset entry. Existing editor projects may need content refresh.
