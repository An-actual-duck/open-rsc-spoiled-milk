# Cosmic combat luck implementation review

Work in progress, October31,2026. This replaces Cosmic robes' critical chance;
Zamorak hostile critical/Stagger/execution spells remain inactive. The active
Cleric roster remains17. No item identities, art, archives or protocol change.

Each equipped Cosmic piece contributes1percentage point per tier, capped50%.
For each reviewed named roll, evaluate the existing pure candidate first. A
successful luck check permits one more candidate, selecting higher damage or
mitigation, or success for a boolean check. No luck at0means no extra draw.
Guaranteed checks retain their original sampler but do not roll luck. A10%
check at50% luck becomes14.5%; a15% authored critical check becomes21.375%.
Normal maxima remain noncritical. There is no enabled modern critical chance
provider after this replacement; the generic authored resolver remains available.

## Source-family audit

| Family | Integration and ownership |
| --- | --- |
| Modern melee/ranged/thrown/Magic | Complete `CombatFormula.rollDamage` distribution, including existing biases, once per amount; source Player owns luck. Offhand and player-owned secondary callers share it. |
| Defense mitigation | Separate victim-owned integer mitigation candidate before subtraction; no second luck on final subtraction. |
| Legacy God spell amount | Actor-aware legacy amount entrypoint opts in; source-less helpers cannot infer equipment. |
| Elemental sword, Hell Blaze, weapon breath | Chance and random damage are distinct named rolls. Random animation/element choice remains unchanged. |
| Blue/Red/Earth/Baby/Elder armor | Existing eligibility first; pure chance and random damage candidates selected before one payload invocation. |
| Infernal Fire and Chaos chain | Chance/payload independently selected; callbacks once; chain checks per admitted hop, target selection and hop caps unchanged. |
| Whip, Sticky and pendant | Actual source/wearer owns chance; pendant random damage percent favors wearer. Existing immunity/eligibility runs first. Sticky charge accumulator1..3 is unchanged. |
| Poison weapon/armor | Original integer predicate selected before exactly one adaptive chance/failure-state mutation. Poisoned ammo integer1/8or1/50 checks separately opt in. Dragon armor breath poison checks also opt in. |
| Startle/Acid/Frostbite/Splinter/Guthix poison | Attack-triggered actual Player caster checks opt in; NPC/summon casters inherit no owner luck. |
| Recoil and Divine Retribution | Defender/wearer owns the beneficial chance; deterministic reflected amounts are not independently rerolled. |
| Goblin Tenacity | Explicit typed combat boundary opts in. Generic Skills, quest/environment and ordinary raw HP subtraction retain the original chance. Reviewed raw dragon-flame callers use a narrow legacy combat adapter, preserving settlement/death order. Poison's typed pre-settlement survival check opts in. |
| Giant Might, death amulet, Soul Ring | Beneficial combat check / random outgoing burst amount opts in. Soul Ring snapshots luck before reset/teleport; generic death-item loss is unchanged. |
| Combat capes and rune preservation | Ranged/throwing cape checks opt in. Magic cape and rune preservation only opt in for explicitly damaging spell context; utility/skilling casts do not, even while fighting. |

## Preserved boundaries

Luck never replays an attack, effect, damage settlement, resource removal, XP,
death or target selection. Launch-owned damage is already selected at launch;
later equipment changes do not rewrite flight receipts. Existing impact-owned
proc equipment checks retain their prior timing. Forced Stagger zero still
bypasses outgoing rolls/procs; non-corporeal nature admission is unchanged.

NPC damage distribution and summons never acquire their owner's robe luck.
Recurring hostile curse ticks, random healing quantities, loot, gathering,
production, random choice between effects/targets, and stateful armor charge
increments are excluded. Kolodion's NPC-only proc is unchanged. Source-less
legacy helpers remain source-less. The optional OSRS formula/profile (disabled
in the supported MyWorld configuration) retains its separate hit/cape mechanics;
this change does not redesign that profile or its Strength-cape critical label.

## Verification

Pending final native and broad regression receipts. Current meaningful gates:
semantic32, native ownership/formula/adaptive61, actual Stagger/launch171;
Java8 bounded server builds passed. These are interim results, not READY.
