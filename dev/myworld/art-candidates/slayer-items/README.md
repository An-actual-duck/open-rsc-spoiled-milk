# Slayer item artwork

Art review assets only: these do not replace live inventory/ground sprites.

- `dagger-of-terror-approved.png`: user-selected larger dagger (2026-09-22),
  native 48x32 RGBA, object 28x19 at (10,7). Approved after comparing the
  smaller 23x16 candidate and granting about 20% more object space. Canvas
  remains unchanged. Keep this selected version; do not regenerate casually.
- `abyssal-whip-candidate.png`: pending user review, native 48x32. Exposed
  vertebrae with slate-blue connective flesh and a rib-bone handle. No approval
  is implied by saving a candidate here.

Artwork generated using the built-in imagegen tool. Prompts are saved alongside
the art. Source renders are retained for reproducible native-size packaging.
The references are in `../../reference-library/items/` (daggers and
bones-and-monster-parts); originals are untouched. The abyssal demon's approved
sheet is the blue flesh palette reference.

`export-native.cjs` uses ffmpeg/ffprobe to decode a generated image, determine
its >=128 alpha bounds, nearest-neighbour fit it to the specified maximum
object box, and centre it on 48x32. Alpha is made binary, with transparent
pixels cleared to zero. It never overwrites an output or repaints artwork.
The approved dagger was packed with the earlier 1.2x exporter, giving 28x19;
its packaged pixels are authoritative.

Review at 1x and matching integer pixel zoom, not from the high-resolution
source alone. Preserving a design does not automatically make it game-ready.
