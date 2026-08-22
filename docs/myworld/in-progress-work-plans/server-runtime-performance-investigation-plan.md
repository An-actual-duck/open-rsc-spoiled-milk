# Server Runtime Performance Investigation

Status: READY FOR MANAGER REVIEW — AUTHENTICATED NETWORK MILESTONE

Branch: `refactor/server-network-runtime-optimization`

Scope: measured, behavior-preserving runtime optimization only; Server R2
architecture remains out of scope.

## Measurement contract

- Rank work by game-thread tick contribution, sampled CPU stacks, allocation
  pressure, and GC pauses rather than source appearance.
- Use the deterministic foundation benchmark with 64 synthetic players, 20
  warmup ticks, and 100 measured ticks for comparison runs. Always state the
  protocol: the fixture defaults to legacy-compatible version 235; the current
  custom-client path is measured explicitly with version 10052.
- Disable per-NPC/deep-NPC timers for end-to-end comparisons. Those timers are
  useful for attribution but add work at NPC frequency.
- Use Java Flight Recorder `profile` settings with 128-frame stacks for CPU and
  allocation attribution. Compare JFR runs only with equivalent JFR runs.
- Repeat accepted changes without JFR. Treat differences below roughly 1% or
  within run-to-run noise as inconclusive and move to another hotspot after two
  attempts.
- Preserve player/NPC ordering, spatial-domain and level isolation, collision,
  task/plugin behavior, packet behavior, and checked-in configuration.
- The synthetic players have no channels. This scenario measures visibility,
  packet construction, and update
  preparation but not socket writes, backpressure, encryption, or real payload
  throughput. The authenticated-network fixture described below now covers
  those costs separately without changing this synthetic comparison boundary.

The active workload is separately fixed at 64 player/giant melee pairs, 15
warmup ticks, 60 measured ticks, custom protocol 10052, layered player/spatial
authority, snapshot visibility input, and four real `TimedEventTrigger`
dispatches per tick. It removes loaded background NPCs inside benchmark mode
before constructing the fixed combat cohort. This retains a dense 64-player,
64-NPC active scene plus the shipped scenery, walls, and ground items while
excluding wall-clock-driven roaming by unrelated NPCs from the comparison.
Production startup and world population are unchanged.

Benchmark mode is listener-free so profiling cannot collide with a private
server in another worker checkout. Synthetic players now enter through the
normal initial-location boundary, allowing the production layered player and
spatial authorities to be profiled together. Ordinary startup still binds the
configured listeners and is unchanged.

## Baseline and ranked hotspots

Layered authority was enabled through benchmark-only JVM overrides while
retaining the local database and content profile. The first equivalent JFR
baseline used the fixture's default protocol 235 compatibility path:

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

## Current custom-client profile

The initial fixture summary called version 235 a custom client even though
`Player.isUsingCustomClient()` authoritatively requires versions 10001–19999.
No conclusions were drawn from the misleading label. The hosted-style
visibility input was rerun with explicit version 10052, layered player/spatial
authority, and snapshot input enabled.

Two equivalent custom-path runs measured 22.358 and 22.335 ms/tick. Their
stage profile was approximately:

| Stage | Custom path |
| --- | ---: |
| Average tick | 22.35 ms |
| NPC processing | 6.30–6.61 ms |
| Client updates | 12.17–12.47 ms |
| Visibility preparation | 3–4 ms (rounded diagnostic) |
| Events | 2.54–2.55 ms |

The exact scene window exposed 64 players, roughly 109 NPCs, 802 scenery
objects, 42 walls, and 58 ground items per synthetic player. Movement snapshot
construction was exercised (roughly 1.5–1.8 MB over a run), but outgoing socket
bytes remained zero because synthetic players have no channels.

A deep attribution-only run measured 7.208 ms of NPC processing, including
6.900 ms in behavior and 5.748 ms attributed to random-roam handling. These
timers execute at NPC frequency and are intentionally excluded from comparison
runs.

## Rejected experiments after custom-path correction

- Removing redundant spatial-domain checks from index-qualified visibility
  snapshots was safe by construction, but two runs averaged 12.75 ms of client
  update work versus the unchanged 12.32 ms mean. It was reverted.
- Moving the NPC roam-cadence check ahead of local-player discovery reduced the
  theoretical scan count, but two end-to-end runs averaged 22.64 ms/tick versus
  the unchanged 22.35 ms mean. It was reverted.

These are two consecutive attempts without a repeatable end-to-end gain. Per
the investigation contract, visibility/NPC micro-tuning stops here.

## Active combat and plugin workload

`ActiveCombatBenchmark` is an explicit, listener-free workload that enters
combat through `PlayerAttackTransaction` and the production `PvmMeleeEvent`,
uses a seeded `GameRandom`, seeds remaining legacy random consumers after world
population, and dispatches the real timed-plugin trigger at a representative
four players per tick. Players and NPCs have enlarged hit pools so no death or
respawn changes the cohort during measurement. The runner requires two or more
runs, a passing gameplay invariant, and an identical aggregate outcome and
random-draw signature.

The fixture intentionally reports the per-pair distribution hash as diagnostic
information only. Scheduler UUID ordering can assign the same deterministic
aggregate damage to different pairs. The acceptance signature instead pins the
pair count, live and engaged counts, plugin dispatch count, aggregate player
and NPC hits, and deterministic-random draw count. The accepted runs repeatedly
produced `64-64-64-296-634499-637741-9613`.

An early profiling run accidentally omitted layered spatial authority. It
ranked legacy `RegionManager.getLocalObjects`, but that path is not the hosted
target and no change was made from that evidence. Corrected layered runs ranked
NPC visibility prioritization first: its sort comparator repeatedly called
combat ownership validation for the same NPCs, accounting for roughly 27% of
sampled game-thread stacks and about 15.3 ms/tick of NPC client updates.

### Accepted optimization 6: single combat classification per visible NPC

Visible NPCs are classified once into direct-combat, other-combat, and
non-combat groups, then each group retains the existing distance, local-cache,
and server-index ordering. This is equivalent to the former primary sort key
without repeatedly pruning and validating the same combat engagement from the
comparator.

| Metric | Before mean | After mean | Change |
| --- | ---: | ---: | ---: |
| Average tick | 53.746 ms | 44.672 ms | -16.9% |
| NPC client update | 15.257 ms | 4.626 ms | -69.7% |

The individual post-change runs were 44.147 and 45.197 ms/tick. The aggregate
combat/plugin signature remained identical.

### Rejected active serialization experiments

The post-priority JFR profile ranked generic custom payload serialization next.
Two behavior-neutral attempts were tested and removed:

- A compatibility fallback around primitive `BitUpdate` access averaged
  45.289 ms/tick, 1.4% worse than the unchanged endpoint.
- Converting the coordinate packet path to typed `BitUpdate` lists averaged
  45.885 ms/tick, 2.7% worse than the unchanged endpoint.

This is the required pair of consecutive non-gains. Packet/appearance
serialization is closed for this milestone; a future change needs a measured,
typed packet representation design rather than another loop micro-tuning.

### Accepted optimization 7: signaled plugin pause boundaries

`PluginTickEvent` previously polled asynchronous `PluginTask` state with a
mandatory one-millisecond sleep. Four trivial timed-plugin callbacks therefore
added about 4.3 ms to every measured game tick. `PluginTask` now signals its
existing initialized, running, future, pause, and completion state transitions;
the game thread waits on that condition and resumes at the same pause or
completion boundary. Callback ordering, tick-bound startup, delays, cancellation,
and plugin semantics remain unchanged.

For this comparison, the benchmark-only fixed NPC cohort removed unrelated
wall-clock roaming variance from both sides:

| Metric | Polling mean | Signaled mean | Change |
| --- | ---: | ---: | ---: |
| Average tick | 35.106 ms | 33.383 ms | -4.9% |
| Plugin events per tick | 4.262 ms | 0.261 ms | -93.9% |

Polling runs were 36.674 and 33.538 ms/tick. Signaled runs were 34.024 and
32.741 ms/tick. All four produced the exact same aggregate gameplay signature.
A final equivalent JFR run measured 31.817 ms/tick, with 0.248 ms/tick in
plugin events and about 0.502 ms/tick in `PvmMeleeEvent` execution. The exact
handoff code was rerun at 34.209 and 31.073 ms/tick with the same signature.

## Authenticated network workload

The bounded authenticated-network fixture starts the foundation benchmark with
its otherwise-disabled loopback TCP listener explicitly enabled. It copies the
checked-in seed database and configuration to disposable ignored files, selects
an ephemeral loopback port, registers eight disposable accounts, and logs all
eight in through the production custom-client decoder and authentication lookup.
Each connected client sends real heartbeat and single-step walk packets while
fully parsing server frames. The client records ordered byte and opcode CRCs;
the server records framed writes, bytes, serialization time, authentication,
disconnects, and channel writability transitions without retaining credentials,
addresses, or payloads.

One named benchmark client pauses reads for four seconds and receives a lower
benchmark-only write watermark. Both baseline and accepted runs observed one
unwritable transition followed by one writable recovery. Ordinary channels keep
their production watermarks. The runner requires at least two repetitions and
fails unless every client authenticated, exchanged gameplay traffic, parsed
complete frames, and the server observed backpressure and recovery. It cleans
the disposable database and configuration after each run and does not contact a
private or public server.

### Network baseline and ranked costs

The two-run baseline used 15 warmup and 15 measured ticks, eight authenticated
clients, and 28 seconds of client traffic. It produced 3,015/3,519 framed writes
and 270,733/293,845 serialized bytes. Framing took 11.373/11.562 ms in aggregate,
or 42.01/39.35 ns per byte (40.68 ns/byte mean). Both server and client
invariants passed.

The observed network costs rank as follows:

1. Every packet was framed into a new unpooled temporary buffer and then copied
   into Netty's provided outbound buffer. This performed an allocation and a
   complete extra copy at packet frequency.
2. Per-write promise and channel bookkeeping remains measurable, but the entire
   game-thread outgoing stage is only 0.058–0.062 ms/tick in the baseline.
3. Incoming custom-frame parsing is smaller still at 0.136–0.153 ms/tick in the
   accepted runs and is not a material tick-budget target.

### Accepted optimization 8: direct packet framing

The shared protocol encoder now writes directly into the Netty-provided TCP
buffer. The WebSocket wrapper allocates its final frame content once, delegates
the same framing routine into it, and releases that content if encoding fails.
Framing branches, lengths, opcode order, ISAAC behavior, payload consumption,
and raw writes are unchanged. An executable `EmbeddedChannel` regression pins
the exact raw, legacy, custom TCP, and custom WebSocket bytes.

Equivalent post-change runs produced 3,867/3,301 writes and
281,631/287,693 serialized bytes. Aggregate framing time was 10.337/8.173 ms,
or 36.70/28.41 ns per byte (32.56 ns/byte mean): a repeatable normalized
reduction of 20.0%. The game-thread outgoing stage remained only
0.054–0.055 ms/tick, all eight clients authenticated, client frame parsing and
ordered CRC collection passed, and both runs again observed exactly one
unwritable transition and one recovery.

No per-write promise or inbound-decoder micro-tuning follows this change. Their
end-to-end contribution is already far below one percent of the tick budget, so
even removing all of it would not clear the investigation's materiality rule.
This is an evidence-based stop for the current network area, not an assertion
that those paths contain no measurable instructions.

## Investigation checkpoint

The equivalent protocol-235 JFR path fell from 58.214 ms/tick at baseline to
27.206 ms/tick after the five accepted changes, a 53.3% reduction. The custom
protocol-10052 endpoint is 22.35 ms/tick in the two-run baseline above; no
pre-change custom-path recording exists, so no custom-path percentage is
claimed.

No release, public server launch, live-state access, or Server R2 redesign was
performed. The active combat/plugin branch adds the deterministic fixture and
retains two material optimizations. The two-attempt rule stopped packet
serialization work, and direct combat resolution is now below one millisecond
per tick in this stress workload.

## Next investigation

1. Treat custom appearance/payload serialization as a future representation
   design with packet byte-parity tests; two local loop changes failed here.
2. Add a separately deterministic projectile/magic/multi-target combat workload
   before optimizing those combat families. The current melee result does not
   authorize extrapolation.
3. Revisit NPC random-roam only with a deterministic NPC-state fixture capable
   of controlling due timers and nearby-player distribution.
4. Return to network work only when a larger connected-client fixture shows
   socket writes, decoder cost, allocation, or GC pressure above the materiality
   threshold; do not micro-tune the current 0.055 ms/tick outgoing stage.
5. Keep Server R2 architecture and configuration redesign outside this branch.

## Validation to date

- `python3 tests/myworld/test-layered-spatial-runtime-authority.py`
- `./scripts/build-server.sh`
- repeated 100-tick production-like layered benchmark runs described above
- JFR CPU/allocation/GC capture through the visibility-copy work
- corrected custom-client version-10052 baseline and JFR capture
- repeated deterministic 64-pair active-combat/plugin runs
- repeated eight-client authenticated TCP runs with real registration, login,
  heartbeat/walk parsing, socket writes, and forced backpressure recovery
- pre/post framing results at 40.68 and 32.56 ns/byte respectively
- bundled Ant `test_network_encoder` exact raw/TCP/WebSocket wire regression
- `python3 tests/myworld/test-authenticated-network-benchmark.py`
- pre/post JFR captures with layered player and spatial authority enabled
- exact aggregate combat outcome and random-draw signature checks
- two rejected serialization comparisons and their removal
- `python3 tests/myworld/test-active-combat-benchmark.py`
- intrusive NPC stage attribution used only for hotspot ranking
- `python3 tests/myworld/test-foundation-optimization-guards.py`
- `python3 tests/myworld/test-layered-spatial-runtime-authority.py`
- `python3 tests/myworld/test-layered-scene-visibility-rings.py`
- `python3 tests/myworld/test-server-sync-modernization.py`
- `python3 tests/myworld/test-path-queue-regressions.py`
- `python3 tests/myworld/test-movement-pathing-release-plan.py`
- `python3 tests/myworld/test-combat-runtime-invariants.py`
- `python3 tests/myworld/test-combat-interaction.py`
- `python3 tests/myworld/test-client-custom-movement-stability.py`
- `python3 tests/myworld/test-legacy-plugin-adapter-parity.py`
- `python3 tests/myworld/test-plugin-default-fallback.py`
- bundled Ant `test_combat` (143 scenarios)
- `./scripts/build-server.sh` (authoritative Ant core and plugins)
- `python3 scripts/lint.py report --base b113c0eb9 --offline`: changed-code
  compiler gate passed with no new gated warnings. The whole-program SpotBugs
  report still flags two pre-existing client findings in unchanged
  `DoSkillInterface` and `PartyInterface`; neither file differs from the branch
  starting commit, so this is baseline drift rather than a server regression.
