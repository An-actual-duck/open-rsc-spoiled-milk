# Hide and tanned leather palette candidates

Six families, two item icons per family. Both stages reuse the existing
items:69 folded-hide geometry. Exact colors are read from the armor palette
manifest so these remain consistent with the six armor sets. No generative
redesign, resizing, shading normalization or new material effects.

`../export-leather-bases.cjs --materials` exports the runtime source read-only
from Custom_Sprites.osar to `../hide-leather-bases/`. The original source is
48x32, occupied bounds (6,5,38,24). `../recolor-hide-leathers.cjs` applies the
client grayscale tint calculation while preserving alpha and colored pixels.
Both exporters refuse to overwrite PNG files.

Raw hide and tanned leather share an image within each family. Names and
item IDs distinguish them, following existing source reuse. Ugthanki now uses
the same requested yellow for both stages rather than its older two browns.

See `manifest.json` for item mappings and `../hide-leathers-preview.html` for
review. Not installed: integration must use either baked PNGs with neutral
tint or the original base with these tint values, never both.
