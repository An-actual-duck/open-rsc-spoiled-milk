# A06.3 projectile impact policy characterization

Status: CHARACTERIZATION COMPLETE; PRODUCTION POLICY UNCHANGED; OWNER DECISIONS
PENDING.

## Scope and stop condition

This branch inventories and executes the current impact-eligibility behavior
for every typed projectile producer. It does not change a combat formula,
damage result, resource cost, experience award, launch check, projectile
timing, visual, packet, effect, death adapter, or runtime eligibility rule.

The implementation branch must not begin until the four visible policy choices
under [Owner decisions](#owner-decisions) are settled. Retargeting, launch-time
protection, sibling ownership, and duplicate callback behavior already have a
clear preserve-current recommendation and do not need to be reopened unless
the owner disagrees.

Evidence comes from the current repository at `e6b452484`, especially:

- `ProjectileEvent.beginProjectileImpact(boolean)`;
- `CustomProjectileEvent.action()`;
- `BenignProjectileEvent.beginBenignImpact(boolean)` and
  `BallProjectileEvent.action()`;
- `ProjectileLaunchSnapshot` and `CombatParticipantSnapshot`;
- all `ProjectileLaunchSpecification.Producer` construction sites; and
- executable scenario
  `projectile_impact_policy_decision_evidence_is_executable` in
  `CurrentCombatProjectileLifecycleCharacterization`.

Classic-Scape commit `f6def6ffb4608f45a493d2c9e5160d4bc7627bb3`
was inspected only as attributed comparison evidence. Its launch-origin range,
dynamic collision, registration, and source-death choices are not authority for
Spoiled Milk.

## Current policy by event family

| Current event family | Typed producers | Impact checks now | Checks not performed now |
| --- | --- | --- | --- |
| Damaging `ProjectileEvent` | player bow, thrown, shuriken, Magic, Iban, cannon, NPC ranged/Magic/compatibility, legacy NPC ranged, summon ranged/Magic/compatibility, admin debug, positional compatibility | Explicit cancellation; source and target are within 15 tiles of each other at the current locations; current world space/level match when layered authority is enabled | Participant snapshots, death, removal, registration, player login/session, respawn reuse, launch domain, launch-origin distance, collision, current combat target, or protection state |
| Scripted `CustomProjectileEvent` | Magic scripted effect; Legends holy water | Explicit cancellation only | All participant, spatial, collision, and protection conditions |
| `BallProjectileEvent` | gnome ball | Explicit cancellation only | All participant, spatial, and collision conditions |
| Base `BenignProjectileEvent` compatibility cleanup | benign compatibility | Exactly-once callback only; its dormant cancel flag is intentionally ignored by base `action()` | All participant and spatial conditions, including cancellation |

The fixed damaging range is not the bow, spell, NPC-profile, summon, or cannon
launch radius. It is one shared compatibility value. The source's current
position is the range origin, not its frozen launch position.

The base benign constructor has no tracked production construction site. It is
still a maintained compatibility and cleanup boundary: `BallProjectileEvent`
inherits from it, and its player attributes must not be stranded by applying a
hostile policy indiscriminately.

## Executed behavior matrix

The 75-scenario combat gate now proves the following current results:

| Change between launch and impact | Damaging result now | Other-family result now |
| --- | --- | --- |
| Explicit cancellation | `EXPLICIT_CANCELLATION`; visual remains; no impact | Scripted and ball also invalidate; base benign cleanup ignores its dormant flag |
| Source or target moves beyond the other's current 15-tile range | `OUTSIDE_CURRENT_SPATIAL_GATE` | Scripted and ball callbacks still execute |
| Source dies after launch | Impact still settles | No lifecycle check |
| Target is already at zero Hits | Impact enters settlement and the existing death adapter | No lifecycle check |
| Source logs out, receives a different session ID, and reconnects | Impact still settles | Scripted callback still executes after source logout |
| Target logs out, receives a different session ID, and reconnects | Impact still settles against the reconnected player object | Ball callback still executes after target logout |
| Target NPC is removed and remains removed | Impact still settles | No lifecycle check |
| Target NPC is removed and its real respawn callback reuses the object | Stale impact damages the new NPC lifetime | No lifecycle check |
| Source moves away while target stays at launch tile | Impact invalidates | No spatial check |
| Source and target teleport far away together | Impact settles because their current locations remain close | No spatial check |
| One participant changes signed level | Impact invalidates through current `withinRange` | No spatial check |
| Both participants change signed level together | Impact settles on the new level | No spatial check |
| An unconfigured non-global world space is requested | Rejected by the current global-only package/projection boundary before impact | Same boundary |
| A blocking tile appears after launch | `PathValidation` reports the current path blocked, but impact still settles | No collision check |
| Source retargets another mob | Original launched target is hit; new target is untouched | Event object likewise retains its target |
| Classic Protect from Missiles is active before a ranged launch | `RangeEvent` creates no projectile | Not an impact rule |
| Classic Protect from Missiles is enabled after launch | Precomputed projectile impact is not rerolled | Not an impact rule |
| One shuriken child becomes invalid | Its sibling retains an independent ledger and settles | N/A |
| Callback is repeated | First owner settles or invalidates; later callback is `DUPLICATE_CALLBACK` | Same exactly-once ledger rule |

Only two runtime invalidation reasons exist today:
`EXPLICIT_CANCELLATION` and `OUTSIDE_CURRENT_SPATIAL_GATE`. `FAILED` is a
terminal ledger state for an exception after callback ownership, not a policy
denial.

## Spatial and compatibility facts

- `Entity.withinRange(Entity, int)` first requires the participants' current
  spatial domains to match, then compares their current compatibility points.
- The launch snapshot already contains both participants' exact launch
  `WorldLocation` and UUID/lifecycle/session/world identity.
- A paired level transition therefore bypasses the current domain check even
  though both launch-domain records differ.
- The committed production layered package currently declares only the
  `global` world space. The runtime correctly refuses an unconfigured instance
  space because no compatibility projection or native terrain exists. A future
  instance-space package must run the A06.3 cross-space fixture before its
  activation gate can pass.
- Player ranged, throwing, and ordinary spell producers validate their
  existing projectile path before launch. NPC profiles and legacy NPC ranged
  use hostile-projectile semantics. Impact does not recheck either semantic.
- Gnome-ball player-to-player passing explicitly permits arbitrary distance in
  its plugin. A global hostile-distance policy would break maintained content.
- Legends holy water selects Ungadulu within four tiles before constructing its
  otherwise effect-empty visual callback.

## Recommended policy shape

These are recommendations, not settled requirements.

### Target lifetime

Recommended: every gameplay-bearing damaging, scripted, and ball impact must
still address the exact target lifetime captured at launch. Reject a dead,
removed, unregistered, logged-out, replaced-session, or respawn-reused target.
This closes the confirmed stale-respawn hit without changing damage or death
authority. Base benign cleanup remains allowed to clear its compatibility
attributes even when a participant has left.

### Source lifetime

Recommended: distinguish a launched projectile surviving its source's death
from a stale source session or replacement lifetime.

| Producer group | Recommended source rule | Reason |
| --- | --- | --- |
| Player bow/thrown/shuriken/Magic/Iban and cannon | A launched impact may survive ordinary source death; reject logout, changed session, different world, or a new live lifetime | Preserves the intuitive and current one-tick fired-projectile result without attributing new work to a replacement login |
| NPC ranged/Magic and legacy NPC ranged | A launched impact may survive the old NPC's terminal death, but never an NPC respawn/reused lifetime | The missile has already been emitted; respawn identity must not authorize it |
| Summon ranged/Magic | Require the exact live summon lifetime and owning-player session | Current post-impact summon credit, lifesteal, and on-hit behavior must not run through a vanished summon |
| NPC/summon compatibility producers | Follow the corresponding NPC or summon rule above | Their stable producer identity is sufficient; the compatibility label must not erase known source ownership |
| Scripted Magic and Legends holy water | Require exact live source and target lifetimes | These callbacks mutate stats, retreat state, quest presentation, or chase state rather than merely settling precomputed damage |
| Gnome ball | Require exact logged-in player sessions or exact live NPC lifetime; keep its deliberate unlimited distance | Prevents inventory transfer to a disconnected/replaced player while preserving the minigame's pass design |
| Admin debug | Require exact live participants | A delayed administrator action should not cross logout or respawn identity |
| Positional damaging compatibility | Require the exact target; allow an already launched hit to survive source death but reject session/reuse changes; retain no impact collision recheck until its caller declares a semantic | Maintained external callers cannot safely be assigned a path type from attack-style integers alone |
| Base benign compatibility cleanup | Permit terminal cleanup exactly once | Its only current responsibility is removing transient compatibility attributes |

Implementing “death may persist but respawn may not” requires a typed terminal
source decision. It must not weaken `CombatParticipantSnapshot.matches()` for
other consumers or infer safety solely from the same Java object/UUID.

### Distance and domain

Recommended for damaging impacts: measure the target's current location from
the source's frozen launch location with the current 15-tile compatibility
ceiling. This is the smallest defensible change: it prevents two actors from
teleporting together to carry a shot across the map while retaining generous
one-tick movement tolerance and avoiding a new balance change to every weapon
and spell radius.

Require both participants to remain in their respective launch world space and
signed level. Ordinary same-domain target walking remains eligible within the
launch-origin ceiling. A short same-level teleport is indistinguishable from
movement unless its existing combat lifecycle advances; do not add a new
global teleport-generation contract in A06.3.

Recommended exceptions:

- Legends holy water uses its established four-tile launch ceiling.
- Gnome ball retains unlimited distance but requires both participants to stay
  in their launch domains and exact lifetimes.
- Base benign cleanup ignores distance and domain so it cannot leak its player
  attributes.

Using each producer's attack radius instead of 15 is a separate combat-range
balance decision. The typed launch specification does not currently carry all
of those radii, and A06.3 should not invent them.

### Collision

Recommended: damaging and scripted gameplay impacts recheck the same semantic
path used by their producer, from the frozen source launch location to the
target's current location. A door or wall that becomes blocking during the
one-tick flight then prevents damage/effects; opening a previously blocked path
cannot create a projectile because launch already failed. Gnome ball and base
benign cleanup continue to ignore collision.

The policy must name the semantic explicitly:

- player ranged, throwing, Magic, cannon, summon, and scripted Magic use the
  established general projectile path;
- NPC profile and legacy NPC ranged use hostile-projectile hard-cover
  semantics; and
- positional compatibility must retain an explicit compatibility semantic
  until its external callers can declare more precise identity.

### Preserve-current decisions

- An in-flight event keeps its original target. A later combat retarget neither
  redirects nor cancels it.
- Prayer/protection remains a producer/launch decision. Impact does not reroll
  damage or consult newly activated protection.
- Each shuriken or other multi-target child keeps an independent snapshot and
  ledger.
- Launch visuals remain published before the delayed decision, including for
  later-invalidated impacts.
- Invalid impact must not replay damage, effects, chase, death, packets, or
  callback work. A later A06.4 decision separately owns already-paid resources
  and launch-time experience.

## Owner decisions

Before production implementation, confirm or replace these recommendations:

1. **Target terminal state:** invalidate all gameplay-bearing impacts against
   dead, removed, logged-out, replacement-session, or respawned targets;
   preserve base benign cleanup.
2. **Source terminal state:** use the family split above—ordinary player/NPC
   damaging missiles may survive death but not logout/reuse; summons, scripts,
   ball transfers, and admin actions require an exact live source.
3. **Distance/domain:** use frozen launch origin to current target with the
   existing 15-tile compatibility ceiling, four tiles for holy water, and no
   distance ceiling for gnome ball; require both participants to remain in
   their launch world space and signed level.
4. **Collision:** recheck each producer's existing semantic path at impact for
   damaging/scripted gameplay, while gnome ball and benign cleanup ignore it.

## Bounded implementation sequence after approval

1. Add immutable, named impact-policy metadata to the typed producer
   specification. Keep positional constructors as explicit compatibility
   facades.
2. Add typed invalidation reasons for target identity/session/terminal state,
   source identity/session/terminal state, launch-domain departure,
   launch-origin range, and impact collision. Do not overload
   `OUTSIDE_CURRENT_SPATIAL_GATE`.
3. Centralize side-effect-free validation over the existing launch snapshot.
   The validator may observe current mobs/world state but may not own damage,
   death, resources, XP, effects, or packets.
4. Apply the validator to damaging, scripted, ball, and base-benign boundaries
   with their declared family exceptions. Preserve ledger claim and terminal
   ordering.
5. Retain every existing characterization assertion and add approved-result
   tests for both sides of every new reason.

Stop if a policy cannot be isolated before `projectileDamage()`/`doSpell()`, if
one family needs damage/effect reordering, if invalidation changes launch-time
resource or XP behavior, or if a positional compatibility caller cannot be
classified safely.

## Implementation acceptance gates

- Target death, removal, logout/reconnect, and real same-object NPC respawn.
- Source death, logout/reconnect, NPC reuse, summon removal, and admin logout.
- Source-only movement, target-only movement, paired long movement, same-level
  teleport, paired signed-level transition, and cross-domain rejection.
- A non-global world-space package fixture once such a package is approved for
  the test runtime; until then retain the executable global-only rejection.
- Clear launch path followed by a new general-projectile barrier and a new
  hostile-projectile hard-cover barrier.
- Retarget preservation and post-launch protection activation.
- Scripted stat/retreat callbacks, Legends holy water, player/NPC gnome-ball
  paths, base benign cleanup, and all damaging producer groups.
- Independent shuriken siblings, explicit cancellation, every new invalidation
  reason, duplicate callback, and deliberate callback failure.
- Full combat gate, authoritative core/plugin builds, plugin discovery,
  production-artifact exclusion of tests, and changed-code static analysis.
- Private gameplay inspection for visible projectile disappearance at death,
  teleport, level transition, and a closing door before READY handoff.
