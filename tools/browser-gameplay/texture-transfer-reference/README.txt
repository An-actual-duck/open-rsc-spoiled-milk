Standalone WebGL2 texture-transfer reference (private diagnostic)
================================================================
Purpose
-------
Compare actual API submission CPU/wall costs of identical960x540 owned synthetic
C (raw BGRA-byte RGBA8) and T (finite Float32/R32F) data. No Java, gameplay,
borrowed-input-plane ownership or production renderer changes. No process starts
from preparation. This reference does not decide runtime adoption.

Variants: full C/T; exact full-width union row bands (NO runtime60% fallback);
minimal strided rectangle views with UNPACK_ROW_LENGTH960; ordinary reusable
staging packed per rectangle; explicitly prepacked control. The captured64x32
coarsening is the comparison layout, not the evolving runtime planner's chosen
coarsening/max8 rectangles. All variants use precisely the same synthetic phases.

Correctness before timing
-------------------------
Every captured layout and all five variants must match every C byte and every
Float32 T bit of CPU/FULL. Integer RGBA8UI framebuffer encodes fetched C bytes or
floatBitsToUint(T); readPixels checks all bytes. FULL is checked by that same
oracle, so agreement is transitive against FULL, not merely two unverified paths.
A failure stops before timing. No EXT_color_buffer_float is required: R32F is
sampled, not attached to the read framebuffer. GPU proof is NOT yet run in worker
preparation. Manager must run it on actual Firefox/hardware. The completed source-bound
manager Radeon/Firefox154 reference is documented in RESULTS.txt.

CPU proof independently checks valid/rejected geometry, every packed row against
its minimal strided source view, output reconstruction, odd width,1x17 and all16
captured layouts. CPU proof cannot establish browser/API/driver correctness.

Timing design and limits
------------------------
After all GPU checks, choose one median-area captured layout; keep it fixed for
all timed variants. Alternate phases every RAF; no extra full reset each frame.
Reset once outside each cohort; perform two consuming integer draws per frame,
outside the API interval but inside wall time. Two orders, five variants,2s each:
~20s timing plus all-layout GPU proof. RAF pacing avoids a tight upload loop but
cannot eliminate real texture dependencies or driver synchronization.

CPU interval includes view creation, ordinary packing, uploads and identical
once-per-plane getError checks (never per rectangle). View/packing times are also
reported, with their performance.now instrumentation overhead. Reusable staging
allocation and explicit prepacked preparation are outside timing; prepacked is
only an explanatory control. No row gather is secretly excluded from 'packed'.
CPU source-creation/prepacking and GPU readback happen before timing. Per-frame
metric bookkeeping and consumer-draw submissions contribute to RAF/wall intervals.
One layout's source pair is retained at a time; buffers/rectangles are bounded.

Report calls, logical texel bytes, source view span bytes, copied staging bytes,
packing/view costs, API CPU and RAF/wall statistics. Source-span bytes describe
submitted views, not actual GPU-bus traffic. API time is not GPU execution time;
RAF throughput is not gameplay FPS. No timers query GPU duration, and no gl.finish
manufactures synchronization. Exact checks are synchronous readbacks BEFORE timing.
This20s synthetic test cannot replace real-game pixel/loading/strict-combat/FPS
acceptance or validate gameplay token/ACK/held-frame history.

Hardware/state
--------------
Require WebGL2 and unmasked renderer metadata; reject recognized software strings
(llvmpipe/softpipe/SwiftShader/lavapipe/etc). Record renderer/vendor/user agent.
This is a diagnostic identification guard, not cryptographic proof of hardware;
manager must inspect it. Focus/visibility loss fails timing. Clean up GL textures,
framebuffers, programs, shaders and VAO. Context is standalone; initialize canonical
unpack alignment4, row length/skips/imageheight0, flip/premultiplyfalse, colorspace
NONE, PBO null; restore row length0 after each plane. Both C/T use the same error
policy. No state restoration into another renderer is implied by this isolated test.

Firefox154 source qualification
-------------------------------
Official release tag resolves032a9fc1ac0cc3209f7c142744ba2e40847c8086. In queued
client WebGL paths, typed source ranges may serialize contiguous spans including
row gaps. Minimal view span is ((height-1)*fullWidth+rectWidth)*elementBytes.
Packing may trade CPU gathering for smaller IPC spans, not necessarily bus bytes.
Sources (primary; a source audit does not prove the installed binary/prefs path):
https://github.com/mozilla-firefox/firefox/blob/032a9fc1ac0cc3209f7c142744ba2e40847c8086/dom/canvas/ClientWebGLContext.cpp#L4368
GetRangeFromData; L4418 range shrink and L4769 TexImage use.
https://github.com/mozilla-firefox/firefox/blob/032a9fc1ac0cc3209f7c142744ba2e40847c8086/dom/canvas/WebGLCommandQueue.h#L111
Contiguous command-queue memcpy. See also WebGLQueueParamTraits.h#L86.
https://registry.khronos.org/webgl/specs/2.0.0/
Typed-array offsets use typed elements; UNPACK_ROW_LENGTH uses pixels. This test
uses srcOffset0 and minimal typed views, avoiding skip-prefix copying ambiguity.

Prepare/run (manager owns HTTP/browser; worker does not launch)
-------------------------------------------------------------
node tools/browser-gameplay/texture-transfer-reference/proof.mjs CAPTURE_JSON
python3 tools/browser-gameplay/texture-transfer-reference/prepare.py \
 --writer-capture CAPTURE_JSON --output NEW_DECLARED_TEST_STAGE --port 8208
Preparation refuses existing destinations, checks loopback port free, freezes
source files/layouts and records exact source commit/dirty state/input+file hashes.
Use stage-manifest.json startCommand verbatim. Open http://127.0.0.1:8208/ in the
manager's isolated Firefox, click Run, inspect completed/renderer/exact checks,
then Save JSON or read window.textureTransferResult. Close HTTP afterward.
