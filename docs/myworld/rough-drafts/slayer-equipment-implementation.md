# Slayer equipment implementation

Updated: 2026-09-21. Feature work only; no deployment or server restart.

Design authority: [unique equipment](slayer-unique-equipment-design.md),
[assembly prices](slayer-tower-task-expansion.md#approved-assembly-prices-below-the-backpack-benchmark).

## Pending owner tier correction — 2026-09-22

The art pass corrected the design authority, not the runtime definitions.
The implemented table below records the old values until a gameplay patch:

- Leaching Bow: use Ebony Logs `2113` instead of Magic Logs `636`; reduce
  ranged offense 44 to 40 (Ebony Longbow `2125`), retaining speed 3, 54 Ranged,
  20% primary-hit lifesteal and 85 Elite currency. Review arrow compatibility
  against the tier-8 benchmark instead of inheriting Magic Longbow's ceiling.
- Thunder Spire Staff: use Magic Logs `636` instead of Blood Logs `2114`;
  reduce magic offense 56 to 48 (Magic Staff `1784`), retaining 62 Magic,
  thunder effects and 200 Champion currency.
- Abyssal Whip remains tier 10 unchanged. Progression is Bloodveld 8,
  Dark Beast 9, Abyssal Demon 10; do not reintroduce the old split tiers.

Update item definitions, shop ingredient data and relevant regression tests
together before considering the correction implemented. Bow artwork now uses
the tier-8 Ebony Longbow, not the superseded Magic Longbow reference.

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

## Thunder casting correction (2026-09-22)

The modern damage table now inherits the existing lower-tier spell book.
This restores Thunder Ball/Splash powers of 4.8/7.2 (Thunder Strike remains
9.6) and the same missing first/second-tier ice, acid and wood entries.
Previously these casts supplied -1 to projectile launch validation and failed.
The invalid-power guard remains intact; no new balance values were introduced.
Thunder Spire retains radius 1/2/3, secondary power scaling 15%/25%/40%, and
removes the primary cap only for Thunder Strike. Electrically Charged now
reads Thunder Splash from this same modern table with unchanged damage.

`ant test_thunder_spell_casting` exercises actual spell packets through rune
payment, launch and impact for all three thunder spells with and without the
staff, plus the six affected sibling spells. It checks primary capped rolls,
splash radius boundaries, PvP exclusion, one-time rune/impact settlement,
lower-book table coverage and continued rejection of invalid launch power.
The fixture reproduced the launch exception before the table correction.

## Still separate work

New leather set effects,
optional gated boss tasks, final sprites and tower map placement remain separate
work. The future slow/poison coating system remains intentionally deferred.

The eight monsters now have [ordinary supply loot and independent equipment drops](slayer-ordinary-loot-implementation.md).
All five protection consumables are sold in every shop at their
[approved single-tier prices](slayer-gimmick-shop-implementation.md).

The six new hide families now have [baseline tanning and armor recipes](slayer-leather-implementation.md).
Their special bonuses remain unassigned. The owner deferred the existing-hide
audit and grandfathering work until all eight new additions and their sprite
work are complete; those do not block the new-content art pass.
