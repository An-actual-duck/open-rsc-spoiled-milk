# Native layer movement audit

Status: in progress in `fix/layer-transition-audit`, based on published
`18d57f9ec6ea1ff8a8d4b1226e1c675ed5e81920`. The owner requested an independent
worker audit after the Draynor Manor basement ladder fix. No deployment is part
of this assignment; Oracle remains on the manager's server-only v0.3.17.

## Contract and boundaries

`Player.teleport(x, y, bubble)` intentionally preserves the native origin level.
Hard cross-level destinations must use an explicit signed level or the checked
legacy-packed destination API. Existing relative native ladder movement, source
ownership, gameplay checks, cache resets and legacy behavior must be preserved.
This audit must not globally reinterpret ordinary movement or every teleport.

The already-fixed Draynor quest override is the reference failure: reaching the
right X/Y on level -1 is not a return to overworld level 0. Similar errors can
also occur when an override compares native object/player Y against a legacy
packed value, preventing the branch from executing.

## Audit sequence

1. Inventory direct teleports and suspicious packed-Y predicates in maintained
   gameplay plugins and core travel code, retaining exact file/line/arguments.
2. Review generic ladder special cases and quest-specific object overrides
   first. Correlate source objects and destinations with the installed canonical
   map, existing telepoint data and earlier signed-layer fixes.
3. Separate confirmed defects from valid same-level movement, legacy-only
   fallback, inactive content, and cases requiring map-design decisions.
4. Make small fixes only where source, destination and behavior are supported.
   Use offline production plugin/Player/map fixtures to reproduce representative
   failures and validate signed destination, landing safety and existing side
   effects. Preserve the original source/map and all evidence.
5. Finish with an exact pushed READY handoff, a reviewed inventory, results and
   explicit coverage limits. The manager will collect it later.

No GUI, browser, listener, public server, player database or private browser
fixture is used. Long local builds/tests run serially at reduced priority.
Generated evidence belongs under `output/tests/layer-transition-audit/` and
`output/reviews/layer-transition-audit/`; ignored prior evidence is preserved.

## Results

Pending inventory and validation. This initial record does not claim that any
additional route is fixed or certified.
