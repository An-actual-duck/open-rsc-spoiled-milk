# Slayer item artwork

Art review assets only: these do not replace live inventory/ground sprites.

- `dagger-of-terror-approved.png`: user-selected larger dagger (2026-09-22),
  native 48x32 RGBA, object 28x19 at (10,7). Approved after comparing the
  smaller 23x16 candidate and granting about 20% more object space. Canvas
  remains unchanged. Keep this selected version; do not regenerate casually.
- `abyssal-whip-approved.png`: user approved with no notes (2026-09-22),
  native 48x32, object 40x22 at (4,5). Exposed vertebrae with slate-blue
  connective flesh and a rib-bone handle. The earlier `candidate` filename
  remains a byte-identical preview reference.
- `thunder-spire-staff-candidate.png`: pending user review. Wood shaft,
  grey-brown Dark Beast horn, leather lashings at the mount, and yellow arcs.
  `staff-preview.html` compares its native canvas with the three god-staff
  bases. In the export catalog Guthix aliases the plain wooden staff (2235),
  Zamorak the red-headed staff (2487), and Saradomin uses sprite 2564.

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
