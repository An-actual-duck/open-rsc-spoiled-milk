# Bounded Idle Ground-Walk Command Admission

Status: technical validation complete; ready for manager review. Owner private
visual acceptance and public activation remain pending. No public runtime changed.
Branch: `fix/idle-walk-command-admission`. Final production receipt identifies
source `cbb9b63c35421889d9ffa5b7d39b83a6da3edbd4`; subsequent handoff changes are documentation only.
The [successful-step clock slice](movement-first-step-implementation.md) is an
independently integrated predecessor and is present in both comparison servers.

## Contract And Ownership

The 640 ms world tick retains spells, combat starts, item/economy handlers,
interactions and complex cancellation. This slice admits only a bounded FIFO
head prefix of ordinary custom-client ground walks and no-op heartbeats, on the
same GameThread immediately before the existing intertick movement pass. The
430 ms walking cadence, movement order, collision, readiness and effects remain
unchanged. No client prediction or protocol timestamp extension was introduced.

The original parser selection, processor, malformed-packet response, active
opcode bookkeeping and packet accounting are extracted once and reused by both
admission paths. Network filtering and queue limits still precede enqueue. No
handler executes on Netty threads. The sole consumer verifies the polled head
identity; it never scans past a barrier or coalesces across earlier actions.

Eligible packets are `WALK_TO_POINT` 187 and zero-payload heartbeat 67. A walk
needs the shipped even-length 4–54-byte payload shape and a non-consuming,
overflow-safe aggregate waypoint distance of at most 24 Chebyshev tiles. Length
alone does not bound `Path.addStep` work. Unknown, malformed, oversized and more
expensive requests stay on the ordinary FIFO path; the optimization does not
newly reject them. Only MyWorld custom walking/current protocol 10059 or newer
qualifies. Connection eligibility requires open, active and writable.

The owner gate excludes busy/dead/sleeping/unregistering/teleporting actors;
plugins, batches, menus and dialogue; following, possession and walk-to actions;
bank/shop/auction/trade/duel state; configured agility; drop/talk callbacks;
cannon, pending attack intents and incoming/outgoing combat. Existing logout
cooldowns conservatively exclude recent combat/exchange contexts. Active Abyssal
`actionsBlocked` is excluded so a near-expiry trap cannot consume a click early.
Admin blink is excluded so teleport/map-loading work retains normal admission.
The pending-intent query is pure and does not expire a transaction.

| Existing owner / incoming intent | Admission and replacement rule |
| --- | --- |
| Ordinary idle/walking / ground walk | Eligible FIFO head may be admitted early; existing WalkRequest resets/builds path; no extra movement step is granted. |
| Idle / heartbeat | Eligible bookkeeping-only head may proceed within the same budgets. |
| Approach plus item, door or NPC action | WALK_TO_ENTITY/action packets remain on the world tick, in original order and with original arrival/reach checks. |
| Earlier item, spell, unknown or unusual packet / later walk | Earlier packet is a barrier; later walk cannot overtake it. |
| Combat, autocast, pending attack, plugin, production or dialogue / walk | Owner exclusion preserves existing world-tick cancellation, cooldown and callback lifecycle. |
| Trap, blink, teleport, death, logout or disconnected actor / walk | No early admission; existing suppression/lifecycle behavior remains authoritative. |

This table bounds the current change; broad action centralization remains a
separate task requiring reproduced transition defects. Existing shuriken,
approach, doorway, PvP and combat checks cover the retained action paths.

## Work And Fairness Budgets

Ceilings are two packets/player/poll, eight/player/world tick and eight globally
per poll. The player method clamps its allowance independently. A global **2 ms
soft budget** checks monotonic elapsed time between individual commands, while
allowing at least one eligible command to progress. Count limits still apply.
A persistent registry-index cursor rotates priority. The normal tick drains the
untouched FIFO remainder. Time subtraction is wrap-safe; production uses
System.nanoTime and tests inject a budget clock.

Two disjoint allocation-free live registry passes use a cached visitor, with no
snapshots, sorting or per-poll callback allocation. An empty registry returns
before reading the budget clock. Empty/raw-barrier queues return before broad
owner checks and checked world-coordinate lookup. One command, GC or cold reads
can exceed 2 ms: this is not a hard deadline for admission or the whole world tick.

## Final Functional Evidence

Evidence is preserved under `output/tests/idle-walk-admission/` and logs under
`output/reviews/idle-walk-admission/` in this worker checkout.

- `soft-budget-functional-1`: **3,135 passing assertions**, including real decoded
  packets through RSCConnectionHandler, unchanged filter, FIFO, custom parser and
  processor. Its synthetic attached peers are explicitly not ordinary logins.
- `soft-budget-baseline-1`: **1,925 passing assertions**, identical final fixture
  source/class bytes, clock-only predecessor core. All 640 arrival phases give
  baseline admission-to-authoritative-step p50/p95/max **320/608/640 ms**, final
  **5/9/10 ms**. These are injected-clock causal timings, not CPU/network/render
  measurements. Synthetic runtime asserts protocol 10059 and NPC capacity 4000.
- Controls cover packet reader/head preservation, approach/pickup, earlier item
  cancellation, owners, active/inactive/unwritable channels, legacy profile,
  heartbeat, payload work bounds, reclick cadence, count renewal, normal-tick
  remainder, concurrent enqueue conservation and spam/fairness with 64/256 peers.
  Forced time exhaustion covers zero/negative/wrapping origins, first progress,
  rotating priority and empty-registry no-clock behavior. Actual near-expiry
  Abyssal trapping and admin blink defer correctly to normal admission.
- With an unexhausted injected time budget,64/256 single-packet peers complete in
  8/32 polls. Forced one-command exhaustion takes 64/256 polls. These are fairness
  controls, not a universal loaded-server latency bound.
- **4,935** successful-step-clock checks, **6** shuriken interaction scenarios,
  **498** walking-doorway assertions, ranged approach controls, **40** PvP
  foundation scenarios and **149** strict combat scenarios pass. Final
  `scripts/build-server.sh` passes generated contracts, compilation and artifact
  classpath audits.

The first aggregate Ant invocation stopped at a missing `passage.fixture.dir`
argument after packet/clock/shuriken success. The corrected doorway invocation
below passed; this setup error is preserved in `soft-budget-regressions.log`.
The worker's historical ignored `server/local.conf` 10052 is untouched; the final
PvP/combat runs use an isolated current 10059 runtime, avoiding its stale wire gate.

## Final Ordinary Native Input Evidence

`output/tests/idle-walk-native-input/final-baseline-java21-13/` and
`final-candidate-java21-13/` are a matched **13-click** pair. Both use actual
Java21.0.12, the same fixed client, map, helper source/classes, plugins, overlay
and dependencies; only `core.jar` differs. Compile tooling is Java8u482 and is
recorded separately. Launch/execution/marker receipts bind the actual artifacts.

- Client: `startup-df7fd5304.jar`, SHA-256
  `990a4bb67bfb71053adf3a38c457cb133239a27237d7a0b35b2d129fdbb49418`.
- Baseline core: clock-only predecessor, SHA-256
  `fe6eb97fd55574d25a68c43ce20d85a315de8447c4796abb4ff6db096f26c92f`.
- Final core: SHA-256
  `f407f248e574185da1944fd4b32d5f57a2bf4dd76e88e8903bf3a387f1a2b46f`.
- Frozen final jars/classes/source hashes:
  `output/tests/idle-walk-admission/final-soft-artifacts/receipt.json`.

The ordinary disposable account logs in through real Server.run and OnLogin.
Its idle lane owner gate is naturally true before input; no forced owner clearing
is used. Scoped AWT mouse callbacks follow the production input handler; the tool
never invokes walkToArea off-thread or injects global physical input. The full
map uses shipped NPC capacity 16384, distinct from the synthetic 4000 suite.
Ambient NPCs are removed for this quiet loopback input fixture. Private ports
43649/43549 and disposable SQLite state are guarded; no public account/map/database
or server is involved.

**AWT callback to first observed client interpolation** has median
**415.000 → 45.310 ms**, range **98.708–551.156 → 35.049–49.833 ms**; nearest-rank
p95 for 13 samples equals each maximum. All13 destinations on each side match
both client and authoritative server positions. Two completed native captures per
run advance frame counters and capture records, before and after input. Thirteen
alternating clicks leave the final character one tile from the initial position.
Capture writes and settling are outside the timing phase. Summary:
`output/reviews/idle-walk-admission/final-native-summary.json`.

This measures observed interpolation, not physical OS click latency, scanout or
the exact first displaced completed frame. Completed screenshots prove eventual
rendered displacement; observer fields are not publication-safe exact-frame
telemetry. Thirteen local samples do not establish a universal percent improvement,
p99, loaded-server behavior or Internet/Oracle RTT. Owner visual acceptance,
weak hardware, slow-client/network profiles and a large native cohort remain open.

Historical pre-soft-budget/P2-client pairs remain unchanged: Java21 baseline
median338.771 →49.393 ms (12+12), Java8 median325.878 →47.334 ms (12+12). They show
baseline phase variation, not final-artifact repeats. Rejected setup runs preserve
full-map capacity 4000 refusal, capture-write timeout and a late welcome modal;
none is relabeled as an accepted input sample or production gameplay defect.

## Bounded CPU Cost

`soft-budget-cost-java21-1.json` retains raw 1,024 polls/case after 256 warmups.
The exact classes/config/JVM receipts accompany it. This measures additional
intake only, excluding enqueue/network, ordinary drain, movement, whole Server.run
and rendering. An untimed control proves the straight 24-tile paths actually retain
24 traversable queued steps; this is not every obstacle/cold-I/O worst case.

| Synthetic peers / work | p95 / maximum intake ms | Commands in 1,024 measured polls |
| --- | --- | --- |
|64 / empty |0.0025 /0.0132 |0 |
|256 / empty |0.0019 /0.0111 |0 |
|64 / saturated valid walks |2.1193 /2.2396 |8,178 |
|256 / saturated valid walks |2.2143 /2.7294 |7,057 |

The pre-soft Java21 saturated 256 run was p95/max2.975/4.035 ms and 8,192 commands.
The final guard reduces admitted work under contention; this is not improved
per-command speed. Keep count ceilings unchanged. We have not measured the
Oracle CPU or slower hosts, and the last command can overrun the soft budget.

## Reproduction Commands

Run from `/home/justin/Core-Framework-ai-3`; use new run names because evidence is
never overwritten. Coordinate exclusive CPU/GPU windows for numerical measurements.

```bash
cd server
bash ../tools/vendor/apache-ant-1.10.5/bin/ant test_idle_walk_admission \
  -Didle.admission.run=NEW_CANDIDATE_RUN
cd ..
python3 tools/movement-responsiveness/run-admission-fixture.py \
  --mode baseline --core output/tests/movement-first-step/candidate-production-core.jar \
  --run NEW_BASELINE_RUN
python3 tests/myworld/prepare-walking-doorway-fixture.py \
  output/tests/idle-walk-admission/walking-doorways-runtime
cd server
bash ../tools/vendor/apache-ant-1.10.5/bin/ant \
  test_walking_doorways test_ranged_approach \
  -Dpassage.fixture.dir=/home/justin/Core-Framework-ai-3/output/tests/idle-walk-admission/walking-doorways-runtime
cd ..
./scripts/build-server.sh
```

For the successful direct PvP/combat gates, run from the isolated runtime below
with current compiled test/core classes. The absolute working directory avoids
historical local.conf overrides. Java21 is explicit.

```bash
cd /home/justin/Core-Framework-ai-3/output/tests/idle-walk-native-input/baseline-java21-1/server
root=/home/justin/Core-Framework-ai-3
cp="$root/server/lib/log4j-api-2.17.0.jar:$root/server/lib/log4j-core-2.17.0.jar:$root/server/lib/log4j-iostreams-2.17.0.jar:$root/server/plugins.jar:$root/output/tests/combat/test-classes:$root/output/tests/combat/core-classes:$root/server/lib/*"
/usr/lib/jvm/java-21-openjdk-amd64/bin/java -Xmx768m -XX:+UseSerialGC -cp "$cp" \
  com.openrsc.server.combat.CurrentPvpFoundationCharacterization
/usr/lib/jvm/java-21-openjdk-amd64/bin/java -Xmx768m -XX:+UseSerialGC \
  -Dcombat.summary.file="$root/output/tests/idle-walk-admission/NEW-combat-summary.properties" \
  -cp "$cp" com.openrsc.server.combat.CurrentCombatCharacterizationTest
```

For native proof, prepare each side with the SAME frozen client and a fresh name,
then run sequentially; baseline core is the clock-only artifact above, candidate
core is `final-soft-artifacts/core.jar`. Each prepare/launch preserves provenance.

```bash
python3 tools/movement-responsiveness/native-input-proof.py prepare \
  --name NEW_NAME --client /absolute/frozen/client.jar --core /absolute/frozen/core.jar \
  --source EXACT_SOURCE_COMMIT --mode candidate
python3 tools/movement-responsiveness/native-input-proof.py run \
  --run output/tests/idle-walk-native-input/NEW_NAME --samples 13 \
  --java /usr/lib/jvm/java-21-openjdk-amd64/bin/java
```

The probe records owned PIDs, actual JVM/version, startup failure/timeout before
cleanup and completed captures. All final probe children exited before the
measurement window was released. Public deployment and user acceptance are
manager/owner follow-ups, not actions taken by these tools.

`git diff --check` passes. Workspace layout validation reports preserved historical
unclassified output-root entries (including static-analysis and old sprite exports);
all new writers use declared tests/reviews categories. Those ignored files were
not removed or reorganized as part of this task.
