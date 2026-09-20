# Dark beast combat test contract

NPC 869 is combat-enabled for private testing. The planned Slayer tower placement
is floor 5. This slice does not place spawns, change assignments/drops, stock a
shop, or activate a public deployment.

## Stats and cadence

Initial tuning: combat level 105; 130 HP; melee offense 110; magic offense 210;
melee/ranged/magic defenses 85/70/85. Legacy attack/strength/defense stay at 1:
the explicit modern offense/defense values are authoritative. Ordinary attacks
use the established melee cadence. The creature approaches normally when not
charging. No ordinary magic projectile attack is added.

With a player target within four tiles, a shared decision gate rolls a 20%
charge chance at most once every three ticks. The first decision at or below
half health guarantees a charge, ignoring that random gate. This guarantee is
once per NPC lifetime, and remains available if half health is crossed during
an earlier random charge. A one-tick recovery preserves the discharge pose.

## Lightning and counter

- Charge begins by capturing living, logged-in players in the same spatial
  domain within four tiles (Chebyshev distance, including the boundary).
- Each captured player receives `The air feels tingly` once per charge and a
  **Static charge** HUD status. Entrants after acquisition are not marked.
- Ten game ticks of charging: no walking or normal attacks; incoming combat
  damage is halved, rounded down. Melee, ranged, magic, poison, and existing
  secondary/splash/reflect damage paths use the same guarded reduction helper.
  Damage-request/settlement infrastructure retains its existing responsibilities.
- First and second approved frames alternate on server tick updates. Tick ten
  selects frame three and releases lightning. At the normal 640ms tick this is
  6.4 seconds, not ten seconds.
- Each uncleared mark resolves the standard NPC magic roll using offense 210
  and its normal spell-power derivation, plus ordinary player magic mitigation.
  The visual is `thunder-3` / `CombatEffect.THUNDER_STRIKE` (34). Damage is not a
  percentage of the player's HP, and can roll zero.
- Distance, line of sight and movement after marking do not cancel the hit,
  including teleporting while the same player lifetime/session remains active.
  Death/logout/session replacement invalidate marks. NPC death/removal cancels
  its pending attack; no orphan lightning persists into a respawn.
- **Static discharge wipe (3/2/1)** uses IDs 3330–3332 and a potion placeholder.
  `Wipe` is usable during combat and clears every current Dark beast mark on its
  user in one use. It grants no subsequent immunity. No active mark means no use
  is consumed. The third use leaves the usual empty vial placeholder.
- `::slayergimmicks` supplies one full three-use wipe alongside existing counters.

## Approved art and transport

`dev/myworld/assets/sprites/npcs/dark-beast-lightning/` preserves the approved
normalized base and final horn-lightened charge strip. `PackDarkBeastLightning`
copies the first six columns unchanged and samples the final strip at 1/7 scale,
origin (0,8) in a 120×110 cell. This matches the accepted comparison's reference
origin (10,10) and charge origin (10,18). No per-frame auto-fit/recentering.

The packed 840×330 sheet contains five movement columns, melee, then lightning.
NPC combat-effect codes 78/79/80/81 are reserved for low pulse/high pulse/release/
cancel pose messages, interpreted only for NPC 869 by the updated client. They
are not generic overlay effects. Pulses refresh each tick so spectators joining
mid-charge pick up the current pose; a short expiry is a lost-update fallback.

## Verification / private acceptance

Automated: `ant -f server/build.xml test_dark_beast_combat`, the existing combat
regressions, client build, `tests/myworld/test-slayer-movement-preview.py`, and
the server inventory audit. The fixture covers actual scheduler timing,
overlapping marks, wipe charges/full inventory, both melee owners, threshold
reset, source/player lifetime cancellation, and asset registration/pixel identity.

Private test: `::spawnnpc 869 4 10`, then `::slayergimmicks`. Observe the full
charge with/without wiping, run out of range after marking, walk into a charge
late, and try two beasts at once. Confirm pulse/release baseline against walking
and melee from both mirrored facings. Check normal melee resumes and the first
half-health crossing charges. Final damage/frequency tuning remains subject to
owner playtesting.

### Implementation verification (2026-09-20)

Passed server core/plugins and client builds; Dark beast, Bloodveld, Terror dog,
Naga, ranged-approach, Banshee, Cockatrice and Slayer gimmick-kit fixtures;
sprite disk/JAR fidelity and fixed registration; Server R2 inventory audit.

The full legacy `test_combat_strict` gate reaches a pre-existing native-terrain
fixture error in `CurrentCombatProjectileLifecycleCharacterization:481`:
`Ordinary movement cannot leave native package terrain`. Rebuilt the untouched
`b1b4804e9` server sources into an isolated temporary directory and reproduced
the same scenario, exception, stack and deterministic RNG trace against the
same map. No terrain/runtime work is included in this NPC change. The full
legacy gate is therefore not claimed as passing. In-game acceptance is pending.
