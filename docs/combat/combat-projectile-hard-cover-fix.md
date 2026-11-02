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

Combat line of fire now has two explicit, allegiance-aware contracts at launch
and delayed impact:

- `PathValidation.checkCombatProjectilePath` is the player-allied route used by
  players, summons, cannons, and administrator projectiles;
- `PathValidation.checkEnemyCombatProjectilePath` is the hostile route used by
  NPC ranged, magic, breath, and special attacks.

Both routes treat authored and dynamic walls and closed doors as hard cover.
Fences and palisades are transparent to player-allied attacks but hard cover
against enemy attacks. Open doors, ordinary blocking scenery, and movement-only
terrain are clear for both. Void, absent terrain, different world spaces, and
different levels fail closed for both.

Legacy object transactions update separate reference-counted structural and
enemy-only fence masks directly. Native layered object transactions stage the
same ownership atomically beside their existing collision aggregate, including
reciprocal cardinal edges and diagonal boundary flags. Door and fence
replacement therefore update the correct cover immediately, and delayed impact
validation observes current cover rather than the launch snapshot's state.

Walking collision, melee approach, range, projectile timing, resource costs,
damage settlement, and non-combat interaction line checks remain on their
existing contracts.

## Regression coverage

The executable hard-cover fixture covers clear paths, asymmetric fence cover,
cardinal walls, diagonal barriers, closed/open door transitions, ordinary
scenery, lava, water, void, missing tiles, and both directions. Layered
location coverage exercises native/legacy selection plus cross-space and
cross-level refusal. The native placement fixture verifies reciprocal
enemy-only fence cover and closed-to-open-to-closed door replacement. Source
contract checks require player-allied and enemy launch callers and delayed
impact policies to use their matching API while explicitly guarding walking
and melee paths from adopting either contract.
