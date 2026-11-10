# Experimental physical browser output

Worker `feat/browser-physical-world-output`, base `f2cca708956167e15eadebc4b437fab896eaa40e`.
Default OFF. No Java/client game logic, source scene, server, art, or native/software
renderer changes. This is a GPU presentation prototype requiring manager review.

The existing browser renders the world into the logical Java framebuffer size and
then CSS scales that image. The prototype instead selects GPU backing dimensions
from the actual game canvas CSS rectangle and device pixel ratio, preserves logical
aspect ratio, and caps backing at 1920×1080, 2,097,152 pixels, and MAX_TEXTURE_SIZE.
It does not increase Java UI pixels, actor composition resolution, input coordinates,
scene preparation, world matrices or logical color-culling dimensions. World,
actors and sky rasterize at the selected backing size; existing shadow maps keep
their current sizes and algorithms. No mip or texture-filtering changes are included.

## Scope and controls

- Explicit private prototype query: `?mode=webgl&physical=on`. Ordinary URL/default
  stays OFF; software ignores the option. Invalid WebGL values fail closed.
- Private benchmark-only `window.browserBenchmarkPhysicalOutput(boolean)` queues
  a policy change. It commits before any pass of a fresh complete source WORLD or
  opaque reset. It cannot commit during held regions, retained BASE or fade frames.
  It does not add a normal game setting or Java property.
- CSS/DPR changes during retained frames defer backing resize until a fresh source
  frame. Region holds replay the existing physical FULL texture without resize,
  readback, copy or token change. Context/Stop errors remain fatal and idempotent.
- Logical RGB/T planes are uploaded unchanged. The physical overlay shader uses
  integer pixel-center mapping to fetch logical source texels, including the Java
  top-down to GPU bottom-up conversion. This preserves nearest pixel-art UI and
  avoids float rounding at fractional scale boundaries. Dimensions are bounded so
  shader integer products cannot overflow. The ordinary OFF shader stays unchanged.
- Middle input receives explicit logical extents; physical canvas backing never
  determines Java drag coordinates. Left/right/wheel remain on the unchanged Java
  AWT display and CSS normalization path.
- Canvas CSS geometry is read only after display/resize/DPR invalidation. The
  ordinary OFF backend does not install physical sizing listeners or read layout.

## Resource contract

Logical RGB8 and R32F T storage remain bounded by the source 2M-pixel contract.
FULL/BASE textures now follow physical backing dimensions, still at most two
2M-pixel RGBA8 textures. The explicit prototype compositor peak is 48MiB, including
logical C/T plus both old and replacement physical pairs. Ordinary standalone/OFF
compositors keep the previous 32MiB cap. An allocation reservation callback from
retained capture accounts for the complete old+new pair before allocation; it
never bypasses the retained module's independent 16MiB steady/32MiB pair peak.

The default framebuffer color/depth storage is additional driver-owned memory.
Receipts estimate eight bytes per physical pixel, separately from texture budgets;
this is not a claim about exact driver allocation or browser compositor copies.

Receipts separate `physicalOutputRequested`, `physicalOutputActive`, pending policy,
logical dimensions, actual backing, cached CSS rectangle, DPR, capped request and
retained/compositor budgets. `physicalOutputWorldFramePolicies` aligns with fresh
world intervals; bounded transition receipts identify commits. FPS comparisons
must report backing dimensions as well as equal logical source work and actors.

## CPU checks so far

- 10,000 sizing/cap cases plus invalid values, default isolation, dirty-only layout,
  DPR changes and idempotent listener cleanup.
- 42 actual backend pipeline cases: logical matrices/lighting/upload sizes unchanged,
  physical viewports/FULL capture, all 16 fade counts, BASE, resize deferral, opaque
  UI, signed underground rendering, held FULL, context loss and cleanup.
- Queued ON/OFF remains pending through fade/hold, commits at complete fresh source
  or opaque reset, and keeps policy samples aligned with source world samples.
- Middle drag test: physical1920×1080 with CSS960×540 and CSS1920×1080 both deliver
  logical center (480,270) and drag (720,400), including ordinary release behavior.
- Existing owned/scoped state42-frame oracle remains unchanged: 570 draws/copies/
  clears, 2777 scoped queries, zero hot owned queries. Standalone/default overlay,
  retained fade, upload fault and source-readiness tests remain passing.
- A maximum-size compositor fixture reaches exactly48MiB transactional peak with
  both logical source planes and both physical pairs accounted, then releases all
  resources. Standalone default32MiB behavior is preserved.

## Prepared hardware proof (manager execution only)

`tests/myworld/prepare-browser-physical-hardware.py --run-id RUN --dependency-jar JAR`
creates a fresh immutable stage under `output/tests/browser-physical-output/hardware/`.
No browser, server, account or GPU process is started. Its HTML exports
`window.physicalOutputProof`.

The fixture compares current default OFF against frozen basef2cca, and physical
output against that same production renderer drawing into an equal physical target
with a CPU-expanded logical UI/T reference. All world/actor matrices and geometry
remain identical between compared physical targets. It includes actual production
shader exports/native sky palettes, all 16 fade counts, fractional-scale patterned
UI, output resize, held FULL after clearing the destination, queued policy changes,
a real queued GL fault before any replay, Stop/init and actual context loss.

Approved immutable dependency:
`/home/justin/Core-Framework/output/tests/browser-gameplay-spike/browser-streaming-check-1/web/client.jar`
SHA256 `02babd3b0be6e1d8067572a97a2bf1ac26f015952098799f18f1bbdafeec1583`.
The worker's ignored JAR was not read or overwritten. Source remains default OFF;
no hardware pixels, Firefox visual improvement or FPS result is claimed yet.
