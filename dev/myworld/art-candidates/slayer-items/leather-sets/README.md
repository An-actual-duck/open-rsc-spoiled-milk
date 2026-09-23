# Six leather armor palette candidates

Thirty inventory/ground PNGs, five per family. User requested deterministic
palette swaps, not image generation. These are review artwork only; client
definitions, cache archives and worn sprites are unchanged.

| Family | Tint |
| --- | --- |
| Giant Frog | #238fff vibrant blue |
| Naga | #589d40 green |
| Terror Dog | #d34538 red |
| Bloodveld | #e3b59a fleshy beige |
| Dark Beast | #55565a black/charcoal |
| Ugthanki | #efd04b yellow |

Sources are the currently assigned Custom_Sprites.osar item bases: coif 5,
cuirass 7, gloves 17, boots 223, chaps 590. These are legacy runtime bases,
not the optional remastered replacements. `../leather-bases/` contains
read-only exports retaining their full 48x32 canvas and offsets. No source
archive or reference-library asset was modified.

Recoloring mirrors the client's grayscale mask multiplication: each gray
channel times its tint channel, shifted right by eight. Non-gray trim remains
unchanged. Source shading is preserved, including the boots' lighter base.
Dark Beast uses charcoal rather than zero black so details remain readable.

`../export-leather-bases.cjs` reads the OSAR format documented by the client's
Unpacker. `../recolor-leather-sets.cjs` creates all candidates, manifest and
preview and asserts unchanged alpha, colored trim and 48x32 dimensions.
Both exporters refuse to overwrite PNGs. Review in `../leather-sets-preview.html`.
When integrating, use either these baked PNGs with neutral runtime tint, or
the existing base sprites with these tint values, never both tints together.
