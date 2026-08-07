# A06.3 projectile impact policy implementation

Status: IMPLEMENTED, AUTOMATED GATES PASS, AND PRIVATE OPENGL ACCEPTANCE IS
COMPLETE ON THE FOCUSED BRANCH; MANAGER INTEGRATION REMAINS PENDING.

## Outcome and boundary

Every typed projectile producer now selects an immutable
`ProjectileImpactPolicy`. One side-effect-free `ProjectileImpactValidator`
evaluates that policy after the event's exactly-once ledger claim and explicit
cancellation check, but before `projectileDamage()`, `doSpell()`, ball transfer,
or compatibility cleanup. Invalid impacts therefore execute none of their
damage, effect, chase, death, packet, or callback work.

This branch does not move or change launch checks, damage formulas, mitigation,
prayer, projectile timing, visuals, ammunition or rune costs, recovery,
experience, contribution, hitsplats, effects, packets, or death adapters.
Launch-time costs and experience remain spent when a later impact invalidates;
A06.4 owns that economy characterization separately.

## Producer policy matrix

| Producer group | Source lifetime | Range/domain | Collision |
| --- | --- | --- | --- |
| Player bow, thrown, shuriken, Magic, Iban, and cannon | Exact player identity/session; an emitted hit may survive death, but not logout or a new live lifecycle | Frozen source launch location to current target, 15 tiles; both participants remain in their launch world space and signed level | General projectile path |
| NPC ranged, Magic, compatibility, and legacy ranged | Exact NPC identity; an emitted hit may survive the old terminal lifetime, but not same-object respawn reuse | Same 15-tile/domain rule | Hostile-projectile hard cover |
| Summon ranged, Magic, and compatibility | Exact live summon lifetime and exact live owner login session | Same 15-tile/domain rule | General projectile path |
| Admin debug | Exact live participants | Same 15-tile/domain rule | General projectile path |
| Positional damaging compatibility | Exact target; source-death persistence without session/reuse crossover | Same 15-tile/domain rule | Explicitly no impact recheck until the caller declares a semantic |
| Scripted Magic | Exact live participants | Same 15-tile/domain rule | General projectile path |
| Legends holy water | Exact live participants | Frozen launch origin, four tiles; both launch domains retained | General projectile path |
| Gnome ball | Exact live participants | Both launch domains retained; unlimited distance | None |
| Base benign compatibility cleanup | Terminal cleanup remains permitted exactly once | None | None |

Every gameplay-bearing target must remain live, registered, and in the exact
captured lifecycle/session. The source-death exception is deliberately
narrower: a dead player must remain registered in the captured login session,
and a terminal NPC must remain the captured object in the world's NPC list. A
live player respawn or NPC same-object respawn has a new combat lifecycle and
is rejected.

The summon owner's combat lifecycle is not projectile identity. Its stable
identity and login session are captured, while the summon itself must retain
its exact combat lifecycle. This prevents unrelated owner combat cleanup from
canceling a live summon projectile without allowing the projectile across an
owner logout.

## Typed decisions and ordering

The ledger retains the compatibility reasons
`EXPLICIT_CANCELLATION`, `OUTSIDE_CURRENT_SPATIAL_GATE`, and
`DUPLICATE_CALLBACK`. The implemented policy adds:

- `TARGET_TERMINAL_OR_UNREGISTERED`;
- `TARGET_IDENTITY_SESSION_OR_LIFETIME_CHANGED`;
- `SOURCE_TERMINAL_OR_UNREGISTERED`;
- `SOURCE_IDENTITY_SESSION_OR_LIFETIME_CHANGED`;
- `LAUNCH_DOMAIN_DEPARTURE`;
- `OUTSIDE_LAUNCH_ORIGIN_RANGE`; and
- `IMPACT_PATH_BLOCKED`.

Validation order is deterministic: target lifetime, source lifetime and
summon-owner session, launch domains, launch-origin range, then collision. The
authorized callback retains all prior effect and settlement ordering. Explicit
cancellation remains first, duplicate callbacks never re-enter validation, and
a callback failure remains terminal `FAILED` work that cannot replay.

## Preserved compatibility

- All positional `ProjectileEvent`, `CustomProjectileEvent`, and
  `BallProjectileEvent` constructors remain available.
- Positional construction still infers known producer identities exactly as in
  A06.2; otherwise it receives the named positional-compatibility policy.
- In-flight retargeting still affects neither the original target nor impact
  ownership.
- Protection prayers remain launch decisions and are not rerolled at impact.
- Each shuriken/multi-target child retains an independent snapshot and ledger.
- Launch visuals remain visible even when the delayed impact later invalidates.
- Gnome-ball distance and base benign cleanup remain explicit non-hostile
  exceptions.
- Authentic-client packet shapes are unchanged.

## Executable coverage

The 75-scenario combat gate now executes the approved result matrix, including:

- target zero Hits, removal, logout/reconnect, and real same-object NPC
  respawn;
- player and NPC source death, source logout/reconnect, NPC source respawn,
  summon removal and owner-session replacement, and terminal admin source;
- source-only movement, target movement, paired long movement, short same-level
  movement, signed-level transitions, and the current global-only world-space
  rejection fixture;
- general-projectile and hostile-projectile barriers appearing during flight,
  plus the positional-compatibility no-recheck exception;
- scripted callbacks, both player/NPC gnome-ball directions, the holy-water
  four-tile boundary, and terminal base-benign cleanup;
- retargeting, protection activated after launch, explicit cancellation,
  independent siblings, duplicate callbacks, and deliberate callback failure;
  and
- stable policy metadata for every typed producer and positional-facade parity.

Automated verification completed before private testing:

- `./server/test_combat`: 75 of 75 scenarios;
- `./scripts/build-server.sh`: authoritative core and plugin builds;
- baseline-aware compiler, Checkstyle, PMD, CPD, and SpotBugs analysis: no new
  gated findings; and
- production artifact checks remain required after the final documentation
  checkpoint.

## Private acceptance and stop condition

Private OpenGL inspection completed on 2026-08-07 against a loopback-only
server on port 43616. The owner confirmed ordinary projectile presentation,
clear-path impact behavior, and blocked-path feedback looked correct, with no
visible combat regression. The executable gate supplies deterministic coverage
for death/disappearance, teleport and signed-level transitions, and barriers
introduced during flight; those timing-sensitive invalidations were not
claimed as independently reproduced by eye.

The private acceptance checklist was to inspect ordinary player and NPC projectiles
whose target dies/disappears, a long teleport or level transition during
flight, and a door or wall that closes during flight. The launch visual should
still appear, but no impact damage/effect should appear after invalidation.
Also confirm an ordinary nearby unchanged projectile still lands normally.

Stop rather than expanding this branch if private testing exposes any change to
launch timing, projectile presentation, damage/effect order, paid resources or
experience, contribution, death handling, or packet behavior. Resource and
progression settlement remains A06.4.
