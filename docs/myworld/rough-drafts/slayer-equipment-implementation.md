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

## Remaining five rewards implemented

These use the same ordinary, atomic Slayer-shop purchase flow as the shield.
No new assembly interface, personal kill counter, or purchase-rank requirement.
All are tradable, noteable, nonstackable, with provisional base price 0. Final
art is outstanding; server worn appearances and recolored client inventory
icons reuse existing items. Exact examines follow the approved design.

| Item / ID | Recipe plus source-tier currency | Wear requirement | Combat benchmark |
| --- | --- | --- | --- |
| Abyssal Whip / 3350 | Rib + 10 Vertibrae + 50 Slimey Residue + 210 Hero | 70 Melee | 72 melee offense, dagger weapon speed 5 |
| Thunder Spire Staff / 3351 | Lightning Horn + Blood Logs (2114) + 200 Champion | 62 Magic | Blood Staff: 56 magic offense |
| Leaching Bow / 3352 | 2 Leach Tongues + Magic Logs (636) + 85 Elite | 54 Ranged | Magic Longbow: 44 ranged offense, weapon speed 3 |
| Dagger of Terror / 3353 | Terror Fang + 85 Elite | 30 Melee | Steel dagger: 9 melee offense; explicit one-tick attack cadence |
| Sullen Pendant / 3355 | Frozen Tear + 105 Veteran | None | Neck slot; no additional ordinary combat bonuses |

Poisoned Dagger of Terror **3354** is produced by the existing weapon-poison
inventory action, not sold separately. It retains the same requirement and
cadence, with ordinary tier-5 poison (20 applied power, 50 maximum power).

### Combat ownership and timing

- Whip: 10% of its own positive primary melee hits delay the victim's attacks
  and movement for one tick. Three further ticks of target-owned immunity
  follow. Multiple attackers cannot refresh, stack or queue the delay. Other
  actions are not locked. State is invalidated on a new combat lifetime.
- Bow: 20% of actual primary bow damage removed from HP; overkill, poison,
  offhand and secondary effects do not heal. Fractional fifths accumulate so
  small hits remain useful, but full HP discards surplus. Fractions survive
  equipment swaps, not a new combat lifetime. Eligibility is captured at
  projectile launch; changing equipment in flight cannot acquire the effect.
  Uses the Magic Longbow's ordinary arrow compatibility.
- Staff: Thunder Ball/Splash/Strike splash radii are 1/2/3 around the impacted
  target. The primary target is excluded; each eligible secondary target gets
  a separate modern magic roll from 15%/25%/40% spell power, rounded up before
  the existing secondary-magic formula, following god-spell conventions.
  Strike's 80% cap becomes 100%; lower thunder caps stay unchanged. Staff
  authorization, spell tier and base power are captured at cast commitment.
  Splash respects area suppression, spatial domains, living/attackable
  participants, summon exclusion and player attack permissions. It carries
  no god-spell debuff, lifesteal or new reward proc chain.
- Pendant: on positive direct primary melee/ranged/magic damage, 10% chance
  to cry out and retaliate in yellow for a uniformly selected integer 1–10%
  of normal NPC maximum HP. Damage rounds down with minimum 1, bypasses
  defense rolls but preserves Dark Beast charge mitigation. No proc on poison,
  offhand or other secondary damage; retaliation never recursively procs.
  Requires a living wearer/attacker at resolution. Boss activation reports
  the approved `Is uneffected` instead of damaging the boss.

Boss immunity uses the explicit `SlayerRewardBosses` identity registry, not
NPC names or a combat-level threshold: Delrith, Count Draynor, Melzar, Elvarg,
Tree Spirit, Chronozon, General Khazard, Bouncer, Black Knight Titan, Khazard
Warlord, King Black Dragon, Grand Tree Black Demon, Nazastorool forms, Iban,
Kolodion forms, Nezikchened, Balrog, Elder Green Dragon and Foundry Dragon.
New bosses must be registered there. Ordinary high-level Slayer enemies are
not bosses merely because of their level.

PvP remains off. Neither splash nor retaliation damages players while it is
disabled. Future rules are guarded by legal wilderness targeting and
party/clan exclusion; pendant player retaliation is 1–5%. Review these and
Shield of Mobility interactions before enabling PvP.

Placeholder icon sources: whip `items:81`, staff `items:123`, bow `items:54`,
daggers `items:80`, pendant `items:24`. These do not represent finished art.

Additional verification: `ant test_slayer_rewards` exercises all five recipes,
wear requirements, whip timing/shared immunity/lifetime reset, bow fractions
and projectile equipment swaps, both dagger schedulers and real poison
conversion, pendant bounds/boss immunity/PvP exclusion, and thunder tier,
cap, radius, target and launch metadata rules.

## Still separate work

Ordinary loot completion, consumable shop pricing, new leather set effects,
optional gated boss tasks, final sprites and tower map placement remain separate
work. The future slow/poison coating system remains intentionally deferred.

The six new hide families now have [baseline tanning and armor recipes](slayer-leather-implementation.md).
Their special bonuses remain unassigned. The owner deferred the existing-hide
audit and grandfathering work until all eight new additions and their sprite
work are complete; those do not block the new-content art pass.
