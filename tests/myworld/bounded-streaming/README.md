# Independent browser streaming and scene review

These fixtures call production APIs. They do not start a game, browser, server,
or GPU, and do not establish pixels, gameplay acceptance, total VRAM or FPS.
Pass explicit source/artifact paths; do not transplant production classes.
Outputs refuse overwrites so prior failed and successful evidence survives.

* `selection-oracle.mjs --source-root ROOT --catalogue CATALOGUE --out FILE`
  admits the complete source catalogue, then compares literal draw arrays with
  an independently filtered and ordered catalogue for every source-cell center.
  It covers mutation/animation/restoration, roofs, rebasing and signed fallback.
  Catalogue coverage and unique selected chunks are explicitly separate from GPU
  allocations (zero in this fixture).
* `fetch-oracle.mjs --source-root ROOT --out FILE` exercises the actual resource
  byte reader with independently produced streams, including pending-read abort.
* `scheduler-oracle.mjs --source-root ROOT --out FILE` uses a caller-owned ledger
  for fixed textures, resident geometry and incomplete allocation. It checks
  readiness, active eviction protection, byte/count/pending/queue caps,
  backpressure, stale producers/uploads, disposal and failures.
* `verify-static-scene.py --jar JAR --catalogue CATALOGUE --package PACKAGE
  --cache CACHE --out DIRECTORY` requires a clean, full-source production build
  receipt and manifests adjacent to the JAR. It compiles only independent fixture
  classes. The same JAR runs literal and retained controls; comparisons cover
  exact rows/metadata, filter ownership/order and descriptor/caster/glow fields.
  The complete index is injected into detached owners to isolate the API:
  catalogue constructor admission and native/GPU work remain separate. Use a
  cache containing `video/Authentic_Landscape.orsc` for the actual World constructor.
* `analyze-stationary-profile.py INPUT --out DIRECTORY` verifies the capture
  receipt and paired stationary markers, analyzes main/Worker CPU samples and
  exports exact current captured source bodies with SHA256. Generated names are
  validated against their bodies. Mapping those bodies to Java requires a
  structural review against the exact production source manifest.

Inclusive, deepest and leaf sample rankings overlap. They must not be added or
used as GPU timings. A recorded incomplete upload never grants fresh readiness.
Dynamic door/tree key changes in the detached fixture exercise actual capture
publication, not gameplay packet decoding or a populated roaming session.
