# Browser native-call dispatch experiment

Baseline: `325bf42b60fe411705f9f14c0a3ef6debabe24a7`. Default remains the original
**async** dispatch. No browser FPS benefit or Java-runtime compatibility is claimed
from the CPU fixtures.

## Contract and design

CheerpJ's official native-method guide explicitly permits a return value or a
Promise resolving to one, while its examples use async functions. Calling back
into Java through the supplied library is genuinely asynchronous. Reference:
https://cheerpj.com/docs/guides/implementing-native-methods (checked 2026-11-10).

Nineteen core WebGL natives perform synchronous JS validation, copying and GL
submission. Their current async declarations and outer async fatal wrapper create
Promise boundaries even though their bodies do not await anything. The shader
initialization native awaits the exact vertex-contract digest and must stay async.
Transport, socket and input natives are outside this experiment.

The maintained `webgl.js` keeps its original async native bodies and fatal wrapper
as the literal control. A strict preparation-time generator derives an ignored
`webgl-sync.js`: it removes async only from the audited nineteen declarations and
changes an explicit capability marker. Both modes use the same maintained bodies.
There is no runtime eval, Function constructor, duplicated maintained renderer,
Promise adapter in the async control, or additional async-control wrapper layer.

The generated mode uses a synchronous fatal wrapper. It returns values or throws
immediately. Unexpected thenables are fatal protocol violations; their rejection
is consumed to avoid an orphan unhandled rejection after the session has failed.
The true async initialization native retains the original async fatal wrapper in
both modes. Allocation checks, upload validation, array copying, GL error polls,
readiness and source-frame ordering are unchanged.

Only the separately approved coherent-sampling receipt differs from frozen async
body source: `javaBridgeReportedWorldFrames` is initialized to zero and copied from
`javaWorldFrames` after successful frameWork completion, identically in both modes.

## Integration API

- `scripts/browser-gameplay/native_dispatch.py` exports
  `synchronous_module(canonical_source: str) -> str`. Unknown/missing/duplicate
  natives, capability-marker drift, any pure-body SHA256 drift or a new await in
  a pure body reject generation. A Promise-returning change without await also
  requires explicit re-audit rather than silently becoming synchronous.
  The generated header records the canonical source SHA256.
- `browser-native-call-dispatch.js` exports
  `browserNativeCallPolicy(mode, options)`. Only benchmark WebGL maps
  `nativecalls=sync|async`; ordinary/software default async and ignore overrides.
- The frontend imports `webgl-sync.js` only for sync, otherwise `webgl.js`, then
  passes `nativeCallDispatch` with the selected string. The module capability
  `BROWSER_NATIVE_CALL_DISPATCH` rejects a mismatched constructor policy before GL
  context creation. No live mode switch is provided.
- `stats.nativeCallDispatch` reports requested/active mode and the 19 synchronous /
  1 asynchronous inventory in sync mode, versus 0 / 20 in the async control.
- ai1 owns prepare.py generation/manifest hashes and browser.js import/wrapper
  selection. Its small outer sync wrappers must retain original async branches
  and preserve cover, alignment and publication order. This branch does not edit
  those files.

## CPU proof

Run `python3 tests/myworld/test-browser-native-call-dispatch.py`. It separately
loads the frozen original backend, canonical async control and generated sync
backend, and guards original native bodies/fatal wrapper text against drift.

The full 42-frame pipeline corpus compares every recorded GL command and primitive
array byte, effective state/draws, native invocation counts, borrowed-array use,
retained fades 1–16, duplicate fades/BASE, resize, sky, shadows, sprites, held FULL
and resource accounting. It asserts actual Promise versus immediate return types.
It also checks unchanged async error/microtask ordering, immediate sync failures,
unexpected Promise rejection consumption, queued-upload rejection before draws,
allocation/RGB/copy failure rollback, Stop during initialization, duplicate init,
context loss and idempotent cleanup. The GL double does not compile shaders or
rasterize pixels; these tests are not actual browser, CheerpJ or GPU evidence.
The CPU command corpus pins a fake frame clock so readiness cadence is identical
between cohorts; otherwise diagnostic byte-recording/JIT time can legitimately
change fence retirement timing. Actual hardware uses real fences/browser tasks.
The final corpus exercises all 20 native methods and compares 12,202 GL commands
per cohort. Command trace SHA256:
`34e2302068aab6d63699497f67731d7793d7a89896d64fe6c3227448cf3452ec`.

Frozen source modules, hashes and receipts live under the declared
`output/tests/browser-native-call-dispatch/` subject. No production Java/client JAR, gameplay,
server, account or public deployment is changed by this experiment.

`prepare-browser-native-call-hardware.py --dependency-jar APPROVED_JAR --run-id RUN`
freezes the current/original/generated modules, actual production shader/native
sky data and a manifest-validated isolated hardware runner. Preparation launches
no browser or HTTP service. Its explicit emitted `run-hardware.mjs` command is
reserved for the manager's exclusive GPU window. The standalone page exposes
`window.nativeCallProof`, compares 42 nonblank actual pixel frames, readiness,
Stop/init and real context loss. It does not execute CheerpJ; that remains a
separate whole-game smoke gate. Initial prepared review stage uses approved clean
325bf JAR SHA256 `f72047bfb006b79a1e51889b46be08a36c9c80d353b4fa5ccece795c06ad1279`.

## Manager-run pixel evidence

The manager ran the manifest-validated `review-native-sync-v1` stage in Firefox
154 on reported Radeon hardware (privacy-sanitized Radeon R9 200 Series string).
All 42 actual production-shader/native-sky cases passed: 8,650,752 RGBA bytes
compared, zero changed bytes, complete readiness/state, Stop/init race and real
context-loss checks. Result:
`/home/justin/Core-Framework/output/diagnostics/browser-performance-pass/native-sync-pixels-2/result.json`.
This establishes this isolated GPU pipeline's parity, not Java interop, input,
game-thread scheduling, gameplay or FPS. No GPU/browser was launched by this worker.

## Authentic Java contract probe

`prepare-browser-native-runtime-probe.py --run-id RUN` compiles/freeze a tiny
target-8 test JAR and page under this subject's `runtime/` directory. It launches
no browser. The page uses the real CheerpJ 5.0/JRE8 runtime and exposes
`window.nativeRuntimeProof`. In one JVM, Java checks 100 scalar/int-array pairs
including MIN/MAX/sign/overflow and unchanged inputs, compares a rejected async
native against a direct synchronous helper throw as caught JavaThrowable class/
message, continues after each failure, and checks an unexpected-thenable protocol
error without an orphan rejection. No game/client classes, display, account or
server are involved. Actual runtime execution remains manager-owned; compilation
and JS syntax checking alone are not a JNI compatibility result.

## Remaining gates

Root must run the integrated frontend in real CheerpJ before any adoption. Check
direct int-array return conversion, sequential Java native ordering, error
propagation, input/Stop responsiveness and asynchronous init/context-loss paths.
Removing Promise boundaries may change cooperative scheduling; the official
return-value contract does not prove identical game-thread scheduling or latency.

Then compare the same immutable JAR and fixed workload using the literal original
async mode versus sync, warm both modes, confirm actual module/source hashes and
dispatch receipts, and measure fresh source-world FPS and CPU/bridge time. Keep
ordinary dispatch async if the measured benefit or lifecycle proof is absent.
