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

The test target compiles production server sources and the four combat fixture
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

## A01-A05.1 boundary

A01 established the real-Java gate without moving production authority. A02
adds the bounded production-preserving clock, random, and whole-tick test seams
recorded in [`combat-a02-required-seams.md`](combat-a02-required-seams.md), so
the same gate now also replays chosen hit/miss, NPC style, cooldown, retreat,
projectile, secondary-target, lifesteal, effect-expiry, and selected-drop
boundaries. Source-text checks remain supplemental and must not replace these
runtime scenarios.

A03 grows the same gate from 20 to 26 scenarios. Its added scenarios exercise
reason-coded compatibility messages, exact melee intent/plugin ownership,
side-effect-free denial, lifecycle and lease cancellation, all maintained
player attack-start styles, missing-resource rollback, and manual priority over
passive autocast. The implementation and retained content-policy boundaries are
recorded in
[`combat-a03-eligibility-transactions.md`](combat-a03-eligibility-transactions.md).

A04 grows the gate from 26 to 32 scenarios. Its added scenarios exercise one
outgoing/many incoming engagement ownership, retargeting without peer teardown,
stale callback isolation, teleport/logout/death cleanup, passive retaliation,
explicit audit repair, and reciprocal melee teardown. The compatibility
projection deliberately preserves the narrower historical meaning of
`getOpponent()` and `inCombat()`. The ownership contract and deferred behavior
decisions are recorded in
[`combat-a04-engagement-event-ownership.md`](combat-a04-engagement-event-ownership.md).

A05.1 grows the gate from 32 to 35 scenarios. Its three added scenarios cover
resolved legacy request/result invariants, lifecycle-aware participant
snapshots, exact current PvM melee observation facts, and isolation from both
observer setup and callback failures. Production keeps the observer disabled,
and no damage or death authority moves. The source inventory and later
source-family boundaries are recorded in
[`combat-a05-damage-observation-foundation.md`](combat-a05-damage-observation-foundation.md).

A05.2 grows the gate from 35 to 38 scenarios. Its fixtures pin zero,
nonlethal, lethal/overkill, contribution, lifesteal, XP, hitsplat, terminal
callback, and transaction-result parity across both primary melee event
classes. They also exercise NPC-to-player settlement through Goblin's Tenacity
and preserve its distinct factual HP loss, displayed hit, and historical
post-hit hook value. Only the primary Hits/damage-update/hitsplat block moves;
the bounded authority contract is recorded in
[`combat-a05-direct-melee-damage-transaction.md`](combat-a05-direct-melee-damage-transaction.md).

A05.3 grows the gate from 38 to 42 scenarios. Its fixtures pin all maintained
player-ranged, thrown, player-magic, NPC, summon, Iban, and cannon primary
projectile variants; zero and overkill settlement; exact contribution, XP,
hitsplat, terminal callback, and transaction metadata; and shared-Hits
compatibility for both projectile styles. An executable exclusion fixture also
proves non-primary compatibility and chain-lightning damage remain outside the
transaction. Only the adjacent primary-impact Hits/damage-update/hitsplat block
moves; the boundary is recorded in
[`combat-a05-primary-projectile-damage-transaction.md`](combat-a05-primary-projectile-damage-transaction.md).

A05.4 grows the gate from 42 to 46 scenarios without moving production damage
authority. Its representative fixtures pin the compatibility helper's
death-before-presentation order, poison/burn distinctions, secondary
Magic/combat contribution, Frostbite/Thorns attribution, hitsplats, death
callback cardinality, and delayed god/Iban helper/chase behavior. The exhaustive
inventory and deliberately split follow-up families are recorded in
[`combat-a05-secondary-damage-characterization.md`](combat-a05-secondary-damage-characterization.md).
