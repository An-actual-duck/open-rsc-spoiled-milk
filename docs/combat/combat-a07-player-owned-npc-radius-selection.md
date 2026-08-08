# A07.3 Player-Owned NPC Radius Selection

## Scope and result

A07.3 gives the compatible player-owned NPC splash families one typed target
enumeration policy. It moves selection only. Damage requests, formulas,
contribution, lifesteal, aggro, presentation, charge mutation, child death, and
caller ordering remain with their existing owners.

`PlayerOwnedNpcRadiusSelection` preserves two established timing contracts:

- Blood Robe, Death Robe, and Death Amulet iterate the captured view candidate
  order while rechecking each candidate immediately before its child body. An
  earlier child can therefore make a later candidate ineligible.
- Hell's Inferno and Elder Green Dragon armor continue through the maintained
  `CombatEffectUtil.findPlayerOwnedNpcSplashTargets` facade, which resolves an
  eager snapshot before any child body runs.

The selector captures Guard Dog suppression and the view candidate collection
once. Every candidate still must be living, present, attackable, non-respawning,
and non-summoned, and the primary/killed NPC remains excluded. View membership
continues to own world and signed-map-level isolation. Radius continues to use
the legacy point comparison and deliberately adds no path or line-of-effect
check.

## Center policies

Primary-centered robe and ordinary splash selection reads the primary NPC's
current location for every candidate, matching the former inline loops.
Death Amulet retains its caller-captured player point for the entire burst, so
movement during one child settlement cannot move the remaining burst center.

## Migrated owners

- `ProjectileEvent.applyBloodRobeSplash`
- the Death Robe overkill loops in `CombatEvent`, `PvmMeleeEvent`, and
  `ProjectileEvent`
- `Player.applyDeathAmuletBurst`
- the NPC side of `CombatEffectUtil.findPlayerOwnedNpcSplashTargets`, while its
  public signature and eager-list behavior remain available to Hell's Inferno
  and Elder Green Dragon armor

The following are intentionally outside this compatible group: PvP splash
selection, Scythe cleave, Splinter, chain lightning, shuriken, boss-to-player
areas, delayed spell areas, Soul Amulet healing, and all damage/proc execution.
A07.4 owns chain and random-single traversal through the separate policy
recorded in
[`combat-a07-chain-random-traversal-policy.md`](combat-a07-chain-random-traversal-policy.md).
A07.5 continues to own any shared proc executor.

## Verification

The compiled A07.3 scenario asserts:

- exact view-order preservation and primary exclusion;
- radius, living, attackable, summon, and signed-level filtering;
- intentional selection through a blocked line of effect;
- lazy candidate revalidation after earlier child work;
- eager compatibility snapshots that do not revalidate after selection;
- moving primary-centered and fixed terminal-burst centers; and
- complete Guard Dog suppression.

The existing robe, Death Amulet, Hell's Inferno, Elder armor, summon-friendly-
fire, and Guard Dog fixtures remain the executable behavior parity gates. The
authoritative combat gate grows from 85 to 86 scenarios.

## Stop conditions

Any future selector expansion must stop if it changes view order, spatial
membership, wall behavior, suppression, exclusions, revalidation, random draw
order, charge timing, aggro, contribution, or per-child death order. A shared
"AoE" label is not evidence that another family belongs in this policy.
