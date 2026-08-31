# Guild Map Gameplay Current State

Status: implemented on `content/guild-map-gameplay-reconciliation`

This record binds guild gameplay to the production World Builder package with
fingerprint
`f07264aca7b93fc3bc27991daf9187c468611a7d636c98b10cf695814a2add7f`
and manifest
`aed90ab3b94b3a86815db9a77b71f03a26b8cb134d09845860507896bc80b752`.
The signed package coordinates and authored placement identity are authority;
the older packed JSON overlays are compatibility inputs, not activity rosters.

## Mage Guild basement

Wizard Frumscone's local encounter contains 26 authored Magic Zombies (NPC
`516`) with starts at signed level `-1`, `x=604..620`, `y=751..753`. There are
no Baby Blue Dragons in this encounter. A kill earns one durable, per-player
`mage_guild_magic_zombie_credits` credit only when the NPC ID, authored-package
provenance, and start bounds all match. This prevents another NPC `516`, a
dynamically spawned copy, or a lured monster from changing eligibility.

One credit and one genuinely noted Stone become one ordinary Stone. Existing
ordinary Stone and the distinct five-Stone market certificate do not satisfy
the input. Frumscone offers quantities `1`, `5`, and `all I can`. The server
rechecks notes, credits, and the final number of inventory slots immediately
before conversion; consuming the complete noted stack may contribute its
freed slot. Any failed output or ledger commit restores the noted input and
does not spend a credit. The former zombie-eye/blue-scale trade and overflow
ground drops no longer exist.

## Rangers Guild basement

The signed level `-1` activity bounds are `x=484..515`, `y=456..483`. Its exact
authored roster is:

| Enemy | NPC | Spawns | Points per ranged kill |
| --- | ---: | ---: | ---: |
| Giant | 61 | 8 | 7 |
| Skeleton | 195 | 6 | 12 |
| Lesser Demon | 22 | 3 | 16 |
| Green Dragon | 196 | 2 | 22 |

NPC `196` is called Earth Dragon in the base definitions and Green Dragon by
the active MyWorld definition. The balance is approximately one point per five
base hitpoints, rounded up, so tougher and slower targets pay more without
changing the established reward prices. Existing
`rangers_guild_points` balances remain valid. The obsolete XP remainder stops
participating in awards; points now require a ranged contribution to the final
credited kill of an eligible authored basement spawn. Melee/magic-only kills,
other monsters, copies without authored provenance, and matching coordinates
on another level award nothing.

Both stair objects are aligned at `x=499`, `y=469`. Using the ground-floor down
stair lands at signed `(499,472,-1)` (legacy packed `(499,3304)`), clear of the
basement stair footprint. Using the basement up stair at packed `(499,3301)`
lands at `(499,468,0)`, immediately north of the ground-floor stair opening.
