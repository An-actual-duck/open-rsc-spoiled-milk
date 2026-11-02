# Independent Zamorak hostile review

Status: **review complete; no confirmed blocker remains**, November 1, 2026. Reviewer branch:
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
`output/reviews/zamorak-independent-review/snapshot-a807eeafe/`. The worker-provided
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

**Confirmed at the first ACTIVE checkpoint; fixed and independently verified
at `e4ede109ab4c1b8784b79aa2a53eb2a3d5435f29`.** `ClericCriticalRuntime.beforeSettlement` checks live
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

Evidence: `output/reviews/zamorak-independent-review/adversarial-a807eeafe/step-2.log`.
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

## Guarded checkpoint and delta audit

The next exact pushed checkpoint is
`e4ede109ab4c1b8784b79aa2a53eb2a3d5435f29`. Its source and immutable artifact
copies are separately preserved under `snapshot-e4ede109a/` and
`artifacts-e4ede109a/`; the original failure evidence remains unchanged.

| Copied guarded artifact | SHA-256 |
| --- | --- |
| `server/core.jar` | `7468574f7edacb8b6eb8cec24c892bb3cb4d592d81e57032a5b15f3c5e1520e5` |
| `server/core-gameplay-overlay.jar` | `5db7fa8851866f35ef00102d107395e0b6233b2875af5f61a06f9d22fe60f8bd` |
| `server/plugins.jar` | `5e89b2489ebc2b5240767d7aa4f9c90a1bf220175a8272608e49d6d3067388e8` |
| `Client_Base/Open_RSC_Client.jar` | `d53177c056af3aaded8f22e3034ab6162a1cdc03432b50a919978da143201d1c` |

The semantic combat change since the first checkpoint adds both captured-source
and captured-target snapshot checks before critical callbacks. The same
independent fixture ran unchanged: **27/27 passed**, including all three
previous failures. It deliberately leaves ordinary damage admission with its
existing producer; it asserts stale requests cannot acquire new-lifetime
execution/Stagger callbacks, not that this transaction is a universal packet
admission boundary.

The remaining delta integrates the already reviewed icon art, retires the
eleven compatibility files, preserves the accepted concise book UI, improves
guide wording, and fixes test schema headers/core-only quest-plugin fixture
loading. `CriticalHitResolver` changes since the first checkpoint are comments,
not another RNG implementation. The added implementation tests exercise all
power/chance boundaries, no-curse paths, Cosmic candidates and expiry/recast
immunity. No further confirmed semantic defect was found in this delta.

The reviewer independently checked the guarded **unified** actual client JAR,
exact exported canonical PNGs and schema-5 6,050-byte golden packet. Result:
**162 native client/art assertions passed**, all twenty code/icon bindings and
embedded cache entries present, twenty source/JAR byte matches, and zero legacy
PNG resources. Native sprite/font rendering also passes. This supersedes the
earlier art task's separate-build integration limit for this exact guarded JAR.

Evidence: `checkpoint-e4ede109a.json`, `adversarial-e4ede109a/step-2.log`,
`e4ede109a-art-review.log`, `e4ede109a-schema5.bin` and the native render directory
under `output/reviews/zamorak-independent-review/`. Hashes were verified before/after
adversarial execution. Worker-reported full gates include 145 combat scenarios,
255 new Zamorak assertions, the focused Slayer/Slow/Cleric/client suites and
native UI/render checks; these broad suites were not all independently rerun
by this reviewer. Core retains its final combined validation responsibility.

## Final exact READY audit

Final implementation handoff reviewed:
**`238fe68b490700d5680e58fb2b3bb32c1191f220`**. Its pushed remote tracking tip
matches the supplied exact commit. Compared with the independently tested guarded
checkpoint, it changes five documentation/evidence files and adds seventeen
fixture lines for the three retained-request regressions. Production Java,
plugins, client code, protocol, configuration and PNG bytes are unchanged.
The source hashes and four artifact hashes in its versioned validation receipt
match the frozen production snapshot and the reviewer's own copied artifacts.

The final fixture additionally asserts that a current valid curse still exists
in each stale-request case, preventing a vacuous pass caused by unrelated curse
cleanup. It then verifies ordinary damage, no pending Stagger and no callback
RNG. The worker reports **264** final authored assertions (the previous 255 plus
nine assertions for these three cases). The reviewer audited that test delta;
it introduces no gameplay change. No redundant rerun of the unchanged guarded
production was needed: the independent **27/27** and unified client **162**
results apply to its exact same production bytes.

The final implementation review accurately separates offline verification from
private gameplay, map/package assembly and public activation. This review is
ready for Core integration of the exact implementation handoff. No further
production correction is requested.

## Coverage limits

The full worker gates were reviewed as supplied evidence, not all independently
rerun. The independent runtime assertions use real production classes and fixed
artifacts but synthetic actors/terrain and in-memory infrastructure. Ordinary
attack-producer/death/quest coverage is corroborated by source tracing and the
worker's real producer fixtures; the reviewer does not claim exhaustive proof
of every legacy script or arbitrary simultaneous actor mutation. Private
playtesting, real desktop rendering, economy balancing and Core's final rebuilt
paired package remain separate gates. The approved boss/script authority was
preserved rather than replaced with a new speculative NPC roster.
No public release or shutdown is authorized by this review.
