# Divine bolt visuals and animation sizing

November 15, 2026. Candidate worker branch: art/divine-bolt-prayer-visuals.
No public deployment, private-client replacement or server/stat changes.

## Owner decisions

Approved: dedicated white prayer icons (heart-piercing bolt, bramble bolt,
blood-dripping bolt), new enveloping holy-bolt trails, the corrected bolt
orientation, bleed at twice its original size, and Revelation at four times its
original size. Poison remains unchanged pending review of the shared sizing.
The bat summon's attack visual was fixed earlier and is not an outstanding fix.

## Implementation

Basic and god bolts use a stable native 96x96 canvas with a horizontal bolt and
fixed center pivot. God trails surround the bolt and taper backward. Source
Aseprite masters/recipe and the original basic bolt are retained in shots-v4/;
older approved shots-v3 and its manifest remain preserved. shot-selection.json
selects the owner-approved v4. Inventory and held-crossbow art is unchanged.

A bounded existing projectile queue now also renders these moving bolts,
projecting their actual world position and aiming toward the target's projected
position. Camera projection works without GPU geometry capture, supporting the
software path too. Caller-supplied NPC/player projectile heights are retained;
flight direction, speed, server mechanics and projectile IDs are unchanged.
Native loading avoids re-fitting each trail phase, and the existing rotated
sprite renderer handles aiming/perspective scaling. Fog culling remains applied.

Revelation and bleed change both world and overlay size paths by the same approved
factors. Poison and other effect sizes are unchanged. New generated prayer icons
have source/provenance and separate credits, retaining existing third-party credits.

## Shared sizing audit

scripts/audit-animation-sizing.py reads the actual packaged loader and all 86
catalog effects. All 86 load successfully. Loading caps are 64 for 40 entries,
48 for 26, 32 for three, and 24 for 17. These are not all globally reduced.

Compounded sizing explains the unusually small new status effects: artwork is
fitted within a small portion of a padded 64x64 canvas, retaining its source
padding, then that canvas is scaled down again by certain drawing paths. At the
64-pixel-overlay reference used by the preview:

| Effect | Loaded peak artwork | Original rendered peak | Selected rendered peak |
| --- | --- | --- | --- |
| Revelation | 18x17 | 7.6x7.2 | 30.4x28.7 (4x) |
| Bleed | 37x19 | 23.7x12.2 | 47.4x24.3 (2x) |
| Poison | 11x10 | 7.0x6.4 | unchanged |

These are preview/overlay-reference dimensions, not fixed pixel sizes at every
in-game camera distance; world effects also scale with projection. Many mage
effects use the full loading cap and do not suffer the same small extra cap.
Cleric buff/debuff playback deliberately uses a separate 64-pixel overlay path,
so compact artwork there is not multiplied by the generic status-effect reduction.

Recommended follow-up: make intended visible size explicit in one shared effect
sizing contract. Measure a stable union of content bounds over the whole sequence
so source padding cannot silently reduce it. Preserve anchors, relative frame
movement and currently approved visible sizes. Avoid independently cropping or
fitting each frame, and do not blanket-enlarge every animation. Poison is the next
specific effect requiring an owner size choice.

## Verification / preview

- Client build passed.
- Dedicated visual suite: 208 checks in each sprite mode, including actual native
  packaged loading, directed queue routing, renderer aiming in eight octants,
  software projection, perspective scale, approved sizes and unchanged poison.
- Existing crossbow visual checks: 427 custom, 430 classic, approved held/archive
  preservation; archived v3 geometry is also retained.
- Existing gameplay suite: 216 runtime and 19 real-client checks passed.
- New prayer icon and source/runtime parity checks passed.
- git diff --check passed. Layout checking reports retained pre-existing legacy
  output-root folders; no evidence was deleted.

Preview: scripts/preview-divine-bolt-visuals.py (requires passing current-JAR
fixture exports). Firefox page is under output/art/god-crossbow-prayer-shots/study-v4.
It includes current poison beside approved bleed and the final Revelation size.
The owner deferred poison resizing to a separate shared animation-sizing pass.
The approved crossbow/bleed/Revelation work is ready for integration.

## Next pass: zoom-aware animation sizing

After completing this batch, standardize intended visible effect sizes and scale
them with camera projection. Effects must retain their size relative to actors
and terrain when zooming, rather than remaining fixed screen-pixel overlays.
Cover on-entity effects, Cleric buff/curse sequences, detached effects and moving/
static projectiles; preserve approved appearances at a documented reference zoom.
Use stable sequence-wide bounds and anchors, avoid per-frame auto-fitting, and
verify software, OpenGL and browser paths across zoom levels. Poison sizing is
part of this follow-up. The previously fixed bat summon is not reopened.

## Release scope and later catalog overhaul — owner direction

For the coming update, establish proper visible animation sizes and make those
sizes follow camera zoom, preserving the accepted reference appearances. Review
poison and other undersized effects without blanket enlargement. Keep the current
asset layout, catalog organization, effect assignments and positioning behavior
except where a focused sizing correction requires otherwise. The larger animation
catalog and positioning overhaul follows this release; it must not delay it.

### Long-term catalog intent (documented, not implemented in this release)

Animation assets will have short descriptive titles such as **Acid Drop** and
**Acid Burst**. Their identity must be independent of folder categorization and
independent of whether they are currently used as a projectile or an on-entity
effect. Names are examples, not a decision to rename existing files now.

Usage keywords/metadata determine how an animation is played: projectile,
on-entity, attachment/recipient, target zone, size percentage, and later anchor/
facing adjustments. An animation is not permanently classified as one use type.
For example, a spell could request:

- Acid Burst; on-entity; 100% size; target middle.
- Acid Drop; projectile; 50% size; target middle.

Reversing the usage keywords should allow Acid Burst to be the projectile and
Acid Drop the on-entity impact, without relocating or duplicating the assets.
The eventual browsing/search interface should center descriptive titles and
keyword assignment rather than the current folder tree. Define what 100%,
on-entity and each target zone mean consistently, and allow explicit exceptions
for unusual artwork. Percentages are not yet a finalized catalog schema.

The later positioning work should support top/middle/bottom zones and artwork
anchors: teleport at the feet, summon arrival beneath the summon, lightning
ending at the ground, fireball at middle/front, and rising effects anchored at
the bottom. Preserve stable anchors and sequence-wide scaling. Do not implement
this larger abstraction or move asset directories as part of the release-sizing
fixes.

## Release correction implementation

A focused sizing fix now applies camera scale to attached spell/status overlays,
Cleric buff/curse sequences, and the world-projected effect queue. Previously
these paths replaced projected sizes with fixed screen pixels. Reference sizing
uses a 128-pixel-tall actor, retaining the owner-approved Revelation (108-pixel
canvas) and bleed (82-pixel canvas) at that reference. NPC overlays follow the
actual projected entity height. All sprite proportions/sequence framing remain
unchanged; no per-frame crop or new animation catalog is introduced.

Poison now uses a 164-pixel reference canvas (4x its prior 41), bringing its
visible peak from roughly 7x6 to 28x26 at reference size, comparable in height to
approved bleed. Other compact Cleric/mage sizes remain as audited; there is no
blanket enlargement. The bat summon remains untouched.

Detached effects use their existing world-position continuation when a world/
scene is available, so they follow camera movement/zoom after the target leaves,
rather than freezing a screen rectangle. The legacy screen-only fallback remains
for contexts with no scene. Projectiles already project through world/camera
space; their sizes and approved visuals are retained.

The dedicated visual fixture exercises the real attached effect draw, Cleric
playback and queued world-effect paths across zoom sizes, including reference
size preservation. Firefox preview adds a zoom selector that scales the figure
and effect together. In-game owner verification is still required before release.

The final sizing sweep also covers projectile impact overlays and static beam
thickness, which otherwise retained fixed pixel sizes even though moving shots
and beam lengths were already projected. Both now use the same reference-actor
scaling. An actual impact-queue regression verifies half/reference/double zoom.

### Rotated bolt transparency correction

Native grid PNG frames retained transparent pixels but were tagged as opaque for
the legacy rotated-sprite renderer, producing black flight rectangles. Native
grid frames now enable its transparent-pixel path while retaining zero offsets
and full canvas dimensions. The visual fixture asserts background preservation
for basic and all holy bolts in eight orientations and every animation phase.
The requested poisoned mithril dagger is catalog ID 562; private scenario edits
must occur only after the test client logs out and its private server stops.

### Owner-selected reverse orientation

At the owner's request, the complete basic/holy bolt artwork (bolt and attached
trail together) is rotated 180 degrees relative to the previous flight facing.
Trajectory, animation timing, colors and transparent canvases are unchanged.
The offline preview uses the same reversal, and all eight octants are checked.
Private test Melee is raised to 40 to equip the poisoned mithril dagger (38
required); no production balance or account is changed.
