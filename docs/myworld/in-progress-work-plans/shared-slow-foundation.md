# Shared combat Slow foundation

Implemented in the Slayer worker, September 2026. This supersedes percentage
attack-speed Slow for the sources listed below. No weapon coating is added yet.

## Power, caps, and cleansing

One transient target-owned pool: `floor(power / 10)` produces Slow 1 or Slow 2.
Source caps are **12** and **22**, including the approved two-power buffer. The
highest received cap is retained until power reaches zero; subsequent lower-cap
sources can refill that retained higher cap. Applications add rather than replace.
Power 1–9 remains present without a timing penalty. Remove two power every four
game ticks, bounded at zero. Reapplication does not restart the decay deadline.
Threshold, buffer, decay amount and interval are centralized in `content/Slow.java`.

| Source | Power | Source cap | Trigger preserved |
| --- | ---: | ---: | --- |
| Earth Strike / Rock Throw | 4 | 12 | Positive resolved damage |
| Earth Bolt / Earth Hammer | 6 | 12 | Positive resolved damage |
| Earth Blast / Earth Burst | 8 | 22 | Positive resolved damage |
| Earth Wave / Earth Impale | 10 | 22 | Positive resolved damage |
| Basic Withering | 8 | 12 | Positive damage to surviving target |
| Advanced Withering | 12 | 22 | Positive damage to surviving target |
| Earth sword | 10 | 12 | Existing 5% proc |
| Moss Giant leather set | 10 | 12 | Existing 20% proc |
| Earth Dragon leather set | 10 | 12 | Existing 20% proc, auxiliary damage unchanged |
| King Black Dragon earth branch | 12 | 22 | Existing elemental follow-up selection |

Other Withering debuffs and all source damage/RNG policies are unchanged. The
published projectile envelope still transports the legacy earth rank × 3 value;
only its impact adapter translates that to the new power/cap. Old percentage
setter APIs remain for compatibility, with no migrated live source invoking them.

## Timing boundaries

Add the effective tier in whole game ticks **after** calculating ordinary action
speed. Dagger of Terror is therefore 1 + Slow tier ticks, not immune to Slow.
Actor-owned melee, player ranged/thrown attacks, legacy NPC ranged events, NPC
profile ranged/magic attacks, and combat spell casting consume this rule.
The extra delay is snapshotted when scheduling the next action; gaining or losing
Slow cannot move an already scheduled deadline. This does not change movement,
food/potion use, skilling, teleporting, enchanting, or other utility spell timing.

Combat magic has its own latched deadline alongside the existing ordinary spell
timer. Manual, queued, and autocast execution check it. Only offensive spell
finalization schedules it; ordinary cast/failure cooldowns retain their existing
behavior. The extra combat deadline is transient and not loaded from saves.

Banshee/Naga/Bloodveld shared attack cooldowns snapshot Slow at attack commitment.
Abyssal stabs use 1 + tier; its fixed four-tick spike animation recovery remains
four ticks, then the snapshotted extra delay blocks its next attack. Dark Beast
keeps its ten-tick telegraphed charge and one-tick stationary recovery unchanged;
a separate snapshotted deadline then delays the next attack without preventing
approach movement. Slow does not alter active projectile flight,
windups already in progress, or periodic/retaliation damage.

**Deferred PvP/duel limitation:** reciprocal `CombatEvent` uses one alternating
event for both actors. Adding Slow to that shared delay would incorrectly delay
the opponent. It deliberately does not consume the new Slow tier. Active PvM
routes through independent `PvmMeleeEvent` instances (`Mob.startCombat`), while
PvP remains disabled. Revisit actor-owned PvP scheduling before reenabling PvP.

## Lifetime and presentation

The pool is not saved: death, logout/session changes, and NPC respawn invalidate
the captured combat lifetime. Explicit lifecycle advance clears the state/HUD;
decay events reject dead, removed, logged-out, or replaced participants. No
offline ticking or persistence migration is introduced.

The maintained status HUD uses existing effect identity kind 2 with appended
codes 4 and 5 for **Slow 1 / Slow 2**, using an Earth rune placeholder icon.
Tier transitions update the HUD silently. Same-tier top-ups refresh its countdown
only if power actually changed; capped applications emit no repeated messages.
Subthreshold buildup has no visible Slow status.

Not converted: Startle and Ogre attack cancellation, Frostbite, Whip delay,
Sticky Skin's pending next-attack delay, frog/cockatrice/abyssal immobilization,
or unused Water Slow/Bear Intimidate APIs. Shield of Mobility coverage is not
expanded by this task. These remain separate effects, not Slow power consumers.

## Validation

- `ant test_slow`: cap/refill/decay, lifecycle/HUD, additive player/NPC speed,
  dagger exception, special cooldown latching, combat-only magic, actual manual
  and autocast gates, actual NPC ranged/magic scheduling.
- `ant test_combat_strict`: existing 144 scenarios; Earth Dragon/KBD old
  percentage/attack-count assertions replaced with power/time-decay assertions,
  preserving their damage, target eligibility, and RNG checks.
- `bash scripts/build-client.sh`; HUD and item-generator regression checks.

No private or public session restart or deployment is part of this implementation.
