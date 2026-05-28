# NPC Stat Migration Plan

This plan covers how to move NPCs from the legacy `attack/strength/defense`
model into the new MyWorld combat model without hand-tuning every NPC first.

## Goal

Derive sensible baseline NPC combat stats automatically from existing NPC
definitions, then use explicit per-NPC overrides only where needed for bosses,
special encounters, and edge cases.

## Current state

NPCs have already moved onto a usable baseline migration path:

- NPCs still load legacy `attack`, `strength`, `defense`, and `hits`
- melee offense currently comes from legacy `strength`
- melee defense now uses explicit `meleeDefense` when set, otherwise it derives
  from legacy `defense`
- ranged and magic defense now also derive from legacy `defense` when not
  explicitly overridden
- NPC offense is also softened by:
  - NPC offense rounding down
  - low-biased NPC damage rolls

That means the remaining gap is no longer "make non-overridden NPCs functional";
it is "finish audit-conformance family baselines and then tune explicit
exception cases so displayed threat and runtime stats feel intentional".

The current rule for that family pass is:

- [npc-family-audit.md](/home/justin/Core-Framework/docs/myworld/npc-family-audit.md)
  is the source of truth for broad family ratios
- family defaults are definitive unless an NPC is explicitly called out as a
  per-NPC exception
- at least one combat style must remain at `1.0`
- nothing should exceed `1.0`
- full encounter balance is intentionally deferred until the migration is fully
  in place; later tuning can focus on player damage output and formula shaping
  instead of weakening family baselines during the migration

## Design principle

Use legacy NPC stats as source data, but convert them into the new runtime model
automatically.

Do not keep relying on old stats directly in combat forever.

Instead:

1. read legacy NPC stats from defs
2. derive new runtime combat values from them
3. let explicit MyWorld NPC overrides replace those derived values when needed

## Target runtime model

Each NPC should end up with runtime values for:

- `melee_offense`
- `melee_defense`
- `ranged_defense`
- `magic_defense`
- `hits`
- displayed `combatLevel`

Displayed `combatLevel` should remain a separate explicit field.

## Baseline automatic conversion

### 1. Melee offense

Use legacy `strength` as the baseline source for melee offense.

Phase 1 rule:

- `derived_melee_offense = legacy_strength`

This matches the current combat direction and keeps NPC offense easy to reason
about.

### 2. Melee defense

Use legacy `defense` directly as the baseline source for melee defense.

Phase 1 rule:

- `derived_melee_defense = floor(legacy_defense * 1.0)`

This keeps melee defense as a `1:1` carry-over from legacy data for the first
balance pass.

### 3. Ranged defense

NPCs need real ranged defense even if they never had it before.

Recommended phase 1 rule:

- derive ranged defense from legacy `defense` using a style multiplier

Initial simple rule:

- `derived_ranged_defense = floor(legacy_defense * ranged_multiplier)`

Initial starting multiplier:

- `0.5`

This avoids all non-overridden NPCs being free targets for ranged while keeping
them broadly weaker to ranged than melee during the first pass.

### 4. Magic defense

NPCs also need real magic defense.

Recommended phase 1 rule:

- derive magic defense from legacy `defense` using a style multiplier

Initial simple rule:

- `derived_magic_defense = floor(legacy_defense * magic_multiplier)`

Initial starting multiplier:

- `0.5`

This gives every NPC at least some baseline resistance until family-specific
rules are added.

### 5. Hits

Keep legacy `hits` directly.

- `derived_hits = legacy_hits`

### 6. Attack

Keep legacy `attack` stored in data for compatibility/reference, but do not use
it in the new combat formulas.

## Implemented baseline

### 1. Explicit derived-stat helpers in NPC runtime

File:

- [Npc.java](/home/justin/Core-Framework/server/src/com/openrsc/server/model/entity/npc/Npc.java)

`Npc.java` now contains helper-backed accessors for:

- derived melee offense
- derived melee defense
- derived ranged defense
- derived magic defense

These accessors:

- prefer explicit MyWorld override values if present
- otherwise derive from legacy stats

This landed and is the current runtime baseline.

### 2. Stop fallback mixing in combat accessors

The live accessors now use:

- explicit new value if present
- otherwise derived new value

This is already true for melee, ranged, and magic defense.

### 3. Centralize derivation constants

The derivation constants currently live in `Npc.java` and cover:

- melee defense multiplier
- ranged defense multiplier
- magic defense multiplier

Recommended baseline multiplier model:

- `melee = floor(defense * 1.0)`
- `ranged = floor(defense * x)`
- `magic = floor(defense * x)`

This keeps all three styles anchored to the same legacy defense stat while
making future style identity easy to tune by changing the multiplier per style or
per NPC family.

### 4. Keep explicit override layer on top

File:

- [NpcDefsMyWorld.json](/home/justin/Core-Framework/server/conf/server/defs/NpcDefsMyWorld.json)

This remains the exception layer for:

- bosses
- demons
- dragons
- undead families
- wizard/mage families
- quest NPCs
- anything with unusual encounter identity

The override file is no longer required just to make normal NPCs functional.

## Remaining follow-up

### 5. Revisit displayed combat levels separately

Do not tie displayed NPC combat level directly to derived offense/defense yet.

Keep using explicit NPC `combatLevel` until combat tuning settles.

After the new runtime model is stable, decide whether to:

- preserve existing displayed levels
- rebalance displayed levels manually
- or derive new displayed levels from MyWorld stats

## Family-based follow-up pass

After generic derivation is in place, add broad family rules before per-NPC
micromanagement.

Examples:

- undead:
  - higher ranged defense
  - lower magic defense
- demons:
  - above-average magic defense
- dragons:
  - above-average melee, ranged, and magic defense
- wizard/mage families:
  - higher magic defense
- armored humanoids:
  - above-average melee defense
- animals/beasts:
  - lower total defense, rely more on offense/hits

These should be implemented as group-level tuning on top of the baseline
derived stats.

Long-term target:

- not every NPC should be weaker to magic and ranged by the same amount
- some enemies should be weak to melee
- some should be weak to ranged
- some should be weak to magic
- some should be weak to multiple styles
- some should stay broadly even

Using `floor(defense * x)` per style is the intended foundation for this.

First live benchmark-family pass now in place:

- skeletons:
  - higher ranged defense
  - lower magic defense
- zombies:
  - lower melee defense
  - higher magic defense
- demons:
  - lower ranged defense
  - higher magic defense
- dragons:
  - broadly sturdy
  - especially stronger against magic
- battle mages:
  - lower melee/ranged defense than their magic defense

## Suggested first-pass formulas

These are starting points, not locked rules.

- `melee_offense = legacy_strength`
- `melee_defense = floor(legacy_defense * 1.0)`
- `ranged_defense = floor(legacy_defense * 0.5)`
- `magic_defense = floor(legacy_defense * 0.5)`

Why this shape:

- offense stays readable and close to existing NPC identity
- melee defense preserves existing NPC sturdiness for the first pass
- ranged and magic stop being mostly undefended by default
- the multiplier model leaves room for real per-style enemy identity later
- explicit override values can still replace any of these

## What not to do

- do not hand-edit every NPC before automatic derivation exists
- do not keep old and new defense logic mixed indefinitely
- do not use displayed `combatLevel` as the combat formula input
- do not use legacy `attack` as a substitute for a proper new-model offense stat

## Phase 1 success criteria

This migration is successful when:

- ordinary NPCs no longer need manual overrides just to feel functional
- demons, dragons, undead, and humanoids have reasonable baseline resistance
- lesser demon style encounters feel appropriately dangerous again
- explicit overrides are only needed for intentional encounter design, not
  basic parity repair
