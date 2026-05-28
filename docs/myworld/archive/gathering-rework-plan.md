# Gathering Rework Plan

This document captures the target MyWorld gathering model and tracks the current
implementation rollout.

## Core Direction

Resource gathering should stop using failure chance as the main progression
lever. A completed gather action should succeed, and progression should come
from yield amount, yield weighting, tool quality, and resource quality.

## Shared Rules

- Completed gathering actions do not fail.
- Resource access should remain on the normal 1-70 tier ladder.
- Skill level improves yield by moving the player through a 10-stage yield
  ladder.
- The 1-91 yield ladder is for gathering benefits only, not for unlocking
  resources.
- Tool tier adds effective skill for yield-stage calculation.
- Better tools should also feel better through weighting, not just access.
- Gathering actions should take longer than the old rapid retry model because a
  completed action is now meaningful.
- Multiple players can gather the same depleting node at once.
- Each player gets their own full reward roll.
- Rewards are not split, shared, or contribution-weighted.
- Group gathering should be beneficial: more people gathering a depleting node
  should make it deplete faster.

## Per-Resource Yield Ladder

Each resource should have its own yield ladder based on that resource's unlock
level. Resource access remains separate from yield benefits: a player must meet
the resource unlock level first, then effective level controls quantity and
weighting for that specific resource.

The yield ladder repeats every 10 effective levels from the resource's unlock
level. It alternates between range expansion and range consolidation:

| Stage | Effective Level Relative To Resource Unlock | Base Yield |
| --- | --- | --- |
| 1 | unlock to unlock+9 | 1 |
| 2 | unlock+10 to unlock+19 | 1-2 |
| 3 | unlock+20 to unlock+29 | 2 |
| 4 | unlock+30 to unlock+39 | 2-3 |
| 5 | unlock+40 to unlock+49 | 3 |
| 6 | unlock+50 to unlock+59 | 3-4 |
| 7 | unlock+60 to unlock+69 | 4 |
| 8 | unlock+70 to unlock+79 | 4-5 |
| 9 | unlock+80 to unlock+89 | 5 |
| 10 | unlock+90 and beyond | 5-6 |

Range stages should weight upward as effective level increases inside that
stage. For example, early in a `1-2` band the player should mostly receive `1`,
while near the end of the band the player should more often receive `2`.

This naturally keeps lower-tier resources ahead of higher-tier resources. For
example, if tin unlocks at `1` and copper unlocks at `11`, then at effective
level `11` tin has moved to `1-2` while copper has just reached its base `1`.
At effective level `21`, tin has moved to flat `2`, while copper has moved to
`1-2`.

If an edge case makes a resource gatherable before the yield math would produce
at least `1`, the result clamps upward to `1`.

## Tool Effective-Level Bonus

Hatchets, pickaxes, shears, and rods should act like other tiered tools by
adding to the player's effective gathering level.

Tool tier bonus formula:

```text
effective_level = skill_level + ((tool_tier - 1) * 5)
```

Examples:

- Tin tool, tier 1: `+0`
- Copper tool, tier 2: `+5`
- Bronze tool, tier 3: `+10`
- If effective level `21` reaches the `2 per gather` stage, a copper pickaxe at
  Mining 16 reaches it because `16 + 5 = 21`.

Current hatchets and pickaxes should keep their existing broad tier-gated access
ladder unless a later pass changes tool requirements.

Tool equip requirements are also gated by the same broad `1-70` ladder. In
MyWorld, pickaxes require Mining, hatchets require Woodcutting, and shears
require Harvesting before they can be equipped. Black hatchet is treated as a
steel-tier sidegrade, and dragon hatchets are tier `11` tools with an `80`
Woodcutting equip requirement.

Resource access should be checked before effective-level yield bonuses. Tool
bonuses should not let the player gather a resource whose base resource
requirement they have not met unless we explicitly design an exception later.

Once the resource is unlocked, tool bonuses should fully count toward yield,
including beyond the player's base skill level. A max-level player with a top
tier tool should gather at the yield appropriate to the combined effective value,
not be capped at the unmodified skill-level yield.

## Logs, Ore, And Harvesting

Woodcutting, ore mining, and harvesting should use the same depleting-node model:

- A completed gather action succeeds.
- The player receives a yield from the ladder.
- The node depletes immediately after the completed action.
- If multiple players complete actions on the same node, each gets their own
  yield and the node should deplete faster.
- Tool tier modifies effective level and yield weighting.

This applies to:

- Trees / logs through Woodcutting.
- Ore rocks through Mining.
- Harvesting nodes through the custom Harvesting skill.

Harvesting uses shears as its single tool family. Fruit pickers, hand shovels,
and herb clippers are retired from harvesting progression. Legacy shears are
treated as tier-1 tin shears so level-1 wool shearing remains available, and
copper through rune shears are craftable from one matching metal bar through
Smithing.

Current access audit:

- Logs are already defined as a clean 10-tier `1-70` ladder: normal `1`, pine
  `8`, oak `15`, willow `22`, palm `30`, maple `38`, yew `46`, ebony/dead tree
  `54`, magic `62`, blood `70`.
- Harvesting is enabled in MyWorld and has resources across `1-70`, but it is a
  denser custom resource table rather than a simple 10-resource ladder.
- Ore has been moved toward a clean tin-to-rune mainline: tin `1`, copper `8`,
  iron `15`, coal `22`, mithril `38`, adamantite `54`, and runite `70`.
  Clay `1`, bluerite `10`, silver `20`, and gold `40` remain at their existing
  unlock levels for now.

## Stone Rocks

Stone from rocks should use the same success, yield, weight, and tool-bonus
model, but should not deplete the node.

- Stone rocks should unlock at level `1`.
- A player can keep mining the same stone rock until inventory is full.
- The same per-resource yield ladder applies.
- Tool tier modifies effective level and weighting.

## Fishing

Fishing should also remove failure, but it is not part of the depleting-node
model.

- Wooden fishing rods should become the main progression ladder.
- Rod tier adds effective Fishing level using the same `+5 per tier after tier
  1` model.
- Fishing progression should be driven by speed and quality of fish gathered.
- Fishing spot location plus rod tier should determine catch quality and catch
  pool.
- Legacy bait, feathers, nets, lobster pots, harpoons, and similar specialized
  fishing tool splits should be reduced or removed over time.

## Gems

Gem rocks currently exist as dedicated mining nodes, and normal ore mining also
has random gem procs.

Target policy:

- Keep dedicated gem rocks as a special mining node type unless a later design
  pass removes or repurposes them.
- Normal ore rocks can produce gems as a side reward.
- Pickaxes should become main-hand equipable tools again.
- When a pickaxe is equipped, repurpose the old combat-style selector as a
  mining focus selector:
  - `More Gems`: higher random gem proc chance.
  - `Some Gems`: normal gem proc chance.
  - `Just Ore`: disables random gem procs.
  - The fourth selector slot can mirror `Some Gems` until a better fourth mode exists.
- The mining focus selector should apply to random gem procs from normal ore
  mining, not necessarily to dedicated gem rocks.
- Under the guaranteed-gathering model, random gems should be a bonus roll in
  addition to ore rather than replacing the ore reward.
- Yield beyond free inventory space is lost. Gathering actions should still
  complete even with a full inventory.

## Implementation Notes

- `[done]` Add shared helper logic for effective gathering level, yield stage,
  and weighted yield rolls rather than duplicating formulas per skill.
- `[done]` Convert ore mining and generic stone mining to guaranteed completion
  rewards using the shared yield ladder.
- `[done]` Convert woodcutting to guaranteed completion rewards using the shared
  yield ladder and immediate tree depletion.
- `[done]` Convert core harvesting and clipping to guaranteed completion rewards
  using the shared yield ladder and immediate node depletion.
- `[done]` Gate equipping pickaxes and hatchets by Mining/Woodcutting on the
  broad `1-70` tool ladder.
- `[done]` Convert Harvesting to tiered shears, including wool shearing as a
  level-1 Harvesting action with yield bonuses from shears and Harvesting level.
- `[done]` Add one-bar Smithing recipes for the metal shears ladder.
- `[done]` Add the mining gem focus selector through the old combat-style
  interface, with `Some Gems`, `More Gems`, and `Just Ore` behavior.
- `[done]` Allow gathering actions to continue with full inventories while
  dropping any rolled yield that does not fit.
- `[todo]` Convert fishing to its rod/spot quality model.
- `[done]` Update tool equip rules before relying on main-hand pickaxe/hatchet
  selector behavior.
- Keep fishing implementation separate enough to support its own spot/rod/fish
  quality model.
- Add guard tests for no-failure gathering, yield ladder boundaries, tool-tier
  effective-level bonuses, stone non-depletion, and mining gem focus behavior.
