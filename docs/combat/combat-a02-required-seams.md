# A02 Combat Determinism Seams

A01 exercises meaningful current combat behavior without changing production
authority. It cannot deterministically select individual random outcomes or
advance all wall-clock/tick boundaries. A02 should introduce only the seams
needed to deepen those executable scenarios.

## Required seams

### Production-preserving clock

Introduce a `GameClock` abstraction with the current system-time behavior as
the production adapter and a test-only mutable clock. Route only A01-relevant
reads initially:

- `Server.getCurrentTick()` and the private event-processing boundary in
  `GameEventHandler.processEvents()`;
- `NpcBehavior` construction/`tick()` wall-clock reads and
  `PvmMeleeEvent.checkRetreat()`'s `System.currentTimeMillis()` comparison;
- `RangeEvent` and `ThrowingEvent` attack-tick checks, plus only the
  projectile launch-to-impact boundary needed by combat fixtures; and
- combat effect duration or pulse boundaries exercised by the fixture.

The adapter must retain the current units, comparisons, boundary inclusivity,
and number/order of reads. A mutable test clock must advance explicitly; it
must never run in a production configuration.

### Production-preserving random source

Introduce `GameRandom` with an adapter over the exact current generator and a
seeded/test implementation. Migrate only the random sites needed to replay:

- `CombatFormula` melee, ranged, and magic accuracy/damage rolls;
- the `NpcBehavior` projectile/style decisions required by the scenario set;
- `PvmMeleeEvent` secondary/proc selection and
  `ThrowingEvent.selectThrowingTargets()` shuriken sampling; and
- the `Npc`/drop-table rolls needed to characterize death/drop cardinality and
  ownership.

The production adapter must preserve generator choice, bound semantics,
distribution, draw count, and draw order. Test failures should report the seed
and enough state to reproduce the sequence.

### Explicit combat tick driver

Provide a test-only way to advance one current scheduler/combat tick through
the production event handler. This may compose `GameClock`; it must not add
fractional cadence, reorder events, or create a second runtime scheduler.

## Coverage unlocked in A02

With these seams, extend A01 from invariant checks to deterministic full-path
replay for:

- actual melee, ranged, and magic hit/miss and impact settlement;
- retreat expiry, re-engagement, cooldown, and projectile impact boundaries;
- candidate sets larger than the shuriken cap and random secondary ordering;
- scythe/shuriken damage, proc, and lifesteal ordering; and
- selected drop outcomes while retaining exactly-once death callbacks.

The A01 plugin-registration and private-method reflection are test-fixture
access techniques, not alternate combat behavior. They are adequate for A01
and do not justify a new production plugin authority in A02.

## Acceptance and stop conditions

A02 must demonstrate production-adapter parity before using the seams for
refactoring. Seeded replay must be repeatable. The full A01 gate, authoritative
server/plugin build, artifact inventory, relevant Python suites, and
changed-code analysis must remain green.

Stop if a proposed seam changes a random distribution or draw, a time unit or
boundary, scheduler/event ordering, combat cadence, or any player-visible
result. Such a change is combat behavior work and requires a separate owner
decision.
