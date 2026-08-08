# A07.4 Chain and Random-Single Traversal Policy

## Scope and result

A07.4 centralizes only candidate enumeration and the candidate-index draw for
Chaos chain lightning and Splinter. The two effects remain separate policies:

- `ChainLightningTraversalPolicy` owns the radius-four next-hop candidates and
  the current three-hop cap.
- `SplinterTargetSelectionPolicy` owns the radius-two random-single candidate.

Event owners still decide proc chance, whether another hop runs, damage and
falloff, projectile presentation, contribution, aggro, mitigation, hitsplats,
death, and callback order. This branch adds no shared effect executor.

## Chain-lightning compatibility

Every event owner draws its per-hop chance before asking the traversal policy
for a target. The policy takes a fresh owner-view snapshot, filters its current
candidates, and consumes exactly one bounded integer draw only when the set is
nonempty. Random-source ownership remains deliberately different:

- reciprocal melee and projectile paths use `ProductionGameRandom`, the A02
  adapter over the legacy `DataConversions` generator;
- PvM melee continues to use the server's injected `combatRandom()` source.

Only the current anchor is excluded. A living earlier node, including the
original primary, may therefore be selected again. A lethal child still
becomes the next anchor, so traversal can continue from its last location; the
dead child itself is no longer a candidate. These are maintained compatibility
facts, not recommendations for new chain effects.

The three-hop cap, chance-before-index order, damage halving, projectile cycle,
and per-child death remain unchanged. If a chance succeeds but no candidate is
available, no index draw occurs and traversal stops.

## Splinter compatibility

`ProjectileEvent` continues to draw the Splinter proc first. Only a successful
proc reaches the policy. The policy snapshots the current player view and
consumes one legacy-random index only when at least one candidate remains.
Damage, Magic contribution, optional chase, and child death remain in
`ProjectileEvent`.

## Shared spatial facts, intentionally separate code

Both policies rely on the owner's view for world-space and signed-level
membership. Both exclude the current anchor/primary, removed, dead,
non-attackable, and summoned NPCs. Neither performs a path or line-of-effect
check. A visible NPC whose respawning flag is set remains eligible because the
three prior selectors did not reject that flag.

A removed anchor/primary continues to supply its last location, while a
removed candidate is excluded. Under layered spatial authority, a removed
owner still triggers the existing membership mismatch failure when its view is
requested. Tightening wall behavior, adding a visited set, excluding visible
respawning NPCs, or converting removed-owner failure into a silent empty result
would be behavior changes and require separate approval.

The similar filters are intentionally not folded into A07.3's ordinary radius
selector. Chain takes a fresh random selection per hop and permits revisits;
Splinter has a distinct proc-before-selection contract; A07.3 owns live versus
eager all-recipient enumeration. Their labels are not interchangeable.

## Migrated facades

The existing private selector methods remain as compatibility facades in:

- `CombatEvent`
- `PvmMeleeEvent`
- `ProjectileEvent`

The facades preserve their signatures and route to the appropriate policy and
random source. Existing source guards now validate the policy owner rather
than requiring duplicated filters in every facade.

## Verification

Two compiled scenarios grow the authoritative combat gate from 86 to 88 and
assert:

- three-hop cap and chance-before-index draw order;
- repeated primary/child selection and exact damage falloff;
- continuation and callback RNG interleaving after child death;
- no index draw for an empty candidate set;
- same-level view membership and intentional wall pass-through;
- removed anchor, removed candidate, visible respawning candidate, and removed
  layered-owner behavior across the current paths;
- Splinter proc-before-index order with a discriminating legacy RNG seed; and
- Splinter's exact empty-selection draw count.

The existing A05 child-damage scenarios continue to prove style, mitigation,
contribution, aggro, hitsplat, and death parity. Guard Dog suppression remains
at each caller before traversal begins.

## Exclusions and stop conditions

This policy does not include Scythe, shuriken, all-recipient splashes, boss
areas, delayed spells, PvP targeting, damage execution, or proc registration.
A07.5 remains the next bounded branch.

Stop any future traversal consolidation if it changes RNG source/count/order,
candidate view order, revisit behavior, cap, signed-level membership, wall
behavior, removal/respawn handling, Guard Dog ordering, projectile sequence,
damage, aggro, contribution, or child-death order.
