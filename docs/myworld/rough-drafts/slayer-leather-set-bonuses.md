# Slayer leather set bonuses and cleansing direction

Updated: 2026-09-22. Six retained new set bonuses and the carapace replacement
are implemented. This is a code status, not a live-deployment claim. This
document supersedes earlier undecided-theme notes and the plan to retain
Banshee hide armor; that separate withdrawal is still pending.

See [runtime rules and verification](slayer-leather-effects-implementation.md).
The owner confirmed the implemented carapace values/stacking, Electrically
Charged hit/reset/roll rules, Sticky Skin positive-hit/non-stacking rules and
Storage Hump additive stacking on 2026-09-22. Cold Blooded's integer rounding
remains an implementation choice rather than a separately confirmed decision.

## Carapace: poison cleansing

All carapace families use faster poison clearance as their shared defensive
identity, replacing their offensive poison-proc bonuses. Strength increases
with armor tier: scorpion tier 2, spider tier 3, magic spider tier 5.
Future carapaces follow this theme as well.

Remove magic-spider armor's special exemption from the leather magic penalty
when replacing its old effect. Carapace no longer needs separate melee,
ranged, and magic offensive identities. Ordinary leather penalties apply.

The wider intended poison model favors clearing accumulated poison, not
preventing application. Anti-poison potions are to become tiered cleansing
potions, allowing combinations of armor, jewelry and consumable cleansing.
Exact potion tiers, durations, strengths, stacking arithmetic and caps remain
undecided. Do not silently convert every existing immunity source or existing
potion definition during a set-bonus patch.

### Verified current foundation

It is the **Nature necklace**, not the Nature amulet. `PoisonEvent` currently
resolves every 8 game ticks and drains 3 poison power per resolution; the Nature
necklace adds its jewelry tier to that drain. This removes more poison power
at the same cadence, rather than speeding up damaging poison ticks.

Implementation references:

- `server/src/com/openrsc/server/event/rsc/impl/PoisonEvent.java`
- `server/src/com/openrsc/server/content/EnchantingItemEffects.java`,
  `getNatureCleansingPoisonDecayBonus`
- `server/src/com/openrsc/server/model/container/Equipment.java`, carapace
  poison getters and magic-spider armor-penalty exemption.

Owner-approved values (2026-09-22): full five-piece scorpion/spider/magic-spider
sets add **2/3/5** poison-power removal per resolution, respectively. This
adds to baseline 3 and the Nature necklace bonus without changing tick cadence.
Mixed/partial sets do not activate it. This approval does not select values
for future cleansing potions.

## Dark beast: Electrically Charged

- Taking damage with the armor equipped charges a hidden meter.
- Each qualifying hit adds a fresh **1d3** roll, not the damage amount.
- At **30 charge**, trigger a **tier-2 thunder spell** against all enemies
  within **2 tiles** of the player.
- This is a retaliation effect, not the monster's marked-target lightning
  mechanic and not a Static discharge wipe interaction.

Owner-approved rules (2026-09-22): positive direct hits only; reset to zero on discharge,
unequip, death or logout. Use the wearer's magic roll with tier-2 thunder power
and cap, and the existing secondary-spell enemy eligibility rules. DOT and
secondary procs do not charge the meter. PvP remains gated off. See the runtime
note for exact formula and test coverage.

## Bloodveld: Essense Absorption

Heal the wearer **1 HP per positive damage event** they cause to an enemy,
regardless of source. This includes primary attacks, poison ticks, summons
and other player-owned secondary damage. Damage must actually remove at least
1 HP; a zero hit does not heal. This is flat healing, not a percentage and not
limited to the primary weapon like Leaching Bow.

Ownership must follow the damage's actual originating player (including
summon ownership and periodic-effect provenance). Count each resolved damage
event once; do not duplicate healing through multiple hit callbacks. Healing
itself is not damage and cannot trigger this effect again. Respect normal
maximum HP. Multi-target damage can qualify separately per damaged enemy;
there is no per-tick event cap in this implementation.

## Giant frog: Sticky Skin

Every time an enemy hits the wearer, **10% chance** to delay that enemy's
**next attack by 1 tick**. This effect is attack delay, not a movement root,
full action lock, or the frog monster's Slimy Spit debuff.

Tie the implementation to the planned **Slow** vocabulary: a delay measured
in action ticks. The eventual slow-stacking weapon coating is a separate
project; do not import its point thresholds or movement restrictions here.
Owner-approved rules (2026-09-22): positive direct hits only, one pending delay with no
stacking/refresh. It waits for the next otherwise-ready attack, including its
normal cooldown and existing action locks; it does not consume a movement turn.

## Banshee: Ectoplasm, no current armor

Banshees are spirits. Replace their hide drop with **Ectoplasm**, currently an
unused collectible reserved for possible future spirit armor. Hide Banshee
armor and remove its player-facing production paths (tanning and crafting),
rather than continuing to present it as an available leather set.

Current implementation to change: hide 3334, leather 3362, armor 3363–3367.
Do not renumber or repurpose old armor IDs, delete account holdings, or invent
a new grandfathering/trading rule without a migration decision. Ectoplasm's
ID, quantity, visuals and treatment of existing Banshee hides/leathers still
need an implementation decision. Frozen Tear remains unchanged. The owner
has not separately changed the existing bones rule in this instruction.

After this and the already selected giant/ogre retirements, the target is
**26 obtainable leather/carapace families**. Tier 4 retains baby dragon and
Ugthanki, so every tier 1–11 still has representation. Runtime currently
retains 32 craftable families until these removals are implemented.

## Terror dog: Ferocious

Enhanced Bear's Maul: the owner approved **two 75% melee hits**, nominally
150% combined before rounding and downstream settlement. Retain Bear's
five-piece, melee-only eligibility and positive-hit/surviving-target checks.
Use its existing rounding behavior with the improved 0.75 factor. This is
implemented as the approved design.

Verified current Bear's Maul baseline:

- Requires all five matching Bear armor pieces.
- Primary melee damage is scaled to `floor(damage * 0.60)`.
- When that hit is positive and the target survives, its second-hit callback
  receives the same damage. No additional random proc/accuracy roll is made
  by `BearMaulSecondHit`.
- Design shorthand: **two 60% hits**, nominally 120% combined before integer
  rounding and downstream damage settlement. Small hits can round to zero.
- References: `Player.applyBearMaulDamage`, `PlayerMeleeDamageBuff`,
  `BearMaulSecondHit`, and melee-event auxiliary-damage settlement.

Ferocious's 75% values are now confirmed. Do not add an independent random
proc chance or broaden the effect to ranged/magic without a new decision.

## Ugthanki: Storage Hump

Food heals **20% more**. Round the boosted healing to the nearest whole HP,
not always upward (`ceil`). For example, base food healing 6 becomes 7
(`6 * 1.20 = 7.2`), while 8 becomes 10 (`8 * 1.20 = 9.6`). This records
"rounded properly, not up" as ordinary nearest-integer rounding, not a
requirement to always round down.

This is a food-healing bonus, not general lifesteal, regeneration or potion
healing. Normal maximum-HP behavior remains unchanged. Owner-approved stacking
(2026-09-22):
add the Nature food bonus to the 20%, then round the total once. Ordinary
food, multi-bite food through the normal eating path, sweet fruit and kebabs
use this rule. Without the set, pre-existing food behavior is unchanged.

## Naga: Cold Blooded

Take **20% less damage from ice and fire magic**. The approved categories are
ice and fire, not all magic or a blanket classification of every water spell
as ice. This mitigates qualifying damage; it does not grant debuff immunity.

Use semantic elemental tags carried by the damage/spell source, not projectile
appearance or display-name matching. Enemy spell-tag coverage is uncertain:
the owner believes ice tagging exists but has not confirmed all spell paths.
Record an [element-tag audit](../in-progress-work-plans/effect-standardization-follow-up.md#enemy-spell-element-tags)
instead of asserting coverage. Verify the paths used by this bonus as part
of implementation; the comprehensive audit remains a follow-up.

Implemented in the common tagged elemental mitigation path, after existing
elemental resistance, using `ceil(damage * 0.80)` (minimum 1 for a positive
incoming hit, matching existing resistance rounding). Ordinary ice NPCs now
carry explicit ICE rather than WATER, and player ice/fire projectile sources
carry their element into impacts. Tagged projectile splash shares that path.
Untagged custom damage/burns/dragonbreath are not guessed to be eligible; the
comprehensive producer/secondary/DOT audit remains deferred.

## Remaining design and sequencing

The six retained new families are giant frog, naga, terror dog, bloodveld,
dark beast and Ugthanki. **All six now have set-bonus concepts determined.**
Runtime implementation and item descriptions now cover these six plus the
carapace replacement; the linked implementation note records defaults and
verification separately from owner-approved concepts. Banshee withdrawal,
old-family retirement and optional boss tasks remain separate work. The
broader [effect standardization follow-up](../in-progress-work-plans/effect-standardization-follow-up.md)
remains a post-tower reminder, explicitly including cleansing and Slow.
