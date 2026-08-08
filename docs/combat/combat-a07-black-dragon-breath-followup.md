# A07.5F Black Dragon Breath Follow-up

## Scope and authority

A07.5F moves one proven-identical payload family, the Black Dragon breath
follow-up, into `BlackDragonBreathFollowup`. The shared unit owns only the
complete-set and exact-marker gate, inclusive payload draw, and invocation of
an event-owned auxiliary true-damage callback. Reciprocal melee, PvM melee, and
projectile impact retain primary-hit eligibility, poison chance and
application, marker creation/clearing, the surviving-target phase, shared
Black/KBD presentation, auxiliary damage settlement, and all surrounding
effect order.

This is a behavior-preserving ownership extraction. It changes no equipment,
chance, poison strength or ceiling, damage, contribution, aggro, lifesteal,
death, packet, animation, timing, or balance rule.

## Preserved contract

- Positive primary damage reaches event-owned poison processing before the
  leather follow-up chain. Poison processing clears the prior marker first.
- A complete Black Dragon set supplies the existing 20% event-owned chance,
  15 applied poison power, 30 poison ceiling, and exact `"black"` marker.
- A failed chance consumes one chance draw, applies no Black Dragon poison,
  records no marker, shows no breath effect, and consumes no payload draw.
- Zero primary damage clears a stale marker and consumes no chance or payload
  draw. The follow-up is therefore not independently eligible on a zero hit.
- A successful marker shows the source-centered Dragon Breath combat effect
  before payload settlement. The shared presentation also remains valid for a
  `"king_black"` marker and stays event-owned.
- The Black follow-up requires both the full set and exact `"black"` marker,
  then draws inclusive damage from 0 through 10. It performs no independent
  chance draw.
- A zero payload still retains the poison, marker, and presentation but does
  not invoke the damage callback.
- Positive payload invokes the original event helper. That preserves armor-proc
  hitsplats, combat-style contribution, player mitigation/stat packets, and
  each event's existing death adapter.
- Reciprocal melee and projectile owners retain the production random adapter;
  PvM melee retains its injected event random source. The marker remains until
  the next event-owned poison attempt clears it.

The descriptor catalog remains descriptive. Runtime execution does not query
`SecondaryEffectDescriptor`.

## Explicit exclusions

King Black Dragon payload damage and its random water/earth/fire follow-up,
poison policy, marker ownership, shared presentation, Elder Green Dragon,
Infernal Fire, splash, boss, summon, delayed, reflection, AoE, and DoT behavior
remain outside this branch. KBD is not approved for consolidation by proximity;
it requires its own executable characterization. The current poison lifecycle
remains separately governed by A08.

## Executable evidence

The compiled A07.5F scenario executes the private poison and leather owners for
reciprocal melee, PvM melee, and projectile impact before and after extraction.
It proves:

- exact chance-then-inclusive-payload RNG transcripts;
- 15/30 poison state, exact marker production, and marker retention;
- positive auxiliary damage, armor-proc presentation, and combat contribution;
- zero-payload success with poison, marker, and presentation intact;
- failed chance, zero-primary stale-marker clearing, and incomplete-set
  no-draw behavior;
- dead-target and non-player-source phase exclusions; and
- preservation of event-owned presentation for the separate KBD marker without
  allowing the Black payload to consume a draw.

The authoritative combat gate grows from 93 to 94 scenarios. Existing poison,
auxiliary-damage, leather, projectile, primary-damage, death, and marker
coverage continues to guard the narrow callback boundary. No private visual
acceptance is required for this server-only internal extraction.
