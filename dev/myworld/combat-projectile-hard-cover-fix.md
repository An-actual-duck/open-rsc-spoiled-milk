# Combat Projectile Hard-Cover Fix

## Cause

The July semantic line-of-fire change created a dedicated hard-cover mask, but
named and routed it as `HOSTILE_PROJECTILE`. NPC ranged and magic launch checks
used that mask, while player ranged and magic launch checks and the
`PLAYER_DAMAGE` delayed-impact policy continued to use `GENERAL_PROJECTILE`.
That older mode derives decisions from traversal and the legacy
`projectileAllowed` flag, so it did not express the required fence/wall/door
versus scenery/terrain distinction. The two impact-policy enum values preserved
the same player/NPC divergence after a projectile was already in flight.

Native layered dynamic objects had an additional gap. Their registration path
returned after committing the immutable layered collision overlay, before the
legacy-only hard-cover updater ran. Consequently an opened or closed native
door changed traversal collision but never changed the dedicated projectile
mask.

## Contract

`PathValidation.checkCombatProjectilePath` is now the single combat
line-of-fire contract at launch and delayed impact for player, NPC, summon, and
cannon ranged/magic projectiles. It reads only the semantic combat-projectile
mask:

- authored and dynamic walls, fences, and closed doors are hard cover;
- open doors, ordinary blocking scenery, and movement-only terrain are clear;
- void, absent terrain, different world spaces, and different levels fail
  closed.

Legacy object transactions update reference-counted mask ownership directly.
Native layered object transactions stage the same ownership atomically beside
their existing collision aggregate, including reciprocal cardinal edges and
diagonal boundary flags. Door replacement therefore removes or restores hard
cover immediately, and delayed impact validation observes the current door
state rather than the launch snapshot's collision state.

Walking collision, melee approach, range, projectile timing, resource costs,
damage settlement, and non-combat interaction line checks remain on their
existing contracts.

## Regression coverage

The executable hard-cover fixture covers clear paths, fence/full cover,
cardinal walls, diagonal barriers, closed/open door transitions, ordinary
scenery, lava, water, void, missing tiles, and both directions. Layered
location coverage exercises native/legacy selection plus cross-space and
cross-level refusal. The native placement fixture verifies reciprocal fence
cover and closed-to-open-to-closed door replacement. Source contract checks
require player and NPC ranged/magic launch callers and every damaging delayed
impact policy to use the shared API, while explicitly guarding walking and
melee paths from adopting it.
