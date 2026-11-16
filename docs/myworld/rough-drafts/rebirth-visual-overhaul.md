# Rebirth Visual Overhaul

Recorded 2026-11-16 at the owner's request so this conversation does not need to
be reconstructed later.

**Status: speculative, distant concept. Not an official goal, approved roadmap
item, implementation assignment, or commitment to replace the current client.**
The owner initially wanted discussion only, then requested this reference note.
No prototype, performance guarantee, asset selection or engine decision follows
from saving it.

## Core idea

A player who has maxed their stats could choose to **“be reborn in another
world.”** This is a possible prestige system with a distinct world and visual
identity. The reborn body starts at level 1. The player can transfer their soul
back to their original RSC body at any time, bringing new equipment, learned
spells and other transferable progression/content back with them.

The modern world should still play like RSC at its core. It supplements the
classic experience rather than replacing it. The goal is not merely a graphics
toggle: separate bodies/progression and another world are part of the concept,
while compatible mechanics and content connect both experiences.

Exact eligibility, what “maxed” includes, inventory ownership, equipment
requirements, stat handling between bodies, transfer restrictions and balance
are undecided. Do not interpret “bring gear back” as permission to ignore item
requirements or duplicate items. Soul-transfer persistence and consistency would
need deliberate design if this is pursued.

## Visual direction

The owner imagines a mixture of higher-resolution pixel-art characters and a
richer polygonal 3D environment, in the spirit of modern games combining detailed
sprites with 3D scenery. It need not be a faithful upscale of RSC artwork.

- Higher-resolution player and NPC sprites with more movement/attack frames.
- Better movement presentation while preserving underlying gameplay timing.
- Improved 3D models: still polygonal in style, but more detailed than RSC.
- Richer terrain textures, grass rising above the ground, gravel/cobblestone
  roads, and subtly moving liquid surfaces.
- A consistent art direction that feels at home in the new setting.

The owner has previously purchased asset bundles that might supply suitable
models, such as low-poly trees. No particular bundle is selected or imported;
actual files, suitability and existing usage rights would be checked when used.

For AI-assisted sprite creation, larger canvases and fewer RSC-specific style
restrictions may make production more practical. Rather than “upscale this RSC
goblin,” use a detailed prompt for a newly designed pixel-art goblin fitting the
new world's style. This is an intended production approach, not proof that AI
will maintain identity across every animation frame automatically.

A future style reference should establish proportions, palette, pixel density,
lighting, camera/facing directions, animation timing and how sprites fit beside
3D scenery. Equipment and character parts must stay aligned across poses.
Whether frames are hand-authored, AI-assisted, rendered from rigged models, or
produced through a mixture remains open.

## Compatibility principle

Separate authoritative gameplay from visual presentation. Both experiences
should understand the same underlying content identities, rules and player
state where appropriate. Different representations need not imply different
combat implementations.

A shared identity with separate body/progression records was discussed as a
possible architecture, not an adopted schema. Transferred items and spells need
appropriate representations in both worlds, or deliberate fallbacks. Different
models must preserve intended collision, interaction and gameplay footprints;
visual size should not silently redefine reach, pathfinding or target selection.

Server tick rate and attack cadence need not increase to support more animation
frames. Smooth visual frames can run between authoritative updates. Animation
length or frame count must not become a second source of damage/movement timing.

## Java, renderer and performance discussion

Java does not inherently prevent higher-resolution sprites, richer 3D scenery
or smooth animation. A 1080p output window does not determine native sprite
resolution. Detailed art can occupy the same world-space size as classic art.

This would require more than substituting larger PNGs. Existing assumptions
about character parts, directions, frame arrangements, sizing and asset loading
would need review. Extending selected systems may suffice; replacing a larger
renderer component could also be justified by evidence. No decision to change
language, engine, graphics API or client architecture has been made.

The owner explicitly welcomes substantial backend upgrades if they provide a
better foundation, rather than requiring the smallest or easiest modification.
A possible technical framing is a modern presentation layer compatible with
RSC gameplay, rather than forcing all new art through classic asset assumptions.

Potential areas for investigation, if resumed:

- Flexible animation definitions and synchronized character/equipment layers.
- GPU sprite drawing/batching, materials and vegetation support.
- Asset streaming, bounded CPU/GPU caches and preloading before world transfers.
- Separate rendering bounds, interaction targets and collision footprints.
- Terrain materials, animated water and appropriate lighting/shadows for sprites.
- Shared authoritative content/state with distinct classic and modern assets.

More frames mainly add stored artwork; ordinarily one frame is shown at a time.
Higher pixel dimensions increase texture memory and bandwidth, and doubling both
sprite dimensions quadruples pixel count. Neither implies a matching FPS loss.
CPU compositing, texture uploads, transparency/overdraw, detailed scenery and
lighting can be larger costs. Performance depends on the actual implementation
and target hardware; maintaining 60 FPS is a goal to investigate, not a promise.

Standing grass could use small planes or selectively scattered geometry, with
simpler representations at distance. Roads could combine textures and selective
raised detail rather than modeling every stone. Water could use animated textures
or shaders. These are options, not chosen techniques or mandatory effects.

Visual/world transitions could feel seamless if destination assets are prepared
beforehand. Both complete asset libraries need not remain loaded permanently.
An entirely stall-free transition cannot be guaranteed without implementation
and measurement, especially on weaker systems or in the browser.

## How to resume the discussion

First re-establish whether the owner wants this to become an actual project.
If so, distinguish the prestige/body-transfer design from visual-engine work,
choose a coherent art direction and inspect candidate purchased assets. A small
representative scene and animated character could test visual fit, equipment
alignment, loading and performance before any world-wide art commitment.

Do not add this to release requirements, schedule workers, import assets or
start an engine rewrite merely because this note exists. The current classic
experience remains independently valuable and must not be displaced by default.
