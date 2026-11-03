# Cleric Sigil Item Sprites

The eight stone-family PNGs and `stone.png` were supplied by the project owner
from `/home/justin/Core-Framework/output/sigils` on 2026-08-03. The maintained
copies preserve those source files byte-for-byte.

The `silver-*.png` files are derived variants. Their transparent canvas,
silhouette, lighting regions, and religious/neutral symbol pixels come from the
matching stone file. Only pixels belonging to the plain `stone.png` substrate
palette are converted to neutral silver values:

| Stone RGBA | Silver RGBA |
| --- | --- |
| `65,74,86,255` | `55,55,55,255` |
| `91,87,98,255` | `75,75,75,255` |
| `95,102,112,255` | `95,95,95,255` |
| `111,120,131,255` | `115,115,115,255` |
| `129,134,145,255` | `190,190,190,255` |
| `143,152,163,255` | `215,215,215,255` |
| `159,168,179,255` | `235,235,235,255` |
| `181,174,195,255` | `255,255,255,255` |

Both source families remain `28x25` RGBA PNGs so symbol pixels are not
resampled in maintained art. Client item definitions render stone sigils at
`28x25` and silver sigils at the slightly smaller `24x21` footprint with the
external item loader's nearest-neighbor scaling.

The item definitions retain canonical packaged fallbacks independently of
these files: stone sigils use `items:443`, and silver sigils use `items:134`.
Do not replace either fallback with an external-only sprite ID.
