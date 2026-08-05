# Current Combat Characterization Gate

The current Spoiled Milk combat characterization suite is an executable Java
regression gate over the current server implementation. It records behavior;
it does not define new balance, formulas, timing, or combat authority.

## Authoritative entry point

Run the Linux/macOS launcher from the repository root:

```bash
./server/test_combat
```

On Windows, run:

```bat
server\test_combat.cmd
```

Both launchers use the tracked Ant 1.10.5 distribution at
`tools/vendor/apache-ant-1.10.5` and the `test_combat` target in
`server/build.xml`. `test_combat_strict` is an equivalent Ant alias for tools
that expect a strict target name. The CI Java 8 build runs the Linux launcher.

The test target compiles production server sources and the two combat fixture
sources into isolated directories under `output/combat-test`. It never adds the
fixture source root to `compile_core` or `compile_plugins`. A successful run
removes those class directories and leaves this machine-readable receipt:

```text
output/combat-test/summary.properties
```

The Ant gate requires the receipt and a positive integer
`combat.summary.scenarios`. A Java process that exits successfully without a
receipt, or a receipt that reports zero scenarios, fails the build.

## Current scenario inventory

The A01 fixture executes these production paths:

1. melee, ranged, and magic damage-share XP plus Hits-focus distribution;
2. NPC attack eligibility/approach before plugin block and action callbacks;
3. signed-level spatial-domain and hostile line-of-effect rejection;
4. ordinary NPC retreat followed by hostile-target re-engagement;
5. NPC poison clearing through death removal and respawn;
6. Cleric Ward, Aegis, Thorns, and Rally ordering and values;
7. summon-owner damage contribution, threat, and style-XP exclusion;
8. scythe secondary-target eligibility;
9. shuriken target cap, eligibility, and uniqueness;
10. shared Cleric AoE caster, range, level, duplicate, and line-of-effect
    filtering; and
11. exactly-once NPC death-listener/drop callback dispatch.

The fixture constructs the production `Server`, `World`, `Player`, `Npc`,
handler, event, plugin, party, and transient-effect types. Test-only reflection
is limited to invoking existing private behavior and installing isolated
fixture state; it does not patch production classes or replace calculations.

## Gate self-tests

Use the deliberate-failure property to prove a launcher or imported build entry
point propagates a failed combat gate:

```bash
./server/test_combat -Dcombat.injectFailure=true
cd server && ./gradlew -Dcombat.injectFailure=true test
```

Use this command to prove an empty successful-looking harness cannot pass:

```bash
./server/test_combat -Dcombat.forceZeroScenarios=true
```

The first two commands must report `Deliberate combat gate failure fixture
requested` and exit nonzero. The zero-scenario command must write a zero receipt,
then report `Combat harness executed zero scenarios` and exit nonzero.

Gradle remains secondary and non-authoritative. Its `test` task imports and
depends on the Ant `test_combat` target before supplemental JUnit discovery; it
does not produce deployment artifacts or establish Gradle dependency parity.

## Packaging and review checks

After `./scripts/build-server.sh`, verify that production artifacts do not
contain A01 fixtures:

```bash
if jar tf server/core.jar | grep -q 'com/openrsc/server/combat/CurrentCombat'; then exit 1; fi
if jar tf server/plugins.jar | grep -q 'com/openrsc/server/combat/CurrentCombat'; then exit 1; fi
```

The server build's artifact inventory enforces these fixture exclusions and
remains the authority for fat JAR contents and plugin discovery. Run
changed-code analysis and the focused
combat, poison, projectile, NPC, Cleric, summoning, dragon, and layered-world
Python suites alongside this gate.

## A01 boundary

These scenarios intentionally assert outcomes that do not depend on a chosen
random roll or elapsed wall-clock boundary. Deterministic formula replay and
multi-tick timing need the bounded production seams recorded in
[`combat-a02-required-seams.md`](combat-a02-required-seams.md). Until A02 lands,
do not weaken this gate with source-text assertions or substitute a new random
or timing implementation inside the fixture.
