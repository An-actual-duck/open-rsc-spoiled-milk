# Player and Hostile NPC Projectile-Range Audit

Status: investigation complete; gameplay implementation pending manager/user
review

Date: 2026-07-23

Branch: `docs/projectile-attack-range-audit`

Baseline: `8b92f31dde17150325e4fd04ce6c6b4319ed887b`

## Scope and conclusion

This audit traces projectile distance independently from the already selected
hostile-projectile collision policy. It does not change a range, aggression
rule, definition, or combat event.

The priority Elder Green Dragon report is a real range mismatch:

- every modern NPC ranged/magic profile has a fixed maximum radius of `5`;
- player longbows have radius `7`;
- player shortbows, crossbows, darts, and combat spells have radius `6`;
- the Elder Green Dragon is a modern `MELEE_MAGIC` NPC and has no range
  override, so its normal fire projectile stops at radius `5`.

The result is deterministic. At center-tile distance `6`, a player can attack
with a spell, shortbow, crossbow, or dart while the Elder can only pursue. At
distance `7`, a longbow can do the same. The recently implemented collision
policy correctly allows the Elder to shoot over lava, but it cannot make an
attack whose distance gate fails.

There is no data or code marker that identifies an NPC as intentionally
outrangeable. The current system therefore cannot express the desired policy
of leaving selected enemies at range `5` while giving major threats equivalent
reach.

## Authoritative distance formula

All ordinary player and modern NPC mob-to-mob range gates call
`Entity.withinRange(Entity/Point, radius)` in
`server/src/com/openrsc/server/model/entity/Entity.java`:

```text
abs(source.x - target.x) <= radius
and
abs(source.y - target.y) <= radius
```

This is a square/Chebyshev radius, not Euclidean distance:

| Delta | In radius 5? | Comment |
| --- | --- | --- |
| `(5,0)` | yes | cardinal boundary |
| `(5,5)` | yes | diagonal boundary, about 7.07 geometric tiles |
| `(6,0)` | no | one cardinal tile beyond |
| `(6,6)` | no | one diagonal tile beyond |

`NpcBehavior.tryProjectileAttack` separately calculates
`max(abs(dx), abs(dy))` for attack-style preference, producing the same
boundary.

`Point.withinRange` uses a truncated Pythagorean calculation, but it is not the
ordinary caster-to-target attack gate. Some secondary AOE selectors do use it.

### Footprints

Mob range is location-to-location. It does not account for sprite size,
`camera1`/`camera2`, model bounds, or occupied tiles. The visually large Elder
Green Dragon is logically a one-tile point at `(263,3430)` for range checks.
`Entity.withinRange(GameObject, radius)` can use a scenery object's closest
bound, but that overload is not used for player-to-NPC or NPC-to-player combat.

## Player projectile ranges

The authoritative family calculation is
`server/src/com/openrsc/server/event/rsc/impl/projectile/RangeUtils.java`.
`PLAYER_COMBAT_RANGE_BONUS` is `2`.

| Player attack family | Base | Effective firing radius | Approach radius | Authority |
| --- | ---: | ---: | ---: | --- |
| Longbows, including Dragon Longbow | 5 | **7** | 6 | `RangeUtils.getBowAttackRadius` |
| Shortbows | 4 | **6** | 5 | `RangeUtils.getBowAttackRadius` |
| Crossbows | 4 | **6** | 5 | `RangeUtils.getBowAttackRadius` |
| Throwing darts | 4 | **6** | 5 | `RangeUtils.getThrowingAttackRadius` |
| Throwing knives | 3 | **5** | 4 | `RangeUtils.getThrowingAttackRadius` |
| Shuriken | 3 | **5** | 4 | `RangeUtils.getThrowingAttackRadius` |
| Combat spells, manual and autocast | config 4 | **6** | 5 | `SpellHandler`, `MagicCombatEvent`, both My World configs |

`RangeUtils.getApproachRadius` subtracts one so a player who must move normally
stops inside the absolute firing boundary. If the player is already stationary
and in firing range, the attack can use the full radius.

### Ordinary attack lifecycle and rechecks

The ranged click handler selects a follow/walk radius, then the recurring
event owns the final gate:

- `RangeEvent` recomputes the equipped bow/crossbow radius every attack tick.
- `ThrowingEvent` recomputes the thrown-weapon radius every attack tick.
- `MagicCombatEvent` uses the configured spell radius and queues the manual
  spell handler once in range.
- Each path rechecks target state, current distance, and its player-projectile
  path immediately before creating a `ProjectileEvent`.
- If the target is outside the firing radius, the player follows toward the
  approach radius. No shot is created while still outside.

Changing equipment can therefore change the next recurring ranged attack's
radius. There is no ammunition-specific distance.

### Player special and secondary projectile paths

These do not extend the initial player-to-primary-target range unless stated:

| Path | Distance behavior |
| --- | --- |
| Ranged-cape extra arrow | Inherits the legal bow/crossbow primary attack |
| Dragon weapon/armor projectile proc | Inherits the legal primary hit; no second firing gate |
| Shuriken multi-target throw | Up to three targets; every selected NPC must be within the shuriken's radius `5` of the player and pass player projectile collision |
| Iban/god-spell AOE | Primary spell uses radius `6`; secondary NPCs are selected within radius `2` of the primary after the cast |
| Blood/death robe splash and splinter | Inherit the primary; secondary radius `2` around the primary |
| Chaos chain lightning | Inherits the primary, then up to three hops at radius `4` from the previous anchor |
| Multicannon | Independent square targeting radius **8** in `FireCannonEvent`; ordinary player path collision; delayed `ProjectileEvent` delivery |

Secondary AOE and chain selectors generally do not run another
caster-to-secondary line-of-fire check. They are AOE radii, not extensions of
the weapon's primary firing radius.

## Modern hostile NPC ranges

The authoritative path is:

```text
NpcAttackStyleProfile.forNpc
  -> profile.prefersProjectileAtDistance
  -> NpcBehavior.tryProjectileAttack
  -> profile.getProjectileRange
  -> checkHostileProjectilePath
  -> ProjectileEvent
```

`NpcAttackStyleProfile.DEFAULT_PROJECTILE_RANGE` is `5`, and every enum profile
returns it. Ranged and magic share the same range, pursuit, timer, and collision
gate.

| Profile | Behavior at distance 1 | Behavior at distance 2–5 | Beyond 5 |
| --- | --- | --- | --- |
| `PURE_RANGED` | projectile | projectile | pursue/no launch |
| `PURE_MAGIC` | projectile | projectile | pursue/no launch |
| `MELEE_RANGED` | 65% projectile preference | projectile | pursue/no launch |
| `MELEE_MAGIC` | 65% projectile preference | projectile | pursue/no launch |
| `MELEE_FREQUENT_MAGIC` | 85% projectile preference | projectile | pursue/no launch |
| `MELEE_RARE_MAGIC` | 10% projectile preference | projectile | pursue/no launch |

The percentage only chooses melee versus projectile while adjacent. Every
hybrid deterministically prefers its projectile when farther than one tile but
still within five.

### Notable current enemies

Definitions are merged from `NpcDefs.json`, `NpcDefsCustom.json`, and
`NpcDefsMyWorld.json`; profile selection is name/ID logic in
`NpcAttackStyleProfile`.

| NPC | ID | Combat level | Profile | Normal projectile radius | Player advantage |
| --- | ---: | ---: | --- | ---: | --- |
| Elder Green Dragon | 844 | 275 | `MELEE_MAGIC` | **5** | +1 most projectiles, +2 longbow |
| King Black Dragon | 477 | 245 | `MELEE_MAGIC` | **5** | +1/+2, subject to separate legacy breath response |
| Balrog | 809 | 217 | `MELEE_MAGIC` | **5** | +1/+2 |
| Black Dragon | 291 | 200 | `MELEE_MAGIC` | **5** | +1/+2 |
| Black Demon | 568 / 290 | 175 / 156 | `MELEE_MAGIC` | **5** | +1/+2 |
| Black Knight titan | 401 | 146 | `MELEE_RARE_MAGIC` | **5** | +1/+2 |
| Red Dragon | 201 | 140 | `MELEE_MAGIC` | **5** | +1/+2 |
| Chronozon | 315 | 121 | `MELEE_MAGIC` | **5** | +1/+2 |
| Green Dragon | 196 | 110 | `MELEE_MAGIC` | **5** | +1/+2 |
| Fire Giant | 344 | 109 | `MELEE_MAGIC` | **5** | +1/+2 |
| Blue Dragon | 202 | 105 | `MELEE_MAGIC` | **5** | +1/+2 |
| Ice Queen | 254 | 103 | `MELEE_MAGIC` | **5** | +1/+2 |
| Kolodion demon form | 760 | 98 | `MELEE_RARE_MAGIC` | **5** | +1/+2 |
| Ogre Guard | 684 | 96 | `PURE_RANGED` | **5** | +1/+2 |
| Battle Mages | 789–791 | 52 | `PURE_MAGIC` | **5** | +1/+2 |

Lower-tier guards, thieves, wizards, druids, demons, giants, mercenaries, and
similar profiled NPCs also use radius `5`. The table is prioritized by threat,
not exhaustive of every duplicate definition.

## Elder Green Dragon priority reproduction

### Definition and arena

- ID: `844`
- start: `(263,3430)`
- authored movement bounds: `(249,3416)` through `(277,3444)`
- profile: `MELEE_MAGIC`
- normal projectile: magic/fire
- normal projectile radius: `5`
- natural aggression radius under My World config: `1`
- normal target/leash envelope: authored bounds expanded by four, inclusive:
  `(245,3412)` through `(281,3448)`

The prior collision audit established a representative cross-lava line from
the Elder-side edge around `(249,3426)` across lava at `(248,3426)` and
`(247,3426)` to player-side land around `(246,3426)`. Lava now permits hostile
line of fire but still blocks walking.

For the distance-only boundary, hold the Elder at any legal clear-line tile and
place the player at exact Chebyshev offsets. The deterministic characterization
is:

| Offset from Elder | Player action | Elder normal response |
| --- | --- | --- |
| `(5,0)` or `(5,5)` | all projectile families can launch | normal magic can launch if timer/path allow |
| `(6,0)` or `(6,6)` | shortbow, crossbow, dart, spell, longbow can launch | no normal launch; pursues |
| `(7,0)` or `(7,7)` | longbow can launch | no normal launch; pursues |
| `(8,0)` or `(8,8)` | no ordinary player family can launch | no normal launch; pursues if target retained |

Because range is square, the diagonal visual gap is substantially longer than
the nominal radius. At `(7,7)`, the longbow is approximately 9.9 Euclidean
tiles from the Elder's center.

### Player attacks first / retaliation

Player projectile damage records the player in the NPC's ranged or magic
damage maps. On delayed impact, `ProjectileEvent` directly calls
`npc.setChasing(...)` when the NPC is alive, idle, and permitted to chase.
This retaliation acquisition is not limited by the natural one-tile aggression
scan.

The next `NpcBehavior` tick:

1. retains the player if inside the expanded spawn bounds and otherwise valid;
2. tries the normal projectile;
3. at distance `6` or `7`, fails the profile range gate;
4. attempts to walk toward the player.

Lava or ordinary blocking scenery can stop that walk while remaining
transparent to hostile projectiles. At distance `6` or `7`, however, no
projectile exists because range fails first. This is the reported safe attack.

### Elder special ranges

`ElderGreenDragonSpecialAttacks` has:

- projectile AOE radius `6`;
- melee sweep radius `2`.

Fireshot/burn are rolled only after an ordinary Elder projectile has already
hit a surviving primary target. They do not let the Elder initiate its normal
attack at distance `6`. If the Elder legally hits someone else at radius `5`,
the resulting AOE can select another clear-line player at radius `6`.

At AOE launch, every player must pass radius `6` and the hostile-projectile
collision policy. Fireshot waits one tick and rechecks state/radius `6`, but
not collision. Burn starts immediately; later burn pulses do not require the
player to remain in range or in line of fire.

## Acquisition, pursuit, retaliation, and leash

These are distinct from firing distance:

| Stage | Current rule | Elder value |
| --- | --- | --- |
| Natural acquisition | NPC-specific hardcode or configured `aggro_range`; state/level/bounds checks; no projectile LOS requirement | 1 |
| Prior-threat acquisition | damage contributor inside authored bounds +4, then `canAggro` | expanded bounds |
| Immediate ranged/magic retaliation | delayed player impact can set chasing directly | no natural-radius gate |
| Maximum normal firing | attack profile | 5 |
| Pursuit | after failed projectile gate, direct `walkToEntity` under current config | target inside bounds +4 |
| Melee engagement | predicted next step within 1 and adjacent collision passes | 1 |
| Leash/target reset | target outside authored bounds +4 causes roaming | `(245,3412)`–`(281,3448)` inclusive |

My World has `want_improved_pathfinding: false`. Pursuit uses direct walking,
not A*. NPC movement still respects collision and authored movement bounds.

The leash is target-based, not a radial distance from spawn. At the exact
expanded edge the target remains valid; one tile beyond it is rejected. With
improved pathfinding enabled, rejection also schedules a return to origin and
normalizes/cures the NPC. With the current setting disabled, it only clears the
behavior target and switches to roaming; `setRoaming` itself does not reset a
previously queued path. That weak reset behavior is an existing concern but is
not the cause of the radius-5 safe spot.

## Launch versus projectile travel

`ProjectileEvent` does not simulate a projectile moving tile-by-tile.

1. The attack event validates current distance and collision.
2. Damage is calculated.
3. The `ProjectileEvent` constructor immediately submits the visual between
   the source and target endpoints.
4. One tick later, `ProjectileEvent.action` applies the committed attack if it
   is not canceled and source/target are still within square radius **15**.

The impact stage does not recheck:

- the weapon/profile firing radius;
- hostile or player projectile collision;
- walls, doors, fences, void, terrain, or scenery;
- the NPC's leash.

Therefore:

| Target movement after a legal launch | Ordinary result |
| --- | --- |
| moves from radius 5 to radius 6 | hit still lands |
| moves behind newly closed cover | hit still lands |
| moves to radius 15, including `(15,15)` | hit still lands |
| moves to radius 16 | impact is suppressed |

The 15-tile impact guard is an independent delivery limit. It is not a
weapon-specific travel distance and does not influence whether an attack can
start.

## Legacy, scripted, and exceptional NPC paths

### Autonomous modern combat

`NpcBehavior.tryProjectileAttack` is the active general NPC ranged/magic path.
It uses radius `5`, the semantic hostile collision API, and the delayed
`ProjectileEvent`.

### `RangeEventNpc`

This legacy event independently hardcodes radius `5`, uses different authored
bounds expansion (`+/-9`), chases when outside range, and uses the hostile
collision API before launch. The only construction sites found are
administrator development commands. It is not the autonomous modern NPC
combat path.

### Dragon breath

There are three distinct behaviors:

| Path | NPCs | Range behavior |
| --- | --- | --- |
| `DragonFireBreath` on melee-combat start | dragon-named NPCs, including Elder | no explicit radius; inherits the adjacent combat start; hostile collision checked |
| `RangeUtils.applyDragonFireBreath` on first bow/thrown attack | authentic Dragon and KBD IDs only | direct response with no independent distance/collision check; inherits the player's legal radius of 5–7 |
| `SpellHandler` magic retaliation | authentic Dragon and KBD IDs only | inherits player spell radius 6 and additionally checks hostile collision |

The latter two do not include Elder ID `844`. The first is an adjacent melee
script and cannot compensate for a failed normal projectile at radius `6` or
`7`.

The `RangeUtils` direct breath response is a policy inconsistency: a Dragon or
KBD can apply that one-time response at a longbow's radius `7`, even though its
normal modern projectile remains radius `5`. This audit does not alter it.

### Boss and splash behavior

- Elder fireshot/burn use radius `6` only after a legal normal hit.
- Elder melee sweep uses radius `2`.
- Balrog magic splash is radius `2` around its primary player after a legal
  normal radius-5 projectile.
- Administrator direct-projectile commands can create `ProjectileEvent`
  without a normal range/collision launch gate and are diagnostics, not combat
  policy.

## Interaction with hostile projectile collision

The current semantic hostile policy is:

- blocked: cardinal walls, diagonal walls, closed doors/gates, void, and all
  fences;
- transparent: water, lava, and ordinary solid scenery such as rocks/trees;
- checked at launch, not ordinary delayed impact.

Distance and collision are both mandatory launch gates. Changing one does not
override the other:

```text
profile permits projectile at distance
AND within profile radius
AND hostile projectile path clear
AND attack timer ready
```

This explains the changed Elder report cleanly. The original cross-lava
failure was collision. After the collision fix, a player within five can be
hit over lava. A player at six or seven remains safe because no normal Elder
projectile can be created.

## Boundary characterization results

`tests/myworld/test-projectile-attack-range-audit.py` pins, without modifying
runtime values:

- all player family calculations and both My World spell configs;
- cardinal and diagonal boundaries for radii 5, 6, 7, 8, and 15;
- modern NPC shared radius `5` and dragon profile selection;
- recurring player range/path checks;
- modern and legacy hostile launch collision call sites;
- Elder AOE radius `6` and its delayed range-only recheck;
- delayed ordinary impact radius `15` and absence of range/collision rechecks;
- Elder placement and its exact expanded target/leash envelope.

This is source-backed characterization because the repository has no
full-world deterministic combat integration harness. The boundary predicates
themselves are evaluated, and their runtime authorities are guarded against
drift.

## Intentional versus unintended safe spots

### Confirmed unintended/high-confidence

- **Elder Green Dragon (844):** the task explicitly identifies it as a major
  threat that should likely have longer/equivalent reach. Its radius `5` comes
  only from the global default, not an authored decision. This is a
  high-confidence unintended mismatch.

### Strong review candidates

The same implicit mismatch affects KBD, Balrog, Black Dragon, high-level Black
Demons, Red Dragon, Chronozon, and other major ranged/magic threats in the
table. Any of them can be attacked from radius `6`/`7` while their ordinary
projectile cannot launch. Whether terrain permits a stable safe spot is
map-specific, but the range advantage exists everywhere.

### Reasonably acceptable candidates

Ordinary guards, thieves, low-level wizards/druids, mercenaries, and other
lower threats may intentionally remain outrangeable. Their current range is
still implicit; nothing distinguishes design intent from default inheritance.

It is not possible to produce an authoritative intentional/unintentional list
from current data. Treating every profiled enemy as range `7` would erase the
selected-enemy exception the task explicitly wants.

## Recommended policy

### Data policy

Add an optional server-authored NPC projectile range with a default of `5`.
`NpcAttackStyleProfile` should select attack style; it should not be the only
owner of every NPC's distance. A definition-backed override makes intent
reviewable and permits characterization of every major threat without
hardcoding arena coordinates.

Recommended conceptual resolution:

```text
npc definition override, if present
else profile/default radius 5
```

Keep natural aggression radius, projectile radius, AOE radius, and leash as
separate concepts. Do not raise global `aggro_range` or enlarge every NPC's
leash as a side effect of increasing one boss's firing reach.

### Elder Green Dragon values

Recommended initial implementation:

| Elder parameter | Current | Recommended | Reason |
| --- | ---: | ---: | --- |
| Normal ranged/magic projectile radius | 5 | **7** | equals the longest ordinary player weapon and removes both +1 and +2 safe bands |
| Natural acquisition radius | 1 | **1 (unchanged)** | not required for retaliation; altering proactive boss aggression is a separate design choice |
| Projectile AOE radius | 6 | **6 (unchanged)** | secondary effect, not the primary reach bug |
| Melee sweep radius | 2 | **2 (unchanged)** | separate melee special |
| Expanded target/leash bounds | current authored bounds +4 | **unchanged** | already much larger than firing distance |
| Delayed ordinary impact guard | 15 | **15 (unchanged)** | global delivery contract, not an Elder range value |

Radius `6` would only match shortbows/crossbows/darts/spells and would leave
longbows safe at `7`. Radius `7` is the consistent equivalent-reach value.

### Broader rollout

1. Implement the optional per-NPC range owner and Elder override `7`.
2. Add runtime integration tests at cardinal and diagonal distances 5/6/7/8,
   including collision and movement.
3. Review KBD, Balrog, Black Dragon, Black Demons, Red Dragon, Chronozon, and
   other bosses individually; explicitly author `5`, `6`, or `7`.
4. Leave ordinary enemies at the default `5` unless their encounter design
   says otherwise.
5. Separately review the first-hit Dragon/KBD breath inconsistency and the weak
   non-A* leash reset; neither is needed for the Elder range fix.

## Implementation regression plan

When values are authorized, add server-level tests for:

- Elder fires at cardinal and diagonal radius `7`, not `8`;
- ordinary default-range NPC still fires at `5`, not `6`;
- player longbow/crossbow/dart/knife/shuriken/spell boundaries remain
  unchanged;
- player attacks first at radius `7`, Elder retaliates without needing natural
  acquisition;
- NPC acquires/chases at `7` but does not launch through a wall, closed door,
  fence, or void;
- NPC launches at `7` over water/lava and past ordinary solid scenery;
- target moving to `8` before launch causes pursuit/no launch;
- target moving from `7` to `8` after launch still receives the committed hit,
  while moving beyond `15` suppresses it;
- target at exact expanded leash edge is retained and one tile beyond is
  released;
- Elder AOE remains `6` unless separately changed;
- a low-tier default NPC remains intentionally outrangeable by longbows.

## Source map

| Concern | Authoritative source |
| --- | --- |
| Player bow/thrown family radius | `server/src/com/openrsc/server/event/rsc/impl/projectile/RangeUtils.java` |
| Player ranged recurring gate | `server/src/com/openrsc/server/event/rsc/impl/projectile/RangeEvent.java` |
| Player thrown recurring gate | `server/src/com/openrsc/server/event/rsc/impl/projectile/ThrowingEvent.java` |
| Player manual combat spell gate | `server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java` |
| Player autocast gate | `server/src/com/openrsc/server/event/rsc/impl/projectile/MagicCombatEvent.java` |
| Initial player attack positioning | `server/src/com/openrsc/server/net/rsc/handlers/AttackHandler.java` |
| Square mob range formula | `server/src/com/openrsc/server/model/entity/Entity.java` |
| NPC attack-style/range default | `server/src/com/openrsc/server/model/entity/npc/NpcAttackStyleProfile.java` |
| NPC acquisition/pursuit/firing/leash | `server/src/com/openrsc/server/model/entity/npc/NpcBehavior.java` |
| NPC prior-threat selection | `server/src/com/openrsc/server/model/entity/npc/Npc.java` |
| Delayed projectile delivery | `server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java` |
| Legacy NPC ranged event | `server/src/com/openrsc/server/event/rsc/impl/projectile/RangeEventNpc.java` |
| Hostile collision policy | `server/src/com/openrsc/server/model/PathValidation.java` |
| Elder AOE/sweep/burn | `server/src/com/openrsc/server/event/rsc/impl/combat/ElderGreenDragonSpecialAttacks.java` |
| Dragon combat-start breath | `server/src/com/openrsc/server/event/rsc/impl/combat/scripts/all/DragonFireBreath.java` |
| Multicannon range | `server/src/com/openrsc/server/event/rsc/impl/projectile/FireCannonEvent.java` |
| Elder definition | `server/conf/server/defs/NpcDefsCustom.json`, `NpcDefsMyWorld.json` |
| Elder placement/leash basis | `server/conf/server/defs/locs/MyWorldNpcLocs.json` |
| My World distance configs | `server/myworld.conf`, `server/myworld-host.conf` |
