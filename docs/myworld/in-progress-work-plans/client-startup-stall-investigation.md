# Client Startup Stall Investigation

Status: native cause captured; minimal startup-order candidate awaiting repeated native verification.
Started from `087359be83953f70980bed240fdc5dcb89e30a02` on November 8, 2026 UTC.
Owner authorization covers bounded private investigation and a proven minimal
fix; public deployment or shutdown is outside this task.

## Observed Failures And Limits

Four preserved private attempts printed `Fetching server configs` and did not
reach login or a timed performance phase:

| Fixture | Frozen client | Meaning |
| --- | --- | --- |
| `portability-scratch-2-base` | Released v0.3.14, `18779f57…` | Failure predates both optimizations |
| `portability-scratch-r2-4-candidate` | Workspace/P1, `cac7b49f…` | Failure after workspace optimization |
| `typed-uniform-wide-p1` | Workspace/P1, `cac7b49f…` | Failure during the float-dispatch comparison's P1 control |
| `startup-detached-p1-hidden-1` | Workspace/P1, `cac7b49f…` | New live pre-STOP witness with native stacks |

Do not relabel the third row as a failed P2 artifact. The typed-float/P2 client
`f272554cf761891bb8a7a7c799fea536bb5f31a72eef4e2642d7f6913addb4fc`
completed its wide functional control. Its unchanged startup code makes it a
useful further diagnostic input, not an already proven failing artifact.

All four failed attempts are excluded from performance acceptance. Waiting for
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

## Live Failure And Ordinary Visible Comparison

A second probe used frozen P1 and Java 21.0.12. GDB started the verified private
child at its first native instruction, detached while retaining original-parent
ownership, and let Java bootstrap run untraced. At 05:17:25 UTC, the receipt
records config incomplete, no owned TCP socket rows, and `beforeAnyStop=true`.
There had been no debugger interruption or STOP/TERM. This is a new live
observation; it does not reuse the earlier post-stop socket inference.

The matching Java 21 jcmd request then timed out after six seconds. GDB
reattached only its original identity-verified child and captured all native
thread stacks before cleanup:

- The OpenGL presenter was in LWJGL `SharedLibraryUtil_getLibraryPath`, inside
  `dl_iterate_phdr` and its callback's `dlclose`, waiting at
  `_rtld_global+2568`.
- The client loop was inside `JVM_LoadLibrary` / `dlopen` loading the JDK's
  `libextnet.so`, waiting in `_dl_add_to_namespace_list` at
  `_rtld_global+2608`.
- Signal Dispatcher was trying to initialize AttachListener, but thread creation
  waited in glibc TLS initialization on another native-loader lock. This also
  explains why a Java attach request could not produce a dump in this witness.

These competing native library-loader paths provide causal evidence for the
observed startup collision. It is not evidence of a game protocol/session or
packet problem. The child was then resumed and terminated only through its own
scoped debugger; the private server exited through STOP.

Capture: `output/tests/renderer-shadow-benchmarks/startup-detached-p1-hidden-1/gdb-20261108T051705Z/`.

The agreed ordinary visible comparison used the same frozen P1 JAR and Java 21
through normal `java -jar`, with its own isolated private endpoint/settings.
It reached configs normally at the 20-second observation. Matching jcmd returned
a full 15.7 KB Java thread dump in 214 ms, so the attach tool is demonstrably
functional on a healthy control. Its successful startup does not prove the
racy native collision is confined to hidden launches.

Capture: `output/tests/renderer-shadow-benchmarks/startup-detached-p1-ordinary-1/gdb-20261108T051828Z/`.

## Minimal Candidate And Verification Queue

The candidate prepares the default Java socket implementation on the initial
thread, before `ScaledWindow.getInstance()` can create/start native graphics.
It uses an unconnected public `Socket`, sets TCP_NODELAY and closes through
try-with-resources. It does not connect, bind, name an internal module, modify
protocol/session behavior or replace a dependency. IOException/security failures
are logged and the ordinary connection path retains its existing error handling.

Focused tests pass on Java 8u482 and Java 21.0.12. The actual Java 21 map probe
records libextnet absent before preparation and present afterward, while native
LWJGL/GLFW libraries remain absent. File descriptors remain 8 -> 8 across 64
additional preparations. Java 8 records no libextnet module and descriptors
10 -> 10. A public-Socket implementation probe verifies no connect, bind, listen
or accept and closure on success, IOException and security failure. Startup
wiring precedes window/native graphics initialization. The default Java 8 build
and complete renderer guardrail suite pass. Repeated controlled baseline and
frozen-candidate native startup observations remain required before acceptance.

The movement worker has priority over CPU/GPU/native windows. The shared
renderer-gpu-benchmark.lock protects the diagnostic child lifetime, including
bounded debugger cleanup; the prepared helper now acquires it before any private
fixture starts. No global ptrace setting or unrelated process is touched. Every
live receipt records UTC ordering relative to detach, jcmd, debugger interruption
and STOP. All historical failed attempts and original receipts remain preserved.
