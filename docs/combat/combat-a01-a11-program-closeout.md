# A01–A11 combat modernization closeout

## Decision

The A01–A11 Combat Modernization Program is complete. It delivered the
approved behavior-preserving characterization and ownership boundaries without
adopting Classic-Scape formulas, balance, timing, or production authority.
Current Spoiled Milk remains the behavior authority.

This closeout does not include Server R2 work. In particular, R2-2 owns
configuration, extension registration, transactional reload, and construction
of the optional combat trace observer. The combat-side contract it must honor
is recorded in
[`combat-a11-observability-profiles.md`](combat-a11-observability-profiles.md).

## Completed program record

| Area | Completion boundary |
| --- | --- |
| A01–A02 | Real production-object characterization harness plus bounded deterministic clock/random and whole-tick seams. |
| A03–A04 | Reasoned attack eligibility and explicit engagement/event ownership with lifecycle cleanup. |
| A05 | Immutable resolved-damage facts and narrowly migrated direct-Hits blocks across primary, auxiliary, reflection, child, owned, delayed, and residual compatibility families; caller-owned formulas, effects, XP, contribution, packets, and death ordering remain intact. |
| A06 | Typed projectile launch snapshot, impact policy, exact-once impact ledger, typed producer specifications, and resource-settlement ledger. |
| A07 | Reviewed secondary-effect descriptor/policy inventory and only the independently proven shared proc/selection boundaries; no generic effect executor. |
| A08 | Typed generic-poison provenance, durable record/restoration, typed damage settlement, retired generic burn, and separate Elder armor/boss burn lifecycles. |
| A09 | Typed factual NPC contribution ledger while preserving reward, XP, tie, loot, and summon settlement semantics. |
| A10 | Resolved NPC combat profile and one-element-per-cast magic payload without a second definition table or watchdog reset. |
| A11 | Disabled-by-default, bounded, reason-coded, redacted combat tracing through the existing read-only observer seam. |

The governing audit’s A01–A11 statuses and source documents remain the detailed
historical record. This document supersedes their intermediate “next branch”
and scenario-count language where it conflicts with the current closeout.

## Closeout reconciliation

Three source-text guardrails had fallen behind completed typed boundaries. They
are now reconciled without production changes:

1. NPC projectile/magic checks now pin `NpcCombatProfile` and
   `NpcMagicAttack`, while retaining the underlying `NpcAttackStyleProfile`
   formula/RNG authority.
2. The reviewed terminal-death call-site inventory includes A08’s typed
   `PoisonEvent` settlement adapter.
3. The projectile range audit recognizes A06’s
   `ProjectileImpactValidator` as the sole delayed-impact validation owner,
   rather than requiring the retired inline 15-tile check.

These are test/documentation corrections only. No formula, RNG draw order,
timing, combat effect, packet, persistence record, or production authority was
changed in the closeout.

## Acceptance matrix

The current baseline is a non-vacuous 134-scenario Java combat gate. Closeout
verification runs:

- `./server/test_combat` and both deliberate negative controls:
  `-Dcombat.injectFailure=true` and `-Dcombat.forceZeroScenarios=true`;
- the authoritative `./scripts/build-server.sh` core/plugin build and shipped
  Ant classpath inventory;
- artifact exclusion/inventory via `scripts/audit-server-build.py --check
  --require-artifacts`, including no `CurrentCombat` classes in `core.jar` or
  `plugins.jar`;
- supplemental data, formula/XP, eligibility, NPC profile/element, projectile
  range/collision, poison, Cleric, summoning, shuriken, dragon-effect, and
  layered-world source fixtures; and
- changed-code compiler, Checkstyle, PMD, CPD, SpotBugs, Ruff, and ShellCheck
  analysis using the baseline-aware offline lint command.

The negative controls must fail at the advertised preflight/zero-receipt
checks. Their failure is evidence the gate rejects a false success; it is not a
test-suite failure.

## Definitive remaining-work record

There is no approved generic A01–A11 production refactor remaining. The
following are deliberately outside the completed program and require a new
owner-specific proposal with executable parity coverage:

- A09 policy choices: deterministic tie behavior, unifying causal plugin and
  reward identity, pending Summoning XP on non-death despawn, a global
  contribution cap, and plugin/script/admin settlement semantics.
- Any further A07 consolidation: the retained owner boundaries must not be
  treated as permission for a generic secondary-effect executor.
- NPC watchdog/reset behavior, special NPC definition migrations, quest/boss/
  devotion/summon policy, or an alteration to the current profile formula/RNG
  authority.
- New DoT families. They must not revive generic burn or infer policy from the
  separate generic-poison, Elder armor burn, and Elder boss burn contracts.
- A11’s Server R2 configuration/extension/reload integration. It is not a
  combat gameplay change and remains owned by R2-2.

Any future branch must stop for an owner decision before changing formulae,
balance, timing, XP, loot, credit, aggro, prayer, PvP, packet order,
persistence, or startup compatibility.
