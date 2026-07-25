# Prayer Devotion Equipment Plan

This document pins the next prayer-equipment direction before implementation.
It supersedes older god-knight conversion rules where they conflict.

## Goals

- Make god gear a combat-focused equipment track.
- Make devotion the long-term progression stat for blessed combat gear.
- Keep blessing costs predictable by deriving requirements from production
  resource cost.
- Keep existing offerings as the primary devotion source.
- Let blessings grant Worship XP without inflating devotion directly.

## God Lines

- Saradomin: white/blessed-by-Saradomin equipment.
- Zamorak: black/blessed-by-Zamorak equipment.
- Guthix: grey/blessed-by-Guthix equipment.

Prayer bonus and god-specific combat benefits only apply while the player is
worshipping the matching god.

## Devotion Rules

- Maximum devotion is `1000` per god.
- Offering XP keeps its current devotion bonus behavior.
- Blessing an item grants no devotion.
- Blessing Worship XP is increased by `1%` per current devotion with the god
  performing the blessing.
- Devotion is clamped from `0` to `1000`.

## Devotion Requirements

General rule:

- `devotion requirement = resource cost * 50`

Resource cost means the production material cost of the equivalent base item.

Examples:

- plate body: `4` bars -> `200` devotion
- plate legs: `3` bars -> `150` devotion
- full helm: `2` bars -> `100` devotion
- gauntlets: `2` bars -> `100` devotion
- greaves: `2` bars -> `100` devotion
- one-bar weapon: `1` bar -> `50` devotion
- two-bar weapon: `2` bars -> `100` devotion
- three-bar weapon: `3` bars -> `150` devotion

This replaces the earlier "halve current requirements" rule anywhere the two
conflict.

## Blessing XP

Blessing any supported item grants Worship XP equal to the production XP of the
base item being blessed.

Examples:

- blessing a steel plate body grants Worship XP equal to the Smithing XP for
  making a steel plate body
- blessing a wool robe top grants Worship XP equal to the Crafting XP for making
  a wool robe top
- blessing a wood staff grants Worship XP equal to the Crafting/Woodcraft XP for
  making that staff

If an item has no clear production equivalent, implementation should add an
explicit mapping rather than guessing.

## Supported Blessed Gear

God gear is combat-focused. Do not add or expose god-aligned skilling tools.

Supported combat families:

- metal weapons
- metal armor
- paladin shields
- wool robe armor
- wood staves

Unsupported families:

- hatchets
- pickaxes
- shears
- fishing rods
- other skilling tools

Existing god-aligned tools should be hidden or retired from normal acquisition.
Hatchets were already hidden in an earlier pass. If god pickaxes or other god
tools exist, they should also be hidden.

## Metal God Gear

Metal god gear is the melee/ranged plus Prayer armor and weapon path.

Supported metal armor slots:

- full helm
- plate body
- plate legs
- paladin shield
- gauntlets
- greaves

Supported metal weapons:

- dagger
- short sword
- long sword
- two-handed sword
- scimitar
- battle axe
- mace
- spear
- crossbow if present in the god equipment family

Excluded metal equipment:

- chain bodies
- medium helms
- square shields
- plate tops
- skirts
- throwing weapons unless explicitly promoted later
- hatchets and all other tools

## Wool God Gear

Wool god gear is the magic plus Prayer armor path.

Supported slots:

- hat
- robe top
- robe bottom
- gloves
- boots

Ordinary wool armor can be blessed into the matching god counterpart at the
matching altar when devotion requirements are met.

## Blessed Staves

Blessed staves need all three god lines.

Existing generic blessed staff item IDs `2228-2237` are the migration path for
current player-owned items. Keep those IDs usable and assign them to Zamorak
rather than retiring them or turning them into unobtainium.

Naming convention:

- `Oak staff blessed by Guthix`
- `Oak staff blessed by Saradomin`
- `Oak staff blessed by Zamorak`

Apply the same naming pattern across all wood tiers.

Blessed staves require devotion using the same resource-cost rule and grant
Worship XP equal to their staff production XP equivalent.

## God Relic Devotion Rewards

The Mage Arena god capes and god staves are moving out of Mage Arena-centered
reward logic and into devotion-based god relic rewards. This section is a
summary of the broader
[`god-relic-reward-plan.md`](god-relic-reward-plan.md), which expands this
from god staves alone into the full god relic reward system.

Reward rule:

- The player must be worshipping/aligned with the matching god.
- The player must have Worship level `80`.
- The player must have at least `800` devotion with that god.
- The player prays at the matching god altar.
- The altar opens god dialogue where the player can request a holy relic.
- The god awards one random unclaimed relic from the matching god's relic pool.
- Claiming a relic removes `400` devotion from that god.

Relic pools include:

- god cape
- god staff
- god mace
- god paladin shield
- god crossbow

This should be a reward claim, not part of the Mage Arena reward flow. Duplicate
relic claims are not allowed; replacement/reclaim behavior is still open.

Late-game extension:

- God excommunication lets a player permanently dedicate themselves to one god
  by destroying another god's relic at their chosen god's altar.
- The player is permanently alignment-locked to the chosen god, jumps to `1000`
  devotion, gains a `2000` devotion cap, loses all other-god relics, and gains
  remaining chosen-god relics.
- The excommunicated god is permanently locked at `-1000` devotion, while the
  untouched god is permanently locked at `0`.
- Full rules and open decisions are tracked in
  [`god-relic-reward-plan.md`](god-relic-reward-plan.md).

## Devotion-Based Scaling

Blessed weapons and armor scale with devotion.

Combat stat growth begins at devotion `250`. The highest planned blessing
requirement is `400`, so growth now starts before the final blessing unlock and
keeps the early devotion climb from feeling flat.

At devotion `1000`, blessed combat gear should land roughly around tier-9
power or defense. All blessed weapons and armor should grow at the same
devotion rate from `250` to `1000`, following roughly a tier-1 to tier-9
combat path.

Scaling applies to:

- blessed metal weapons
- blessed metal armor
- blessed paladin shields
- blessed wool armor
- blessed staves, for combat stats only

Prayer bonus scaling is separate:

- baseline Prayer bonus is always active when the player wears matching
  blessed gear while worshipping that god, even at `0` devotion
- baseline Prayer bonus is `+1` per equivalent resource cost
- devotion can add up to `+10` additional Prayer bonus by devotion `1000`
- a four-resource chest piece therefore reaches `+14` Prayer bonus at maximum
  devotion
- equipment that already has a natural Prayer bonus keeps the equivalent base
  bonus and adds the blessed devotion bonus on top
- example: a god mace keeps the normal steel-equivalent mace Prayer bonus and
  also gains the blessed bonus from its resource equivalency
- blessed staves do not use devotion Prayer-bonus scaling; their Prayer bonus
  mirrors wood tier, so tier-1 wood gives `+1`, tier-2 gives `+2`, and so on

The likely implementation shape is runtime stat scaling from the player's
current devotion, rather than duplicating static item definitions for every
devotion level.

Open tuning decision:

- define exact tier-1 to tier-9 combat stat curves for metal weapons, metal
  armor, wool armor, paladin shields, and staves.
- confirm resource equivalency for edge-case weapons where the base production
  cost is unclear.

## Destroy Opposing Blessed Object

New altar mechanic:

- Player uses a blessed item from an opposing god on the altar of the god they
  currently worship.
- The blessed object is destroyed.
- Player gains Worship XP equal to `5x` the production-equivalent XP for the
  destroyed object.
- Player gains `1` devotion per equivalent resource cost with the currently
  worshipped god.
- Player loses `1` devotion per equivalent resource cost with the god tied to
  the destroyed object.
- Blessed god symbols are a special lightweight case: destroying one gives a
  `0.5` devotion gain/loss instead of using the normal resource-cost rule.
- Devotion changes are clamped between `-1000` and `1000`.

Example:

- Player worships Saradomin.
- Player uses a black sword on a Saradomin altar.
- The sword is destroyed.
- Saradomin devotion increases.
- Zamorak devotion decreases.
- Player gains Worship XP.

These values are intentionally explicit starter tuning and can be adjusted
after testing.

## Implementation Order

1. Add shared constants and helpers:
   - max devotion `1000`
   - devotion requirement from resource cost
   - blessing XP multiplier
   - god identity detection for blessed items
2. Convert existing steel-to-god blessing logic to the new requirement and XP
   rules.
3. Remove devotion gain from blessing.
4. Add devotion requirements to blessed staves.
5. Add all three god variants for blessed staves and apply the new naming
   convention.
6. Add wool armor blessing outputs, including gloves and boots.
7. Hide or retire any remaining god-aligned tools from normal acquisition.
8. Add devotion-based combat scaling for blessed weapons and armor.
9. Add the destroy-opposing-blessed-object altar mechanic.
10. Update Prayer guide, Devotion guide text, altar messages, and examine text.

## Validation Targets

Add or update tests for:

- devotion cap and clamping
- devotion requirement equals `resource cost * 50`
- blessing grants Worship XP and no devotion
- blessing XP scales by `1%` per devotion
- staves require devotion and exist for all three gods
- wool hat/top/bottom/gloves/boots blessing coverage
- god tools are not obtainable through normal blessing/drop/shop paths
- blessed item bonuses only apply for the matching worshipped god
- devotion scaling reaches the intended tier-9 target at devotion `1000`
- opposing blessed item destruction changes both devotion tracks correctly
