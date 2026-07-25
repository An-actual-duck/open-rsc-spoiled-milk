# Bank Tag Filter Plan

Status: deferred planning note.

This feature is intended as a lower-risk bank quality-of-life improvement, not an immediate implementation target. The goal is to keep the idea visible while avoiding work that could destabilize the bank, which is core item storage for players.

## Goal

Add optional bank filters based on item tags. Major items would receive one or more tags such as skills, resource categories, or quest relevance. Players could then narrow the visible bank contents without changing how their bank is stored.

Examples:

- Lobster: Fishing, Cooking, Food
- Yew logs: Woodcutting, Fletching, Firemaking, Resource
- Gold bar: Smithing, Crafting, Resource
- Law rune: Magic, Runecraft, Resource
- Quest-only items: Quest

The existing bank search remains the fallback for items that are not tagged or for direct name lookup.

## Current Bank Shape

The current bank is a single ordered list on the server. The client receives that list, stores it locally, and renders it into pages. Current page buttons are not true tabs; they are slices of the same flat item list.

Because of that, the safest filter model is view-only:

```text
server bank list
  -> client bankItems
    -> selected tag filters
      -> filtered currentItems
        -> draw page of 48
```

This means the filter system should not alter storage, item counts, bank slot ownership, or save/load behavior.

## Proposed UX

Add a secondary panel beside the current bank window. The panel title should be `Filters`.

The filter panel contains checkboxes for supported tags:

- Skills: Mining, Smithing, Woodcutting, Fletching, Fishing, Cooking, Magic, Runecraft, Crafting, Worship, Summoning, and other relevant skills.
- Broad categories: Resources, Combat, Food, Quest, Tools, Runes, Jewelry, Armor, Weapons.

Default state:

- No filters selected.
- No selected filters means show everything, equivalent to all filters being allowed.

Selection behavior:

- One or more selected filters narrows the visible bank list.
- First implementation should use OR behavior: show an item if it has any selected tag.
- Reset the selected bank item when filters change.
- Page count and page buttons should be based on the filtered visible item list.

## Safety Rules

First implementation should avoid changing the stored bank model.

Do:

- Keep server bank storage as the existing flat list.
- Keep deposit and withdraw logic unchanged.
- Treat filters as client-side display state.
- Clear selected item when filters change.
- Show all items when no filters are selected.

Do not do in the first version:

- Do not split the bank into separate server-side tab containers.
- Do not change bank save/load format.
- Do not reorder or sort items automatically when a filter is selected.
- Do not allow drag/reorder operations against filtered indexes unless they are carefully mapped back to real bank slots.

## Implementation Notes

The most important technical concern is slot identity. The current UI works with indexes into the displayed item list. A filtered item index may not match the real server bank slot. Withdraw and deposit paths are mostly item-id based, but any swap, insert, or order-changing behavior must use real bank slots.

For a safe first pass:

- Store tag definitions in client-accessible data.
- Build a lookup from item id to tag set.
- Apply filters while building the client-side visible bank list.
- Preserve the original bank item slot id on each visible entry for future operations.
- Disable or avoid reordering while any filter is active.

Longer term, if server-side sorting or persistent filters are desired, move tag definitions into shared/generated data so both client and server can use the same tag set.

## Tag Data Strategy

Start with a practical tag set rather than trying to tag every item.

Suggested first pass:

- Skill resources and products.
- Tools.
- Runes and magic equipment.
- Food and cooking inputs.
- Combat gear by broad category.
- Quest items.

Items can have multiple tags. Tags should be additive and descriptive, not exclusive.

The initial tagging pass will be the largest manual cost. It should be done in batches and protected by lightweight tests that confirm important item families have expected tags.

## Risk Level

Low risk:

- Client-only display filters.
- Side panel checkboxes.
- No storage changes.
- No bank packet changes.

Medium risk:

- Server-side sorting using tags.
- Persisted favorite filters.
- Reordering visible filtered items.

High risk:

- True server-side tab containers.
- New bank save/load shape.
- Changing bank packet structure.
- Automatic categorization that moves player items without explicit confirmation.

## Recommended Phasing

1. Add client-side tag data and a hidden/internal filter function.
2. Add the side filter panel with checkboxes.
3. Support view-only filtering with no selected filters meaning show all.
4. Add tests for important tagged item families.
5. Later, consider sort commands or manual tabs only after the filter model is stable.

This gives useful quality-of-life without putting the reliability of bank storage at risk.
