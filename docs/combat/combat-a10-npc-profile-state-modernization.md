# A10 NPC profile and state modernization

## Outcome

A10 is complete as a behavior-preserving consolidation and characterization
milestone. `NpcCombatProfile` is a typed resolved view over the existing
`NpcAttackStyleProfile`, and `NpcMagicAttack` binds one per-cast element to its
dependent visual, impact, debuff, poison, and proc values. The original enum
remains the sole profile-selection authority; no broad data table, formula,
timing, or special-NPC policy was introduced.

## Profile authority inventory

| Concern | Active owner | A10 result |
| --- | --- | --- |
| Base style, range, offense, name/ID rules | `NpcAttackStyleProfile` | Retained as authoritative. |
| One-cast magic element and payload | `NpcCombatProfile` / `NpcMagicAttack` | Implemented typed composite; one element draw, no reroll by payload reads. |
| Projectile launch, identity, impact | `NpcBehavior`, A06 policy, `ProjectileEvent` | Retained; profile only supplies existing payload values. |
| Dragons, Kolodion, Balrog, elemental effects | profile plus their existing event/script owners | Explicit exceptions; no universal profile migration. |
| Aggro, chase, retreat, leash, home return | `NpcBehavior` | Retained; behavior state remains authoritative. |
| Definition offense/defense | `NPCDef` plus `Npc` accessors | Retained. |
| Devotion hostility and summon/imp gates | `NpcBehavior`, `Devotion`, `Summoning` | Retained. |
| Poison/DoT lifecycle | A08 / `PoisonEvent` / `Mob` | Retained; A10 consumes its live-lifetime predicate only. |
| Death, removal, same-object respawn | `Npc`, A05 lifecycle authority | Retained. |
| Contribution and pending summon XP | A09 `NpcContributionLedger` / `Npc` settlement | Retained; no economy policy chosen. |
| Quest/plugin/boss scripts | individual plugin/event owner | Retained compatibility boundaries. |

## Lifecycle characterization

The compiled combat gate now proves that:

- a selected Battle Mage element is drawn once and its payload reads do not
  consume a second RNG value;
- logout/removal of a chase target clears the NPC behavior target;
- signed-level mismatch clears chase rather than crossing spatial domains;
- improved-path out-of-bounds chase preserves the current home/leash cleanup:
  roaming state, normalized stats, and cleared poison;
- ordinary retreat/re-engagement, poison death/respawn, target lifecycle,
  exact-once death, A06 projectile identity, and A09 contribution/summon
  boundaries remain covered by existing compiled scenarios.

`Npc.remove` remains the terminal lifetime boundary. Same-object respawn
clears poison and contribution ledger state before restoring membership, home
location, normalized stats, and live state. A replacement NPC begins fresh.
There is intentionally no Classic-style watchdog reset: an unexplained combat
state is not silently reset outside the existing lifecycle and behavior rules.

## Explicit non-migrations

- Do not convert name-based/ID-based exceptions to profile data until each
  special owner has a migration contract and executable parity.
- Do not merge boss scripts, quest plugin deaths, dragon breath, Kolodion
  phases, devotion gates, or summon restrictions into `NpcCombatProfile`.
- Do not change the legacy retreat windows, aggro-level check, pathing choice,
  home point, profile chance rolls, projectile range, or profile formulas.
- A09's pending-Summoning-XP-on-non-death-despawn question remains an
  owner/economy decision; A10 records it but does not clear or award it.

## Verification

The authoritative `./server/test_combat` gate executes 132 scenarios,
including the new A10 profile/lifecycle cases. Required follow-up gates remain
the server/plugin build, layered movement fixtures, NPC profile/element tests,
poison lifecycle fixture, relevant summon/boss fixtures, artifact exclusion,
and changed-code static analysis.
