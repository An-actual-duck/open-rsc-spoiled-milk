# Client Startup Stall Investigation

Status: active diagnostic pass; no gameplay/client production fix proposed.
Started from `087359be83953f70980bed240fdc5dcb89e30a02` on November 8, 2026 UTC.
Owner authorization covers bounded private investigation and a proven minimal
fix; public deployment or shutdown is outside this task.

## Observed Failures And Limits

Three preserved private attempts printed `Fetching server configs` and did not
reach login or a timed performance phase:

| Fixture | Frozen client | Meaning |
| --- | --- | --- |
| `portability-scratch-2-base` | Released v0.3.14, `18779f57…` | Failure predates both optimizations |
| `portability-scratch-r2-4-candidate` | Workspace/P1, `cac7b49f…` | Failure after workspace optimization |
| `typed-uniform-wide-p1` | Workspace/P1, `cac7b49f…` | Failure during the float-dispatch comparison's P1 control |

Do not relabel the third row as a failed P2 artifact. The typed-float/P2 client
`f272554cf761891bb8a7a7c799fea536bb5f31a72eef4e2642d7f6913addb4fc`
completed its wide functional control. Its unchanged startup code makes it a
useful further diagnostic input, not an already proven failing artifact.

All three failed attempts are excluded from performance acceptance. Waiting for
both private status.json and the TCP listener did not eliminate the symptom.
Matching Java 21 jcmd attempts were bounded but yielded no useful Java stacks;
that is an observation, not proof of a JVM or protocol deadlock.

The `typed-uniform-wide-p1` own-socket/thread receipt was recorded at
04:46:55 UTC, **after** private STOP/TERM at 04:46:44 UTC. Its absent TCP sockets
and futex waits cannot identify the original stall stage or exclude an earlier
connection. This provenance correction supersedes the initial pre-TCP inference.
The original files and corrected receipt remain preserved. Server logs in that
fixture are empty, so no session/config timing correlation is available.

Source alone supplies hypotheses, not an accepted cause: config bootstrap opens
a socket, flushes opcode 19 and reads a config response synchronously; earlier
protocol/timer speculation remains unproven. Window initialization, configuration
application, class initialization, native loading and shutdown paths must also
remain in scope until live evidence distinguishes them.

## First Live Native Control

A frozen P2 hidden benchmark client was launched as GDB's own child under MI2,
with normal JVM implicit-fault signals passed through and ordinary ASLR retained.
No global ptrace setting was changed, and no unrelated process was attached or
signaled. The private endpoint remained loopback 43639 and the registered
renderer fixture safeguards were retained.

Fixture: `output/tests/renderer-shadow-benchmarks/startup-gdb-hidden-1/`
Capture: `gdb-20261108T045648Z`
Own inferior PID: 139572 (now exited).

At 20 seconds, before any STOP or debugger interrupt, the receipt records
`beforeAnyStop=true`, successful config completion, a live loopback TCP socket,
thread names/wait channels and private server state. The child was then
interrupted through its own debugger and an all-thread native backtrace captured.
It was a **healthy control**, not a reproduced hang. GDB's startup timing can
change race occurrence; this success does not clear the original failures.

The child and private server exited; all files remain preserved. Raw MI output,
readable native backtrace, launch/JAR/argument receipts and the pre-stop process
receipt are in that capture. Exact helper source and private lifecycle logs are
under `output/diagnostics/client-startup-stall/`.

## Next Bounded Evidence

The movement worker has priority over CPU/GPU/native windows. No Java/GPU probe
runs concurrently with its native acceptance work. Source preparation may proceed.

The next prepared helper compares ordinary visible `java -jar` startup with the
hidden fixture. It may start the same verified private child at its first native
instruction, detach while GDB remains its original parent, then capture the live
receipt before any STOP and reattach only that identity-verified child for native
stacks. This permits ordinary untraced Java bootstrap without a global ptrace
change. A matching Java 21 jcmd probe remains bounded to six seconds.

Every observation must record UTC timing, original parent/PID/cwd/argv/JAR hash,
config stage, whether STOP/TERM/debugger interruption had occurred, and its
actual evidence limits. A failed diagnostic mechanism is not a game defect.
Stop after a bounded few attempts/time if the hang does not reproduce; preserve
a report rather than inventing a protocol or rendering fix. A production change
requires a causal witness and focused regression proof first.
