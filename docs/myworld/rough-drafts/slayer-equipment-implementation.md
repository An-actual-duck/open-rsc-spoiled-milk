# Slayer equipment implementation

Updated: 2026-09-21. Feature work only; no deployment or server restart.

Design authority: [unique equipment](slayer-unique-equipment-design.md),
[assembly prices](slayer-tower-task-expansion.md#approved-assembly-prices-below-the-backpack-benchmark).

## Implemented: Shield of Mobility

- Item **3349**, `MyWorldItemId.SHIELD_OF_MOBILITY`.
- Adept shop (`port_sarim`): **500 Cockatrice Feathers + 110 Adept currency**.
  Any existing Slayer associate can open the normal shop picker. No new
  assembly interface, kill-credit requirement or rank gate was added.
- Tradable, nonstackable, noteable offhand; no wear skill/level requirement.
  Standard item death/trade behavior; provisional base price 0, as with parts.
- Tier-3 bronze square-shield defenses: 5 melee, 1 ranged, 0 magic.
- Temporary inventory icon `items:3`, olive tint `#8A9675`; existing bronze
  square-shield worn appearance 98. Final artwork remains outstanding.
- Exact examine: `A lightweight shield that doesn't block well but keeps you mobile`.

Equipped protection is tested at effect application, never at active-effect
lookup. Existing Slimy Spit, Stony Glare and Abyssal flesh traps survive a
forced equipment-state change; normal trap action guards still prevent wielding.
The shield prevents new applications and the demon's extra five-second Solvent
drain. Normal Solvent countdown continues. Poison is applied separately and
is not blocked. No wail, lightning, Feeding Frenzy or general damage immunity.

### Enemy immobilization audit

The current combat/movement admission gates and enemy effect producers resolve
to `GiantFrogCombat.onSpitImpact`, `CockatriceCombat.onMeleeSwing` and
`AbyssalDemonCombat.onDamage`. Each now uses the shared
`SlayerEquipmentEffects.preventsEnemyImmobilization` application boundary.
Future enemy locks must use that same boundary. Legacy `STUN` is a stat-reduction
spell, not an action/movement root; quest/dialogue busy states, terrain readiness
holds, ordinary combat movement rules and Bloodveld's forced pull are not
immobilization debuffs and were not changed.

The helper explicitly accepts NPC sources only. Player-sourced/PvP protection
remains deferred for review before PvP is enabled.

## Reusable shop purchase support

- Reward definitions optionally carry 1–3 distinct unnoted ingredient IDs and
  positive counts. Unknown/duplicate/self-referential IDs, malformed fields,
  oversized counts and empty ingredient lists are rejected during load.
- Ingredient-bearing rewards require source-tier currency only. Ordinary
  supply prices and backpack prices remain unchanged.
- Existing production recipe packets already carry ingredient arrays, so no
  new wire format or separate UI was needed. Ingredient icons/counts appear
  bottom-left alongside the currency display; quantity changes update both.
  Exact counts remain in tooltips when large counts are abbreviated.
- The server rechecks materials, currency and output capacity. Materials may
  free inventory space for the result. A failed/partial grant restores original
  inventory instances, slot order, quantities and currency, under transaction
  locks. Noted and wielded materials are not consumed.
- Existing infinite shop stock and rank-independent shopping remain intact.

## Verification

- `ant test_slayer_mobility_shield`: recipe packet data, no rank/wear gate,
  missing materials/currency, full inventory, bulk/overflow quantities,
  partial-grant rollback, concurrent requests, prevention-only combat,
  poison and Solvent isolation.
- `ant test_monster_slayer_player_state` and
  `ant test_monster_slayer_tower_routes`: existing progression/rollout behavior.
- Client runtime item catalog, production UI/behavior/flow checks, component
  regression checks and Monster Slayer schema/foundation tests.
- A player-driven visual shop/combat test remains to be done; no private or
  public session was launched by this task.

## Remaining sequence

Five finished rewards remain: Abyssal Whip, Thunder Spire Staff, Leaching Bow,
Dagger of Terror and Sullen Pendant. Their approved recipes/prices/behaviors
remain in the design documents; no finished items for them are claimed here.
Recommended next pass: Abyssal Whip, including its target-owned one-tick delay
and three-tick immunity, using this same ordinary purchase route.

Ordinary loot completion, consumable shop pricing, leather/tanning/set effects,
optional gated boss tasks, final sprites and tower map placement remain separate
work. The future slow/poison coating system remains intentionally deferred.
