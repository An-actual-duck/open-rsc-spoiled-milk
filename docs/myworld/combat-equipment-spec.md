# MyWorld Combat Equipment Spec

This document is the working source of truth for how combat stats are intended
to function in MyWorld while the equipment and NPC audit is underway.

It exists to replace fallback assumptions with explicit rules.

## Audit Scope

The audit must cover both:

- player equipment stat behavior
- NPC combat stat profiles and resistance splits

The goal is to verify that migrated code still matches the intended MyWorld
combat model rather than legacy OpenRSC behavior.

## Core Combat Stat Model

The active combat model uses separate offense and defense stats.

### Offense

- `Melee Offense`
- `Ranged Offense`
- `Magic Offense`

### Defense

- `Melee Defense`
- `Ranged Defense`
- `Magic Defense`

Equipment and NPCs should be evaluated against these explicit combat stats.

Legacy item fields and fallback derivation rules should not be treated as
authoritative design by themselves. They are compatibility inputs unless
explicitly confirmed by this spec.

## Armor Allocation Rule

Armor pieces allocate defense points based on:

- the item's tier
- the number of base resources used to craft that item

### Total Defense Points

The total defense budget for an armor piece is:

- `tier * resource_cost`

Example:

- a tier `2` armor piece
- that costs `4` resources
- gets `8` total defense points to allocate

## Armor Family Budget Multipliers

After the base `tier * resource_cost` budget is calculated, apply the armor
family multiplier:

- `Metal`: `1.0x`
- `Leather`: `0.9x`, rounded up
- `Cloth`: `0.6x`, rounded up

### Example

For a chest piece costing `4` resources:

- tier `1` metal budget: `4`
- tier `10` metal budget: `40`
- tier `10` leather budget: `ceil(40 * 0.9) = 36`
- tier `10` cloth budget: `ceil(40 * 0.6) = 24`

## Metal Armor Rule

Metal armor uses the full base defense budget and a fixed split:

- `75%` melee defense
- `25%` ranged defense
- `0%` magic defense

### Example

For a tier `2` item costing `4` resources:

- total defense budget: `8`
- melee defense: `6`
- ranged defense: `2`
- magic defense: `0`

Rounding should preserve the total defense budget.

## Leather Armor Rule

Leather uses the base defense budget with the `0.9x` multiplier applied and
rounded up.

Leather defense splits are determined by the source creature the material came
from. The armor should inherit the creature's defensive profile as closely as
possible using simple ratios.

### Leather Split Rule

- convert the source creature's defense profile into a melee/ranged/magic ratio
- allocate the armor piece's total defense budget by that ratio
- if the split is uneven, assign remainder points in this order:
  - melee first
  - ranged second
  - magic third

### Example: Even Distribution

For a tier `2` item costing `4` resources:

- base defense budget: `8`
- leather defense budget: `ceil(8 * 0.9) = 8`
- source creature has even melee/ranged/magic defenses
- target split is approximately `2.67 / 2.67 / 2.67`

Remainder handling order:

- melee gets first extra point
- ranged gets second extra point
- magic gets what remains

Final result:

- melee defense: `3`
- ranged defense: `3`
- magic defense: `2`

## Magic Armor Rule

Magic armor will follow the same general point-allocation principles, but its
behavior is tied to Enchanting and altar-family design.

Cloth/robe armor is the active magic-armor model.

### Cloth / Robe Direction

- cloth magic armor expands to five pieces:
  - hat
  - top
  - bottom
  - gloves
  - boots
- unenchanted base wool robes are the neutral baseline appearance and remain
  white
- robe enchanting remains altar-based
- robe tier controls defensive strength
- altar family controls rune-preservation identity, not defensive split

Cloth should have:

- the lowest overall defense budget of the three armor families
- the easiest access to magic defense
- effective but clearly lower raw defense than metal
- rune-cost negation as the reason to use it over heavier armor

Cloth must not become the strongest overall armor type.

### Cloth / Robe Budget Rule

Cloth uses the family-level `0.6x` multiplier as the broader balance target,
but unenchanted wool clothing uses a fixed tier-1 baseline floor that must not
drop below one defense point per resource cost.

- hat baseline magic defense: `1`
- top baseline magic defense: `4`
- bottom baseline magic defense: `3`
- gloves baseline magic defense: follows the standard glove recipe/resource
  cost once wool gloves are added
- boots baseline magic defense: follows the standard boot recipe/resource cost
  once wool boots are added

Enchanted wool clothing follows the same tier path as other enchanted gear.
All active cloth lines are magic-defense-only in current item data, but their
budget conversion is not yet uniform:

- Life cloth uses the intended `ceil(tier x resource cost x 0.6)` budget,
  subject to the baseline floor
- existing Air through Soul cloth rows retain the prior full tier-scaled
  magic-defense values in the current data
- tier `1` enchanted cloth must match the unenchanted wool baseline for that
  slot
- baseline magic defense is always preserved as a floor
- altar identity must not restructure cloth defense into melee or ranged

This retires the current altar-based defense redistribution model. Air, Water,
and Earth cloth no longer add ranged or melee defense. Normalizing all older
cloth rows to the Life budget is a balance change, not a documentation fix,
and must be decided separately.

### Cloth Rune Preservation

Each enchanted cloth armor piece increases the chance to negate the cost of its
matching rune by `10%`.

Matching enchanted staves increase the same chance by `50%`.

A player wearing all five matching enchanted cloth pieces plus the matching
enchanted staff has a `100%` chance to preserve that rune whenever a spell or
other rune-consuming action spends it.

Rune preservation is altar-specific:

- Air cloth preserves air runes
- Fire cloth preserves fire runes
- Life cloth preserves life runes
- mixed cloth only helps with the rune matching each equipped piece

Altar enchanting uses two independent level gates:

- altar gate: the player must meet the altar's Enchanting requirement
- item gate: the player must meet the target item's tier requirement

For cloth upgrades, the item gate is the target cloth tier. Each upgrade only
advances one tier. The current intended resource rule is documented in
`altar-enchantment-and-conversion-plan.md`: no metal bars, only
`target tier x 100` matching altar runes.

For staves, the item gate follows the ten-step wood staff curve from level `1`
through `70`. The current intended resource rule is `staff tier x 200`
matching altar runes.

For jewelry, the item gate follows the five-step gem curve shown in the
Enchanting guide. The current intended resource rule is `gem tier x 50`
matching altar runes.

Necklaces no longer provide universal rune preservation. Enchanted cloth and
matching staves own that role; necklaces now use altar-specific effects
documented in `jewelry-and-retired-robe-effects.md`.

### Cloth / Robe Examples

For a robe top costing `4` resources:

- tier `1` base budget: `4`
- unenchanted tier `1` robe top result:
  - magic defense: `4`

For a tier `2` enchanted robe top:

- raw budget before cloth multiplier: `8`
- cloth budget: `ceil(8 x 0.6) = 5`
- baseline magic-defense floor: `4`
- final defense:
  - magic defense: `5`
  - melee defense: `0`
  - ranged defense: `0`

For a tier `10` enchanted robe top:

- raw budget before cloth multiplier: `40`
- cloth budget: `ceil(40 x 0.6) = 24`
- final defense:
  - magic defense: `24`
  - melee defense: `0`
  - ranged defense: `0`

### Retired Robe Effects

Robe-driven special effects have been retired. Cloth armor now focuses on
magic defense and matching-rune preservation. Altar jewelry owns the retained
special-effect identities, including the live Nature poison-decay behavior;
the authoritative effect list and formulas are in
`jewelry-and-retired-robe-effects.md`.

### Cloth / Robe Visual Direction

Enchanted robes should use altar-themed visual identity rather than player-dyed
variants.

Current intended altar palette:

- `Air`: sky blue
- `Water`: deep blue
- `Earth`: brown
- `Fire`: red
- `Mind`: brown-red
- `Body`: tan
- `Cosmic`: yellow
- `Chaos`: orange
- `Nature`: green
- `Law`: teal
- `Death`: black
- `Blood`: dark red
- `Soul`: silver-blue

This replaces the idea of preserving player-applied robe dyes through
enchanting. Robes are not player-dyeable: baseline wool robes remain white, and
altar enchantment is the only robe recolor path.

Capes are the only player-dyeable cloth equipment. The craftable wool cape is
named `White Cape` so it is not mistaken for an enchantable wool robe. Dyeable
capes can be dyed into any supported dye color.

`Black Cape` is a fixed-color exception. It remains available from its own
source, but cannot be dyed into another cape and cannot be created by dyeing.
Holiday/event cosmetics may keep their own dye interactions when they are purely
cosmetic.

Gnome clothing remains a cloth outlier. It should use wool-equivalent defensive
values, keep its gnome appearance, and never enter the enchantment system.

## Relative Armor Family Targets

The three armor families should feel distinct:

### Metal

- highest raw defense values
- lowest coverage
- primarily focused on melee defense

### Leather

- broader defense coverage than metal
- total defense values should cap slightly below metal at higher tiers
- uses the same source-creature split rules described above
- receives a small total-budget penalty compared to metal

### Leather Budget Penalty

Leather should receive a `0.9x` budget multiplier compared to the equivalent
metal budget.

This penalty should be:

- applied after the base `tier * resource_cost` budget is calculated
- rounded up so early tiers do not collapse too hard
- felt more noticeably at later tiers

### Cloth

- lowest defense values
- easiest access to magic defense
- special effects from enchantment families justify its use

No armor family should be the universal best choice.

## God-Aligned Knight Armor

Black, white, and grey knight armor are implemented as god-aligned steel
counterparts rather than separate power tiers.

### God Mapping

- `Black Knight` equipment: Zamorak
- `White Knight` equipment: Saradomin
- `Grey Knight` equipment: Guthix

### Armor Stats

All three knight armor lines should use steel-equivalent core armor stats.

The intent is:

- black, white, and grey armor are prayer/god identity variants of steel
- they should not invalidate higher metal tiers
- their prayer bonuses are the differentiator, not raw armor scaling

Grey knight equipment should visually stay close to steel armor, with enough
Guthix-blessed identity to distinguish it from ordinary steel.

### Prayer Bonus Gate

Any prayer bonus or god-specific benefit from black, white, or grey knight
equipment should only apply while the player is worshipping the matching god.

This means:

- Zamorak bonuses require active Zamorak worship
- Saradomin bonuses require active Saradomin worship
- Guthix bonuses require active Guthix worship

### Altar Conversion

Steel equipment converts into the matching god counterpart at the
appropriate god altar.

Current route:

- take ordinary steel equipment to a god altar
- use the steel equipment on that altar
- convert it into the matching god equipment line

Conversion has no resource cost, but it is steel-only and one-way. Ordinary
steel equipment can be blessed into the matching god line at the matching god
altar. Black, white, and grey knight equipment cannot be converted into each
other.

### World/NPC Sources

- Existing `White Knight` NPCs remain the Saradomin direct equipment source;
  their table covers the supported white weapon and armor outcomes.
- Dedicated `Grey Knight` NPC `836` is the Guthix direct equipment source,
  with four first-pass MyWorld locations in the Taverley druid/white-wolf
  combat region.
- Existing Black Knights remain available as combat identities, while direct
  black equipment drops currently come from `Dark Warrior` `199` rather than
  the Black Knight shared table; altar conversion is also available.

## Armor Weapon-Power Penalties

Armor families impose flat penalties to the weapon power of one opposing combat
type.

The penalty model is:

- `Metal` armor lowers `Ranged Power`.
- `Leather/carapace` armor lowers `Magic Power`.
- `Cloth/robe` armor lowers `Melee Power`.

Only major armor slots count:

- head
- chest
- legs

The ignored slots are:

- gloves
- boots
- back

Each qualifying piece applies `-8` weapon power to the affected combat type.
This penalty is flat per piece and does not scale by armor tier, so a low-tier
piece such as copper is just as detrimental to its affected weapon power as a
high-tier piece such as rune.

The combat offense calculation clamps equipment-side style power at `0`, so
armor penalties can reduce or null out weapon/staff/bow contribution but do
not directly subtract from the player's base skill level. Display/debug views
may show the raw negative net equipment value so the penalty remains visible.

## Leather And Carapace Set Bonus Source Of Truth

Leather and carapace armor use full-set bonuses tied to the source creature.

### Activation Rules

- bonuses only activate when all matching `5` armor pieces are worn
- the matching pieces are `coif`, `gloves`, `boots`, `chaps`, and `cuirass`
- mixed sets do not activate any set bonus

### Passive Vs Proc Rules

- some sets are passive and are always active while the full set is worn
- some sets are proc-based and roll on each qualifying attack
- unless noted otherwise, proc chance is `20%` per qualifying attack
- trait damage uses the trait's own stated max hit
- weapon power does not increase trait damage

### Attack Counter Rules

- temporary buffs on the player use `next 5 attacks`
- temporary debuffs on the target use `next 5 attacks` of that target
- debuff attack counters are consumed by the debuffed enemy's attacks, not by
  the attacking player
- buff attack counters are consumed by the buffed player's own attacks
- this same attack-counter model should also be used for spell-line debuffs in
  place of timer-based behavior

### Poison Rules

- poison tracks current poison power and max poison power separately
- every `10` poison power deals `1` poison damage on a poison tick
- poison ticks every `8` server ticks and drains `3` poison power per tick
- poison reapplications add their applied poison power up to the combined max
  poison power, without resetting the existing poison tick timer
- weapon poison starts at `100%` proc chance against a target, drops to `50%`
  after the first successful proc, then drops by `10%` per successful proc to
  a `20%` floor
- armor poison starts at `50%` proc chance against a target and drops by `10%`
  per successful proc to a `10%` floor
- each poison source recharges after `5` failed proc attempts; armor recharges
  by `10%`, while weapon poison at `50%` jumps back to `100%`
- if weapon and armor poison both proc on the same attack, only the stronger
  applied poison power is added for that hit
- weapon max poison power and armor max poison power are additive
- dragon-breath armor traits keep their own source-specific proc rates; their
  poison is applied when the breath trait procs

### Elemental Trait Rules

- if a trait deals elemental damage, it also applies the corresponding elemental
  debuff for the specified tier
- elemental trait debuffs follow the same stacking and strongest-wins rules as
  normal spell-line debuffs

### Set Bonus Definitions

- `Cow` -> `Hardy`
  - passive
  - `+5` max `Hits`

- `Goblin` -> `Enraged`
  - proc-based
  - `20%` chance per attack
  - grants a temporary `10%` attack-speed increase
  - buff lasts for the player's next `5` attacks

- `Unicorn` -> `Divine`
  - passive
  - `+10` prayer points while worshiping `Saradomin`

- `Black Unicorn` -> `Unholy`
  - passive
  - `+10` prayer points while worshiping `Zamorak`

- `Bear` -> `Intimidate`
  - proc-based
  - `20%` chance per attack
  - lowers the opponent's attack speed by `10%`
  - debuff lasts for the opponent's next `5` attacks

- `Scorpion` -> `Tail Sting`
  - proc-based
  - `20%` chance per attack
  - melee attacks only
  - applies `1` poison

- `Wolf` -> `Spirit Wolf Companion`
  - passive full-set summon
  - wearing all `5` matching pieces summons an invulnerable spirit wolf
  - no resource cost and no Summoning skill scaling
  - does not absorb player damage
  - attacks the player's current target for up to max hit `3`

- `Spider` -> `Spitting Venom`
  - proc-based
  - `20%` chance per attack
  - ranged attacks only
  - applies `1` poison

- `Giant` -> `Brute Force`
  - proc-based
  - `20%` chance per attack
  - melee damage rolls get a `1.1x` multiplier
  - this multiplier is applied after the damage roll is made

- `Ogre`
  - `Staggering Blow`
  - proc-based
  - `20%` chance per attack
  - reduces the opponent's next hit to `0`
  - once triggered, cannot proc again until either:
    - the player's next `5` attacks have passed
    - or `10` seconds have elapsed

- `Baby Dragon` -> `Blow Smoke`
  - proc-based
  - `20%` chance per attack
  - lowers the opponent's accuracy by `10%`
  - this means the opponent's attack rolls are skewed downward
  - debuff lasts for the opponent's next `5` attacks

- `Magic Spider` -> `Mystic Venom`
  - proc-based
  - `20%` chance per attack
  - magic attacks only
  - applies `2` poison

- `Moss Giant` -> `Terra Brute Force`
  - proc-based
  - `20%` chance per attack
  - `1.1x` earth magic damage
  - `1.1x` melee damage

- `Ice Giant` -> `Icy Brute Force`
  - proc-based
  - `20%` chance per attack
  - `1.1x` water magic damage
  - `1.1x` melee damage

- `Demon` -> `Hell's Fire`
  - proc-based
  - `20%` chance per attack
  - deals fire magic damage up to max hit `8`
  - applies fire debuff tier `2`

- `Hellhound` -> `Spirit Hellhound Companion`
  - passive full-set summon
  - wearing all `5` matching pieces summons an invulnerable spirit hellhound
  - no resource cost and no Summoning skill scaling
  - does not absorb player damage
  - attacks the player's current target for up to max hit `6`
  - has a `5%` chance per attack to proc `Hell's Fire`

- `Fire Giant` -> `Fiery Brute Force`
  - proc-based
  - `20%` chance per attack
  - `1.1x` fire magic damage
  - `1.1x` melee damage

- `Blue Dragon` -> `Icy Dragon Breath`
  - proc-based
  - `20%` chance per attack
  - dragon breath attack
  - no combat type
  - ignores defense
  - deals water damage up to max hit `10`
  - applies water debuff tier `2`

- `Earth Dragon` -> `Terra Dragon Breath`
  - proc-based
  - `20%` chance per attack
  - this is the renamed former `Dragon` line
  - dragon breath attack
  - no combat type
  - ignores defense
  - deals earth damage up to max hit `10`
  - applies earth debuff tier `2`

- `Red Dragon` -> `Fiery Dragon Breath`
  - proc-based
  - `20%` chance per attack
  - dragon breath attack
  - no combat type
  - ignores defense
  - deals fire damage up to max hit `10`
  - applies fire debuff tier `2`

- `Black Demon` -> `Hell's Blaze`
  - proc-based
  - `20%` chance per attack
  - deals fire magic damage up to max hit `12`
  - applies fire debuff tier `3`

- `Black Dragon` -> `Poisonous Dragon Breath`
  - proc-based
  - `20%` chance per attack
  - dragon breath attack
  - no combat type
  - ignores defense
  - deals non-elemental damage up to max hit `10`
  - applies `3` poison

- `Balrog` -> `Hell's Inferno`
  - proc-based
  - `20%` chance per attack
  - deals fire magic damage up to max hit `18`
  - applies fire debuff tier `4`

- `King Black Dragon` -> `True Dragon's Breath`
  - proc-based
  - `60%` chance per attack
  - dragon breath attack
  - no combat type
  - ignores defense
  - randomly selects `water`, `earth`, or `fire` damage when it procs
  - deals up to max hit `10`
  - applies `4` poison regardless of chosen element

### Examine Text Rules

Each armor piece should explain:

- what creature material it was made from
- that `5` matching pieces are required
- the trait name
- the trait's actual effect in plain language

Examples:

- `Boots made from cow leather. When wearing 5 matching Cow leather armor
  pieces you will get the Hardy trait. Hardy increases your max hits by 5`
- `Chaps made from demon leather. When wearing 5 matching Demon leather armor
  pieces you will get the Hell's Fire trait. Hell's Fire has a 20% chance to
  proc with each attack dealing up to 8 magic fire damage to your opponent`

### Debuff Stacking Rules

Debuffs should stack by `source category`, not by `player count`.

That means:

- different source categories can stack together
- duplicate applications from the same source category do not stack
- multiple players using the same debuff source still produce only one active
  instance of that source on the target

Examples:

- a leather-set debuff and a spell debuff may both be active together
- two players wearing the same leather set do not stack two copies of that set
  debuff
- two players casting the same spell-line debuff do not stack two copies of
  that spell debuff

### Spell-Line Debuff Rules

Spell debuffs within the same line should behave as a single escalating effect.

Rules:

- stronger spell tiers replace weaker spell tiers within the same line
- weaker spell tiers do not reduce or overwrite a stronger active debuff
- a weaker application should still refresh the attack counter on that spell
  line
- if a stronger application lands while a weaker version is active, the stronger
  effect replaces it immediately

The intended result is:

- different source categories may stack
- duplicate sources do not stack
- only the strongest active debuff in a spell line determines the live effect
  strength

## Immediate Audit Goals

The first audit pass should answer the following:

1. Which player equipables still rely on fallback name-based combat-stat
   derivation?
2. Which metal armor pieces are missing explicit melee/ranged/magic defense
   values under the intended split model?
3. Which leather armor pieces are missing explicit melee/ranged/magic defense
   values under the source-creature model?
4. Which items are incorrectly granting offense when they should be defense
   only?
5. Which NPCs lost intended melee/ranged/magic defense distributions during
   migration?
6. Which enchanting item lines still have complete altar coverage, valid
   product mappings, and live runtime effect hooks?
7. Which enchanted items are still relying on fallback combat-stat derivation
   instead of explicit intended behavior?
8. Which leather/carapace families are active end to end and ready for full-set
   bonus implementation?

## Current Audit Status

The following live gaps have been corrected during this audit pass:

- standard `plate mail legs` now use explicit melee/ranged defense values
  instead of legacy `armourBonus` fallback
- early leather `coif` and `chaps` pieces that were still picking up ranged
  defense through name matching now have explicit ranged-defense values
- blessed staffs now have explicit magic-offense values instead of relying on
  `magicBonus`-derived fallback

The remaining fallback-heavy bucket is now mostly made up of:

- capes and other low-priority defensive cosmetics
- legacy one-off jewelry and novelty items
- ironman/decorative holdover armor pieces
- older wizard/gnome/holiday costume items

Those items should still be reviewed, but they no longer overlap much with the
core active metal/leather/robe/staff lines.

## Guardrail

During the audit, do not treat current runtime output as proof of correctness.

If current runtime behavior conflicts with this document, the document is the
intended model unless it is later revised explicitly.
