# Herblaw Potion Rework Plan

This document is the new source of truth for the planned Herblaw and potion
direction. Existing potion names, recipes, and item IDs may be legacy or
compatibility leftovers until this plan is implemented.

## Goals

- Rebuild Herblaw from square one instead of patching around inherited
  RuneScape Classic potion behavior.
- Make potion effects feel useful for their full active window.
- Support the current MyWorld skill set, including newer skills such as
  Enchanting and Summoning.
- Keep potion families understandable by grouping related skills where that
  makes gameplay sense.

## Core Behavior Changes

### Stable Potion Strength

Current RuneScape-style boosts decay over time, which means the maximum benefit
only lasts briefly. MyWorld potions should instead keep their full effect for the
entire potion duration.

Planned rule:

- A potion applies its full listed bonus when consumed.
- The bonus remains at full strength until the effect expires.
- When the duration ends, the potion bonus is removed cleanly.
- The effect should not tick down one stat point at a time.

Open implementation detail:

- Decide whether potion bonuses should be tracked as timed modifiers separate
  from the player's current skill level, or whether existing boosted-level code
  should be adapted to suppress decay for potion-tagged boosts.

## General Tier Model

Most potions should be tiered by potency, not by changing what the potion does.
For example, `Potion of Brawn` and `Potion of Deftness` are equal concepts, but
each can exist from tier 1 through tier 6.

General rule:

- The herb determines potion tier.
- The secondary ingredient determines potion family/effect.
- Skipped herbs are reserved for special-purpose potions such as restores,
  immunities, poison, and antidotes.
- Quest potions should not be changed by this rework.
- Tiered potion names use explicit `v1` through `v6` suffixes, for example
  `Potion of Brawn v1`.
- Brew names use normal and `Strong` variants instead of numeric suffixes, for
  example `Skiller's Brew` and `Strong Skiller's Brew`.

### Standard Buff Tiers

| Tier | Herb | Potency | Duration |
| --- | --- | --- | --- |
| 1 | Guam leaf | 5% | 5 minutes |
| 2 | Tarromin | 8% | 8 minutes |
| 3 | Ranarr weed | 11% | 11 minutes |
| 4 | Avantoe | 14% | 14 minutes |
| 5 | Cadantine | 17% | 17 minutes |
| 6 | Torstol | 20% | 20 minutes |

Potency and duration currently follow `+3%` and `+3 minutes` per tier after
tier 1.

Skill buff rule:

- Potency applies directly to the player's current skill level as a temporary
  visible boost.
- Example: a player at `60/60` using a `5%` buff is treated as `63/60` while
  the potion is active.
- The boosted value should remain at full strength for the potion's full
  duration, then be removed cleanly when the duration ends.

### Standard Buff Secondaries

| Secondary ingredient | Potion family | Affected skills |
| --- | --- | --- |
| 10 Fish oil | Deftness | Ranged, Pickpocketing, Crafting, Agility, Fishing |
| Eye of newt | Insight | Magic, Enchanting, Summoning, Cooking, Worship |
| Limpwurt root | Brawn | Melee, Mining, Smithing, Woodcutting, Hits |

Example:

- `Guam leaf` + `10 Fish oil` = tier 1 `Potion of Deftness`
- `Tarromin` + `10 Fish oil` = tier 2 `Potion of Deftness`
- `Ranarr weed` + `10 Fish oil` = tier 3 `Potion of Deftness`
- `Avantoe` + `10 Fish oil` = tier 4 `Potion of Deftness`
- `Cadantine` + `10 Fish oil` = tier 5 `Potion of Deftness`
- `Torstol` + `10 Fish oil` = tier 6 `Potion of Deftness`

The same herb-tier pattern applies to `Eye of newt` for `Potion of Insight` and
`Limpwurt root` for `Potion of Brawn`.

### XP Boost Potions

XP boost potions are timed account-session effects for non-combat training.

### Skiller's Brew

Recipe:

- `Irit leaf` + `Snape grass`

Effect:

- Grants `20%` bonus XP to all non-combat skills.
- Lasts 30 minutes.
- Duration does not tick down while the player is logged out.

Excluded combat skills:

- Melee
- Magic
- Ranged
- Worship
- Hits
- Summoning

### Strong Skiller's Brew

Recipe:

- `Dwarf weed` + `5 Snape grass`

Effect:

- Grants `40%` bonus XP to all non-combat skills.
- Lasts 1 hour.
- Duration does not tick down while the player is logged out.

Excluded combat skills:

- Melee
- Magic
- Ranged
- Worship
- Hits
- Summoning

### Warrior's Brew

Recipe:

- `Irit leaf` + `White berries`

Effect:

- Grants `20%` bonus XP to combat skills.
- Lasts 30 minutes.
- Duration does not tick down while the player is logged out.

Affected combat skills:

- Melee
- Magic
- Ranged
- Worship
- Hits
- Summoning

### Strong Warrior's Brew

Recipe:

- `Dwarf weed` + `5 White berries`

Effect:

- Grants `40%` bonus XP to combat skills.
- Lasts 1 hour.
- Duration does not tick down while the player is logged out.

Affected combat skills:

- Melee
- Magic
- Ranged
- Worship
- Hits
- Summoning

Open questions:

- Should XP boost potions stack with equipment bonuses, events, or rested-style
  bonuses?
- Should the bonus apply to all XP drops during the duration, or only to actions
  started after drinking the potion?

### Weapon Poison Potion

Desired:

- Current recipe to preserve: `Unfinished Harralander potion` + `Ground blue dragon scale`.
- A potion used to apply poison to weapons that support poison.
- Only eligible weapons should accept poison.
- The potion is not primarily a drinkable buff.
- Preserve the existing weapon-poison application flow if it remains verified:
  weapon poison is used on eligible weapons or ammunition to create their
  poisoned item variant.

Open questions:

- Should poison application consume the entire potion, one dose, or a separate
  poison item?
- Should poisoned weapons track charges, duration, or permanent poison state?
- Which weapon families are poison-eligible?

### Poison Antidote

Desired:

- Recipe: `Marrentill` + `Red spiders eggs`.
- A potion that cures current poison.
- Also grants poison immunity for 5 minutes.

Open questions:

- Whether stronger antidotes should exist.
- Whether immunity should block all poison sources or only normal poison.

### Stat Restore

Desired:

- Recipe: `Harralander` + `Unicorn horn`.
- Restores reduced stats.
- Grants immunity to stat reduction for 10 minutes.

Open questions:

- Whether this restores all reduced stats or only combat/gathering stats.
- Whether this also blocks self-inflicted reductions, enemy debuffs, or only
  ordinary stat drain.
- Whether the immunity should prevent a drain attempt entirely or allow the
  attempt but immediately restore the affected stat.

## Skill Buff Potions

Skill buff potions should cover every skill. Some skills should be grouped into
shared potion families rather than requiring one potion per skill.

### Potion of Insight

Grouped skills:

- Magic
- Enchanting
- Summoning
- Cooking
- Worship

Intent:

- A mental/arcane skill potion.
- Should support players doing spellcasting, altar enchanting, summon work, and
  other magic-adjacent systems.

Recipe family:

- Uses `Eye of newt` as the secondary ingredient.
- Herb determines tier.

### Potion of Brawn

Grouped skills:

- Melee
- Mining
- Smithing
- Woodcutting
- Hits

Intent:

- A physical strength and force potion.
- Covers direct melee combat plus force-heavy production skills.

Recipe family:

- Uses `Limpwurt root` as the secondary ingredient.
- Herb determines tier.

### Potion of Deftness

Grouped skills:

- Ranged
- Pickpocketing
- Crafting
- Agility
- Fishing

Intent:

- A precision and hand-skill potion.
- Covers ranged combat, dexterous utility, and fine production work.

Recipe family:

- Uses `Fish oil` as the secondary ingredient.
- Herb determines tier.

## Still To Define

The following skills still need potion-family placement or explicit standalone
potions:

- Defense, if treated separately from Melee
- Firemaking
- Herblaw
- Harvest
- Runecraft, if still present as a skill in the active build
- Any hidden, compatibility, or renamed skills still exposed by the server
- XP boost potion recipes and scope

## Recipe Direction

Recipes are partly defined now. Known direction:

- Herblaw recipes should be rebuilt around the herb-tier plus
  secondary-family model.
- Old runecraft/enchanting potion IDs and names need an audit before reuse.
- Fish oil is now available from cooking raw fish, so recipes using fish oil can
  be early-game accessible without requiring high Fishing or Cooking.
- Quest potion recipes and behavior should be left unchanged.

Defined standard potion families:

- `Guam leaf`, `Tarromin`, `Ranarr weed`, `Avantoe`, `Cadantine`, and
  `Torstol` determine tiers 1 through 6.
- `10 Fish oil` creates `Potion of Deftness`.
- `Eye of newt` creates `Potion of Insight`.
- `Limpwurt root` creates `Potion of Brawn`.
- `Harralander` + `Unicorn horn` creates `Stat restore`.
- `Marrentill` + `Red spiders eggs` creates `Antidote`.
- `Unfinished Harralander potion` + `Ground blue dragon scale` creates weapon poison.
- `Irit leaf` + `Snape grass` creates `Skiller's Brew`.
- `Dwarf weed` + `5 Snape grass` creates `Strong Skiller's Brew`.
- `Irit leaf` + `White berries` creates `Warrior's Brew`.
- `Dwarf weed` + `5 White berries` creates `Strong Warrior's Brew`.

## Compatibility Cleanup Needed

Before implementation, audit these areas:

- Item IDs `1411-1416` are reused as `Potion of Insight v3` and
  `Potion of Insight v4`; their Java constants still carry the legacy
  Runecraft names, so treat those constants as id aliases only.
- Existing tests that mark those IDs as retired compatibility potions.
- `Herblaw.java` hardcoded custom potion recipes.
- `Drinkables.java` behavior for active potion effects.
- Skill restoration and boost decay code.
- Potion combining/decanting support.
- Client item names and runtime item definition coverage.

## Implementation Notes

When this moves from planning to code:

- Prefer one centralized timed-buff model for potion effects.
- Make potion expiration deterministic and visible enough for players.
- Keep old item IDs only where compatibility is useful; otherwise rename or
  retire clearly.
- Add tests for recipe matching, dose transitions, duration behavior, and
  non-decaying boost behavior.
