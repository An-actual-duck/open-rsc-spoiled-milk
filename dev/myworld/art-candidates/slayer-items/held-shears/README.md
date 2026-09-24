# Held shears candidate

Reference preparation only; no production assignment or replacement yet.

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
