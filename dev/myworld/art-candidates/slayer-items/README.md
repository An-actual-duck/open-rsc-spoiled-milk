# Slayer item artwork

Art review assets only: these do not replace live inventory/ground sprites.

`cockatrice-eye-approved.png`: user approved, yellow birdlike Eye of Newt
variant with a round dark pupil. Native 48x32; fitted 11x10 at (18,14) to match
the source eye placement. Generated art fitted with export-native.cjs to 11x11,
then translated from (19,11) to (18,14) without resampling using ffmpeg crop/pad.
See `cockatrice-eye-preview.html` and `cockatrice-eye-prompt.txt`.

`abyssal-vertibrae-candidate.png`: pending review. Single ivory vertebra,
based on the user's photographic shape reference, with RSC bone shading.
48x32 canvas, fitted 10x11 at (18,14), comparable in size to the approved eye.
Packaged at 11x11 maximum, then crop/pad translated from (19,11) to (18,14).
See `vertebra-preview.html`; prompt and reference URL in `vertebra-prompt.txt`.

Latest gland revision: `sticky-saliva-gland-candidate-v2.png` lowers the gland
into the puddle to remove the floating appearance. Same native 48x32 canvas
and 16-pixel width; fitted height 13 pixels. User approved; selected copy is
`sticky-saliva-gland-approved.png`. Original retained.
See `gland-preview-v2.html` and `gland-v2-prompt.txt`.

Remaining order, approved armor colors and simple worn-item reuse are specified
in [the sprite completion plan](../../../../docs/myworld/rough-drafts/slayer-sprite-completion-plan.md).

- `dagger-of-terror-approved.png`: user-selected larger dagger (2026-09-22),
  native 48x32 RGBA, object 28x19 at (10,7). Approved after comparing the
  smaller 23x16 candidate and granting about 20% more object space. Canvas
  remains unchanged. Keep this selected version; do not regenerate casually.
- `abyssal-whip-approved.png`: user approved with no notes (2026-09-22),
  native 48x32, object 40x22 at (4,5). Exposed vertebrae with slate-blue
  connective flesh and a rib-bone handle. The earlier `candidate` filename
  remains a byte-identical preview reference.
- `thunder-spire-staff-approved.png`: user approved first attempt. Wood shaft,
  grey-brown Dark Beast horn, leather lashings at the mount, and yellow arcs.
  `staff-preview.html` compares its native canvas with the three god-staff
  bases. In the export catalog Guthix aliases the plain wooden staff (2235),
  Zamorak the red-headed staff (2487), and Saradomin uses sprite 2564.
- `leaching-bow-approved.png`: user approved, Ebony Longbow reference with
  muted red woven Bloodveld-tongue string. Tier-8 stats/logs supersede the
  previous tier-9 design target; see the corrected equipment design and
  implementation ledger. The Magic Longbow reference is historical only.
- `shield-of-mobility-approved.png`: user approved. Round feather shield,
  densely mottled center and clear protruding feather tips; brown/olive/straw
  cockatrice palette. See `shield-preview.html` for native-size comparison.
- `sullen-pendant-approved.png`: user approved. Pale-blue crystallized tear
  on a thin white/grey thread, based on the authentic strung amulet references.
  See `pendant-preview.html` for native-size comparison.

Artwork generated using the built-in imagegen tool. Prompts are saved alongside
the art. `sticky-saliva-gland-candidate.png` is pending user review: pale-green
rounded gland with attached drips and a small puddle, inspired by the spider
eye's Eye of Newt source sprite (items:116). Native 48x32, fitted 16x18 at
(16,7). See `gland-preview.html` and `gland-prompt.txt`. No production replacement.
Sources and prompts for all candidates are kept alongside
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
