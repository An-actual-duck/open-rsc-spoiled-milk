# Server Runtime Performance Investigation

Status: ACTIVE

Branch: `refactor/server-runtime-optimization`

Scope: measured, behavior-preserving runtime optimization only; Server R2
architecture remains out of scope.

## Measurement contract

- Rank work by game-thread tick contribution, sampled CPU stacks, allocation
  pressure, and GC pauses rather than source appearance.
- Use the deterministic foundation benchmark with 64 synthetic custom-client
  players, 20 warmup ticks, and 100 measured ticks for comparison runs.
- Disable per-NPC/deep-NPC timers for end-to-end comparisons. Those timers are
  useful for attribution but add work at NPC frequency.
- Use Java Flight Recorder `profile` settings with 128-frame stacks for CPU and
  allocation attribution. Compare JFR runs only with equivalent JFR runs.
- Repeat accepted changes without JFR. Treat differences below roughly 1% or
  within run-to-run noise as inconclusive and move to another hotspot after two
  attempts.
- Preserve player/NPC ordering, spatial-domain and level isolation, collision,
  task/plugin behavior, packet behavior, and checked-in configuration.
- The synthetic players have no channels. This scenario measures update
  preparation but not socket writes, backpressure, encryption, or real payload
  throughput; networking needs a later bounded authenticated fixture.

Benchmark mode is listener-free so profiling cannot collide with a private
server in another worker checkout. Synthetic players now enter through the
normal initial-location boundary, allowing the production layered player and
spatial authorities to be profiled together. Ordinary startup still binds the
configured listeners and is unchanged.

## Baseline and ranked hotspots

Production-like layered authority was enabled through benchmark-only JVM
overrides while retaining the local database and content profile. The first
equivalent JFR baseline was:

| Metric | Baseline |
| --- | ---: |
| Average tick | 58.214 ms |
| Maximum tick | 93.669 ms |
| NPC processing | 26.828 ms |
| Client updates | 28.140 ms |
| Visibility snapshot preparation | 20 ms (rounded diagnostic) |
| Events | 2.211 ms |

Initial JFR evidence ranked the work as follows:

1. NPC aggro/player discovery copied every entity in each layered window before
   filtering to players. It dominated large `ArrayList` growth and sampled
   allocation.
2. NPC step collision searched the full 16-grid scenery window for every tested
   adjacent tile. After the first fix, this accounted for 20.8% of sampled
   game-thread CPU through `GameObject.getGameObjectDef()` and the scenery
   predicate.
3. Client visibility/update preparation became the next leader at roughly
   26–29 ms per tick, including roughly 18–20 ms of visibility construction.
   Typed projections, single-pass object classification, and corrected spatial
   key hashing have since reduced it substantially.
4. Plugin/event dispatch is measurable at roughly 2–3 ms in this synthetic
   workload, but it is not yet the top target.
5. Network serialization and writes require a real-channel fixture; the current
   benchmark correctly reports zero outgoing payload bytes.
6. GC is pressure rather than the primary pause bottleneck. Post-change GC-log
   runs spent about 0.21–0.25 seconds in pauses over full startup plus benchmark,
   with worst observed pauses of 24–38 ms.

The legacy spatial compatibility profile was also sampled. It spent most of its
time and allocation materializing large `LinkedHashSet`s in
`RegionManager.getLocalObjects`. That remains a meaningful compatibility
candidate, but the hosted layered profile was prioritized first.

## Accepted optimization 1: player-only NPC discovery

`LayeredSpatialEntityIndex.snapshotPlayersWithinRange` now reads the existing
player-only membership projection directly. `RegionManager` no longer builds a
complete entity snapshot just to service NPC aggro scans. Region iteration,
insertion order, range checks, world-space isolation, and level isolation are
preserved.

Equivalent JFR comparison:

| Metric | Before | After | Change |
| --- | ---: | ---: | ---: |
| Average tick | 58.214 ms | 44.617 ms | -23.4% |
| NPC processing | 26.828 ms | 14.104 ms | -47.4% |

Non-JFR confirmation runs were 46.021 and 46.365 ms/tick, with NPC processing
at 14.173 and 14.321 ms.

## Accepted optimization 2: footprint-bounded NPC scenery collision

The layered index retains its general full-window object query. NPC collision
uses a new bounded overload that visits only object-origin regions capable of
covering the destination tile. Shipped scenery definitions are at most 6x6;
the runtime bound deliberately supports footprints up to one complete 48x48
logical region. A regression fails if shipped definitions exceed that contract.

Non-JFR comparison using the two runs immediately before and after the change:

| Metric | Before mean | After mean | Change |
| --- | ---: | ---: | ---: |
| Average tick | 46.193 ms | 38.074 ms | -17.6% |
| NPC processing | 14.247 ms | 6.812 ms | -52.2% |

The individual post-change runs were 40.180 and 35.967 ms/tick. Client update
time remained 28.783 and 26.015 ms, confirming the gain came from NPC work
rather than visibility variance.

## Accepted optimization 3: typed NPC and ground-item projections

Layered visibility no longer snapshots every entity before discarding all but
NPCs or ground items. The index now materializes only the requested entity
type while retaining the same region order, insertion order, range checks, and
world/level isolation.

| Metric | Before mean | After mean | Change |
| --- | ---: | ---: | ---: |
| Average tick | 38.074 ms | 33.722 ms | -11.4% |

The individual post-change runs were 33.459 and 33.985 ms/tick. Visibility
preparation fell from roughly 18–20 ms to 14–15 ms.

## Accepted optimization 4: remove redundant visibility copies and scans

Spatial snapshots now expose an unmodifiable view of the newly built private
list instead of immediately copying that list again. Layered local scenery
uses its index-guaranteed uniqueness rather than paying for a redundant
`LinkedHashSet`, and `VisibilitySnapshot` separates scenery and walls in one
pass instead of two. The collections remain private, ordered, and immutable to
callers.

| Metric | Before mean | After mean | Change |
| --- | ---: | ---: | ---: |
| Average tick | 33.722 ms | 30.188 ms | -10.5% |

The individual post-change runs were 32.079 and 28.296 ms/tick. An equivalent
post-change JFR run measured 29.982 ms/tick, 7.512 ms of NPC work, 18.454 ms of
client updates, and 10 ms of rounded visibility preparation.

## Accepted optimization 5: spatial-key hash distribution

`WorldRegionKey` previously used a conventional chained hash whose effective
coordinate term was `31 * regionX + regionY`. Large two-dimensional windows
therefore created systematic diagonal collisions, treeified `HashMap` buckets,
and made `WorldSpaceId.equals` one of the hottest remaining sampled stacks.
The immutable key now precomputes a mixed hash with independently mixed axes.
Equality and all spatial behavior are unchanged. A regression covers a dense
signed local grid and rejects a return to systematic collision patterns.

| Metric | Before mean | After mean | Change |
| --- | ---: | ---: | ---: |
| Average tick | 30.188 ms | 28.175 ms | -6.7% |

The two post-change runs were 26.629 and 29.721 ms/tick. The variance reinforces
the policy of retaining only changes which repeat above the noise floor.

## Rejected experiment: raw scenery snapshot cache

A bounded, version-invalidated cache of immutable raw scenery snapshots was
tested after the hash correction. Its two runs were 26.688 and 30.968 ms/tick,
for a 28.828 ms mean—slightly worse than the uncached 28.175 ms mean. The cache
was removed rather than retaining complexity for a result within measurement
noise.

## Next investigation

1. Capture an equivalent post-hash JFR and rerank CPU and allocation stacks.
2. Reprofile events/plugin dispatch now that layered spatial and visibility
   costs are lower; do not optimize it unless the stack evidence identifies a
   behavior-preserving target.
3. Add a bounded authenticated network fixture before making networking claims.
4. Run focused spatial/pathing tests, combat characterization, authoritative
   core/plugin builds, and changed-code analysis before handoff.

## Validation to date

- `python3 tests/myworld/test-layered-spatial-runtime-authority.py`
- `./scripts/build-server.sh`
- repeated 100-tick production-like layered benchmark runs described above
- JFR CPU/allocation/GC capture through the visibility-copy work
