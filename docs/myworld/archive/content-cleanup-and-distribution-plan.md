# MyWorld Content Cleanup And Distribution Plan

Authoritative prerequisite:

- Check
  [important-game-changes-that-must-be-adhered-to.md](/home/justin/Core-Framework/docs/myworld/important-game-changes-that-must-be-adhered-to.md)
  before using this plan.
- Record family retirements and repurposes in
  [retired-replaced-and-repurposed-items.md](/home/justin/Core-Framework/docs/myworld/retired-replaced-and-repurposed-items.md).

## Summary

This plan tracks the broad content cleanup pass that follows the major MyWorld
combat, crafting, enchanting, jewelry, ranged, and potion reworks.

For the consolidated high-level backlog, use
[remaining-work.md](/home/justin/Core-Framework/docs/myworld/remaining-work.md)
first. This file remains the more detailed source/distribution plan.

The goal is not only to remove invalid legacy content, but also to rebalance
where valid content enters the game now that the tier ladder has changed
substantially.

This is the working plan for:

- retiring obsolete item inflow
- re-tiering surviving drops and shop stock
- introducing missing tier stock and rewards
- distributing the newer higher-tier items intentionally across shops, drops,
  and world sources

It is intentionally separate from the broader cleanup/polish checklist because
this pass is primarily about progression content and source placement, not UI.

## Scope

This pass should cover at least:

- shops
- NPC drops
- ground spawns / map item sources
- quest rewards
- dialogue-driven sources
- crafting outputs that still leak obsolete items into the world
- admin/dev helper sources where they distort normal progression assumptions

It should be treated as a source-and-distribution audit, not a narrow bug fix.

## Priority Order

### Phase 1: Remove invalid legacy inflow

Remove active item sources that no longer make sense anywhere in live MyWorld.
This phase comes first so later tier-distribution work is not built on top of
dead or misleading content.

#### Main categories to sweep first

- old potion families and potion rewards that no longer belong in the live
  potion line
- legacy magic staves that no longer fit the wood-tier elemental staff system
- obsolete jewelry with retired enchant effects
- old clothing lines that are being replaced by the newer robe-bottom and robe
  family direction
- retired battlestaff/orb and other superseded magic progression holdovers
- stale ranged-shop content that still reflects the old ranged/leather layout
- any quest/shop/drop outputs that still produce retired opal-ring, legacy
  enchanted-jewelry, or other explicitly removed progression items

#### Examples already worth watching

- old magic staff variants that were replaced or demoted to utility
- obsolete skirt-shop and colored-clothing stock if those items are being
  replaced by robe-bottom lines
- retired potion stock, drop entries, and shop purchases
- legacy jewelry items whose old effect identity no longer exists

#### Deliverables

- a removal ledger of active invalid sources by category:
  - shops
  - drops
  - spawns
  - quests/rewards
  - dialogue/exchanges
- source-by-source decisions:
  - remove entirely
  - keep as quest-only compatibility
  - redirect to a replacement item

## Phase 2: Re-tier surviving content sources

After invalid content is removed, audit the remaining live sources for items
that are still valid but no longer sit at the right progression band.

This is expected to be a sweeping pass.

#### Main pressure points

- metal equipment shifted upward because tin and copper now exist below the old
  bronze start
- rune is no longer an old tier-6 endpoint and should not be distributed with
  its old assumptions
- magic equipment has a very different progression shape than the old wizard /
  battlestaff / random-staff mix
- arrows, bolts, darts, knives, and other ammunition now follow the newer
  metal progression
- leather equipment is no longer just an old ranged-shop side line and needs
  its own sensible placement
- robe, cloth, and caster-equipment stock should reflect the new tier model
  rather than old color-themed shop assumptions

#### Audit targets

- NPC drops that still award former “mid/high” items too early because those
  items moved upward by several tiers
- shops whose stock or prices still reflect the old 6-tier ladder
- ranged stores that should now be split or re-authored around bows,
  ammunition, and leather separately
- general stores or specialty stores that sell old progression shortcuts
- quest rewards that now land too high or too low relative to current tiers

#### Deliverables

- a re-tier matrix for live source items:
  - keep source, same band
  - keep source, move to earlier band
  - keep source, move to later band
  - replace with a different item in the same role
- a shop pricing review by family:
  - metal equipment
  - ammunition
  - staffs / magic gear
  - leather / cloth
  - jewelry
  - utility tools where applicable

## Phase 3: Add missing distribution for new tiers

Once invalid content is removed and surviving content is re-tiered, fill the
distribution gaps created by the new progression ladder.

This phase is about intentional placement, not just “make the item obtainable.”

#### Main objective

Ensure the new tier families actually show up in the world in sensible places
instead of existing only in item defs or crafting tables.

#### Main areas to cover

- newly introduced lower tiers:
  - tin
  - copper
- newer mid and high progression families introduced by the expanded ladders
- leather-family additions that need live acquisition paths
- updated ammunition tiers that need both shop and drop placement
- revised staff / robe / magic lines that need coherent source placement
- any standard-line equipment currently craftable but effectively absent from
  normal loot or commerce

#### Deliverables

- missing-tier source map:
  - what tier/family has no practical live inflow
  - whether it should enter through shops, drops, crafting only, or quests
- first-pass distribution rules for where new higher tiers belong:
  - elite shops
  - stronger NPC families
  - quest rewards
  - rare-table vs rare-normal-drop placement
  - unique encounter drops versus broad shared distribution

## Shop Rebalance Track

Shops need their own sub-pass inside Phases 2 and 3 because they are both a
pricing system and a progression/distribution system.

### Shop priorities

1. Remove retired stock.
2. Re-tier surviving stock against the 10-tier baseline and dragon exceptions.
3. Re-price stock around the new progression ladder.
4. Introduce missing tier stock where shops are meant to support progression.
5. Decide which higher-tier items should stay off shops entirely and remain
   drop- or reward-driven.

### Shop categories likely needing direct attention

- skirt / clothing / clothier shops
- wizard and magic-themed shops
- ranged and ammunition shops
- smith/armor/weapon shops
- jewelry or gem-adjacent shops
- general stores that still leak outdated progression items

## Drop Rebalance Track

Drops need a parallel pass because the old tiering assumptions are no longer
safe.

### Drop priorities

1. Remove retired invalid items from drop tables.
2. Re-score surviving drops by current tier, not old item prestige.
3. Revisit rare normal drops now that former endpoint items like rune moved.
4. Place the newer higher-tier items intentionally instead of inheriting old
   rune-era logic.

### Specific drop concerns

- old rune drops likely need widespread review
- old magic-item drops may be completely wrong after the staff/magic overhaul
- ammunition drops should align to current metal progression
- leather and cloth drops may need new families or substitutions
- old potion drops and stock rewards now need replacement with the new potion
  line or non-potion rewards

## Placement Principles

Use these rules during the sweep:

1. Remove invalid content before tuning valid content.
2. Do not preserve a source just because it existed historically.
3. Treat the current 10-tier ladder as the baseline for standard families.
4. Treat dragon or other explicit post-standard items as exceptions, not as
   evidence that the old ladder is still correct.
5. Prefer coherent family placement over scattered nostalgia stock.
6. Keep quest-only compatibility items clearly documented if they survive.
7. Use shops for progression support, not for trivializing higher-tier rewards.
8. Use drops to reflect enemy strength and identity under the new tier model,
   not the old one.

## Recommended Execution Order

1. Build the Phase-1 invalid-source ledger.
2. Sweep potions, magic staves, jewelry holdovers, and obsolete clothing first.
3. Audit shops family-by-family for retired stock and obvious tier mismatches.
4. Audit NPC drops family-by-family for old rune/magic/ammo assumptions.
5. Build a missing-tier distribution map for shops and drops.
6. Place the new tiers intentionally.
7. Revisit quest rewards and special sources after the main economy sources are
   aligned.

## Current Start State

The first concrete Phase-1 ledger has now been started in
[source-cleanup-audit.md](/home/justin/Core-Framework/docs/myworld/source-cleanup-audit.md).

Initial buckets already confirmed as live cleanup targets:

- potion-family stock, certificates, and drop sources
- legacy magic-gear inflow such as `Magic staff` drops and still-live
  battlestaff/orb crafting surfaces
- obsolete clothing and skirt-shop inflow
- early high-tier mismatch candidates such as direct rune shop stock and old
  rune-centric drop-table assumptions

## Immediate Next Implementation Order

Use this order for the first live cleanup changes:

1. Decide which potion-family shop/drop/certificate sources should be remapped
   immediately versus deferred until the full potion economy pass.
2. Retire or explicitly suppress remaining battlestaff/orb acquisition paths if
   they are not meant to survive as active MyWorld content.
3. Rework standard shops around the current authoritative family rules:
   no standard chain, no standard medium helms, no standard square shields,
   no standard metal skirts, and no standard black/white tiers.
4. Start the first rune-era shop/drop re-tier pass once the invalid-source
   removals stop feeding those old items into the world.

## Immediate Next Audit Buckets

When implementation starts, the first buckets should be:

1. potion sources
2. magic-gear sources
3. clothing / robe-bottom / leather shop sources
4. ammunition sources
5. metal-equipment drops and shop stock

That order removes the most obviously invalid inflow first, then moves into the
larger tier-distribution rebalance.
