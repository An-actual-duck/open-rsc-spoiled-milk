# Bounded Idle Ground-Walk Command Admission

Status: active private implementation and validation. No public runtime changes.
Branch: `fix/idle-walk-command-admission`; baseline source `729476caa8faef2fd45dbcff316198df03c72d34`.
Follows the independently accepted [successful-step clock slice](movement-first-step-implementation.md).

The 640 ms world tick still owns spells, combat starts, item/economy handlers,
interactions and complex cancellation. This slice admits only a bounded FIFO
head prefix of ordinary custom-client ground walks and no-op heartbeats, on the
same GameThread immediately before the existing intertick movement pass.

The original packet parser selection, processor, malformed-packet response,
active-opcode bookkeeping and packet timing/count accounting now live in one
helper used by both admission paths. Network filtering and queue limits still
precede enqueue. No handler is duplicated or run on Netty threads.

A fast walk must have the shipped even-length 4–54-byte payload shape and a
non-consuming, overflow-safe aggregate waypoint-distance estimate of at most
24 Chebyshev tiles. Length alone does not bound `Path.addStep` work. Unknown,
odd, oversized and more expensive requests remain ordinary-tick FIFO barriers;
this optimization does not newly reject valid requests or bypass the parser.
Approach (`WALK_TO_ENTITY`) plus interaction packets remain together on the
normal path, including when earlier item/spell packets precede a ground click.

The owner gate excludes busy/dead/unregistering/teleporting actors; plugins,
batches, menus and NPC dialogue; following, possession and walk-to actions;
bank/shop/auction/trade/duel state; configured agility; drop/talk callbacks;
cannon, pending attack intents and all outgoing/incoming combat owners.
Existing logout cooldowns conservatively retain recent-combat/exchange contexts
on the ordinary path. A pure pending-intent query does not expire transactions.
Connection eligibility requires open, active and writable. Older custom
protocols and other walking profiles retain normal admission.

Budgets are two packets per player per poll, eight per player per world tick,
and eight globally per poll. The player method clamps its own allowance too.
A persistent registry-index cursor rotates admission priority without changing
movement order. Two allocation-free live registry passes avoid player snapshots
and sorting; an empty registry returns immediately. Empty/raw-barrier queues
return before broad owner checks, with no checked world-coordinate lookup.

## Functional Evidence And Limits

Early matched runs through real `RSCConnectionHandler`, its upstream packet
filter, the FIFO and actual custom parser/processor pass 1,924 baseline and
2,328 candidate assertions. All 640 command-arrival phases were exercised with
an injected clock: baseline admission-to-first-authoritative-step p50/p95/max
320/608/640 ms; candidate 5/9/10 ms. The preserved first slice is in both builds.
These are logical timing results, not network, CPU throughput or rendered-input
latency measurements. Synthetic attached peers are explicitly not real logins.

At the global eight-packet allowance, 64 one-packet peers were all served in
eight polls, and 256 in 32 polls. Further ownership, spam fairness, authentic
private login, native input/presentation and broad regression checks remain
pending before READY. The earlier candidate runs precede the added inactive
channel guard and internal allowance clamp; final receipts will identify the
exact final sources/artifacts rather than silently replacing them.

Reproduce in a fresh current-protocol/native world with
`tools/movement-responsiveness/run-admission-fixture.py`, providing mode,
a frozen core JAR, optional candidate overlay and a new run label. The tool
prepares isolated configuration and unused test database credentials, preserves
every log/result and records source/class/config SHA-256 receipts under the
declared `output/tests/idle-walk-admission/` category. Ignored historical
`server/local.conf` remains untouched and cannot override these private runs.
