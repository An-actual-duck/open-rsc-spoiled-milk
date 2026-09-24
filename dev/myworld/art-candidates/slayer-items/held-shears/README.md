# Held shears candidate

Review candidate only; no production assignment or replacement. `preview.html` shows existing versus candidate shears in eight directions. `generated.png` preserves the manager's selected second built-in generation, `frames/` contains 15 cropped native sprites, and `full-canvas/` registers them within 64×102 native canvases. `candidate-manifest.json` records generation intent, crop bounds, approximate grip anchors, alpha at the anchor, and offsets.

Processing uses a shared 0.13 nearest-neighbor scale to fit the existing native bounds and aligns generated red-handle centroids to the prior sword-derived grip estimates. This is more compact than the current shears; sizing needs visual approval. Original sampled alpha is preserved, not replaced with binary alpha. No pixels are painted or recolored. Generated fist gaps are imperfect; some grips contain artwork and require review. Final body occlusion is not validated, and these files are not game-ready merely because their canvas bounds pass.

Reprocess the saved candidate with `node dev/myworld/art-candidates/slayer-items/package-held-shears-candidate.cjs`. An optional argument imports a new source; do not supply one unless intentionally replacing this candidate source.

`references/` mechanically composes existing pixels at their recorded runtime offsets. Each sheet has three walk phases per row, five rows: front, diagonal front, side, diagonal away, away. The cells are 84×102 presentation space with the native 64×102 bound centered (10 pixels padding); previews are enlarged 3× with nearest-neighbor. Padding preserves original tool pixels extending beyond their nominal bounds. No attack frames are included.

Sources:

- Authentic inventory design: `dev/myworld/reference-library/items/tools/gathering/id-02216.png`, 48×32 canvas, visible bounds (2,5,43,19).
- Existing shears: `dev/myworld/assets/sprites/equipment/shears/numbered/00.png` through `14.png`; last source update `688f48e85` (Align equipped shears sprites). These are preserved untouched.
- Original custom pickaxe and sword: `/home/justin/Core-Framework/output/sprite-png-export-20260702-154351/custom-sprites/equipment/`, with offsets from the export's root `sprite-manifest.csv`.
- In-game body/occlusion reference: `../held-abyssal-whip-v3/eight-direction-reference.png`, assembled from `/home/justin/Pictures/8 directional held sword`.
- Prior reviewed sword-derived hand anchors: `../held-abyssal-whip/candidate-manifest.json`, `handX`/`handY` for frames 0–14. These are estimates from the original sword handle/guard, not independently measured character skeleton coordinates.

The renderer maps directions 5→3, 6→2, and 7→1 with horizontal mirroring; walk phases repeat 0,1,2,1. Fifteen source frames therefore cover all eight directions. Main-hand layer ordering and intentional transparent gaps permit the body and fist to occlude the tool. A mirrored isolated-tool preview is not a substitute for final in-game occlusion testing.

Regenerate references from the worker root:

```sh
node dev/myworld/art-candidates/slayer-items/build-held-shears-reference.cjs /home/justin/Core-Framework/output/sprite-png-export-20260702-154351
```

`references/manifest.json` records exact source crop dimensions and shifts. No original reference images are modified. Generation is performed separately by the manager using the built-in image tool; subsequent candidate processing must retain provenance, preserve alpha, and avoid adding attack frames.
