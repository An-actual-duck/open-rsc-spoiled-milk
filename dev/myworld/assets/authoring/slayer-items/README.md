# Slayer item artwork

## Current integration status — 2026-09-24

Approved equipment inventory and held/worn art, all 30 leather armor icons,
all 11 component icons, all five consumable families (each charge variant),
and the 12 raw/tanned `hide-leathers-v2` images are now installed in production
asset sources and packaged by the client build. This is worker-branch state,
not a claim of public deployment.

`material-icons-integration.json` maps the 28 material/consumable images to
38 active item definitions. `package-material-icons.cjs` copies accepted art
verbatim; retired Banshee hide/leather/armor are deliberately excluded.
See [integration and verification](../../../../docs/myworld/rough-drafts/slayer-material-icon-integration.md).

## Historical art review log

The following candidate-review notes describe the sequence of earlier passes.
Their "pending" and "not installed" statements are historical, superseded by
the current integration status above. Original references/candidates are kept.

`wax-earplugs-approved.png`: user approved unchanged.
`dog-treats-candidate.png`: pending review, unbranded yellow carton with brown
bone symbol. Native 48x32, fitted 34x28 at (7,2). Built-in imagegen;
see `dog-treats-preview.html` and `dog-treats-prompt.txt`. Source retained.

`eye-drops-approved.png`: user approved without changes.
`wax-earplugs-candidate.png`: pending review; warm cream-yellow soft wax lump
with one small stretched/pinched-off piece beside it. Native 48x32, fitted
30x15 at (9,9). Built-in imagegen; see `wax-preview.html` and `wax-prompt.txt`.

`lightning-horn-approved.png` and `slime-solvent-approved.png`: user approved,
unchanged from candidates. All eleven unique drop subjects now have approved art.
`eye-drops-candidate.png`: pending review. Smaller blue potion bottle with
pipette bulb top. 48x32 transparent canvas, fitted 14x22 at (17,5).
Built-in imagegen; see `eye-drops-preview.html` and `eye-drops-prompt.txt`.

`abyssal-rib-approved.png`: user-approved single rib, unchanged.
`lightning-horn-candidate.png`: pending review, detached Dark Beast grey-brown
horn, fitted 32x20 at (8,6) on 48x32.
`slime-solvent-candidate.png`: pending review, orange liquid in classic conical
potion vial, fitted 20x29 at (14,2) on 48x32. Both use built-in imagegen.
See `horn-solvent-preview.html`, `horn-prompt.txt`, `solvent-prompt.txt`.
Other gimmick icons remain deferred until these are reviewed.

`leach-tongue-approved.png`: user-approved tongue candidate, unchanged.
`abyssal-rib-candidate.png`: pending review. One plain curved ivory rib bone,
48x32 canvas, fitted 32x20 at (8,6). Built-in imagegen; source render retained.
See `rib-preview.html` and `rib-prompt.txt`.

`cockatrice-feathers-approved.png`: user-approved multicolor v2, unchanged.
`leach-tongue-candidate.png`: pending review. Detached thin red tongue lying
in a loose squiggle, based on approved Bloodveld tongue-attack frames and RSC
rat-tail inventory scale. Native 48x32, fitted 38x9 at (5,12). Built-in imagegen;
see `tongue-preview.html` and `tongue-prompt.txt`. Source render retained.

User approved Frozen Tear and Terror Fang: authoritative selected copies are
`frozen-tear-approved.png` and `terror-fang-approved.png`, unchanged from candidates.
The single-color feather was rejected. `cockatrice-feathers-candidate-v2.png`
adds distinct olive, brown and cream plumage areas using built-in imagegen.
Native 48x32, fitted 35x16 at (7,8). Pending approval; see `feather-v2-preview.html`
and `feather-v2-prompt.txt`. Earlier candidate retained for comparison.

Pending review: `cockatrice-feathers-candidate.png` preserves the authentic
feather alpha/geometry, recolored brown/straw by `recolor-cockatrice-feather.cjs`.
`frozen-tear-candidate.png` is a glossy pale-blue gem (15x22 at 17,5).
`terror-fang-candidate.png` is an oversized ivory fang (27x22 at 11,5).
The tear and fang use built-in imagegen, with prompts/source renders retained.
All three use 48x32 canvases. See `feather-tear-fang-preview.html`.

`cockatrice-eye-approved.png`: user approved, yellow birdlike Eye of Newt
variant with a round dark pupil. Native 48x32; fitted 11x10 at (18,14) to match
the source eye placement. Generated art fitted with export-native.cjs to 11x11,
then translated from (19,11) to (18,14) without resampling using ffmpeg crop/pad.
See `cockatrice-eye-preview.html` and `cockatrice-eye-prompt.txt`.

`abyssal-vertibrae-approved.png`: user approved. Single ivory vertebra,
based on the user's photographic shape reference, with RSC bone shading.
48x32 canvas, fitted 10x11 at (18,14), comparable in size to the approved eye.
Packaged at 11x11 maximum, then crop/pad translated from (19,11) to (18,14).
See `vertebra-preview.html`; prompt and reference URL in `vertebra-prompt.txt`.

`slimey-residue-approved.png` and `ectoplasm-approved.png`: user approved.
Gelatinous puddles matching approved demon skin blues and banshee exposed
skin purples respectively. Each uses 48x32 canvas, fitted 34x14 at (7,9).
Swamp tar was the footprint/style reference. See `puddles-preview.html`,
`residue-prompt.txt` and `ectoplasm-prompt.txt`. Generated with built-in imagegen.

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

Held Dagger of Terror: `held-dagger-of-terror/` contains the full custom dagger
18-frame bone recolor, source copies, offsets, manifest and animated preview.
Yellow hilt removed; original shape/gaps/alpha retained. Attack alignment is
inferred from the existing frames, not yet verified in game. Not installed.

Latest hides/leathers: `hide-leathers-v2/` keeps raw hides unchanged and
darkens each tanned leather tint by 20%, following existing tanning palettes.
See `hide-leathers-v2-preview.html`. Original pairs retained; not installed.

Hides/leathers: all 12 inventory candidates are in `hide-leathers/`, using
the armor palettes and original items:69 geometry. See its README/manifest
and `hide-leathers-preview.html`. Palette swaps only, not installed.

Leather armor: all 30 inventory candidates are in `leather-sets/`, with the
six requested family palettes. See `leather-sets-preview.html` and the folder
README/manifest for exact tints, source IDs and verification. Not installed;
worn sprites remain a separate task.

Poisoned dagger approved by user; selected copy:
`dagger-of-terror-poisoned-approved.png`.

Poison dagger: `dagger-of-terror-poisoned-candidate.png` adds a green tip to
the approved dagger. Built-in imagegen edit, tip colors composited onto original
with `package-poison-dagger.cjs`. Original alpha and pixels outside x>=31
remain identical. Native 48x32; see `poison-dagger-preview.html` and prompt.
Pending review, not installed.

Static wipes v2 was approved by the user; selected copy is
`static-wipes-approved.png`.

Static wipes revision: `static-wipes-candidate-v2.png` supersedes the first
candidate for review. Plain brown product sleeve with straight opening and
three white sheets, referencing anti-static dryer sheets rather than letters.
Native 48x32; fitted 30x27 at (9,3). See `static-wipes-preview-v2.html` and
`static-wipes-v2-prompt.txt`. Built-in imagegen edit; original retained.
Pending approval, not installed.

Latest approvals and candidate:
- `dog-treats-approved.png`: user approved the yellow box with a brown bone
  symbol; no branding. Native 48x32, object 34x28.
- `static-wipes-candidate.png`: pending user review. Exactly three blank white
  sheets fanned out of a tan envelope. Native 48x32, packed within 34x27.
  See `static-wipes-preview.html` and `static-wipes-prompt.txt`. Source retained
  in `sources/static-wipes-generated.png`; not installed in production.

Review at 1x and matching integer pixel zoom, not from the high-resolution
source alone. Preserving a design does not automatically make it game-ready.
