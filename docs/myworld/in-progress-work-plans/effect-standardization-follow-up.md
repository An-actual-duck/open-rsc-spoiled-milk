# Effect timing and numerical-rule standardization

## Current implemented boundary — November 2, 2026

Shared Stagger/critical outcomes, Cosmic combat luck and the bounded Whip/Sticky
attack-delay interaction are integrated. Zamorak's Malice/Cruelty/Ruin now use
the shared critical/Stagger/execution paths and are required release content.
All 15 battleaxe coatings are implemented: one level-46 single-use recipe,
permanent application/wiping, 4 Slow power with flat cap 12, and half-standard
weapon poison contributions. Shared Slow and poison/antidote cleansing are
implemented; their broader standardization remains a separate follow-up.

Use the [current release checklist](next-major-release-goals.md#release-closeout-checklist)
for the bounded task-activation/Deathwing corrections, combined validation and
ordinary-player acceptance. This document's older proposals do not create new
release requirements or reopen settled recipes/values. See the
[coating implementation](battleaxe-coatings-implementation-review.md) and
[Zamorak implementation](zamorak-hostile-implementation-review.md) for current receipts.

## Implemented shared attack foundation

The integrated attack foundation replaces separate Ogre/Startle loss flags with
one actor-owned Stagger state. All existing sources retain one attack; the API
accepts an explicit count and rejects replacement while losses or five normal
recovery attacks remain. Count actual admitted attacks, including natural zero;
never count scheduler polls or projectile impacts again. Suppression snapshots
at melee strike, projectile launch or custom telegraph commitment. An already
committed projectile/telegraph is not retroactively canceled by a new Stagger.
Death/respawn/logout lifetimes clear the state.

Ogre's full-set20% and Thunder Startle's7/15/25% authored base chances remain
independent of critical hits. Eligible Player-owned checks now receive combat luck;
the existing Stagger recovery/admission rules still prevent stacked applications. Cosmic now supplies combat luck, not critical chance; explicit immutable outcomes distinguish true crits, normal maxima and
forced zero. The implemented Zamorak hostile chain uses those explicit outcomes;
only eligible authored criticals can trigger its Stagger/execution components.

Whip keeps its 10% proc, one-tick movement pause and three following immunity
ticks. Sticky keeps its 10% qualifying-hit, next-attack-only delay. Each source
can own one actor-wide extra-delay token. Otherwise-ready attacks latch the sum
after their ordinary base/Slow deadline; polls and event/style changes cannot
multiply, clear or hide tokens. A source arriving during a latched wait extends
that same deadline once. Ordinary style cooldowns stay with their existing
owners. Each owner records the exact Slow tier already included in its interval.
At attack admission, only a newly uncaptured current tier delta is added to the
shared wait: fresh Slow1 + Whip + Sticky adds three ticks to the next otherwise-
ready attack, while captured Slow1 becoming Slow2 adds only one further Slow
tick. Polling/style changes cannot duplicate credit; decay cannot shorten a
latched wait. This upcoming-attack policy is the manager's disclosed working
interpretation of the owner's three-ticks example. The optional clarification
question had not received an explicit answer when implemented; it is not
recorded as a separate owner answer. Reciprocal melee's existing Slow exclusion
and utility spell timing stay unchanged.

See [implementation and validation](shared-critical-stagger-foundation-review.md).
Historical proposals below remain context rather than approval of a larger
game-wide status overhaul.


## Historical broader follow-up request — September 21, 2026

Status: the broader owner-requested review remains deferred. The dated proposals
below are preserved history; bounded implemented exceptions are summarized above.
They are not unfinished prerequisites for this release.

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

**Historical examples below are superseded for the existing combat Slow foundation.**
The approved rule is 10 power per tier, bounded source caps 12/22, and two power
removed every four game ticks, affecting subsequent combat action cadence only.
See [shared Slow foundation](shared-slow-foundation.md). Earlier 20/50-power and all-action examples
remain design history, not current behavior. The coating recipe, single-use
output, permanent application/wipe and exact Slow/poison values are now
implemented as summarized above. The broader post-Slayer review reminder remains
active; the illustrative proposal below does not supersede that implementation.


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

## Historical leather-design boundary and later bounded decisions

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

The owner subsequently approved the bounded carapace +2/+3/+5 additive
cleansing rules on 2026-09-22. Later bounded antidote/coating/Slow choices are
implemented as summarized above. A global stacking/cap or accumulating-Slow
immunity redesign remains separate; this does not authorize that overhaul.

The bounded [Slayer leather coverage/theme review](../rough-drafts/slayer-hide-and-leather-overhaul.md#material-coverage-and-shared-armor-themes)
records that historical planning and its bounded implemented rules. All carapace
families are intended to
increase the wearer's poison cleanse rate. Full-set +2/+3/+5 additive removal
is now owner-approved (2026-09-22) for scorpion/spider/magic-spider;
later antidote strengths have since been implemented; any broader global
stacking/cap redesign remains deferred. That does not bring this game-wide
standardization project into
the tower's scope or make it a prerequisite for finishing the tower.
