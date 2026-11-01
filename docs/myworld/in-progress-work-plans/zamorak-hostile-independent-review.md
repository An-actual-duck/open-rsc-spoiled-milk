# Independent Zamorak hostile review

Status: **in progress**, November 1, 2026. Reviewer branch:
`test/zamorak-hostile-review`, based on published `02e060c50`.
This is review and adversarial-test work only. No production fixes, game assets,
owner maps/accounts, toolkit, staging package or public operations are changed.

## Preliminary exact snapshot

Reviewed pushed ACTIVE checkpoint
`a807eeafe4f67dc557a7923ad2ed602037a593d5` from
`feat/zamorak-hostile-spells`; this is not its final READY state.
The implementation delta is compared with its base `508a05908`; differences
against later art-only main are not treated as an art regression before the
implementation worker's planned main merge and eleven-file retirement.

Read-only source was exported with `git archive` into the reviewer's ignored
`output/zamorak-independent-review/snapshot-a807eeafe/`. The worker-provided
immutable checkpoint JARs were copied into the reviewer's own evidence tree and
verified against the supplied SHA-256 receipts before use. No mutable worktree
JAR was tested and the implementation worktree was not opened or edited.

| Copied artifact | SHA-256 |
| --- | --- |
| `server/core.jar` | `57f1819b0433c97fb37d42f86e2b99d24cec596c536465e67afd6b483e1e7b9d` |
| `server/core-gameplay-overlay.jar` | `3d872da56d50fadc4304e234505b696b7d2d68ca3817c607d9c653ceb2270079` |
| `server/plugins.jar` | `e53e03f70bb3bf9f8aeea439d58a359aa8f8e67d6c16785f301af9208e4dfea9` |
| `Client_Base/Open_RSC_Client.jar` | `52d91be0121bc2d2e42b58365bb822eccdc6f9731d11298c0abc602cc6ba2803` |

## Preliminary finding — stale request callback fence

**Confirmed at the ACTIVE checkpoint; implementation worker already has the
matching fix in progress.** `ClericCriticalRuntime.beforeSettlement` checks live
source/target eligibility and the current curse, but does not compare the
request's captured combat-participant snapshots. Retain a positive AUTHORED
primary request, advance the target lifetime and reapply a valid curse, then
settle the old request: it can execute the new lifetime. The same callback gap
occurs after attacker lifecycle or login-session change.

The independent fixture makes all three failures explicit. At the checkpoint,
a retained one-damage hit executes the target's remaining 25 HP and consumes
callback RNG; the expected ordinary one-damage result has no new curse callback.
The finding concerns execution/Weak Legs admission at this boundary, not a claim
that arbitrary stale network packets hit: the existing projectile impact
validator already checks launch participant snapshots. The producer still owns
ordinary damage admission. The final review must rerun these unchanged cases
against the completed snapshot guards and check the exact source delta.

Evidence: `output/zamorak-independent-review/adversarial-a807eeafe/step-2.log`.
Result: **27 independent assertions, 24 passed, 3 failed** (the three stale
request variants). This is retained failure-first evidence, not a passing gate.

## Other preliminary observations

The independent source trace covers actual Spoiled Milk melee, bow/throwing,
ordinary/bolt/Crumble/God/Iban Magic and fixed Salarin primary paths. Optional
OSRS formulas remain disabled and separate. Primary formulas attach explicit
AUTHORED outcomes; downstream settlement requires positive resolved/roll damage,
correct actor/style/key provenance and legal Player→NPC admission. Phase rejection
precedes callbacks. Normal lethal hits skip execution/Stagger RNG. Execution
raises the same request amount to pre-hit remaining HP, and all three primary
settlement callers use that settled amount for their existing contribution and
death paths.

Boss immunity reuses the existing explicit boss authority and Slayer boss-task
identities, the existing script-owned death predicate, and the approved Witch's
Experiment forms/Salarin identities. It does not disable ordinary criticals or
Stagger on those targets. Cast slots, power caps, resources and origin validation
reuse the existing hostile registry; critical outcomes stay immutable in delayed
launch specifications, while impact callbacks require a live applicable curse.

Additional independent passing cases cover rejected COSMIC/zero-roll/wrong-style/
secondary/summon/cannon provenance, equal refresh and lower-tier refusal without
clearing pending Stagger, curse expiry/recast preserving recovery, exact fifth
recovery commitment, and real full-set Cosmic equipment retrying pure chance
candidates without replaying an ordinary attack or damage settlement. These
checks use the production classes through the retained offline combat harness,
not a substitute implementation.

## Running the independent fixture

`tests/myworld/test-zamorak-hostile-independent.py` requires explicit fixed
`--snapshot`, `--artifacts`, `--receipt` and `--evidence` directories. The receipt
contains the exact reviewed commit and copied artifact hashes. It verifies all
JAR hashes before/after, prepares only versioned definitions/SQL schema text and
a fresh synthetic map, and uses the real in-memory server/world without calling
server startup, opening accounts or binding listeners. Java 8 work is serialized
under `/home/justin/.local/share/core-heavy-build.lock`, with 512 MiB SerialGC.

## Remaining review gates

This report is not final acceptance. Audit the implementation's final exact
READY commit and every delta from the preliminary checkpoint; rerun independent
adversarial checks on its own copied fixed artifacts. Confirm final resource
retirement and twenty distinct canonical source/JAR icons, schema 5/client 10056
metadata, the accepted concise spellbook UI, and reported core/client gates.
Private gameplay, rendering on real desktop platforms, broader economy balancing
and every legacy script are not proven by this scoped offline review. No new
architecture, unrelated gameplay rework or public deployment is authorized.
