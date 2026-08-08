# A07.5B Baby Dragon Smoke Proc

## Scope and authority

A07.5B moves one proven-identical proc family, Baby Dragon smoke, into
`BabyDragonSmokeProc`. The shared unit owns only the positive equipment-effect
gate, one chance draw, smoke projectile, and target-debuff application. The
three event owners retain the surviving-target and player-source phase gates,
settled damage, surrounding effect order, and all terminal handling.

This is a behavior-preserving ownership extraction. It changes no equipment,
chance, magnitude, duration, accuracy formula, damage, contribution, aggro,
lifesteal, death, packet, animation, timing, or balance rule.

## Preserved contract

- Reciprocal melee, PvM melee, and projectile impact remain the three owners.
- The proc remains after Ogre Stagger and before Infernal Fire in each leather
  on-hit chain.
- A positive equipment-provided smoke percentage is the eligibility gate. The
  current full Baby Dragon set provides a 20% chance and 10% accuracy debuff.
- Settled zero damage remains eligible after the event reaches the
  surviving-target phase.
- An ineligible source consumes no random value. An eligible attempt consumes
  exactly one value whether it succeeds or fails.
- Reciprocal melee and projectile owners retain the production random adapter;
  PvM melee retains its injected event random source.
- Success installs the existing `Projectile.BLOW_SMOKE` presentation from the
  player source to the target, then applies the target-owned smoke debuff.
- The target retains the existing max-merge magnitude and five-attack refresh
  policy. A successful repeat restores five remaining attacks rather than
  stacking another independent effect.
- A failed or rejected attempt does not replace an existing projectile. This
  includes the primary projectile installed by `ProjectileEvent` construction.

The descriptor catalog remains descriptive. Runtime execution does not query
`SecondaryEffectDescriptor`.

## Explicit exclusions

The branch does not consolidate Elemental Giant, Infernal, dragon-breath,
poison, splash, boss, summon, delayed, reflection, AoE, or DoT behavior. It
does not move the event phase gates or the debuff state/consumption methods in
`Mob`. Any further proc family requires its own characterization and focused
approval.

## Executable evidence

The compiled A07.5B scenario executes the private production owners before and
after extraction. Across all three paths it proves:

- success on settled zero and exact smoke-projectile identity;
- one draw for successful and failed complete-set attempts;
- no draw for incomplete equipment, dead targets, or non-player sources;
- 10% target accuracy bias with five remaining attacks;
- successful refresh after partial consumption and expiry after five attacks;
- unchanged projectile state on failure and rejection; and
- exact injected and production random transcripts.

The authoritative combat gate grows from 89 to 90 scenarios. Existing leather,
projectile, primary-damage, and debuff coverage continues to guard surrounding
ordering and consumption behavior. No private visual acceptance is required
for this server-only internal extraction.
