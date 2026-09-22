# Effect timing and numerical-rule standardization

Status: deferred owner-requested follow-up; not part of current Slayer Tower work.
Recorded: 2026-09-21.

## Reminder trigger

**Bring this back up once Slayer Tower work is done.**

When reviewing completion of the Slayer Tower project, explicitly remind the
owner about this review and propose it as a follow-up. Do not quietly mark the
reminder resolved because one tower mechanic or armor bonus uses clear rules.
This is a project milestone reminder, not a calendar alert or authorization to
begin a broad refactor automatically.

## Why it is needed

Earlier effects were often specified as player-facing durations or percentages,
such as "lasts 3 seconds" or "prevents 20% damage", before internal tick timing
and shared mechanics were considered consistently. The owner wants to review
and tune the accumulated numbers and standardize how these effects work.

## Proposed review scope, for discussion after the tower

- Inventory existing buffs, debuffs, armor/weapon effects and relevant enemy
  mechanics with their actual implementation values and intended behavior.
- Establish explicit timing units and boundary rules: combat ticks versus real
  time, rounding, activation, expiration and action scheduling. Do not assume
  every real-time effect must be converted to ticks.
- Establish refresh, reapplication, stacking, caps, cooldown and immunity rules,
  including interactions between multiple sources.
- Define percentage bases, calculation order and rounding for damage reduction,
  bonus damage, retaliation and healing; record which primary/secondary damage
  sources qualify and what happens on zero damage.
- Review poison accumulation/clearance and other shared status behaviors,
  including how armor, consumables and other counters combine.
- Propose consistent terminology, tuning rules and player-facing descriptions,
  supported by deterministic timing and interaction tests.

These are review topics, not approved replacement formulas or balance values.
Audit and discuss before changing existing mechanics; this may materially alter
combat balance and should have its own focused implementation plan.

## Current-work boundary

### Explicit owner directions added during leather design

- Make poison **cleanse rate** the principal counter rather than prevention.
  Nature necklaces already add poison-power removal at the existing poison
  event cadence. Plan tiered cleansing potions in place of anti-poison
  potions, with stacking across sources; exact numbers/rules remain open.
- Standardize **Slow** as tick-based action delay, especially for the future
  stacking weapon coating. Giant-frog Sticky Skin specifically delays only
  the next attack by one tick on a 10% on-hit proc; it does not implicitly
  root movement or lock every action.
- [Set-bonus design](../rough-drafts/slayer-leather-set-bonuses.md) records the
  immediate armor scope. Carapace cleansing replaces its offensive poison
  procs and removes magic-spider's leather magic-penalty exception.

These directions do not select numerical cleanse scaling, stacking caps or
Slow immunity rules, and do not authorize the deferred game-wide overhaul now.

The bounded [Slayer leather coverage/theme review](../rough-drafts/slayer-hide-and-leather-overhaul.md#material-coverage-and-shared-armor-themes)
is current planning work. In particular, all carapace families are intended to
increase the wearer's poison cleanse rate, with exact mechanics still to be
decided. That theme does not bring this game-wide standardization project into
the tower's scope or make it a prerequisite for finishing the tower.
