# MyWorld Pre-Feature Hardening Checklist

This checklist covers the cleanup work that is worth finishing before adding
more major `MyWorld` content or systems.

The goal is simple: reduce duplication, lock in the current source-cleanup
direction, and tighten the fork boundary so future content is cheaper to add
and safer to rebalance.

## Priority 1: Lock Existing Cleanup In Place

- `[doing]` Keep a concrete source ledger in
  [source-cleanup-audit.md](/home/justin/Core-Framework/docs/myworld/source-cleanup-audit.md).
- `[doing]` Add dedicated regression coverage for retired source paths:
  - old jewelry enchant spell redirects
  - dragonstone fountain redirect
  - crown crafting hidden from live gold-jewelry flow
  - known remaining quest-reward holdovers kept explicit
- `[todo]` Expand the same validation model to cover:
  - shops
  - drops
  - world spawns
  - admin/dev kits
  - replacement dialogs

Why first:

- this prevents old acquisition paths from silently coming back while feature
  work continues

## Priority 2: Finish The Generator Layer

- `[todo]` Decide whether `generators.json` is meant to stay minimal or become a
  truly manifest-driven workflow layer.
- `[doing]` If it stays minimal, remove unused or half-used fields and simplify
  the runner.
- `[todo]` If it becomes fully manifest-driven, make the runner and generators
  consume the manifest consistently instead of validating fields that are barely
  used.
- `[todo]` Add one small regression check that protects the final chosen shape.

Primary files:

- [`run-generators.py`](/home/justin/Core-Framework/tools/generators/run-generators.py)
- [`generator_common.py`](/home/justin/Core-Framework/tools/generators/generator_common.py)
- [`generators.json`](/home/justin/Core-Framework/tools/generators/generators.json)

## Priority 3: Remove Parallel Manual Mapping Tables

- `[todo]` Replace the large hand-maintained altar/jewelry/staff/wool mapping
  tables with a more declarative registry or generated data source.
- `[todo]` Have runtime helpers and tests read from the same source of truth.
- `[todo]` Keep special-case behavior explicit, but stop encoding normal tier
  ladders in multiple places.

Primary files:

- [`EnchantingItemEffects.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/content/EnchantingItemEffects.java)
- [`ItemDefsCustom.json`](/home/justin/Core-Framework/server/conf/server/defs/ItemDefsCustom.json)
- [`test-enchanting-data.py`](/home/justin/Core-Framework/tests/myworld/test-enchanting-data.py)

Why before more content:

- every new altar family or jewelry/staff variant currently multiplies edit and
  audit cost

## Priority 4: Tighten The MyWorld Boundary

- `[todo]` Keep moving fork-owned behavior into
  [`custom/myworld`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/custom/myworld).
- `[todo]` Reduce direct `MyWorld` rules embedded in broad shared handlers where
  a delegation hook or small service would work instead.
- `[todo]` Replace the remaining compatibility bridge once downstream callers no
  longer require it, or add stronger parity checks while it still exists.

Primary files and areas:

- [`PeelingTheOnion.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/custom/quests/free/PeelingTheOnion.java)
- shared `server/src` handlers
- shared `authentic` plugins carrying MyWorld-specific rules

## Priority 5: Stabilize The Production UI Layer

- `[doing]` Tighten the production-session model before more crafting and
  enchanting flows adopt it.
- `[doing]` Replace loosely coupled parallel arrays and integer type tags with a
  slightly stronger typed model where practical.
- `[doing]` Add focused tests for production menus that now represent live
  progression rules.

Primary files:

- [`ProductionSession.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/content/production/ProductionSession.java)
- [`ProductionRecipe.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/content/production/ProductionRecipe.java)
- [`ProductionInterfaceStruct.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/net/rsc/struct/outgoing/ProductionInterfaceStruct.java)

## Recommended Order

1. Lock cleanup and retirement rules with source-level regression checks.
2. Finish the generator-layer simplification decision.
3. Replace manual mapping tables with a single source of truth.
4. Tighten the namespace and compatibility boundary.
5. Then continue on broader content/features.

## Change Log

- `2026-04-03` Added the first explicit pre-feature hardening checklist to make
  structural cleanup work visible before the next content push.
- `2026-04-03` Started production-layer hardening by centralizing production
  payload assembly, making production-session recipe lists immutable, and adding
  a focused regression check for the live production UI model.
- `2026-04-03` Tightened the runtime production path so session starts validate
  recipe ids and quantities, and stale legacy production option packets are now
  treated as invalid instead of silently ignored.
- `2026-04-03` Added focused production behavior regression checks for default
  recipe selection, recipe gating, and representative disabled-state truth
  across the live smithing, crafting, and fletching production flows.
- `2026-04-03` Added production flow-contract regression checks for quantity
  clamping, event scheduling, and handoff from the production window into the
  existing batch execution paths.
