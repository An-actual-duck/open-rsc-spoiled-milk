# Offline NPC capacity evidence

Source baseline: `96cdabf534a123b31db9c763d7a079d3ae812361`.
CPU: AMD Ryzen 9 7900X. JVM: Temurin 1.8.0_482-b08, 64-bit Server VM.
Reported measurement jobs were bounded and collected sequentially. No listeners, server/client
loops, owner-map copy/export/import or deployment are part of these fixtures.

## Reproduction

```sh
./scripts/test-npc-capacity.sh
python3 tests/myworld/benchmark-entity-list-scan.py
python3 tests/myworld/test-npc-overlap-layout.py
python3 tests/myworld/benchmark-npc-capacity-runtime.py --baseline-registry --output /tmp/npc-baseline-1
python3 tests/myworld/benchmark-npc-capacity-runtime.py --output /tmp/npc-current-1
# Repeat each runtime command into new folders for repetition 2.
python3 tests/myworld/test-layered-native-placement-runtime.py
```

At the original Dot handoff the last command had the inherited source-assertion
failure preserved in known-baseline-failure.txt. Core subsequently corrected the
assertion to require the actual maintained collision-facade path and passed all
three tests, including all previously unreached assertions. No coverage was
bypassed; see core-review-follow-up.txt and the main review report.

## Method

Registry microbenchmarks compile the actual starting and current EntityList sources
against identical minimal identity objects. Capacity is 16384; packed populations
are 4000/8000/16384; sparse-low and sparse-scattered populations have 64 entries.
Fixed -Xms256m/-Xmx256m, 200 warm-up batches, 61 measured batches, 50 traversals per
batch. Median/p95 use per-batch average nanoseconds/pass. ThreadMXBean records
thread allocation. The snapshot consumer and live consumer perform the same sum.
This is a registry benchmark, not an AI or tick throughput claim.

Overlap benchmarks compile production NpcOverlapLayout against minimal character
fields, compare rank/count arrays exactly with the original algorithm, and retain
the unchanged offset functions in mudclient. Fixed seed 18153, -Xms128m/-Xmx128m,
100 warm-up batches, 51 measured batches, 10 calls/batch. Nulls, tied IDs, negative
and moving positions, sparse and dense groups are included in 22 scenes. Allocation
figures are transient bytes/call, excluding preallocated output arrays.

Runtime measurements use production Server construction, World/Npc/Player, actual
ActiveCombatBenchmark transactions/driver, scheduler, status events, NPC behavior,
movement, per-player visibility/payload generation and cleanup. Server.start and
listeners are never called. Each JVM has -Xms512m/-Xmx1024m, 20 warm-up ticks and 80
measured ticks, seed 1511506148. Populations are 4000/8000/16384. Idle NPCs are real
registered entities in a distant region; active workloads add 32 real player/NPC
pairs with survival HP, actual attacks and representative plugin dispatch. Client
channels are in-memory proxies solely to permit production packet queueing.

Baseline runtime replaces only EntityList with the starting implementation; the
rest of the expanded protocol/world implementation is identical. This isolates
registry implementation cost without pretending the original 12-bit protocol
supported a 16K world. Two separate JVM repetitions of every population/workload/
implementation are preserved. p99 is effectively the maximum of 80 samples and
should not be interpreted as a long-duration tail estimate.

Each phase has wall time in nanoseconds; workload_allocated_bytes_per_tick is the
whole combined pass allocation repeated on each phase row for context. A pass has
one movement iteration, not all 10ms server polls. Visibility includes payload
construction; socket transport/encoding are separate wire fixtures. No phases are
replaced by alternate AI/combat logic. Actor live/engaged counts must remain valid;
full scheduler hit hashes are observational because existing event ordering can
vary, even with the same seed and draw count. Heap values include map/definition
state and uncollected garbage and are snapshots rather than retained/peak values.

## Files

- registry-scan.txt and client-overlap.txt: raw benchmark output.
- runtime-{baseline,current}-{1,2}.txt: raw measurement, visibility, allocation,
  heap and combat invariant lines; log prefixes preserved.
- offline-gate.txt: filtered PASS/build summary from the dedicated gate.
- production-protocol.txt: latest stronger production/login/interaction fixture.
- failure-first-client.txt: captured initial stale-chat and malformed-type failures.
- known-baseline-failure.txt: exact historical inherited assertion evidence.
- core-review-follow-up.txt: baseline reproduction, complete repaired suite, hosted
  profile failure-first check and independent Core gate result.
- artifact-hashes.sha256: final source-built artifacts and neutral contract.

The original Dot export denial remains historical evidence; Dot did not retry.
Core later authorized a separately verified isolated owner-candidate copy. Its
export/validation acceptance is tracked separately in the main review report;
no owner export artifact is bundled in this worker evidence package. Core private gameplay,
Toolkit import preview, real reconnect/picking and full network/movement cadence
acceptance remain separate. See the main review document for the contract matrix.
