# Stone window scenery family — owner-approved assets

The owner approved pointed scenery1349 in the private OpenGL client on
October24,2026, liked its chunky frame/sill, and requested the remaining window
styles. The owner subsequently approved the standard window and arrowslit,
then approved revised stained004 in the private OpenGL game on October24,2026:
"yep that's better, these can all be added". All four scenery styles are now
owner-approved, including the sword depicted within the stained glass.
No existing wall or map placement is replaced.

## Scope and game ownership

| New scenery | Model | Original wall / texture | Existing tall wall |
| --- | --- | --- | --- |
|1350 Stone window|`sm_stone_window`|3 /5|216|
|1351 Stained glass window|`sm_stained_glass_window`|6 /18|217|
|1352 Stone arrowslit|`sm_stone_arrowslit`|13 /7|218|

The existing tall wall variants retain the same192-native-unit opening artwork
and add83units of masonry above. Consequently these decorative props work with
both wall sizes without redundant tall window assets. Approved pointed1349
(texture27, walls34/219) is preserved byte-for-byte. Wooden window textures22
and45 are outside this stone-window batch.

Each new definition is passive type0,1×1,groundItemVar0,WalkTo/Examine, with
matching appended server XML and client registry entries. They are shallow
opaque cutouts, not working doors, transparent holes, combat cover or transport.
They add no collision or interaction behavior. All1350previous server
definitions and512previous archive entries are preserved exactly.

Assets belong to the game. The standalone editor must discover them from the
server/client normally, with no bundled models, custom-ID list, special shader
or texture registration. This worker changes no owner project, private game
installation, toolkit, process or public deployment.

## Source provenance

Original family authoring READY: `d567d34175d753e04e5ad3737978420b43ab8080`.
Stained-glass correction READY: `38ab022e7961d4ef741ea47049deb8035a9a8432`
(42authoring tests and saved Blender/native-geometry inspection passed).
The manager independently reviewed41authoring tests, preserved hash-verified
masters at `/home/justin/rsc-model-studio/projects/local/window-family-reviewed-20261024/`,
and integrated only Model Studio tooling into that independent repository.
No Model Studio history enters Core.
Reviewed sources are under `/home/justin/rsc-model-studio-ai-1/projects/local/`:

- `stone-window-003/stone-window.obj`: `7587bbda6781717762554ffe8b8b2e2ac0270fd09d8cb7740058aee1342b03f0`
- `stained-glass-window-004/stained-glass-window.obj`: `c7205bffa6ba8f30c37197b8c896b782fa3ca463ffc52f31c899e44d7907e545`
- `stone-arrowslit-001/stone-arrowslit.obj`: `5caff07f6c17bcb28daaff7d871d8da5d0ca533114bfdb244412718c27ce041d`

Each matching MTL has SHA256
`cf75cc0129a55269aafd7de12f438a04b4627765e812cc08608c816f8386b0ab`.
These are derivatives of authentic local RSC window artwork, not original art:

|Source PNG|Texture|SHA256|
|---|---|---|
|`id-03230.png`|5|`5c3b375de2ee1e6f351552f2cc35a52a5deb88021511124533b0b7e94833f58e`|
|`id-03243.png`|18|`4d4d32ff41512d3a0d454b143ed94443e533ad7b0714b12dabe697a1a31ab766`|
|`id-03232.png`|7|`2c45c9846fa0c42b38c6c30fc78990fb829c2a55861f9b6e8980410e5f703a94`|

Surrounding brick fields are excluded. Grey closed beveled frames/sills retain
the accepted chunky appearance. The standard window has wood mullions and blue
panes; stained glass retains blue sections, a sword-like central detail and
green jewel; the arrowslit retains the narrow cross-shaped opening.

## Conversion and native budgets

The unchanged reviewed `scripts/import-native-scenery.py` converts Z-up OBJ to
native `(128X,-128Z,128Y)`. Each model is appended sequentially to the original
512-entry archive, without replacing any earlier entry. Existing runtime
Gouraud scenery lighting applies to shared vertices on closed components.
All native materials are opaque negative RGB555 on both sides; no new image,
texture slot, renderer change or runtime asset generation is required.

|Model|Vertices / faces|Native X / Y / Z bounds|Payload bytes|
|---|---|---|---|
|Stone window|236 /190|[-40,40] /[-177,-40] /[-56,-45]|3312|
|Stained glass|356 /272|[-34,34] /[-190,-33] /[-60,-51]|5980|
|Arrowslit|162 /133|[-24,24] /[-190,-33] /[-60,-51]|2304|

The standard window uses six short top stones and an8-unit rear mounting gap;
the others use a4-unit gap. Authored hanging height is preserved. Stained glass
requires16-bit vertex indices; the others use one-byte indices. Native face
budgets are comparable to the198-face hanging cage and lower than518-face
shackles. Dense repeated placements still incur normal scenery drawing work.

- Standard OB3: `f045b0e8c7ef6c2d1279a6623adfa882b470ea68dbb536cae765859ea8056779`
- Stained OB3: `3b5ae950db76393c2a3288c9f51205051f80c1cf0cf75c54682088d1a54350b6`
- Arrowslit OB3: `550dff4184ed6d770dc58db9b78819663cf7ad3e4ead62fa00792838d6870281`
- Complete515-entry archive: `8ad7965411abd4cf5cf5907c5de2bf52dcb736ff690589829685de1f4d18b5ca`
- Preserved512-entry header/payload prefix: `2d1534597440605ae9c5ab9fdefd19b9b373ef50c7813291ae78f2fe55f89a62`

## Native proof and explicit software limitation

The maintained RSModel decoder, current definitions and actual Scene software
rasterizer render all candidates against actual textured stone spans. The
parameterized harness exercises four cardinal rotations from both sides.
For275-unit walls, all three candidates have readable front frames and zero
rear changes from independent wall-only images. Front coverage ranges:
standard3946–4035pixels, revised stained3063–3231, arrowslit2021–2116.
Maximum front brightness ranges155–182,143–206 and156–182 respectively.

On192-unit walls arrowslit still shows zero pixels through opaque stone.
The original stained001 also passed this check, but revised004 regresses it
as documented below. Their2–3rear silhouette pixels additionally lie outside
the wall, over the background: relief can physically project above the edge.
For example direction0 differences are x254–256,y162; the wall starts aty163.
The harness classifies using the independent wall-only image and background
colour, not an asset-specific mask.275-unit walls still require zero total
rear differences, including background.

**The standard window does not pass192-unit software rear occlusion.** Across
four directions282–334pixels of the upper frame appear through stone. The
unchanged owner-approved pointed1349 similarly shows133–142pixels in this
new short-wall software fixture despite passing the original275-unit proof.
Revised flat-glass004 also shows181–200on-wall pixels plus2–3outside-wall
silhouette pixels on192-unit walls; unlike original001, it does not pass this
short-wall software check. These are documented failures, not accepted as
successful occlusion. They need
actual private OpenGL review at both wall heights; no universal192-unit
compatibility is claimed here, and no renderer change is made.

Standard001 at4-unit clearance leaked276pixels even on tall walls. Splitting
its top beams alone did not resolve that. The final003 keeps the subdivided
frame and moves it4units farther forward; all275-unit rear views then pass.
Larger clearances were diagnosed but rejected as excessive floating. Initial
sources, diagnostic archives and failures remain preserved separately. No
further iterations are hidden behind a passing claim.

Original family evidence is under
`output/window-scenery-family/final-<model>-wall<height>/`. Corrected stained004
evidence is under `output/stained-glass-inset-correction/wall<height>/`.
It includes front/back and wall-only PNGs plus `native-proof.log`. The standard
192log deliberately ends in an assertion failure after recording every view.
The approved pointed192failure is preserved in the same output tree. The owner subsequently approved revised stained004 in private OpenGL, completing
approval of all four styles. This does not fix or invalidate the measured
192-unit software-rasterizer failures above.

## Reproduction and regression checks

Compile `EntityHandler.java` and `tests/myworld/PointedWindowNativeHarness.java`
against the private installation's existing build classes, placing results in
`output/window-scenery-family/classes`. The harness takes:

```text
archive output-directory stone-texture [model-name object-id wall-height]
```

The stone reference is authentic texture2, `id-03227.png`. Use275for strict full
occlusion;192exercises short walls and reports the limitation above. Native
regressions pin prior entries/definitions, new model hashes and bounds,
nondegenerate polygons, index widths, RGB555 material parity and passive
bindings. Projectile collision policy is checked separately. Registry473models
plus24animations remains below1000. The manager stages private fixtures and
owner review separately; worker READY is not gameplay acceptance or release.


## Stained-glass-only revision

Owner feedback on001 was specific: the sword was raised in front of the glass.
004 replaces glazing/motif with a tessellated coloured-glass mosaic and continuous
silver blade region. Every coloured cell, including sword and jewel, has the
same native front plane Z=-55 and back=-57. Thin gaps reveal recessed grey lead
backing at front=-58/back=-59. It has no raised sword, overlapping coplanar
motif/base planes or separate physical blade/hilt. Native regression explicitly
checks those common glass depth planes.

All nine stone arch/sill components were independently compared against001 by
material and ordered polygon coordinates and are identical. Only archive entry
513 (`sm_stained_glass_window.ob3`) is replaced; definition1351 stays unchanged.
This is a reviewed fixed-entry replacement, not an importer collision bypass:
the existing converter generates OB3 and the archive is reconstructed with the
single matching header length/payload changed. All514other stored headers and
payloads match the prior515-entry candidate byte-for-byte. Their concatenated
SHA256 is `dcd18b437b850ddfe936fd8ec34d281abe849c606f9a052eb0a8a410b1cc95b1`.
The previous archive and comparison proof are preserved under
`output/stained-glass-inset-correction/`. No renderer or lighting change is made.

004 passes all four275-unit front/rear views, with zero rear differences. Its
192-unit rear check deliberately fails and records all directions; the failure
must not be described as passing or silently classified as an outside-wall
silhouette. The manager staged the exact asset for private OpenGL review, and the owner
approved004 on October24,2026. The measured192-unit software limitation remains
known; owner approval must not be reported as a software-rasterizer fix.


## Maintained integration and discovery

Owner acceptance covers pointed1349, standard1350, revised stained1351 and
arrowslit1352 as game-owned scenery. The manager may integrate this exact
reviewed family and verify normal server/client content discovery. The editor
must find the model archives and matching definitions from Core; no model-ID
roster, bundled window asset or special editor implementation is authorized.
Existing editor projects may need their ordinary content refresh to see newly
integrated scenery. Private comparison placements do not become tower or live
map placements automatically.

This owner acceptance is not authorization to publish a player release, stop or
restart the public server, or deploy these assets live. The public-server
shutdown gate and normal release/integration checks remain applicable.
