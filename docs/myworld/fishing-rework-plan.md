# Fishing Rework Plan

This is the active design and implementation record for moving Fishing from
legacy tool-specific spots to MyWorld's tiered rod and location-pool model.

Use [fishing-spot-map.md](fishing-spot-map.md) as the coordinate source of truth for current live fishing spots.

## Goals

- Fishing spots should be location based, not tool based.
- Every fishing spot inside a location cluster should use the same combined fish pool for that location.
- The player's equipped fishing rod tier should determine the catch window inside that location pool.
- The player's Fishing level plus rod bonus should skew catches upward toward better fish and improve catch speed.
- Old fishing tools should be retired from normal progression:
  - bait
  - feathers
  - nets
  - big nets
  - lobster pots
  - harpoons
  - fly rods
  - oily rods
- Legacy tools can remain only where required for quests, and quest usage should be swapped to the new rod line where practical.
- Fishing rare side rewards use the mining gem-focus style:
  - `Just the fish`
  - `A little loot`
  - `Plenty of loot`
  - `Lots of loot`
  The same option selector appears when a fishing rod is equipped.

## Core Model

Each fishing location receives a fish pool made from the union of fish currently available in that location.

Example:

- A location currently has tuna, lobster, swordfish, and shark spread across different spot objects.
- Under the new model, every spot in that location can draw from the shared pool: tuna, lobster, swordfish, shark.
- A tier `6` rod catches fish mapped to tiers `3-6`.
- A tier `7` rod catches fish mapped to tiers `4-7`.
- High-tier rods intentionally stop catching very low-tier fish; players can
  swap to a lower rod when they want those fish.
- Higher Fishing level and better rod bonus increase the odds of rolling the better eligible fish.

The important behavior is that a spot no longer means "this is a harpoon spot" or "this is a cage spot." The location defines possible fish, and the rod defines what the player can reach within that location.

## Current Implementation

The first gameplay pass is in place in
`server/plugins/com/openrsc/server/plugins/authentic/skills/fishing/Fishing.java`.

Implemented:

- ten rod IDs are registered as the MyWorld rod ladder
- rods are equipable main-hand Fishing tools through the shared tool setup
- knife-on-log production creates the matching rod for each log tier
- normal MyWorld fishing scenery uses coordinate-based `FishingLocation` pools
- legacy spot commands such as `net`, `bait`, `lure`, `harpoon`, and `cage`
  redirect into the MyWorld rod model on normal MyWorld spots
- eligible catches are filtered by equipped rod tier, from the rod's tier down
  through three tiers below it
- catch weighting uses effective Fishing from the shared gathering formula
- catch delay improves with effective Fishing and rod tier
- examine text lists that location's full possible fish pool
- direct use of old fishing tools on normal MyWorld fishing spots is blocked
  with a rod-equipping prompt
- normal fishing shops sell rods through tier 6
- the Fishing Guild shop sells the full tier 1-10 rod line
- Gerrant keeps the current explicit quest exceptions for `Fishing Bait` and
  `Lobster Pot`
- `Hero's Quest` lava-eel guidance now points MyWorld players at the tier `9`
  `Magic Fishing Rod` instead of creating an oily rod as the active path
- fishing has a configurable rare side-reward roll:
  - `Oyster`, `Seaweed`, and `Casket` are side rewards, not normal fish-pool
    catches
  - current focus rates are off, `1/30`, `1/20`, and `1/15` for `Just the fish`,
    `A little loot`, `Plenty of loot`, and `Lots of loot`
  - legacy generic `Boots` are removed from the MyWorld side table
  - leather/carapace boots and gloves use the shared resource-side-reward
    gating model from [resource-seed-plan.md](resource-seed-plan.md):
    normal weights at or below equipped rod tier, half weight at `+1`, and
    quarter weight at `+2`

Ongoing upkeep:

- source tests should continue guarding the implemented rod model and
  legacy-source cleanup
- longer field testing should tune catch weighting, delay, and rare side-reward
  rates

## Rod Ladder

Fishing rods should follow the wood line and behave like pickaxes and hatchets:

- equipped in the main hand
- require Fishing to equip
- add an effective Fishing bonus
- determine the fish tier window that can be caught

Tool bonus uses the shared gathering formula:

```text
effective_fishing = fishing_level + ((rod_tier - 1) * 5)
```

| Rod tier | Rod name | Fishing requirement | Effective bonus |
| - | - | - | - |
| 1 | Fishing Rod | 1 | +0 |
| 2 | Pine Fishing Rod | 8 | +5 |
| 3 | Oak Fishing Rod | 15 | +10 |
| 4 | Willow Fishing Rod | 22 | +15 |
| 5 | Palm Fishing Rod | 30 | +20 |
| 6 | Maple Fishing Rod | 38 | +25 |
| 7 | Yew Fishing Rod | 46 | +30 |
| 8 | Ebony Fishing Rod | 54 | +35 |
| 9 | Magic Fishing Rod | 62 | +40 |
| 10 | Blood Fishing Rod | 70 | +45 |

No tier `11` fishing rod is planned for the first pass.

Rods are crafted from the matching logs by using a knife on the log. The
knife-on-log production menu includes fishing rods alongside staffs, bows, and
other wood products.

## Fish Tier Mapping

The fish tier map keeps the existing unlock order but converts unlocks from old tool and bait requirements into rod-tier requirements. A rod catches its own tier plus up to three tiers below it, so a tier `10` rod catches tiers `7-10`, while a tier `4` rod catches tiers `1-4`.

| Fish/resource | Current Fishing level | New rod tier | New effective unlock |
| - | - | - | - |
| Raw Shrimp | 1 | 1 | Fishing Rod |
| Raw Sardine | 7 | 1 | Fishing Rod |
| Raw Herring | 13 | 2 | Pine Fishing Rod |
| Raw Mackerel | 16 | 3 | Oak Fishing Rod |
| Raw Anchovies | 19 | 3 | Oak Fishing Rod |
| Raw Trout | 25 | 4 | Willow Fishing Rod |
| Raw cod | 25 | 4 | Willow Fishing Rod |
| Raw Pike | 31 | 5 | Palm Fishing Rod |
| Raw Salmon | 37 | 5 | Palm Fishing Rod |
| Raw Tuna | 43 | 6 | Maple Fishing Rod |
| Raw Lobster | 49 | 7 | Yew Fishing Rod |
| Raw Swordfish | 55 | 8 | Ebony Fishing Rod |
| Raw Bass | 55 | 8 | Ebony Fishing Rod |
| Raw lava eel | 62 | 9 | Magic Fishing Rod |
| Raw Shark | 70 | 10 | Blood Fishing Rod |

Fishing side rewards are handled separately from normal fish pools:

- `Oyster`
- `Seaweed`
- `Casket`
- leather/carapace boots and gloves through the shared `+2` tier-window model

The side-reward focus option controls the proc chance. Legacy generic `Boots`
are not part of the MyWorld fishing table.

Side-reward tier gating should match
[resource-seed-plan.md](resource-seed-plan.md):

- rewards at or below equipped rod tier use normal weights
- rewards `1` tier above equipped rod tier use half weight
- rewards `2` tiers above equipped rod tier use quarter weight
- rewards more than `2` tiers above equipped rod tier are not eligible

## Location Pool Rules

For each cluster from `fishing-spot-map.md`:

1. Build the cluster's pool from every fish currently available in that cluster.
2. Remove old tool distinctions from the spot.
3. Make every spot in the cluster resolve to the same location pool.
4. Filter the pool by equipped rod tier window.
5. Roll catch quality using effective Fishing.
6. Roll catch speed using effective Fishing and rod tier.

If the equipped rod's tier window does not overlap the location pool, the
player receives: `This rod isn't appropriate for any fish available here`.

Normal fishing scenery should expose a single `Fish` action. The examine text
should list the fish available in that location so players can see the location
pool without needing to trial-and-error the old tool commands.

The existing `ObjectFishing.xml` is object-ID based, which is not enough for this design because the same object ID appears in many different regions. The implementation should add a MyWorld fishing-location registry keyed by exact fishing spot coordinates or cluster membership, then use the clicked spot's coordinate to resolve the correct location pool.

Avoid creating one new scenery object ID per location unless the registry approach proves impractical. Duplicating object IDs would make the data harder to maintain.

## Catch Weighting Direction

The final formula can be tuned during implementation, but the behavior target is:

- Low rod tier means only low-tier fish can be caught.
- Higher rod tier shifts the eligible pool upward instead of retaining every
  lower tier forever.
- Low Fishing level within a rod's range should favor the lowest eligible fish.
- Higher effective Fishing should increasingly favor the best eligible fish in the location.
- Lower-tier fish within the rod's four-tier window should not vanish
  completely unless the location has only one eligible fish.
- Catch speed should improve with effective Fishing and rod tier.

This preserves the current tuna/swordfish style of progression, where the better fish becomes more common as the player improves.

## Quest And Legacy Tool Audit

Before deleting or hiding old tools, audit every quest and plugin path that references:

- `Fishing Rod`
- `Fly Fishing Rod`
- `Oily Fishing Rod`
- `Net`
- `Big Net`
- `Lobster Pot`
- `Harpoon`
- `Fishing Bait`
- `Feather`

Preferred handling:

1. Replace the old requirement with the new rod line when the quest logic does not specifically need the legacy item.
2. Keep the old item only as a compatibility or quest item if replacement would break the quest's identity.
3. Remove old tools from shops, drops, and normal Fishing progression after quest paths are safe.

Current explicit quest exceptions:

- `Fishing Contest` still requires the baseline `Fishing Rod` and
  `Fishing Bait` or worms.
- `Dragon Slayer` still consumes a `Lobster Pot` for the map-piece door.
- `Hero's Quest` lava-eel fishing now uses the tier `9` `Magic Fishing Rod` in
  MyWorld mode. The old blamish-oil/oily-rod path is compatibility-only outside
  the MyWorld fishing model.

## Implementation Passes

1. Add the ten rod items and make them equipable main-hand Fishing tools. Done.
2. Add knife-on-log crafting recipes for every rod tier. Done.
3. Add rod-tier and effective Fishing lookup helpers shared by Fishing logic. Done.
4. Add the location-pool registry using the cluster coordinate map. Done.
5. Replace object-tool catch checks with location-pool, rod-tier, and effective-level checks. First pass done.
6. Retire bait/feather/net/harpoon/lobster-pot requirements from normal Fishing. First pass done.
7. Update fishing shops and skill guides. Done.
8. Audit quests for legacy tool usage and swap to the rod line where safe. First pass done.
9. Add configurable rare side rewards for fishing. Done.
10. Add tests for:
   - rod equip requirements
   - location pool resolution
   - rod-tier fish filtering
   - catch weighting boundaries
   - legacy tool retirement guardrails
   Done for the current implementation guardrails; expand with runtime tests if
   the fishing model gets another behavior pass.
