# Production UI Tasklist

## Summary

Replace the old text-menu production flow for multi-output actions with a
material-driven item-selection window.

The first implementation target is smithing:

- use a bar on an anvil
- show the items craftable from that specific bar
- grey out outputs the player cannot currently make
- select an output without starting production
- choose quantity in-window
- press `Start` to begin batching

This same pattern should later cover other multi-output material workflows such
as:

- needle + leather
- needle + ball of wool
- knife + logs
- soft clay + pottery wheel
- glassblowing pipe + molten glass
- gold bar + furnace
- silver bar + furnace
- other future crafting/enchanting production sets with one input and many
  possible outputs

Single-output actions should keep the current simpler flow and should not open a
menu.

## Locked Direction

- The menu opens from the material-on-station interaction, not from clicking the
  station by itself.
- The input material determines the recipe list.
- No top-level material tabs are needed in the first pass.
- Recipes should be displayed as item icons in a grid.
- Clicking an icon selects it and highlights it.
- Clicking an icon does not begin crafting.
- A quantity selector sits at the bottom of the window.
- Quantity controls should use left/right arrows:
  - `-5`
  - `-1`
  - current quantity
  - `+1`
  - `+5`
- A `Start` button sits to the right of the quantity controls.
- The server remains authoritative on recipe validity, level checks, resources,
  and batch stopping conditions.
- If the player cannot craft anything at all from the input material, do not
  open the window. Show:
  - `You are not skilled enough for that yet`

## Status Key

- `[todo]` not started
- `[doing]` in progress
- `[done]` completed
- `[blocked]` waiting on a dependency or design decision

## Phase 1: Protocol Foundation

Note:

- The current live MyWorld client path only uses the production-window
  `Start` and `Close` interface options.
- Earlier `select recipe` and quantity-step option ids still exist in the
  shared interface-options enum for protocol compatibility, but the server now
  treats them as stale legacy input rather than part of the live production
  flow.

- `[done]` 1. Audit the unused `DoSkillInterface` client path and define exactly
  which parts should be reused versus replaced.
- `[done]` 2. Add a dedicated outbound packet for opening/updating the
  production UI from the server.
- `[doing]` 3. Reuse the existing `INTERFACE_OPTIONS` inbound path for
  production-window actions.
- `[done]` 4. Add new interface-option ids for:
  - select recipe
  - adjust quantity down by `1`
  - adjust quantity down by `5`
  - adjust quantity up by `1`
  - adjust quantity up by `5`
  - start production
  - close production window
- `[done]` 5. Add a server-side session model that tracks:
  - source skill or station context
  - input material id
  - recipe list
  - selected recipe
  - selected quantity

## Phase 2: Client Window Rewrite

- `[doing]` 6. Rewrite `DoSkillInterface` so it is data-driven instead of using
  hardcoded `skillToDo` string branches.
- `[done]` 7. Render recipe icons from packet-driven recipe data.
- `[done]` 8. Show disabled or greyed-out recipes when:
  - required level is too low
  - required materials for that output are unavailable
- `[done]` 9. Add persistent selection highlight state.
- `[done]` 10. Replace the unfinished right-click make menu with the new bottom
  quantity controls.
- `[done]` 11. Add the `Start` button and close button behavior.
- `[done]` 12. Show recipe detail text for the selected output:
  - item name
  - required level
  - material cost

## Phase 3: Smithing Pilot

- `[done]` 13. Refactor smithing recipe collection so the server can build a
  flat recipe list for a specific input bar.
- `[done]` 14. Replace the current smithing `multi(...)` chain with the new
  production window for normal bar-on-anvil flows.
- `[doing]` 15. Keep special smithing one-offs outside the first window pass
  where needed:
  - dragon square combine
  - gold bowl
  - any other single-output/special-case path that does not fit the generic
    recipe grid cleanly yet
- `[done]` 15a. Fold ordinary bar-specific smithing outputs into the new
  window when they are still just another product of the input material:
  - bronze wire from bronze bars
  - nails from steel bars
- `[done]` 16. Make `Start` trigger the existing batch smithing path using the
  selected recipe and selected quantity.
- `[done]` 17. Stop smithing batches when:
  - quantity is complete
  - bars run out
  - interruption occurs

## Phase 4: Crafting Migration

- `[done]` 18. Move needle + leather onto the same production window pattern.
- `[done]` 19. Move needle + ball of wool onto the same production window
  pattern.
- `[done]` 20. Move gold-bar furnace jewelry selection onto the same
  production window pattern for unstrung jewelry outputs.
- `[done]` 21. Move silver-bar furnace jewelry selection onto the same
  production window pattern.
- `[done]` 22. Move knife + logs onto the same production window pattern for
  arrow shafts, bows, and staff shaping.
- `[done]` 23. Move soft clay + pottery wheel onto the same production window
  pattern for pottery shaping outputs.
- `[done]` 24. Move glassblowing pipe + molten glass onto the same production
  window pattern for glass outputs.
- `[todo]` 25. Keep single-output crafting actions on the current direct flow.
- `[doing]` 26. Continue auditing remaining production plugins for any legacy
  multi-output menus that still fit the new one-input, many-output model.

## Phase 5: Validation And Polish

- `[todo]` 27. Add targeted tests or validation for smithing recipe generation
  and selection payloads.
- `[todo]` 28. Verify disabled states match server-side level/resource truth.
- `[todo]` 29. Verify batch start, cancel, and resource exhaustion behavior from
  the new UI.
- `[todo]` 30. Revisit search, filters, or richer navigation only after the
  core material-driven window is stable.

## Current Recommendation

The material-driven production window is now the live baseline for:

- bar + anvil smithing
- needle + leather crafting
- needle + ball of wool crafting
- knife + logs Crafting for arrow shafts, bows, crossbows, and staff shaping
- soft clay + pottery wheel pottery shaping
- glassblowing pipe + molten glass crafting
- gold-bar furnace jewelry selection
- silver-bar furnace jewelry selection

The next expansion should keep moving horizontally into other one-input,
many-output production sets instead of adding more ad hoc menus, while treating
single-output actions as intentionally out of scope for the window.
