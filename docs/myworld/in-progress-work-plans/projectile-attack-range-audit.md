# Player and Hostile NPC Projectile-Range Audit

Status: Elder priority implementation complete; broader range diversification
and tactical retreat remain planned work

Date: 2026-07-23

Audit branch: `docs/projectile-attack-range-audit`

Implementation branch: `fix/elder-dragon-projectile-range`

Audit baseline: `8b92f31dde17150325e4fd04ce6c6b4319ed887b`

Implementation baseline: `f937186888e51c859e518222629b751c82cb071a`

## Scope and conclusion

This audit traces projectile distance independently from the already selected
hostile-projectile collision policy. Its first approved implementation adds a
reusable NPC-definition range override and changes only the Elder Green
Dragon's normal projectile radius.

The original Elder Green Dragon report was a real range mismatch:

- modern NPC ranged/magic profiles default to radius `5`;
- player longbows have radius `7`;
- player shortbows, crossbows, darts, and combat spells have radius `6`;
- the Elder Green Dragon is a modern `MELEE_MAGIC` NPC and previously inherited
  radius `5`.

The approved correction authors `projectileRange: 7` for Elder ID `844`.
Ordinary NPCs without an override remain at `5`; Elder normal ranged/magic
attacks now reach the longest ordinary player weapon. The recently implemented
collision policy remains an independent launch requirement.

The new optional field also provides the data marker the audit found missing:
future enemies can explicitly receive different ranges without changing their
attack-style profile. Selecting those ranges, and diversifying player weapons
and spells, remains a separate balance phase.

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

`NpcAttackStyleProfile.DEFAULT_PROJECTILE_RANGE` is `5`. An NPC's positive
definition-backed `projectileRange` overrides that default. Ranged and magic
still share the same selected range, pursuit, timer, and collision gate.

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
| Elder Green Dragon | 844 | 275 | `MELEE_MAGIC` | **7** | none |
| King Black Dragon | 477 | 245 | `MELEE_MAGIC` | **5** | +1/+2, subject to separate legacy breath response |
| Balrog | 809 | 217 | `MELEE_MAGIC` | **5** | +1/+2 |
| Black Dragon | 291 | 200 | `MELEE_MAGIC` | **5** | +1/+2 |
| Black Demon | 568 / 290 | 175 / 156 | `MELEE_MAGIC` | **5** | +1/+2 |
| Black Knight titan | 401 | 146 | `MELEE_RARE_MAGIC` | **5** | +1/+2 |
| Red Dragon | 201 | 140 | `MELEE_MAGIC` | **5** | +1/+2 |
| Chronozon | 315 | 121 | `MELEE_MAGIC` | **5** | +1/+2 |
| Green Dragon | 862 | 110 | `MELEE_MAGIC` | **5** | +1/+2 |
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
- normal projectile radius: `7` (definition override; formerly `5`)
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
| `(6,0)` or `(6,6)` | shortbow, crossbow, dart, spell, longbow can launch | normal magic can launch if timer/path allow |
| `(7,0)` or `(7,7)` | longbow can launch | normal magic can launch if timer/path allow |
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
3. at distance `6` or `7`, passes the Elder's definition-backed range gate;
4. launches if collision and its combat timer also permit.

At distance `6` or `7`, the Elder no longer needs to walk before launching.
Beyond `7`, it still pursues if the target is retained; lava or ordinary solid
scenery can stop that pursuit while remaining transparent to hostile
projectiles. The old range-only safe attack at distance `6` or `7` is therefore
removed for the Elder. Authored movement bounds can still prevent an NPC from
closing a larger gap, which is the separate tactical-retreat problem planned
below.

### Elder special ranges

`ElderGreenDragonSpecialAttacks` has:

- projectile AOE radius `6`;
- melee sweep radius `2`.

Fireshot/burn are rolled only after an ordinary Elder projectile has already
hit a surviving primary target. The Elder's normal attack can now initiate
through radius `7`; the resulting AOE still independently selects clear-line
players only through radius `6`.

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
| Maximum normal firing | positive NPC definition override, else attack-profile default | 7 |
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
It uses the optional NPC definition override or default radius `5`, the
semantic hostile collision API, and the delayed `ProjectileEvent`.

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

The latter two do not include Elder ID `844`. The first remains an adjacent
melee script and is independent of the Elder's normal radius-7 projectile.

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

This separates the two Elder reports cleanly. The original cross-lava failure
was collision. After the collision fix, lava no longer blocks launch. The
definition-backed range change now also permits a normal Elder projectile at
six or seven, provided the semantic collision path and attack timer pass.

## Boundary characterization results

`tests/myworld/test-projectile-attack-range-audit.py` now guards both the
original characterization and the approved Elder implementation:

- all player family calculations and both My World spell configs;
- cardinal and diagonal boundaries for radii 5, 6, 7, 8, and 15;
- modern NPC default radius `5`, the definition-backed selection path, and
  dragon profile selection;
- generated Elder ID `844` override `projectileRange: 7`;
- NPC-definition loading/copying and generator validation for the optional
  range;
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

### Resolved unintended/high-confidence

- **Elder Green Dragon (844):** the task explicitly identifies it as a major
  threat that needs equivalent reach. It now has an authored radius of `7`
  rather than implicitly inheriting the default `5`.

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

## Selected policy

### Data policy

An optional server-authored NPC projectile range now exists with a default of
`5`.
`NpcAttackStyleProfile` should select attack style; it should not be the only
owner of every NPC's distance. A definition-backed override makes intent
reviewable and permits characterization of every major threat without
hardcoding arena coordinates.

Implemented resolution:

```text
npc definition override, if present
else profile/default radius 5
```

Keep natural aggression radius, projectile radius, AOE radius, and leash as
separate concepts. Do not raise global `aggro_range` or enlarge every NPC's
leash as a side effect of increasing one boss's firing reach.

### Elder Green Dragon values

Implemented initial values:

| Elder parameter | Previous | Current | Reason |
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

1. **Complete:** implement the optional per-NPC range owner and Elder override
   `7`.
2. Add runtime integration tests at cardinal and diagonal distances 5/6/7/8,
   including collision and movement.
3. Review KBD, Balrog, Black Dragon, Black Demons, Red Dragon, Chronozon, and
   other bosses individually; explicitly author `5`, `6`, or `7`.
4. Leave ordinary enemies at the default `5` unless their encounter design
   says otherwise.
5. Separately review the first-hit Dragon/KBD breath inconsistency and the weak
   non-A* leash reset; neither is needed for the Elder range fix.

## Planned phase 2: diverse projectile ranges

The new NPC definition field is only the first data foundation. A deliberate
range pass should make distance part of encounter and equipment identity
without turning it into another global constant.

### NPC design

- Review major threats individually and explicitly author their intended
  projectile range. The current candidates are listed above, but no additional
  values are approved by this implementation.
- Keep `5` as the compatibility default. An explicit `5` can later communicate
  intentional outrangeability for important encounters.
- Keep normal projectile range separate from natural acquisition, pursuit,
  leash, AOE radius, and special-attack reach. A longer attack should not
  silently make an NPC notice players sooner or roam farther.
- Preserve the field's range of `1` through `15`. Fifteen is the current
  delayed-projectile delivery limit; authoring a larger firing radius would
  create projectiles that the delivery stage can reject.
- Characterize pure ranged, pure magic, hybrid, dragon-breath, legacy event,
  and scripted boss paths before assigning a value. A definition range governs
  the modern normal attack and generic NPC reach query; it does not silently
  rewrite a scripted special's own radius.

### Player design

Player range should eventually be data-driven at the correct level:

- bows, crossbows, and thrown weapons should receive weapon/family metadata
  with the current family calculation as a compatibility fallback;
- ammunition should not own distance unless a particular ammunition type is
  intentionally designed to change it;
- combat spells should receive an optional per-spell casting range in their
  authoritative definition, with the configured radius as the fallback;
- special attacks and procs should explicitly say whether they inherit the
  primary attack, perform a new caster-to-target check, or use an AOE/hop
  radius.

The server must remain authoritative, but the client interaction/path
prediction must consume equivalent data so it does not propose attacks the
server rejects. The implementation should name and test these separately:

1. acquisition/click eligibility;
2. approach radius;
3. maximum firing radius;
4. secondary AOE or chain radius;
5. delayed impact limit;
6. NPC pursuit and leash.

Required coverage includes every weapon and spell family, manual versus
autocast magic, equipment swaps between attack ticks, cardinal and diagonal
boundaries, PvM and PvP, target movement, collision policy, secondary effects,
and client/server data agreement.

## Planned phase 3: radius-constrained tactical retreat

The desired behavior is not a general flee mechanic. It specifically addresses
an NPC that is being attacked by a player, wants to pursue that player, but
cannot close into its attack radius because its authored movement envelope
prevents the required pursuit step. Instead of standing at the boundary and
letting the player attack indefinitely, the NPC should move away from the
player inside its legal area. The player must then move closer to maintain the
attack.

### Missing architecture

Today `NpcBehavior.handleAggro` calls `npc.walkToEntity(...)`. Path construction
can stop at collision, while `WalkingQueue.processNextMovement` separately
rejects a queued NPC destination outside its authored `NPCLoc` bounds and
resets the path. Neither layer returns a structured failure reason to
`NpcBehavior`.

Retreat must therefore begin with an explicit pursuit result such as:

```text
PursuitOutcome:
  MOVING
  ALREADY_IN_ATTACK_RANGE
  BLOCKED_BY_AUTHORED_BOUNDS
  BLOCKED_BY_COLLISION
  NO_ROUTE
  INVALID_TARGET
```

The behavior must trigger only for `BLOCKED_BY_AUTHORED_BOUNDS`. Inferring it
from an empty or finished walking queue would misclassify walls, closed doors,
fences, void, lava, ordinary scenery, transient occupancy, disabled movement,
or a genuinely unreachable route.

### Proposed behavior

1. Require a valid current player threat and recent player-originated attack;
   natural roaming with no attacker must never cause tactical retreat.
2. Try normal projectile/melee selection and pursuit first.
3. When pursuit reports only an authored-bounds failure, choose a collision-
   legal tile inside the NPC's hard movement envelope that increases Chebyshev
   distance from the attacking player.
4. Walk normally to the best deterministic candidate; never teleport, leave
   authored bounds, heal, cure, or reset combat/threat ownership.
5. Add a short cooldown and directional hysteresis so an NPC cannot oscillate
   or rebuild retreat paths every tick.
6. Cancel the retreat immediately when the target becomes attackable, threat
   ends, the target dies/logs out, or the NPC dies, despawns, or is reset.
7. If no legal retreat tile exists, remain in place rather than crossing
   collision or bounds.

For multiple attackers, the current/preferred threat should be selected
deterministically; retreat should not bounce between players each tick. Guard
Dog targeting restrictions and other combat ownership rules must continue to
win. The first implementation should cover modern NPC behavior and explicitly
leave legacy/scripted encounters unchanged until reviewed.

### Retreat regression and visual plan

- boundary-restricted NPC under player fire retreats inward/away;
- the player must follow closer and the NPC resumes its normal attack once in
  range;
- no retreat occurs without a recent hostile player action;
- walls, doors, fences, void, lava, scenery, occupancy, and general no-route
  failures do not masquerade as authored-bounds failures;
- cardinal, diagonal, corner, one-tile-bound, and no-valid-candidate cases;
- default-range and explicit-range NPCs, including Elder radius `7`;
- pure ranged/magic, hybrid, and melee NPCs;
- multiple players, threat changes, and Guard Dog restrictions;
- no oscillation, path churn, or abnormal world-tick load;
- death, logout, despawn, reset, and target-leash cleanup;
- current direct walking and optional improved/A* pathfinding configurations.

This is a behavior change with significant encounter implications. It should
receive deterministic movement tests, a private-server multi-player matrix,
and extended visual observation before any broad activation.

## Implementation regression coverage

Current source-backed tests cover the definition/generator contract, default
and Elder range selection, exact distance predicates, and all guarded call
sites. A future deterministic full-world combat harness should add:

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
| Optional NPC projectile range field/loading | `server/src/com/openrsc/server/external/NPCDef.java`, `EntityHandler.java` |
| Generated Elder range source | `tools/generators/npc-overrides/00-strength-overrides-and-bosses.json` |
| NPC acquisition/pursuit/firing/leash | `server/src/com/openrsc/server/model/entity/npc/NpcBehavior.java` |
| NPC authored-bound movement enforcement | `server/src/com/openrsc/server/model/WalkingQueue.java` |
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
