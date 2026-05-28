# Resource Seed Plan

This is the active design record for using rare stackable seed items as the
side-reward system for Woodcutting and Harvesting, with the same tier-gating
shape applied to Fishing side rewards.

## Goals

- Create a rare side-reward system for Woodcutting and Harvesting that feels
  more interesting than directly adding more logs, herbs, or gems to normal
  gathering rolls.
- Reuse the existing temporary-object capability already proven by `Mithril
  seed` and quest-grown Yommi trees.
- Keep the reward categories distinct by source skill.
- Let the reward item be stored, traded, and used later.
- Make seed rewards stackable, even though they occupy a similar rare-reward
  role to non-stackable gems.
- Keep the first implementation small enough to prove the framework before
  filling out every possible seed.

## Source Skill Reward Categories

Woodcutting side rewards can produce:

- ore tree seeds
- gem tree seeds
- log tree seeds
- `Knowledge` tree seeds for XP
- `Money` tree seeds for gold

Harvesting side rewards can produce:

- food plant seeds
- herb plant seeds
- potion-ingredient plant seeds
- `Knowledge` plant seeds for XP
- `Money` plant seeds for gold

Harvesting food seeds should favor finished food rewards, not raw cooking
ingredients. Target food rewards include pizzas, cakes, pies, stews, and cooked
meats/fish starting around salmon-tier food. All herbs can be represented,
including guam. Potion-ingredient seeds should cover the broader secondary
ingredient set rather than only the first few examples.

Fishing does not use resource seeds in the current design, but its side-reward
tier gating should use the same tier-window rules described below.

## Tier Gating

Side-reward rolls should use the same general gating concept as the current
Fishing boots/gloves rewards, with one important change:

- A tool can roll side rewards up to `2` tiers above the equipped tool tier.
- Rewards at or below the equipped tool tier use normal weighting.
- Rewards `1` tier above the equipped tool tier roll at `1/2` normal weight.
- Rewards `2` tiers above the equipped tool tier roll at `1/4` normal weight.

Example:

- A tier `7` tool can roll tier `1-9` side rewards.
- Tier `1-7` rewards use normal weights.
- Tier `8` rewards use half weight.
- Tier `9` rewards use quarter weight.
- Tier `10+` rewards are not eligible from that tool.

This rule should be shared by:

- Fishing side rewards
- Woodcutting seed side rewards
- Harvesting seed side rewards

## Seed Item Rules

- Resource seed items should be stackable.
- Seed items can be traded and stored.
- A seed is consumed when planted.
- A planted seed creates a temporary owned object.
- Only the owner can harvest or cut the grown object.
- Grown resource objects have no skill or tool requirement to harvest once they
  exist; ownership is the access requirement.
- Grown resource objects use fixed action timing, not skill-scaled action speed.
- Each grown resource object gives `3` successful yields.
- After the third yield, the object despawns.
- If not fully harvested, the object despawns when its lifetime timer expires.

## Yield Rules

Each seed type should define its own:

- spawned object id
- action type, such as `Chop` or `Harvest`
- fixed seconds per yield
- total yield count
- item reward per yield
- XP per yield
- maximum lifetime

Default first-pass behavior:

- total yield count: `3`
- maximum lifetime: `60 seconds`
- action time: `5 seconds` per yield

Normal resource seeds should give modest baseline XP rather than full
resource-tier XP.

For Woodcutting, non-`Knowledge` seeds should grant the standard level-1 tree XP
for each successful yield. Since a grown object has `3` yields, that XP can be
awarded up to `3` times.

`Knowledge` seeds are the exception. They exist specifically as larger XP
rewards and should have their own XP tuning.

## Initial Proof: Mithril Tree

The implemented `Mithril seed` behavior works like this:

1. The player plants a `Mithril seed`.
2. The seed creates a temporary owned `Mithril Tree`.
3. The player chops or harvests the tree.
4. Each yield takes `5 seconds`.
5. Each yield grants `2-5` mithril ore.
6. The tree gives `3` yields total.
7. The tree despawns after the third yield.
8. If not fully harvested, it despawns after `60 seconds`.

## Current Mithril Seed Balance

`Mithril seed` already exists in the game.

Current known sources:

- `Waterfall Quest` reward
- Legends Guild shop stock

`Mithril seed` is now a resource-yielding item. The Legends Guild price has
received a first-pass increase from `200` to `5000`; further balance changes
should be based on limited-release play data.

Possible follow-up tuning:

- Keep the quest reward as-is if the yield is acceptable.
- Change the quest reward into a small variety of resource seeds if that fits
  better.
- Increase the Legends Guild shop price to match the expected resource value.
- Potentially limit or replace shop stock if direct purchase undermines rare
  side-reward acquisition.

## Current Implementation Status

- Fishing side rewards now use a shared `reward tier <= tool tier + 2` helper,
  with half weight at one tier above and quarter weight at two tiers above.
  Fishing rolls the side table at `1/30`, `1/20`, or `1/15` depending on
  whether the equipped rod is set to `A little loot`, `Plenty of loot`, or
  `Lots of loot`; `Just the fish` disables the roll.
- `Mithril seed` has been moved out of the Waterfall Quest item handler and
  into a MyWorld resource seed handler.
- Opening a `Mithril seed` now plants an owned `Mithril Tree` proof object.
- The planted `Mithril Tree` gives `3` yields, `2-5` mithril ore per yield,
  `100` Woodcutting XP per yield, and despawns after the third yield or after
  `60` seconds.
- Resource seed handling is now registry-driven in
  `ResourceSeeds.java`: seed item, spawned scenery, reward item, yield range,
  XP, action delay, lifetime, skill, and tool bubble are all per-definition.
  `Mithril seed` was the first proof definition; the registry now covers the
  Woodcutting and Harvesting seed families listed below.
- The first Woodcutting ore seed set is implemented:
  `Tin seed` (`2691`), `Copper seed` (`2692`), `Iron seed` (`2693`),
  `Coal seed` (`2694`), `Silver seed` (`2695`), `Gold seed` (`2696`),
  `Adamantite seed` (`2697`), and `Runite seed` (`2698`).
  The existing `Mithril seed` (`796`) remains the mithril ore seed.
- Woodcutting can now award ore seeds as rare side rewards after a successful
  log yield. The reward table uses the shared `reward tier <= tool tier + 2`
  gating rule, with reduced weight for one and two tiers above the equipped
  hatchet tier. If inventory is full, the seed drops at the tree.
- The first Harvesting seed set is implemented using a generic `Resource Plant`
  object based on the existing `herb` model: `Potato seed` (`2699`),
  `Onion seed` (`2700`), `Garlic seed` (`2701`), `Redberry seed` (`2702`),
  `Limpwurt seed` (`2703`), `Snape grass seed` (`2704`), `Guam seed`
  (`2705`), and `Ranarr seed` (`2706`).
- Harvesting can now award those plant seeds as rare side rewards after a
  successful produce yield. The reward table uses the equipped shears tier and
  the same `reward tier <= tool tier + 2` gating rule. If inventory is full,
  the seed drops at the harvesting spot.
- Resource seed nodes can now grant item, XP, or gold yields.
- Knowledge and money seeds are implemented separately by skill so the planted
  node can scale from the correct skill:
  `Woodcutting knowledge seed` (`2707`), `Woodcutting money seed` (`2708`),
  `Harvesting knowledge seed` (`2709`), and `Harvesting money seed` (`2710`).
  Knowledge yields grant `skill level * 100-400` XP per yield. Money yields
  grant `skill level * 500-2000` coins per yield. Both still resolve 3 yields.
- Woodcutting log seeds are implemented for oak through blood logs:
  `Oak log seed` (`2711`), `Willow log seed` (`2712`), `Palm log seed`
  (`2713`), `Maple log seed` (`2714`), `Yew log seed` (`2715`),
  `Ebony log seed` (`2716`), `Magic log seed` (`2717`), and
  `Blood log seed` (`2718`). These currently yield `2-5` logs per harvest
  regardless of log tier.
- Woodcutting gem seeds are implemented and are the rarest Woodcutting seed
  category: `Sapphire seed` (`2719`), `Emerald seed` (`2720`),
  `Ruby seed` (`2721`), `Diamond seed` (`2722`), and
  `Dragonstone seed` (`2723`). Sapphire through diamond currently yield `1-3`
  gems per harvest; dragonstone yields exactly `1` gem per harvest.
- Ore seed definitions currently yield `2-5` ore per harvest regardless of ore
  tier. Tin and copper seed definitions remain spawnable/dev-compatible, but
  they are not awarded by the Woodcutting side-reward table.
- Woodcutting side-reward weighting now follows:
  Knowledge/Money most common, log seeds common, ore seeds less common, gem
  seeds rare. Standard/pine log seeds and tin/copper ore seeds are not in the
  acquisition table.
- Harvesting side-reward weighting now uses Knowledge/Money as the tier 1/2
  replacement rewards, with higher-tier plant seeds starting after that.
- Harvesting seed coverage now includes finished-food seeds, all standard herb
  seeds, and the potion-ingredient seeds used by the current potion recipes.
  Food seeds avoid raw cooking ingredients and instead yield cooked fish/meat
  starting at salmon-tier plus stews, pies, cakes, and pizzas.
- Harvesting seed rarity is tier-driven rather than category-driven: food,
  herb, and potion-ingredient seeds at the same reward tier use the same base
  weight. Category is flavor; tier is the rarity axis.
- Woodcutting and Harvesting both use equipped-tool focus options for seed
  frequency: `No seeds for me`, `A few seeds`, `More seeds`, and
  `Even more seeds!`. Their current base seed-roll chances are off, `1/50`,
  `3%`, and `4%`.
- Mining gem focus now uses the same off, `1/50`, `3%`, and `4%` roll chances
  for `Just the ore`, `A few gems`, `Plenty of gems`, and `Lots of gems`.
- Planted resource nodes auto-repeat their three yields after one interaction,
  show the appropriate tool bubble, and drop overflow rewards on the ground
  when inventory space runs out.
- Legends Guild shop balance has a first-pass increase via the `Mithril seed`
  base price, from `200` to `5000`.
- Harvesting seed rarity and yield amounts should be tuned after live testing.

## Completed Implementation Record

The steps below describe the completed first-pass build and are retained as a
design and validation record. Future new seed families should reuse this
model.

### 1. Build A Shared Registry

The implementation provides a MyWorld-owned seed registry under
`server/plugins/com/openrsc/server/plugins/custom/myworld/`.

The registry should map each seed item to:

- seed item id
- spawned object id
- owner requirement
- action label
- reward item id, or XP/gold reward
- reward quantity range
- yields remaining
- action delay
- despawn timer
- source skill family
- reward tier

### 2. Add Temporary Resource Object Handling

The shared object handler can:

- spawn the owned resource object
- prevent planting on blocked or occupied tiles
- consume one seed on successful planting
- reject interactions from non-owners
- process fixed-delay yields
- decrement remaining yields
- despawn after `3` yields
- despawn after the configured lifetime timer

This should use the existing object APIs:

- `registerGameObject`
- `unregisterGameObject`
- `replaceGameObject`
- `delayedRemoveObject`
- timed `SingleEvent` cleanup where state checks are needed

### 3. Prove One Woodcutting Seed

`Mithril seed` served as the first proof and its first source/price adjustment
is in place.

The first proof should validate:

- stackable seed item behavior
- planting
- owner-only interaction
- fixed yield timing
- three-yield despawn
- lifetime despawn
- item reward delivery
- baseline XP delivery

### 4. Add Side-Reward Tier Gating Helper

The shared side-reward eligibility helper implements:

```text
eligible if reward_tier <= equipped_tool_tier + 2
weight = base_weight       when reward_tier <= equipped_tool_tier
weight = base_weight / 2   when reward_tier == equipped_tool_tier + 1
weight = base_weight / 4   when reward_tier == equipped_tool_tier + 2
```

Then use it for:

- Fishing boots/gloves side rewards
- Woodcutting resource seed rolls
- Harvesting resource seed rolls

### 5. Add Initial Woodcutting And Harvesting Tables

The registry expanded through the current ore, gem, log, food, herb,
potion-ingredient, `Knowledge`, and `Money` sets described above. Further
families should wait for field-test evidence that the current tables need
extension.

### 6. Source Balance Review

The first pass audited and adjusted the existing resource-bearing `Mithril
seed` path, including:

- `Waterfall Quest`
- Legends Guild shop
- any admin/dev kits
- any other direct seed sources

Continue tuning these sources after observed play if direct acquisition makes
the seed too generous.

### 7. Guardrail Coverage

Keep guardrail coverage for:

- seed registry completeness
- seed stackability
- source balance guardrails for `Mithril seed`
- tier-window eligibility and reduced weights above tool tier
- owner-only interaction
- three-yield despawn behavior
- timeout despawn behavior

Add further runtime interaction coverage only where new behavior or a
regression justifies it.
