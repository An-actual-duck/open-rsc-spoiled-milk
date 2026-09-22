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

## Debuff power versus cleanse rate: owner clarification

The intended shared model is a contest between **power being applied** and
**power being removed**. Applications build a debuff's accumulated power;
power thresholds determine its effective strength. Cleansing reduces that
power over time, allowing effects to weaken as they fall below thresholds.
Use **cleanse rate** as the player-facing term for this removal rate.

Armor, jewelry and cleansing potions should be able to contribute to that
rate. The exact combination formula, caps and timing boundaries remain to be
designed. This is not a request to make incoming application fail, nor to
speed up damaging poison ticks. The intended gameplay is applying poison
faster than an opponent can cleanse it, or cleansing faster than it builds.

### Poison: verified existing arithmetic

`PoisonEvent` uses integer division before its `Math.round` call, so for
nonnegative poison power the damage is effectively `floor(power / 10)`.
Thus 10–19 power yields 1 damage and 20–29 yields 2. With no further
application and baseline cleansing, a resolution at 20 power deals 2 damage
and reduces power to 17; the next resolution deals 1 damage. Damage is
calculated before that resolution's removal, not after it.

The current resolution interval is 8 game ticks and baseline removal is 3
power per resolution. Nature necklaces add their tier to that removal amount.
`PoisonPowerReduction.shouldCure` treats power below 10 as cured; preserve
this as an observed current rule, not an implicit decision that all future
debuffs must discard below-threshold power in the same way.

Repeated application is intended to keep pushing accumulated power upward.
The later audit must trace the actual application/refresh/cap rules of each
existing poison source; checking the damage formula alone does not establish
that every current application source already behaves consistently.

References: `server/src/com/openrsc/server/event/rsc/impl/PoisonEvent.java`,
`server/src/com/openrsc/server/content/PoisonPowerReduction.java`, and
`EnchantingItemEffects.getNatureCleansingPoisonDecayBonus`.

### Slow: proposed extension for the weapon coating

The future coating applies accumulating **slowing power**. As it accumulates,
the NPC becomes progressively "slimed up". The owner's illustrative threshold
is **20 power per additional tick of action delay**: at 20 power, its actions
are delayed by one turn/tick; at higher effective thresholds it becomes more
slowed, subject to limits still to be chosen. This is broader than delaying
only an attack: the intended scope is **all of that NPC's actions**.

Twenty is an example, not a finalized balance constant. This latest example
supersedes treating any earlier illustrative 50-point threshold as fixed.
Review exactly which schedulers/actions count, whether delay affects already
scheduled actions or subsequent cadence, movement handling, threshold
crossings, cleanse intervals, residual power, maximum delays, immunity and
multi-attacker accumulation. Increasing power must not be allowed to defer
the same queued action indefinitely by repeatedly rescheduling it.

### Post-Slayer review sequence

1. Establish the shared vocabulary, power thresholds, application rules and
   cleanse-rate calculation, using current poison as the initial reference.
2. Audit existing slows and slow-adjacent effects for suitable conversions.
   Candidate review entries include weapon action delays (Abyssal Whip),
   Sticky Skin, stagger/frostbite and action-lock or root mechanics. Record
   actual behavior before deciding; a root or one-off delay is not
   automatically the same as accumulating Slow.
3. Assess other existing debuffs for the same build-up-versus-cleansing model.
   Inventory their current behavior and identify which benefit, which should
   retain distinct mechanics, and what balance/migration work each needs.
   Do not convert every status merely to make it uniform.
4. Propose tuned values and tests for repeated application, combined cleansing,
   caps, threshold crossings, event order and recovery before implementation.

These are deferred design/audit tasks, not authorization to perform that audit
or change gameplay during current Slayer work. Keep the reminder above active.

## Enemy-spell element tags

Add a comprehensive enemy-spell element-tag audit to the post-Slayer work.
The immediate motivation is Naga armor's **Cold Blooded** bonus: 20% damage
reduction from ice and fire magic. The owner believes ice tagging is present
but is unsure whether all enemy spells have appropriate tags. This is an
unverified expectation, not an audit result.

- Inventory ordinary enemy spells and custom/scripted magic attacks, including
  secondary, AOE and periodic damage paths, and record their intended element.
- Trace whether that identity reaches damage settlement and resistance checks;
  a projectile or spell name alone is not a reliable semantic tag.
- Find missing, incorrect or lost tags and decide how truly untyped magic is
  handled. Distinguish ice from other water magic and explicitly classify
  special cases such as dragonbreath rather than guessing from visuals.
- Add resistance tests proving intended ice/fire hits are reduced and
  unrelated attacks are not. Define ongoing-damage and mitigation-order rules.

This note does not authorize an immediate broad combat refactor. Cold Blooded
implementation now has bounded verification of the common tagged projectile
paths, explicit ICE versus WATER NPC profiles, and player ice/fire producer
tags. See [implementation coverage](../rough-drafts/slayer-leather-effects-implementation.md).
The global audit of scripted/secondary/periodic sources remains open.

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
increase the wearer's poison cleanse rate. Full-set +2/+3/+5 additive removal
is now the documented implementation default for scorpion/spider/magic-spider;
future potion strengths and global stacking/caps remain undecided. That does
not bring this game-wide standardization project into
the tower's scope or make it a prerequisite for finishing the tower.
