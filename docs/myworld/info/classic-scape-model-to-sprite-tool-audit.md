# Classic-Scape Model-to-Sprite Tool Audit

Audit date: 2026-08-14

Repository: [`aicovergod/Classic-Scape`](https://github.com/aicovergod/Classic-Scape)
(private; links require repository access)

## Result

The described 3D-model-to-RuneScape-Classic-sprite tool is **not present in
the accessible repository**. No accessible branch, tag, release, reachable
historical tree, documentation page, script, build entry point, or generated
example contains a Blender, OBJ, FBX, glTF/GLB, or comparable model importer
connected to offscreen multi-angle rendering or sprite-sheet generation.

The repository does contain two superficially similar systems:

1. [Sprite Studio](https://github.com/aicovergod/Classic-Scape/tree/eadf40f2182b594e9bb52e756c5146f19b90c682/tools/sprite-studio)
   composes and recolors existing two-dimensional RSC sprites.
2. [OpenGLFrameCapture.java](https://github.com/aicovergod/Classic-Scape/blob/eadf40f2182b594e9bb52e756c5146f19b90c682/PC_Client/src/orsc/OpenGLFrameCapture.java)
   records diagnostic layers from complete live game frames.

Neither accepts a general 3D model or produces a turntable-style RSC sprite
sheet.

## Scope and evidence

The audit used a full `git clone --mirror`, not a shallow or default-branch
checkout. At the time of inspection, GitHub and the advertised Git refs agreed
on this inventory:

- default branch `main` at
  [`eadf40f2182b594e9bb52e756c5146f19b90c682`](https://github.com/aicovergod/Classic-Scape/commit/eadf40f2182b594e9bb52e756c5146f19b90c682);
- branch `npc-combat-master-refactor` at
  [`1f79a15ee1e6bc9e7e54266f797cb62cc1fc4250`](https://github.com/aicovergod/Classic-Scape/commit/1f79a15ee1e6bc9e7e54266f797cb62cc1fc4250),
  already an ancestor of `main`;
- 235 reachable commits, two branches, no tags, and no GitHub releases.

All reachable object paths and text history were searched for model formats,
importers, rendering/capture vocabulary, camera rotation, transparency,
cropping, scaling, palette reduction, sprite sheets, tooling entry points, and
build dependencies. The history contains no tracked `.obj`, `.fbx`, `.gltf`,
`.glb`, `.blend`, `.dae`, `.3ds`, `.stl`, or `.ply` file. It also contains no
Blender Python API, Assimp, Wavefront, Three.js model loader, or equivalent
dependency.

The game does load its own archived `.ob3` models from
`Client_Base/Cache/video/models.orsc` through `RSModel`; this is runtime game
asset loading, not an external-format importer or sprite generator. The
MapEditor has a 3D map renderer, but no model-to-sprite export path. Tracked PNG
frame sets show the established RSC layouts, but no model source or generation
recipe ties them to a 3D capture pipeline.

This conclusion cannot cover unpushed local files, deleted unreachable Git
objects, private refs not advertised by GitHub, or expired CI artifacts. No
release assets existed to inspect.

## Closest match: Sprite Studio

The closest reusable tool is documented at
[`tools/sprite-studio/README.md`](https://github.com/aicovergod/Classic-Scape/blob/eadf40f2182b594e9bb52e756c5146f19b90c682/tools/sprite-studio/README.md).
Its initial rescued implementation is commit
[`93ff05191c6bd3f4d7baff2611a1115c30cc91aa`](https://github.com/aicovergod/Classic-Scape/commit/93ff05191c6bd3f4d7baff2611a1115c30cc91aa),
and the item-aware version was finalized in
[`49d106a9dbc8cf85d99bf030d35736fb3430b338`](https://github.com/aicovergod/Classic-Scape/commit/49d106a9dbc8cf85d99bf030d35736fb3430b338).
The audited current form is on `main` at `eadf40f2`.

Invocation:

```powershell
py -3 tools/sprite-studio/server.py --open
py -3 tools/sprite-studio/server.py --check
```

Windows also has `Run Sprite Studio.bat`. The server binds to `127.0.0.1`,
prefers port 8765, and supports `--port`. It requires Python 3, Pillow, and a
browser with Canvas and `createImageBitmap` support.

Inputs and outputs:

- reads the proprietary `Custom_Sprites.osar` archive, repository PNG frames,
  remastered manifests, item definitions, and client/server Java tables;
- accepts an arbitrary browser-decodable image for NPC recoloring;
- exports transparent PNG character/NPC sheets and JSON character recipes;
- preserves the six-column, three-row, 18-frame RSC layout and native frame
  bounds;
- performs exact-color replacement, not automatic palette reduction;
- does not import, animate, light, rotate, or render 3D models.

Sprite Studio is tightly coupled to Classic-Scape's repository layout,
definitions, archive format, animation indices, layer masks, and frame-offset
tables. Its browser compositor and palette editor could be extracted, but its
catalogue backend is not independently reusable without adapters.

## Other near match: OpenGL frame capture

Renderer diagnostics can enable frame capture through `scripts/run-client.sh`
and request a burst with `Ctrl+F9`. The capture writes full-frame PNG layers,
depth/material masks, metadata, and renderer command diagnostics. It operates
only inside the running Classic-Scape client and captures the world/UI pipeline;
it has no isolated model stage, turntable camera, transparent sprite output,
cropping, palette conversion, or sprite-sheet packing. It is therefore useful
for renderer debugging, not as the described art tool.

## Licensing and adoption

Classic-Scape declares
[`AGPL-3.0`](https://github.com/aicovergod/Classic-Scape/blob/eadf40f2182b594e9bb52e756c5146f19b90c682/LICENSE).
Any copied or adapted code would require license and copyright-notice review,
source availability, modification notices, and AGPL compliance. Asset rights
must be checked separately; the repository license does not prove that every
input model, texture, or generated sprite can be redistributed. Spoiled Milk is
also AGPL-3.0, which reduces code-license incompatibility but does not remove
attribution or third-party asset obligations.

There is no existing 3D pipeline here to adopt. Implementing one for Spoiled
Milk would require a separate, reviewed project that supplies:

1. a defined source format and importer, preferably headless Blender for broad
   OBJ/FBX/glTF support or a deliberately narrower library;
2. deterministic orthographic camera, lighting, animation sampling, and RSC
   directional/frame conventions;
3. transparent offscreen rendering with stable anchors and bounds;
4. nearest-neighbor scaling, optional crop/padding rules, palette policy, and
   sprite-sheet/metadata output;
5. golden-model regression fixtures and license/provenance manifests;
6. an adapter into Spoiled Milk's existing sprite-import and definition
   workflow.

The useful part to borrow conceptually is Sprite Studio's explicit RSC sheet
geometry and preview/recipe contract. A future 3D renderer should emit into that
contract rather than modifying the live client renderer or treating diagnostic
frame capture as an art pipeline.
