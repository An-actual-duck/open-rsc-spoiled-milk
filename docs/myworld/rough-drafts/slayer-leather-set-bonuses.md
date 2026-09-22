# Slayer leather set bonuses and cleansing direction

Updated: 2026-09-21. Owner-approved design directions; runtime implementation
pending. This document supersedes earlier undecided-theme notes and the plan
to retain Banshee hide armor. It does not claim these effects are active.

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

Before implementation, settle numerical tier scaling, full-set versus
per-piece activation, mixed-set behavior, and how all cleansing sources stack.
Recommended starting architecture: one shared poison-clearance calculation,
without shortening the damaging poison event interval. This is a proposal,
not approval of an additive formula or particular values.

## Dark beast: Electrically Charged

- Taking damage with the armor equipped charges a hidden meter.
- Each qualifying hit adds a fresh **1d3** roll, not the damage amount.
- At **30 charge**, trigger a **tier-2 thunder spell** against all enemies
  within **2 tiles** of the player.
- This is a retaliation effect, not the monster's marked-target lightning
  mechanic and not a Static discharge wipe interaction.

Still settle: whether only direct hits qualify or damage-over-time also counts;
reset versus remainder carry after discharge; charge retention on unequip,
death and logout; and the spell's offensive-stat source, accuracy/damage
calculation and valid enemy-target policy. No arbitrary damage value, free
PvP activation, or recursion between retaliatory effects is authorized here.

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
confirm that granularity before implementation if introducing an event cap.

## Giant frog: Sticky Skin

Every time an enemy hits the wearer, **10% chance** to delay that enemy's
**next attack by 1 tick**. This effect is attack delay, not a movement root,
full action lock, or the frog monster's Slimy Spit debuff.

Tie the implementation to the planned **Slow** vocabulary: a delay measured
in action ticks. The eventual slow-stacking weapon coating is a separate
project; do not import its point thresholds or movement restrictions here.
Still settle: whether a zero-damage hit qualifies, repeated-proc stacking or
immunity, and interaction with existing whip delays or other slows.

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

Enhanced Bear's Maul: a second hit. Exact improved values await the owner.

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

Discussion proposal only: Ferocious could use two 75% hits (nominally 150%)
with Bear's existing melee-only eligibility. Do not implement these proposed
values without confirmation, or add an independent proc chance unrequested.

## Remaining design and sequencing

The six retained new families are giant frog, naga, terror dog, bloodveld,
dark beast and Ugthanki. Naga and Ugthanki still need effect concepts; Terror
dog needs final numbers. The other three have the directions above, with
event/stacking boundaries to settle before implementation.

Document and resolve these bounded set rules before runtime changes. The
broader [effect standardization follow-up](../in-progress-work-plans/effect-standardization-follow-up.md)
remains a post-tower reminder, explicitly including cleansing and Slow.
