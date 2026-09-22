# Slayer leather set effects — implementation

Updated: 2026-09-22. Implements the [selected bonuses](slayer-leather-set-bonuses.md)
under `WANT_MYWORLD` and `WANT_CUSTOM_LEATHER`. No deployment, server restart,
map placement, art changes or account migration is part of this patch.

## Activation and arithmetic

All effects require the complete matching five-piece set. Borrowed visual
appearance IDs do not grant another family's effect.

| Set | Implemented effect |
| --- | --- |
| Giant frog — Sticky Skin | 10% on positive direct damage: one pending delay to the attacker's next otherwise-ready attack, exactly 1 tick; no refresh/stacking, movement unaffected. |
| Naga — Cold Blooded | Ice/fire magic damage multiplied by 0.80 after elemental resistance, rounded upward with positive minimum 1; no water/general-magic resistance or status immunity. |
| Terror dog — Ferocious | Bear's melee callback with two `floor(damage * .75)` hits; positive first hit and surviving target required. No independent reroll; existing second-hit settlement/mitigation retained. |
| Bloodveld — Essense Absorption | Heal 1 HP once per positive actual HP-loss event owned by wearer, including secondary damage, DOT and summons; normal maximum HP, no overheal, no zero-hit/overkill duplication. |
| Dark beast — Electrically Charged | Positive direct hits add 1d3; reaching 30 resets meter and rolls tier-2 thunder separately against eligible enemies within radius 2. |
| Ugthanki — Storage Hump | `round(foodHeal * (1.20 + natureFoodBonus))`, once per food-healing application; no general potion/regeneration bonus. |
| Scorpion / spider / magic-spider carapace | Full sets add 2 / 3 / 5 poison power removed at each existing poison resolution, added to baseline 3 and Nature necklace. Old offensive poison procs removed. |

Owner confirmation on 2026-09-22 approved all three requested rule groups:

- Full five-piece carapace +2/+3/+5 cleansing, added to baseline and Nature necklace.
- Electrically Charged positive direct hits only; reset on discharge,
  unequipping, death or logout; the wearer's normal tier-2 thunder roll against
  eligible nearby enemies.
- Sticky Skin positive direct hits only, one pending one-tick attack delay
  without stacking; Storage Hump added to Nature food bonuses before rounding once.

These rules were already implemented, so confirmation required no runtime
change. Cold Blooded mitigation rounding remains an implementation default;
the three confirmations do not separately approve that choice or the deferred
game-wide potion/Slow overhaul.

## Damage ownership and timing

`SlayerLeatherEffects` is the shared policy. `ResolvedDamageTransaction`
invokes its explicit post-settlement hook once, after recording actual HP
loss. The diagnostic damage observer is not gameplay authority. Original
callers still own their pre-existing contributions, XP and death settlement.
Healing itself never enters a damage transaction. Summon damage resolves the
registered summon owner's identity. Positive damage to each AOE target is a
separate qualifying Bloodveld heal; there is no per-tick cap.

Sticky Skin gates the melee and NPC projectile attack schedulers, including
shared Naga/Banshee/Bloodveld cooldowns and Dark beast charge selection. It
does not expire while chasing or waiting on a normal attack cooldown. Legacy
reciprocal melee retries the same hitter after the extra tick. A lifecycle
snapshot prevents a dead/respawned creature inheriting the pending delay.
Existing whip locks resolve before the pending melee delay. Future PvP Slow
standardization remains outside this enemy-focused change.

Charge is session/lifecycle-bound and clears when a piece is removed, on death
or logout, and on discharge (no remainder). DOT/owned secondary procs cannot
recharge it or create retaliatory loops. The burst uses the wearer's normal
secondary magic formula with Thunder Splash power 7.2 and its 60% damage cap,
not Thunder Spire's 25% splash multiplier. The explicit Thunder Splash power
is read from the existing F2P-only spell table because MODERNMAGIC currently
does not contain that spell entry; this patch does not change general casting
tables. Existing secondary-spell spatial, summon, party/clan and attack rules
apply. Players are excluded while PvP is off. Actual damage receives normal
credit, hit presentation and death settlement.

## Element coverage and cleansing

Added an ICE element distinct from WATER. The existing ice giant, ice warrior
and ice queen magic profiles emit ICE, retaining their existing projectile
visuals and water-robe resistance. Player ice/fire spell producers now provide
semantic elements to the projectile's normal and splash mitigation paths.
The bonus does not classify a spell solely from its projectile art. Arbitrary
untagged scripts, periodic burns and dragonbreath have not been reclassified.
The [post-tower tag audit](../in-progress-work-plans/effect-standardization-follow-up.md#enemy-spell-element-tags)
is still required.

Poison damage still resolves before removal, every 8 ticks. Carapace changes
the removal amount, not the damaging tick rate or immunity. Magic-spider now
pays the usual leather magic penalty. Legacy non-MyWorld behavior remains
unchanged. Anti-poison potion conversion is deferred.

## Verification

`ant test_slayer_leather_effects` exercises real equipment, food consumption,
poison resolution and damage transactions: all six full-set gates, partial
sets, both rounding directions, elemental discrimination/tag producers,
primary/secondary/DOT/summon ownership, zero hits, overkill, max HP, one-tick
delay through normal cooldowns, charge threshold/reset/unequip/lifecycle,
radius-two inclusion/radius-three exclusion, PvP-off exclusion, cleansing
stacking and removal of magic-spider's exemption.

Also run Slayer reward/production regressions, individual new-monster combat
fixtures, the strict combat suite, client build/catalog checks and existing
leather-defense/poison source-contract checks. The secondary-effect catalog
test is updated for the two previously registered Slayer rewards and the new
Electrically Charged entry; this preserves its exact inventory assertion.

Results: focused leather effects, Slayer rewards/production, Dark beast,
Bloodveld, Naga and Abyssal demon fixtures passed against the installed map.
Client build and six leather/poison/defense/client-catalog checks passed.
The strict combat gate passed **144/144 scenarios** with an isolated empty
map-discovery root. Its default run encounters an unrelated pre-existing
fixture move outside installed native-map terrain; no map or runtime policy
was changed to bypass that boundary. Reproduce the isolated run with an empty
`mktemp -d` directory passed only to the test process through
`JAVA_TOOL_OPTIONS=-Dopenrsc.worldBuilderTargetRoot=<empty-directory>` and
`ant test_combat_strict`.

## Separate pending work

Banshee Ectoplasm/armor withdrawal, selected old hide/armor retirements,
optional boss-task opt-ins, final sprites and map integration are unchanged.
The broader cleansing-potion, stacking Slow and effect-standardization work
still waits until the Slayer Tower is done.
