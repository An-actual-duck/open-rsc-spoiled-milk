# PvM Population And Cluster Plan

This document tracks the implemented pre-release world-population passes and
the field-review decisions for a PvM-focused MyWorld server.

## Goals

- Increase the general population size of hostile NPCs where the world feels
  sparse.
- Create better enemy clusters now that AoE spells and AoE-style effects exist.
- Maintain the implemented White Knight and Grey Knight placement/drop sources.
- Preserve quest-critical NPC placement and behavior.
- Avoid new monster art as a dependency unless a usable low-resolution asset
  direction is available.
- Make sure multiple monsters can aggro and actively attack the same player,
  especially in areas intended for AoE combat.

## Design Direction

The practical path is to reuse existing NPC art, names, and combat identities
where possible. New monsters are not blocked forever, but they are not the
default plan because matching the low-resolution Classic style is its own art
pipeline problem.

AoE-friendly populations should use clustered groups rather than random dense
spam. The goal is for a player to find clear PvM pockets that support
multi-target combat while still leaving traversal and quest spaces readable.

## Knight-Specific Work

- White Knights already exist around Falador, but should be reviewed for whether
  Saradomin-aligned coverage is broad enough.
- Grey Knights now use dedicated NPC `836`, with four MyWorld locations in the
  Taverley druid/white-wolf combat region and a direct Guthix equipment table.
- Black Knights already have several world presences and a shared drop table.
- White Knights now directly cover the supported white weapon/armor outcomes;
  Grey Knights provide the equivalent grey line.
- Standard Black Knights remain ordinary-drop sources; Dark Warriors and altar
  conversion supply the direct Zamorak equipment line.
- The Black Knight titan should remain separate from normal Zamorak-aligned
  knight sourcing.

## Spawn Cluster Audit

Before changing placements, audit the existing NPC location data and group
hostile NPCs by proximity.

For each cluster, record:

- area name or nearest landmark
- NPC IDs and names
- approximate coordinate bounds
- count of hostile NPCs
- whether the area is quest-sensitive
- whether the cluster is already good for AoE
- whether the cluster should be expanded, left alone, or thinned

## Placement Guidelines

- Prefer adding clusters to existing monster-themed areas before scattering
  enemies into empty roads or towns.
- Keep quest NPCs, shop NPCs, and skilling hubs readable.
- Favor clusters of the same or compatible NPC family.
- Avoid placing aggressive clusters where low-level traversal depends on safe
  routes, unless that danger is intentional.
- Use existing dungeon, wilderness, fort, cave, ruins, and lair spaces first.
- Keep respawn density high enough for PvM flow but not so high that pathing and
  targeting become noisy.

## Implementation Plan

1. Build an NPC-location audit that groups nearby hostile NPCs by proximity.
   Initial tooling is now in `tools/myworld/audit-npc-clusters.py`; the first
   review snapshot is in `pvm-npc-cluster-audit.md`.
2. Completed: review the generated cluster list and identify sparse Wilderness
   PvM areas using the Wilderness-only audit and level-band summary.
3. Completed: retain existing White Knight placement, finish its
   direct drop line, and add dedicated Grey Knight definition/locations/drops.
4. Completed first pass: add a MyWorld-only overlay of four Dark
   Warriors to the level 12-14 Wilderness pocket and four Greater Demons to the
   level 44-46 Wilderness pocket.
5. Completed broad pass: add 52 further Wilderness hostiles across sparse or
   undersized encounter pockets and introduce three Hellhounds to the deep
   demon region.
6. Completed themed pass: add altar-guarding aggressive Monks of Zamorak,
   Chaos Druid Warriors, a Wilderness Graveyard Necromancer, elite Hobgoblins,
   and Shadow Warriors outside the Mage Arena route.
7. After limited release: field-test AoE behavior, pathing, multi-aggro,
   respawn flow, and quest-space interference.
8. After field data: expand cluster density iteratively where it improves PvM
   flow.

## Testing Tools

- `::aggroall [radius]`, also available as `::aggronear` and `::forceaggro`,
  forces nearby attackable NPCs to attack the player. The radius defaults to 8
  and caps at 20.
- Use this to separate general multi-attacker behavior from normal aggression
  rules. If several forced NPCs can all attack correctly, any remaining issue is
  probably in the natural aggression or damage-provocation rules rather than the
  PvM combat loop itself.

## Wilderness Expansion Pass

New locations are isolated in `server/conf/server/defs/locs/MyWorldNpcLocs.json`
and loaded only when MyWorld mode is enabled.

- The overlay adds `74` Wilderness hostiles, distributed through every
  ten-level Wilderness band rather than only one combat tier.
- Expanded established regions include darkwizards, thugs, bears, ghosts,
  bandits, black unicorns, black knights, chaos dwarves, red dragons, lesser
  demons, grey wolves, rogues, Dark Warriors, and Greater Demons.
- Three `Hellhound` `294` spawns introduce a new deep-Wilderness enemy to the
  existing lesser/greater demon hunting region.
- Four aggressive `Monk of Zamorak` `293` spawns defend the previously empty
  level-39 Wilderness altar destination.
- Three `Chaos Druid warrior` `555` spawns reinforce the level 35-37
  chaos-dwarf region, improving the least populated depth band.
- One `Necromancer` `358` joins the Wilderness Graveyard undead pocket; three
  level-48 `Hobgoblin` `311` spawns act as elite camp members; and three
  `Shadow Warrior` `787` spawns add a magical threat outside the Mage Arena
  route without occupying its arrival/bank entrance.
- Four non-aggressive Grey Knights join the existing Taverley
  druid/white-wolf combat region as the Guthix-aligned direct equipment source.

## Deferred Limited-Release Decisions

- Whether Grey Knight placement inside the existing Taverley wolf/druid combat
  region is the right difficulty/access level for that equipment source.
- Whether level `31-40` Wilderness needs another expansion pass; it remains
  the least populated ten-level band at `36` hostile NPCs after this overlay.
- Which of the expanded regions should be retained as intentional AoE training
  pockets after field testing.
- Whether chaos jewelry splash/recoil-style damage should provoke
  non-aggressive NPCs into combat or only give damage/loot credit.
