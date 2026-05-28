# Combat Tasklist

## Status Key

- `[todo]` not started
- `[doing]` in progress
- `[done]` completed
- `[blocked]` waiting on a design decision or dependency

## Current Focus

- `[done]` Combat baseline is feature-complete until playtesting.
- `[blocked]` Final tuning is intentionally paused until real encounter
  playtesting produces feedback.
- `[doing]` Magic-equipment redesign planning is now tracked in
  [magic-equipment-tasklist.md](/home/justin/Core-Framework/docs/myworld/magic-equipment-tasklist.md)

## Goal

Build the documented MyWorld combat model into a complete playable system:
- `Melee`, `Magic`, and `Ranged` as the core combat skills
- always-hit offense-versus-defense combat
- meaningful weapon and armor sidegrades
- updated NPC combat behavior
- modernized combat interaction rules

This tracker is the active implementation checklist for combat.

Scope note:

- `MyWorld` is a PvM-only server
- duel, wilderness PvP, and OpenPK parity are not required for combat
  completion here
- PvP-specific code only stays in scope when it interferes with shared PvM
  cleanup

Closeout criteria:

- Core combat implementation is considered complete when automated checks pass
  and the remaining issues are encounter tuning, UI/player-facing clarity, or
  optional profile expansion.
- Final balance remains `finished until playtesting`; do not keep retuning
  vacuum numbers without live or scripted encounter evidence.
- Use [combat-playtest-checklist.md](/home/justin/Core-Framework/docs/myworld/combat-playtest-checklist.md)
  for the first manual validation pass.

## Phase 1: Core Combat Foundation

- `[done]` 1. Add `Melee` as the visible replacement for the old `Attack` slot.
- `[done]` 2. Introduce phase-1 combat stats:
  - `meleeOffense`
  - `rangedOffense`
  - `magicOffense`
  - `meleeDefense`
  - `rangedDefense`
  - `magicDefense`
- `[done]` 3. Update equipment aggregation to expose the new style offense and defense values.
- `[done]` 4. Replace melee and ranged hit/miss logic with always-hit offense-versus-defense rolls.
- `[done]` 5. Route direct-damage magic into the new offense-versus-defense structure as a first pass.
- `[done]` 6. Migrate player `Attack + Strength + Defense XP` into `Melee XP`.
- `[done]` 7. Remap old melee equipment requirements to `Melee`.
- `[done]` 8. Map client-facing stat display so `Melee` occupies the old attack slot.

## Phase 2: Melee Baseline Completion

- `[done]` 9. Create a MyWorld item override layer for combat rebalance.
- `[done]` 10. Establish explicit melee weapon offense values for the core weapon families.
- `[done]` 11. Add weapon speed as a stored stat and wire it into melee attack cadence.
- `[done]` 12. Establish the current core melee family identity:
  - dagger fastest / lowest direct damage
  - scimitar faster / lower damage
  - longsword baseline
  - spear baseline damage but slow
  - battleaxe slower / higher damage
  - 2h slowest / highest damage
- `[done]` 13. Establish the current metal armor defense model by tier and bar cost.
- `[done]` 14. Add lighter-armor sidegrade mechanics:
  - medium helm high-roll bias
  - chainmail speed bonus
  - square shield split melee/ranged defense
- `[done]` 15. Finish the melee equipment pass so all major melee families and armor variants are intentionally tuned, not still leaning on fallback assumptions.
- `[blocked]` 16. Review remaining melee outliers and special items after playtesting produces real use cases.
- `[blocked]` 16a. Revisit the full melee weapon family pass now that the extra
  metal tiers and NPC defense migration are in place, so each family has a
  clear role and the current first-pass numbers are rebalanced against the
  actual live combat environment:
  - dagger
  - short sword
  - scimitar
  - long sword
  - mace
  - spear
  - battleaxe
  - 2h sword
  - plus special one-offs like dragon and quest weapons

## Phase 3: Ranged Baseline

- `[done]` 17. Move ranged onto the same structural offense-versus-defense model.
- `[done]` 18. Add first-pass ranged offense overrides for bows, ammo, and thrown weapons.
- `[done]` 19. Finish explicit ranged weapon tuning so core ranged families are balanced intentionally rather than partially through fallback behavior.
- `[todo]` 19a. Add `4` more tree tiers so bow and staff progression can catch up
  to the expanded melee-metal ladder instead of topping out too early relative
  to the rest of the combat equipment curve.
- `[done]` 20. Remove ranged-armor progression from combat scope. MyWorld armor
  is defensive material-family gear only, with no ranged/melee/magic armor
  lanes.
- `[blocked]` 21. Decide whether ranged needs more weapon-family identity beyond offense and speed after playtesting.
- `[done]` 22. Add targeted ranged benchmark fixtures against representative NPCs and armor profiles.

## Phase 4: Magic Baseline

- `[done]` 23. Add first-pass `magicOffense` / `magicDefense` support to the item/runtime model.
- `[done]` 24. Route direct-damage spell hits through the new mitigation model.
- `[done]` 25. Replace the old first-pass magic-equipment tuning with the new
  staff redesign so magic weapons have intentional progression instead of
  bootstrap heuristics.
- `[blocked]` 26. Audit current spell damage bands against the new combat model and retune if needed once active playtesting starts.
- `[blocked]` 27. Confirm magic survivability and mitigation feel correct in PvE during playtesting before the later spell overhaul.
- `[todo]` 28. Hold the broader spell redesign for the later `Enchanting` / spellbook pass.

## Phase 5: NPC Combat Migration And Balance

- `[done]` 29. Allow NPCs to roll low-biased damage, including `0`, with softer rounding than players.
- `[done]` 30. Add the initial NPC override layer and early benchmark fixtures.
- `[done]` 31. Implement automatic NPC style-defense derivation from legacy `defense` using per-style multipliers.
- `[done]` 32. Replace manual one-off NPC balancing with broader baseline migration rules wherever possible.
- `[done]` 33. Finish the audit-conformance NPC migration so the authored
  family defaults in [npc-family-audit.md](/home/justin/Core-Framework/docs/myworld/npc-family-audit.md)
  are the actual source of truth for broad NPC defense profiles, with
  quest/special buckets remaining explicit exceptions.
- `[done]` 34. Run the remaining focused pass on representative monsters and
  exception cases after the family defaults are aligned:
  - beginner mobs
  - undead
  - demons
  - dragons
  - guards/knights/giants
- `[blocked]` 35. Decide whether displayed NPC combat levels need rebalance once runtime stats settle during playtesting.

## Phase 6: Testing And Benchmarking

- `[done]` 36. Add smoke and combat-data validation scripts for MyWorld.
- `[done]` 37. Add benchmark fixtures for beginner and rune-tier melee scenarios.
- `[done]` 38. Add family-comparison fixtures for melee weapon throughput.
- `[done]` 39. Expand benchmark coverage so ranged and magic have the same level of visibility as melee.
- `[done]` 39a. Add a style-matchup matrix for representative benchmark enemies so
  melee, ranged, and magic mitigation differences are visible in one place.
- `[done]` 40. Add balance fixtures for more real encounter targets and armor archetypes.
- `[done]` 41. Mirror new runtime NPC migration rules more fully inside the balance harness where needed.
- `[done]` 41a. Add combat runtime invariant checks for projectile engagement,
  target priority, damage-share XP, personal contributor loot, and rare-drop
  contribution gating.
- `[done]` 41b. Add combat exception guardrails so PvM-only config, reviewed
  kill/drop bypasses, and quest/plugin-owned death exceptions stay explicit.
- `[done]` 41c. Add representative scenario checks for group rewards, target
  priority, projectile-role intent, and style viability.

## Phase 7: Combat Interaction Rules

- `[done]` 42. Remove locked-in melee engagement for movement.
- `[done]` 43. Let players flee combat at any point.
- `[done]` 44. Allow food and potions during combat.
- `[done]` 45. Permit multiple players to attack the same target.
- `[done]` 46. Permit multiple enemies to attack the same player.
- `[done]` 47. Rework related targeting, XP, and loot-attribution rules once multi-attacker combat exists.
- `[blocked]` 47a. After the later spell pass, revisit
  air-spell behavior so the planned adjacent / stacked splash model can be
  implemented cleanly.

Notes:
- Threat has been transitioned toward open-target combat:
  - ranged and magic attackers can keep pressuring NPCs without hard target lock
- NPC chase preference now uses live threat instead of only last-hit chasing
- melee-range targets get first priority, then the lowest combat level target is
  selected
- players can now retarget between NPC opponents during PvM combat instead of being hard-locked to their first melee target
- PvM melee is now running through a unified attacker-driven event instead of the earlier reciprocal-pair-plus-pressure sidecar model
- additional players can now contribute real PvM melee damage against an NPC that is already focused on another target
- additional NPCs can now keep real PvM melee pressure on a player who is already fighting something else
- ranged retargeting in PvM now follows the same open-target direction instead of being blocked by the old in-combat gate
- XP is now split across multiple PvM damage contributors by damage share
- loot now rolls personally per active contributor, with rare-table and
  rare-normal-drop chances scaled by contribution
- legacy PvP and duel combat still exist in shared server code, but they are outside MyWorld scope unless they block PvM cleanup.

## Phase 8: Combat Style Identity And Formula Completion

- `[done]` 48. Preserve the current player-favored offense / defense rounding rules and validate them during balance work.
- Balance note: the live damage model already includes upward roll-weighting
  hooks such as high-roll bias effects, so future tuning should account for
  those backend roll-shape modifiers instead of balancing only around raw max
  hit and mitigation values.
- `[done]` 49. Finish baseline style identity so melee, ranged, and magic each have clear strengths, weaknesses, and counters.
- `[blocked]` 50. Revisit the offense-to-max-hit and defense-to-mitigation curves once playtesting produces encounter feedback.
- `[blocked]` 51. Decide whether additional dice-shape variety belongs in later combat phases after the current model is tested.
- `[done]` 52. Expand enemy style weaknesses beyond the baseline generic multiplier model.
- `[done]` 52a. Add the first family-style defense identities for benchmark NPCs
  using per-style multipliers:
  - skeletons: ranged-resistant, magic-weaker
  - zombies: magic-resistant, melee-weaker
  - demons: magic-resistant, ranged-weaker
  - dragons: broadly sturdy, especially against magic
  - battle mages: magic-resistant caster archetype
- `[done]` 52b. Audit live NPC attack styles and expand enemy offense variety so
  the new player defense split has real encounter relevance:
  - confirm which NPCs already use ranged, magic, breath, or other non-melee
    attacks
  - identify whether multi-style or alternate attack behavior already exists in
    code
  - add new non-melee or mixed attack profiles where needed so enemies can
    pressure melee, ranged, and magic defense differently

## Phase 9: Spell And Combat-Adjacent Rework

- `[doing]` 53. Reduce the live spellbook to the kept elemental, healing,
  teleport, alchemy, and special-spell set.
- `[doing]` 54. Remove redundant or dud spell lines from active progression:
  - debuff spells
  - old enchant spells
  - orb-charge spells
  - bones conversions
  - other dormant or low-value leftovers
- `[doing]` 55. Treat elements as equal spell families by tier rather than a
  hidden wind-to-fire damage ladder.
- Live simplified elemental debuffs:
  - wind: outgoing low-roll bias, `5%` per tier
  - water: outgoing max-hit reduction, `5%` per tier
  - earth: attack-speed reduction, `3%` per tier
  - fire: all-style defense reduction, `3%` per tier
- `[doing]` 56. Add the first healing spell line:
  - `Weak Heal`
  - `Heal`
  - `Strong Heal`
- `[todo]` 57. Review final spell costs, unlock levels, and special-spell
  balance after the robe/staff/spell path is fully in place.
- `[todo]` 58. Decide which removed spell slots/icons should be repurposed into
  later new spells instead of simply staying empty.

## Phase 10: Cleanup And Special Cases

- `[blocked]` 59. Review special combat items from the item-effects audit and re-hook them intentionally into the new system after playtesting clarifies which effects need priority.
- `[done]` 60. Audit and clean up the remaining live PvM combat paths that still assumed legacy `Attack`, `Strength`, and `Defense` behavior.
- `[blocked]` 61. Remove or neutralize remaining stance-specific behavior that no longer serves a purpose after UI/player-facing cleanup starts.
- `[blocked]` 62. Update player-facing explanation surfaces such as tooltips once combat behavior stabilizes after playtesting.

## Notes

- Melee, ranged, and magic have a complete baseline implementation for the
  current PvM-only scope.
- Combat interaction work is structurally implemented for PvM.
- Spellbook redesign remains adjacent work and should not block combat baseline
  closeout.
- Enemy style variety has baseline defense identities and attack profiles; more
  profiles can be added later if playtesting shows gaps.
- Final combat tuning is now expected to pause at a stable baseline until
  active playtesting produces real encounter feedback.
- Phase 7 PvM interaction work is now structurally in place:
  - movement no longer waits for a three-round retreat lock
  - players can flee combat immediately
  - food and potion item actions are no longer globally blocked during combat
  - PvM melee now uses the unified attacker-driven path
- PvP and duels remain shared-code leftovers, not active MyWorld roadmap work
- Phase 10 legacy-stat cleanup is now done for the live PvM combat layer:
  - melee damage and melee defense formulas now read the unified player `Melee`
    stat instead of stale legacy substats
  - live spell buffs / debuffs now remap old player melee substats onto `Melee`
  - active NPC curse / drain scripts now weaken player `Melee` once instead of
    touching dead `Attack` / `Strength` / `Defense` slots separately
- Automated combat coverage now includes:
  - `test-combat-data.py`
  - `test-combat-interaction.py`
  - `test-npc-attack-styles.py`
  - `test-combat-runtime-invariants.py`
  - `test-combat-exceptions.py`
  - `test-combat-scenarios.py`
  - `test-defense-distribution.py`
  - `test-balance-fixtures.py`

## Change Log

- `2026-03-19` Created active combat tracker based on the current combat docs and implemented baseline work. Current focus is Phase 2 onward: completing the baseline beyond the first melee pass.
- `2026-03-20` Expanded benchmark coverage with a style-matchup matrix and moved
  the first benchmark NPC families onto per-style defense multipliers so enemy
  weaknesses/resistances are visible in fixtures instead of only in theory.
- `2026-03-28` Shifted the NPC migration back onto strict audit conformance.
  `npc-family-audit.md` is now treated as the definitive source for family
  ratios, including the rule that at least one style stays at `1.0` and nothing
  exceeds `1.0`. Full encounter balance is still intentionally deferred until
  the family migration is in place; later tuning can lean on player damage
  output and formula adjustments instead of weakening the documented NPC family
  baselines mid-migration.
- `2026-04-11` Closed the combat baseline as feature-complete until playtesting.
  PvM targeting, group XP, personal contributor loot, ranged/magic NPC profiles,
  projectile engagement, PvM-only guardrails, and scenario-level automated
  checks are now in place. Remaining combat work is encounter tuning,
  player-facing clarity, and optional expansion after playtesting.
