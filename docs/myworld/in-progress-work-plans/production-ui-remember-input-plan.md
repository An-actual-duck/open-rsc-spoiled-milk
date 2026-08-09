# Production UI Remember Last Input

Status: implementation and focused automated validation complete; live visual
acceptance pending. The companion Keep open preference is implemented with the
same account-scoped production UI protocol.

## Goal

Add an opt-in quality-of-life setting to the modern production interface. A
player may remember the last recipe that successfully began for each distinct
production activity or workstation, then return to that route without
repeating intermediate category choices.

## Accepted behavior

- `Remember last input` appears at the bottom of production interfaces and is
  off by default.
- The preference and remembered routes are stored in the existing per-account
  player cache.
- Anvils and furnaces use stable, separate workstation identities. Other
  production activities use a stable key composed from interface type, input
  item, and normalized title.
- A route contains recipe/item IDs only. Quantity always opens at `1` and is
  never persisted.
- A recipe is recorded only after the existing production callback reports a
  successful start. Merely selecting an item or entering a category does not
  overwrite the saved route.
- Reopening traverses saved picker choices using the same server-side callback
  used for a player click. Existing level, material, membership, access, and
  output-support checks therefore remain authoritative.
- Missing, corrupt, overlong, inaccessible, or no-longer-level-permitted routes
  stop at the closest valid screen. That screen uses its normal safe default
  when the saved selection itself is unavailable.
- Nested eligible production screens expose a server-driven `Back` button.
- Teleport destinations and both Rangers Guild redemption screens neither show
  the setting nor participate in route memory.
- `Keep open` is a separate, off-by-default account preference. When enabled,
  a successfully started final production batch keeps its existing screen open
  after the start request so the player can choose a different recipe when the
  batch is finished. A second start while busy is refused; the close button
  still clears the session immediately.

## Implementation map

- `ProductionMemory` owns transient navigation frames and versioned account
  cache keys.
- `ActionSender.showProductionInterface` asks that service which screen and
  selected recipe should be sent. Intermediate restore screens are suppressed
  so reopening presents only the deepest valid result.
- `InterfaceOptionHandler` starts transition tracking before calling a
  production plugin and records only a confirmed final start afterward.
- Production packet 138 has an additive one-byte UI-flags trailer for remember
  support, current preference, and Back availability.
- Interface options 24 and 25 carry the preference toggle and Back action.

## Compatibility considerations

- No database migration is required. Cache entries use the versioned keys
  `prod_remember_v1` and `prod_route_v1_<activity>`. Activity names that would
  exceed the existing 32-character database-key limit use a stable truncated
  SHA-256 suffix; the short `anvil` and `furnace` keys remain readable.
- The packet-138 flags are appended after all existing recipe data. Older
  custom clients finish parsing the known recipe payload and ignore the
  trailer. The updated client also checks packet length, so an older server
  produces the existing interface with this feature disabled rather than an
  under-read.
- Original/retro clients retain their existing production paths; this feature
  only augments the modern custom production interface.
- Renaming a generic production interface title may intentionally produce a
  new activity key. The old cache row becomes inert and the UI falls back to
  its normal default. The explicit `anvil` and `furnace` keys are unaffected by
  title changes.
- Disabling the preference stops restore and future recording but does not
  delete prior routes. Re-enabling it resumes from the last successful route.

## Regression coverage

Focused tests cover:

- preference default and typed account-cache persistence;
- separate anvil and furnace routes;
- deepest-screen restore, server-owned Back navigation, and successful-start-
  only recording;
- route payloads containing no quantity;
- malformed and over-depth route fallback;
- missing-material parent fallback and no-longer-level-permitted fallback;
- exclusion of teleport and Rangers Guild interface types;
- stable workstation identities and separation of generic activities;
- valid remembered selection, unavailable-selection fallback, UI flags, and
  quantity reset to one.

Validation commands:

```bash
cd server && ./gradlew test --tests com.openrsc.server.content.production.ProductionMemoryTest
./scripts/build-server.sh
./scripts/build-client.sh
```

## Manual acceptance pass

1. Confirm every eligible production interface opens with the checkbox off on
   an account without the new cache key.
2. Enable it at an anvil, choose a metal and item, start a non-default quantity,
   then reopen the anvil. Confirm the item screen and recipe return but quantity
   is `1`.
3. Use Back to return to the metal picker and choose a different route.
4. Repeat at a furnace with both a bars path and a category/material path;
   confirm it does not replace the anvil route.
5. Remove a required mould/material before reopening. Confirm restore stops at
   the nearest usable parent and cannot bypass the existing validation.
6. Log out and reconnect to confirm account persistence.
7. Confirm Law Jewelry teleport destinations and both Rangers Guild redemption
   screens have no checkbox or Back behavior.
