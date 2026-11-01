# Private practice feedback corrections

Branch: `fix/practice-feedback` (base `117f45056362eb822c4b773e7b34a73ec5fdd259`).

The owner reported an unclear coating/gland icon, the furnace's mould thumbnail,
missing player health/coordinates and partial auto-retaliate control, plus green
liquid corners in the disposable practice yard.

## Changes

- Approved native Aseprite coating/gland revisions are integrated as game-owned
  external PNGs. Coating retains its one-dose recipe/use; gland retains its item
  identity and gets its own icon instead of recoloring the eye. The eye is unchanged.
- The furnace's Blank sigils category now uses the Blank Silver Sigil product
  (3431). The reusable Sigil mould (3433) remains required and remains the moulding
  session's input identity. Actual production is unchanged.
- `FloatingHitSplatRenderer.draw` now restores its caller's renderer phase.
  Previously it reset to SCENE even with no hit numbers. The following health,
  coordinates and auto-retaliate commands were therefore classified as world
  scene content and failed to replay correctly over the OpenGL world. This is a
  real client regression, not an account preference or test-server setting.

The native HUD fixture runs empty and queued hits from WORLD_OVERLAY/UI_OVERLAY/
SCENE callers, then draws the actual player HUD at 800/960 widths. It checks
health/coordinate pixels, captured command ownership, the actual button's hit
bounds/click consumption, and emitted auto-retaliate setting packet.

## Test-map finding

Core's separate read-only runtime inspection found the temporary yard's hidden
terrain backing was palette 93 (green), explaining its half-tile corners. Core
corrected only that disposable fixture, preserving its previous package and
normal player saves. No game loader, editor, owner-authored map or custom liquid
asset change is included here. Actual OpenGL screenshot acceptance of the fixed
HUD and corrected private pool border remains a Core gate.

## Validation

Commands run serially with Java 8 and bounded heaps:

```sh
JAVA_HOME=/home/justin/jdk8u482-b08 ANT_OPTS='-Xmx512m -XX:+UseSerialGC' sh tools/vendor/apache-ant-1.10.5/bin/ant -f server/build.xml compile_core compile_plugins
JAVA_HOME=/home/justin/jdk8u482-b08 ANT_OPTS='-Xmx512m -XX:+UseSerialGC' sh tools/vendor/apache-ant-1.10.5/bin/ant -f Client_Base/build.xml compile
JAVA_HOME=/home/justin/jdk8u482-b08 python3 tests/myworld/test-cleric-metal-sigils.py
PATH=/home/justin/jdk8u482-b08/bin:$PATH python3 tests/myworld/test-floating-hit-splats.py
JAVA_HOME=/home/justin/jdk8u482-b08 python3 tests/myworld/test-practice-item-art.py
python3 tests/myworld/test-auto-attack-hud.py
python3 tests/myworld/test-furnace-mould-flow.py
git diff --check
```

Results: both builds passed; 206 native metal-production assertions passed;
236 HUD-specific assertions passed across software/phased/native-UI profiles;
existing hit-number behavior/layout/native-font, settings/persistence and pixel
centering assertions passed. Approved art reproducibility/packaged loading and
existing furnace/auto-attack checks passed. No broad combat re-run was needed
for this focused visual correction. Native archives, original potion reference,
server item definitions and all gameplay values are unchanged.

No server operation, owner-map import/export, live integration or deployment was
performed by the worker. Core owns packaging and private-session acceptance.
