# Darker tanning revision

Supersedes the identical-color pairs in `../hide-leathers/` for review.
Raw hides retain their original family palette; tanned leathers use a 0.8
multiplier on each tint channel (rounded), then the existing client grayscale
mask operation. Original alpha, shape, positioning and shading pattern remain.
Armor colors are unchanged. Existing bear, wolf and dragon tanning palettes
in EntityHandler.java darken their materials by roughly 20–25%, motivating
this 20% revision. No production definitions or source sprites were edited.

Reproduce with `node ../recolor-hide-leathers.cjs --darker-tanning` from this
directory. Export refuses to overwrite PNGs. Exact tint values and item IDs
are in `manifest.json`; preview is `../hide-leathers-v2-preview.html`.
