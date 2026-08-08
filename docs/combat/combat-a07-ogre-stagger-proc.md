# A07.5A Ogre Staggering Blow Proc

## Scope and selection

A07.5A moves one proven-identical proc family, Ogre Staggering Blow, into
`OgreStaggeringBlowProc`. It does not create a general on-hit registry or move
the surrounding leather-effect chains.

Ogre Stagger was selected because reciprocal melee, PvM melee, and projectile
impact had the same complete policy:

- only a complete Ogre set is eligible;
- an eligible attempt consumes exactly one 20% random draw;
- settled zero damage is eligible;
- success replaces the target's Ogre state with one staggered attack; and
- the proc has no damage, contribution, lifesteal, aggro, projectile, charge,
  death, or descendant policy.

Nearby duplicated effects were deliberately excluded. Elemental Giant Might
has a projectile-style gate. The full leather chains use different random
sources, PvP behavior, debug labels, damage adapters, and descendants. Chain
lightning and Death Robe use path-specific styles, contribution, mitigation,
and death handling. Those differences are not callback parameters invented by
this branch.

## Ownership boundary

Each event owner retains its existing surviving-target phase check: the source
must be a player and the target must still have Hits. The shared proc owns only
the complete-set gate, one chance draw, and `applyOgreStaggerDebuff()` call.
Its position remains immediately after Elemental Giant Might and immediately
before Baby Dragon smoke in all three current chains.

Random-source ownership remains unchanged:

- reciprocal melee and projectile impact pass `ProductionGameRandom`, the A02
  adapter over the legacy `DataConversions` generator;
- PvM melee passes its injected `combatRandom()` source.

An incomplete set consumes no draw. A complete set consumes one draw whether
the roll succeeds or fails. The shared method does not inspect damage, so the
caller's existing settled-zero eligibility is preserved.

## Compatibility and exclusions

The target's existing one-charge replacement and consumption behavior remains
in `Mob`. Applying another successful Ogre proc still resets the state to one
attack rather than stacking. Player feedback remains in
`applyOgreStaggerDebuff()` and `consumeOgreStaggerDebuff()`.

This branch changes no proc chance, equipment definition, status duration,
attack suppression, message, packet, animation, combat timing, damage,
contribution, death, or balance rule. The descriptor catalog remains
descriptive; the shared proc does not use it as a runtime registry.

A07.5 remains a one-family-per-branch sequence. Baby Dragon smoke is the most
obvious next candidate, but it requires its own pre-migration fixture for
projectile presentation and draw ordering. No other leather effect is approved
for consolidation by this result.

## Executable evidence

The compiled A07.5A scenario executes the private production phase owners
before and after extraction. Across reciprocal melee, PvM melee, and projectile
impact it proves:

- success on settled zero damage;
- exactly one draw for successful and failed complete-set attempts;
- no draw for an incomplete set, dead target, or non-player source;
- the same full-set chance source in every path; and
- exactly one attack of target stagger state.

The authoritative combat gate grows from 88 to 89 scenarios. Existing leather,
projectile, primary-damage, and attack-suppression coverage continues to guard
the surrounding call order and consumption behavior.

Stop any later consolidation if it changes the event-owned phase gate, random
source/count/order, zero-damage eligibility, replacement semantics, target
message, or attack-consumption point.
