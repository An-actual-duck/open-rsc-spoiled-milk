# Hostile NPC Projectile Reach Audit

Status: implementation complete; automated verification passed; private visual
verification pending

Date: 2026-07-23

Branch: `docs/ranged-enemy-line-of-fire-audit`

Baseline: `cf41e2d526af463a50b113f8872454b311306866`

## Scope

This audit traces whether a hostile NPC can acquire, pursue, and hit a player
with a ranged or magic projectile. The Elder Green Dragon safe spot across the
lava in the underground Mining Guild area is the primary reproducible case.

The investigation and selected implementation cover:

- natural aggression and existing-threat selection;
- pursuit and movement collision;
- projectile attack-style selection, range, and line-of-fire validation;
- projectile creation and delayed damage delivery;
- terrain, wall, door, scenery, void, distance, and floor behavior;
- modern ranged and magic NPC attacks;
- legacy and boss-specific exceptions;
- semantic hostile-projectile collision ownership and all known launch paths.

## Pre-implementation Executive Finding

The lava does not prevent the Elder Green Dragon from being an eligible target
owner. It prevents both movement and projectile launch.

Before this implementation, the decisive launch check was:

```java
PathValidation.checkPath(
    npc.getWorld(),
    npc.getLocation(),
    target.getLocation(),
    true
)
```

The final `true` means `ignoreProjectileAllowed`. Despite several misleading
field and method names, this is the strict mode: it ignores the tile's
projectile-transparency exemption and applies the ordinary traversal mask.

Lava overlay `11` has both of these properties:

1. its tile definition has nonzero `objectType`, so the tile receives
   `FULL_BLOCK_C`; and
2. `WorldLoader` marks overlays `2` and `11` as `projectileAllowed`.

Player ranged and magic checks use the default path mode, which honors
`projectileAllowed` and therefore treats water/lava as transparent. Modern
hostile NPC ranged and magic attacks use strict mode, which ignores that
exemption and sees lava's `FULL_BLOCK_C`. The Elder's launch is rejected before
a `ProjectileEvent` exists.

This strict hostile-NPC mode was deliberately introduced in commit
`da4a672e0` to stop hostile NPCs shooting through Heroes Guild fence
collision. Replacing `true` with the default mode would make the Elder shoot
over lava, but it would also restore the fence behavior that commit fixed and
would affect water, selected walls, trees, and many one-tile scenery objects.
That one-line change is therefore not a safe policy-neutral fix.

## Elder Green Dragon Reproduction

### Definitions and placement

The relevant authored data is:

- NPC ID: `844`, `ELDER_GREEN_DRAGON`
- name: `Elder Green Dragon`
- spawn: `(263,3430)`
- authored bounds: `(249,3416)` through `(277,3444)`
- aggression: enabled
- explicit offenses: melee `250`, ranged `235`, magic `270`

`NpcAttackStyleProfile` classifies any dragon by ID or name as
`MELEE_MAGIC`. The Elder therefore uses magic projectiles even though its
legacy definition field says `"ranged": false`. Its normal projectile range is
five tiles and its default dragon element is fire.

The server's My World configuration uses:

- `aggro_range: 1`
- `want_improved_pathfinding: false`

The natural aggression scan is consequently small. A player who has already
damaged the Elder can still be selected as its preferred threat without being
inside that one-tile natural scan.

### Map evidence

The Elder is in archive sector `h3x53y49` in
`server/conf/server/data/Custom_Landscape.orsc`. A read-only decode of the
10-byte tile records confirmed an overlay-`11` lava boundary around the
walkable interior.

A deterministic local line-of-fire example is:

| Coordinate | Raw elevation | Overlay | Walls | Meaning |
| --- | ---: | ---: | --- | --- |
| `(249,3426)` | 10 | 0 | none | reachable interior edge for the Elder |
| `(248,3426)` | 2 | 11 | none | lava |
| `(247,3426)` | 2 | 11 | none | lava |
| `(246,3426)` | 60 | 0 | none | walkable player-side tile |

The two endpoints are three tiles apart and both lie inside the Elder's
expanded target bounds. The path's only obstruction is the two lava tiles;
there is no raw wall on this line. Raw elevation is different but is not read
by attack-range or line-of-fire code.

Reproduction:

1. Engage the Elder on the interior so it owns the player as a threat.
2. Move to the player-side land around `(246,3426)` and let the Elder pursue
   to the interior edge around `(249,3426)`.
3. Keep the two entities within five tiles.
4. Observe that the Elder stops at the lava and produces no normal magic
   projectile or normal projectile damage.
5. Return to a clear, same-distance line on walkable ground. Once the combat
   timer is ready, its normal projectile attacks resume.

If the player has not engaged the Elder first, natural acquisition can also
fail simply because the configured aggression radius is one tile. That is a
separate distance condition, not a lava visibility rule.

## Pre-implementation Decision Path

### 1. Attack-style classification

`NpcAttackStyleProfile.forNpc` decides whether an NPC is melee, ranged, magic,
or hybrid. The modern path does not use the legacy `ranged` definition boolean
as the final authority.

For the Elder:

```text
NPC name contains "dragon"
  -> MELEE_MAGIC
  -> magic projectiles enabled
  -> range 5
  -> fire element and fireball visual
```

Hybrid profiles always prefer a projectile beyond melee distance. At melee
distance, `MELEE_MAGIC` rolls a 65% projectile preference.

### 2. Target acquisition

`NpcBehavior.handleRoamAggroScan` has two sources of targets:

1. A prior damage contributor returned by `Npc.getPreferredThreatTarget`.
   Contributors are chosen by combat level and damage with a preference for
   someone already in melee range.
2. A natural scan of visible players within the configured `aggroRadius`.

`canAggro` checks aggression/level rules, spawn bounds expanded by four tiles,
combat and retreat timers, login state, invulnerability/invisibility, and
summon protections. It does not call `PathValidation`, inspect terrain, or
require line of sight.

Result: collision does not directly prevent acquisition. A player can remain
the selected threat while separated by an impassable wall or lava. Distance,
expanded spawn bounds, or player state can independently prevent acquisition.

### 3. Pursuit and pathfinding

`NpcBehavior.handleAggro` first tries a projectile. If that fails and movement
timers allow, it asks the NPC to walk toward the target.

With My World's current `want_improved_pathfinding: false`, this is a direct
`walkToEntity` path rather than A*. `Path.addStep` builds one-tile movement
steps and stops building an NPC path when `PathValidation.checkAdjacent`
rejects a step. `WalkingQueue.processNextMovement` checks the next step again
before applying it and enforces the NPC's authored bounds.

Movement uses:

- directional wall flags;
- both diagonal full-block flags;
- full-tile blocking;
- dynamic scenery collision;
- NPC/player blocking rules where configured.

Lava overlay `11` receives `FULL_BLOCK_C`, so the Elder cannot step onto it.
Enabling A* could find a route around an obstruction, if one exists and fits
the search depth, but would not make a lava tile walkable.

Melee begins only when the predicted next movement is adjacent and another
adjacent collision check succeeds. An unreachable player can therefore remain
targeted while the NPC repeatedly stops at collision.

### 4. Projectile preference, range, and launch

`NpcBehavior.tryProjectileAttack` applies the same gate to modern ranged and
magic NPC attacks:

1. the NPC profile supports a projectile;
2. its profile chooses a projectile at the current distance;
3. the target is within the profile's five-tile range;
4. strict `PathValidation.checkPath(..., true)` succeeds; and
5. the three-tick projectile combat timer is ready.

The distance calculations are square/Chebyshev-style:

- preference uses `max(abs(dx), abs(dy))`;
- `Entity.withinRange(entity, radius)` requires both `abs(dx)` and `abs(dy)`
  to be within the radius.

Thus `(5,5)` is still in projectile range. This is not a Euclidean circle.

`checkPath` constructs a one-tile-at-a-time diagonal/cardinal staircase from
source to destination. It is not a geometric ray cast or balanced Bresenham
line. Each step uses directional and full-tile collision. Corner behavior can
therefore depend on which staircase the algorithm selects.

If this gate fails, no projectile update and no `ProjectileEvent` are created.
The NPC falls back to pursuit.

### 5. Projectile creation and damage delivery

When the launch gate succeeds, damage is calculated immediately and a
`ProjectileEvent` is created. Its constructor submits the projectile visual.
One tick later, `ProjectileEvent.action` applies damage if:

- the event was not canceled; and
- caster and target are still within a square radius of 15.

Damage delivery does not recheck:

- the original five-tile attack range;
- line of sight;
- walls, doors, scenery, water, lava, or void;
- the NPC's authored/expanded bounds.

Consequently, collision never produces the state “ordinary projectile
launched, then collision prevented it landing.” Once a legal ordinary
projectile launches, a newly closed door or a target moving behind cover does
not stop the hit. Non-collision state can still suppress damage after the
visual, including event cancellation, moving beyond 15 tiles, Guard Dog target
rules, or an attack-suppression effect.

### 6. Elder-specific follow-up attacks

After a surviving target receives an ordinary Elder projectile hit,
`ElderGreenDragonSpecialAttacks.maybeApplyProjectileAoe` can roll:

- fireshot AOE within square radius 6; or
- burn AOE within square radius 6.

These follow-ups validate player state and range only. They do not validate
line of sight.

- Fireshot sends its visual, waits one tick, rechecks state/range, and deals
  ranged damage directly.
- Burn sends its visual and starts the burn immediately. Subsequent burn ticks
  require a live player and a still-present source dragon, but do not require
  range or line of sight.
- The melee sweep similarly checks square radius 2 and player state, not
  intervening collision.

The current lava safe spot prevents the ordinary attack from launching, so it
also prevents the projectile-triggered AOE roll. If an ordinary attack legally
lands on another target, however, the resulting AOE can hit secondary players
through walls, doors, or lava.

## Pre-implementation Collision Ownership and Naming

`TileValue` starts with `FULL_BLOCK` so missing/uninitialized world tiles are
blocked. Loaded terrain then derives a traversal mask from several independent
owners:

| Owner | Derived traversal state |
| --- | --- |
| blocking terrain overlay | `FULL_BLOCK_C` |
| north/east terrain walls | directional wall flags on both neighboring tiles |
| diagonal terrain walls | `FULL_BLOCK_A` or `FULL_BLOCK_B` |
| blocking scenery type 1 | counted `FULL_BLOCK_C` |
| directional scenery type 2 | counted cardinal wall flags |
| closed boundary/door type 1 | cardinal or diagonal wall flags |

Separate terrain and dynamic counts allow removal to preserve collision owned
by another source.

`projectileAllowed` is an exemption layered over this traversal mask:

```java
if (!ignoreProjectileAllowed && tile.projectileAllowed) {
    return false; // this tile is not treated as blocking
}
```

Sources that set this exemption include:

- water overlay `2`;
- lava overlay `11`;
- raw wall values `5, 6, 14, 42, 63, 128, 229, 230`;
- trees;
- many one-by-one scenery objects other than chests;
- named objects such as fences, gates, railings, signs, ladders, tables, and
  chairs.

Names such as `projectileClipAllowed`,
`setTerrainOverlayProjectileBlocked`, and `addDynamicProjectileBlock` suggest
the opposite behavior. In the default `checkPath` mode, these values make the
associated tile transparent by bypassing its traversal collision. In strict
hostile-NPC mode, the exemption is ignored and that same traversal collision
blocks.

The exemption applies to the whole checked tile, not to one collision owner.
For example, honoring the lava exemption also bypasses a wall or blocking
scenery mask that happens to share that tile. This coupling is why selecting
the default mode is broader than “allow shots over lava.”

The `OBJECT` collision bit is not itself tested by `checkPath`; registered game
objects are relevant because they derive cardinal, diagonal, or full-tile
flags.

## Pre-implementation Behavior Matrix

“Acquire” describes collision's direct effect. Other target-state and distance
rules can still reject the player. “Land” describes collision after an
ordinary modern projectile has already launched.

| Condition between NPC and player | Acquire player | Walk through it | Launch modern hostile ranged/magic | Ordinary projectile already launched |
| --- | --- | --- | --- | --- |
| Cardinal terrain wall | Collision has no direct effect | No when the crossed edge's flag matches | No when the rasterized line crosses the flagged edge | Collision is not rechecked; hit can land |
| Diagonal wall, either rotation | Collision has no direct effect | No through the diagonal/full flag | No when the line enters the diagonal-wall tile | Collision is not rechecked; hit can land |
| Closed door/boundary | Collision has no direct effect | No when its dynamic flag is registered | No when the line crosses its registered flag | Closing it after launch does not stop the hit |
| Open/removed door | Collision has no direct effect | Yes if no other owner blocks | Yes if no other owner blocks | No impact-stage collision check |
| Blocking scenery type 1 | Collision has no direct effect | No; full-tile block | No when the line enters its tile | Collision is not rechecked; hit can land |
| Directional scenery type 2 | Collision has no direct effect | No across its registered edge | No across its registered edge | Collision is not rechecked; hit can land |
| Nonblocking scenery | Collision has no direct effect | Usually yes | Yes; a visual model alone is not cover | No impact-stage collision check |
| Water overlay 2 | Collision has no direct effect | No; blocking terrain | **No** in strict hostile mode | Collision is not rechecked; hit can land |
| Lava overlay 11 | Collision has no direct effect | No; blocking terrain | **No** in strict hostile mode | Collision is not rechecked; hit can land |
| Void/missing tile | Collision has no direct effect | No; defaults to full block | No; defaults to full block and has no exemption | Collision is not rechecked; hit can land |
| Raw elevation change on one floor | Collision has no direct effect | Elevation itself does not block | Elevation itself does not block | Elevation is not checked |
| Different encoded floor/plane | Normally outside view/range because Y differs by about 944 | No practical path | No practical launch due distance/bounds | A launched event only uses coordinate distance |
| Target beyond natural aggro radius | No natural acquisition; prior threat may still be selected | NPC can pursue if selected | Independent five-tile launch range still applies | Independent 15-tile delivery range applies |
| Target beyond five tiles | Existing target can remain selected | NPC pursues | No | Target may move as far as 15 after a prior legal launch |
| No route to target | Target can still be selected | Pursuit stops or fails | Route existence is not checked; only straight line, range, and timer matter | Route existence is not checked |
| NPC/player standing between endpoints | Collision has no direct effect | Entity-blocking configuration may stop movement | Mobs are not projectile cover | No impact-stage collision check |

Selected walls/scenery marked `projectileAllowed` are transparent to the
default player path but **not** to the strict hostile path. Other walls and
blocking scenery block both. Therefore “wall” and “scenery” do not currently
have one universal player-projectile policy.

## Pre-implementation Ranged and Magic Comparison

| Stage | Modern hostile ranged | Modern hostile magic |
| --- | --- | --- |
| Profile source | `NpcAttackStyleProfile` | `NpcAttackStyleProfile` |
| Range | square radius 5 | square radius 5 |
| Launch path | strict `checkPath(..., true)` | strict `checkPath(..., true)` |
| Pursuit fallback | shared `NpcBehavior` | shared `NpcBehavior` |
| Damage formula | ranged offense/formula | magic offense/spell-power formula |
| Visual | arrow/knife/dart/bolt profile | elemental/profile projectile |
| Impact | normal ranged processing | elemental impact/debuff processing |
| Protect prayer exception | non-MyWorld Protect from Missiles can stop launch | no matching launch exception here |
| Delivery collision recheck | none | none |

The Elder's ordinary long-range attack is the right-hand column. Its configured
ranged offense does not make that ordinary projectile a ranged attack.

## Pre-implementation Exceptional and Legacy Call Sites

### Dragon fire on melee-combat start

`combat/scripts/all/DragonFireBreath` applies to NPCs whose name contains
`dragon`, including the Elder. It executes when reciprocal melee combat starts,
not when the modern long-range projectile is selected. It calls the same
strict `checkPath(..., true)` before directly damaging the player.

Because the Elder cannot begin melee combat across lava, this script does not
rescue or explain the safe-spot attack. It is nevertheless another hostile
dragon damage path that must remain aligned with a future collision policy.

There is also older spell-handler retaliation for NPC IDs `DRAGON` and
`KING_BLACK_DRAGON`. It uses strict path validation and direct damage, but does
not include Elder ID `844`.

### Legacy NPC range event

`RangeEventNpc` also uses strict `checkPath(..., true)`. The only construction
sites found are administrator test commands (`npcShootNpc` and
`npcRangedPlayer`); autonomous modern NPC combat uses `NpcBehavior` instead.
The class contains legacy reach/chase branches and should be covered or retired
if the central policy changes.

### Direct projectile and special paths

- The administrator `npcShoot` command creates `ProjectileEvent` directly and
  bypasses launch line-of-fire checks.
- Elder fireshot/burn create raw projectile update visuals and direct damage
  events without `PathValidation`.
- Player attacks, multicannon, and combat summons use the default transparent
  path mode. They are not hostile-NPC policy call sites but demonstrate the
  current asymmetry.

## Pre-implementation Risks

1. **A one-boolean fix is too broad.** Changing the modern hostile call to the
   default mode simultaneously changes lava, water, fences, selected terrain
   walls, trees, and many scenery objects.
2. **Movement and projectile opacity are conflated.** Strict hostile line of
   fire is derived from walking collision rather than a dedicated opacity
   model.
3. **The transparency exemption is tile-wide.** It cannot independently make
   lava and ordinary scenery transparent while retaining a co-located wall or
   fence owner.
4. **Names obscure semantics.** “Allowed,” “blocked,” and “clip allowed” are
   used for the same exemption, increasing the chance of a reversed fix.
5. **Boss follow-ups bypass the central gate.** Fixing only
   `NpcBehavior.tryProjectileAttack` would not define Elder AOE behavior.
6. **Launch and impact policies differ implicitly.** Cover matters only at
   launch; changing cover during flight cannot stop an ordinary hit.
7. **The line algorithm is not a ray cast.** Diagonal/corner outcomes can look
   surprising and should be locked down before refactoring.
8. **Existing coverage is structural.** `test-npc-projectile-clipping.py`
   checks source strings and preserves the Heroes Guild intent, but does not
   execute collision combinations.

## Policy Options

### Option A: preserve strict hostile collision

Keep all traversal blockers opaque to hostile NPC projectiles.

- Elder safe spot remains.
- Heroes Guild fence behavior remains fixed.
- Lowest implementation risk.
- Water/lava continue acting as hard projectile cover only against hostile
  NPCs, not against players.

### Option B: use the existing default projectile path

Change hostile NPCs to honor `projectileAllowed`, matching player attacks.

- Elder can shoot over lava.
- Water/lava become transparent.
- Heroes Guild fences, selected walls, trees, and many scenery objects also
  become transparent to hostile NPCs.
- Co-located collision owners may be bypassed together.

This is simple but is not recommended as a global fix.

### Option C: add semantic projectile collision ownership

Separate movement blocking from projectile opacity and use an explicit policy,
for example:

| Collision source | Selected hostile projectile policy |
| --- | --- |
| lava and water surface | transparent; blocks walking only |
| cardinal/diagonal structural wall | opaque |
| closed door/gate | opaque |
| open/removed door | transparent |
| fence of any kind | opaque, regardless of location or visual subtype |
| ordinary solid scenery, including rocks and trees | transparent; still blocks walking where applicable |
| void/unloaded tile | opaque |

This would let the Elder attack over lava without reopening the Heroes Guild
fence case or allowing rocks to become Elder safe spots. It should use an enum
or named projectile-path API rather than another ambiguous boolean.
Source-specific masks or counts must preserve a wall or fence blocker sharing
a lava tile while allowing ordinary solid scenery to remain projectile
transparent.

This is the selected implementation direction.

### Option D: Elder-area or boss-only exception

Permit Elder projectiles over lava in its arena while keeping global strict
behavior.

- Smallest behavioral blast radius.
- Creates a location/boss exception rather than a coherent collision rule.
- Does not resolve water/lava behavior for other hostile ranged or magic NPCs.
- Still requires a decision for Elder AOE and dragonfire.

This is acceptable only as a consciously temporary compatibility choice.

## Selected Implementation Policy

The implementation decisions are:

- water, lava, and ordinary solid scenery block movement where applicable but
  do not block hostile ranged/magic line of fire;
- rocks, trees, and other ordinary scenery must not create hostile-NPC safe
  spots;
- walls, diagonal walls, closed doors, void, and fences of every kind remain
  opaque;
- fence opacity is a global semantic rule, not a Heroes Guild coordinate or
  object-ID exception;
- validate collision at launch and commit the ordinary hit after launch;
- validate each Elder AOE target at AOE launch, then allow a legally applied
  burn to finish;
- keep raw elevation visual-only until the renderer/world format has an
  intentional height-aware LOS model;
- route autonomous, legacy, dragonfire, and boss launch decisions through one
  explicit server API, while clearly labeling administrator bypasses.

The implementation must inventory all fence forms in the definitions,
including fence scenery, fence boundaries, and fence-like gates. It should use
explicit semantic metadata or one centrally tested classifier rather than
assuming that the existing Heroes Guild raw wall value represents every
fence. An open gate or removed fence segment is transparent because its
blocking collision is no longer present; a closed fence gate is opaque.

## Regression Coverage Plan

The implementation phase should add deterministic behavioral tests, not only
source-string guards.

### Core acquisition and pursuit

- Natural acquisition inside and outside configured aggro radius.
- Preferred-threat reacquisition across an unreachable barrier.
- Target retained while direct pursuit stops at collision.
- Target dropped only by bounds/state rules, not merely by failed pathfinding.

### Collision matrix

- Cardinal wall in every crossing direction.
- Both diagonal wall rotations and corner approaches.
- Closed door blocks; opening/removing it permits the selected policy.
- Solid type-1 scenery such as a rock remains movement-blocking but
  projectile-transparent.
- A tree remains projectile-transparent.
- Every fence family found in the definitions blocks hostile projectiles,
  including cardinal, diagonal, scenery, boundary, and closed-gate forms.
- Opening a fence gate removes its projectile obstruction; closing it restores
  the obstruction.
- Water overlay `2`, lava overlay `11`, ordinary walkable overlay, and void.
- A lava tile sharing a wall or fence owner still blocks the projectile.
- A lava tile sharing an ordinary solid-scenery owner remains projectile
  transparent.
- Dynamic register/unregister cycles do not lose another collision owner's
  state.

### Range and timing

- `(5,0)`, `(5,5)`, and `(6,0)` boundaries to preserve or deliberately change
  square range.
- Target moves behind a wall after launch.
- Target moves from range 5 to range 6, then to range 16, during flight.
- Projectile timer failure produces pursuit, not a duplicate launch.

### Attack types and bosses

- One PURE_RANGED and one PURE_MAGIC NPC use the same collision policy.
- One MELEE_MAGIC dragon chooses a projectile across walk-blocking terrain.
- Elder ordinary fireball across the known lava line.
- Elder fireshot/burn with one clear secondary target and one behind cover.
- Burn behavior after the target moves out of range or behind cover.
- Elder melee sweep near a thin wall.
- On-combat-start dragon breath follows the selected policy.
- Legacy `RangeEventNpc` follows the selected policy.
- Administrator direct-projectile behavior is explicitly tested or documented
  as a bypass.

### Delivery and protections

- Legal launch then closed door, according to the selected impact policy.
- Guard Dog rejection after a projectile visual is already queued.
- Dead, removed, logged-out, or more-than-15-tiles-away target at delivery.
- No duplicate damage when an ordinary Elder projectile rolls a special.

## Implementation

The selected policy is now implemented through an explicit
`PathValidation.checkHostileProjectilePath` API. It consumes a dedicated
semantic mask from `TileValue` instead of either reusing all walking collision
or honoring the old tile-wide transparency exemption.

Collision ownership is derived as follows:

- loaded cardinal and diagonal terrain walls remain hostile-projectile
  collision;
- uninitialized tiles and raw overlay `10` are opaque void;
- registered boundary walls and closed doors own counted dynamic collision;
- a central scenery classifier makes walls, closed doors/gates, and every
  inventoried fence form opaque;
- open doors/gates, water, lava, rocks, trees, and other ordinary scenery do
  not add hostile-projectile collision;
- registration and removal use counts, so overlapping hard-cover owners cannot
  erase each other.

The four pre-existing hostile launch paths now use the named API:

1. autonomous ranged/magic attacks in `NpcBehavior`;
2. legacy `RangeEventNpc`;
3. combat-start dragon breath;
4. legacy spell-triggered dragon breath.

Elder fireshot and burn validate each prospective player through the same API
before submitting their projectile visual or damage. Fireshot deliberately
does not recheck collision at delayed delivery, and a legally started burn
continues, preserving the selected launch-commit policy. The Elder melee sweep
is not a projectile and remains unchanged. Administrator direct-projectile
commands remain documented bypasses.

### Implemented behavior matrix

| Collision source | Walk | Hostile NPC projectile |
| --- | --- | --- |
| cardinal/diagonal terrain wall | blocked | blocked |
| boundary wall or closed door/gate | blocked | blocked |
| open/removed door or gate | open if no other owner blocks | transparent |
| fence scenery, including type `0` | definition-dependent | blocked |
| ordinary solid scenery such as rock/tree | blocked | transparent |
| water overlay `2` | blocked | transparent |
| lava overlay `11` | blocked | transparent |
| void overlay `10` or uninitialized tile | blocked | blocked |
| raw elevation change | unchanged | transparent |

Player-originated projectiles retain the existing default path API and were
not changed.

## Verification

The following existing tests are relevant to this audit:

- `tests/myworld/test-npc-projectile-clipping.py`
- `tests/myworld/test-combat-runtime-invariants.py`
- `tests/myworld/test-combat-scenarios.py`
- `tests/myworld/test-mining-guild-smithing-expansion.py`
- `tests/myworld/test-summoning-combat-assist.py`

Focused implementation coverage is in
`tests/myworld/test-hostile-projectile-collision-policy.py`. Its executable
Java harness verifies terrain, void, ordinary-scenery, counted hard-cover,
copy-on-write, open/closed gate, structural-wall, and all current fence
definition fixtures. It also guards every known hostile call site, both Elder
AOE launch loops, launch-versus-delivery timing, and unchanged player
projectile routing.

The following regression tests pass:

- `tests/myworld/test-hostile-projectile-collision-policy.py`
- `tests/myworld/test-npc-projectile-clipping.py`
- `tests/myworld/test-combat-runtime-invariants.py`
- `tests/myworld/test-combat-data.py`
- `tests/myworld/test-npc-attack-styles.py`
- `tests/myworld/test-world-editor-tile-collision.py`
- `tests/myworld/test-world-editor-region-collision.py`

The authoritative server and plugin build passes through
`./scripts/build-server.sh`. Private visual verification remains required
before final handoff.
