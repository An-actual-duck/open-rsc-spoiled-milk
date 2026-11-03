# Static Analysis Baseline

## Contract

Static analysis protects new and changed code without requiring a repository-
wide cleanup. Existing whole-program javac and SpotBugs findings are stored as
stable fingerprints. Checkstyle, PMD, ShellCheck, and Ruff fail only when a
finding intersects lines added or changed relative to the selected Git base.
CPD is report-only.

SpotBugs reads the exact Ant-built jars and uses their dependency classpaths,
but `-onlyAnalyze` is limited to the maintained `orsc`/`com.openrsc`
application packages. Vendored classes merged into the client and server fat
jars are type-resolution inputs, not repository-owned findings.
The initial include filter is limited to correctness and multithreaded-
correctness categories plus reviewed equals/hashCode, swallowed-failure,
broad-catch, and resource-lifecycle patterns. Style, performance, exposure,
internationalization, and unused-code findings remain outside this gate.

No formatter runs, no generated source is rewritten, and no analyzer result is
evidence that a reflection target, plugin, protocol implementation, asset, or
configuration-only feature is dead.

## Scope

| Product | Java roots | Ant build | SpotBugs input |
| --- | --- | --- | --- |
| Desktop client | `Client_Base/src`, `PC_Client/src` | `scripts/build-client.sh` | `Client_Base/Open_RSC_Client.jar` |
| Server | `server/src`, `server/plugins` | `scripts/build-server.sh` | `server/core.jar`, `server/plugins.jar` |
| World Builder tools | `tools/world-builder/src` | `scripts/build-world-builder-tools.sh` | `output/builds/world-builder/world-builder-tools.jar` |

`tools/myworld/ExportBasicProjectileSheets.java` is source-analyzed when it
changes, but is not a compiled product because it has no maintained build
contract. Maintained Python under `scripts`, `tests/myworld`, and
`tools/generators` is checked. Maintained shell files outside the excluded
roots are checked, including player and World Builder release launchers.

The default exclusions are:

- `legacy/`;
- bundled tools under `tools/vendor/` and `server/inc/ant/`;
- build output and ignored local state;
- the generated Java catalogs enumerated in `scripts/lint.py`.

World Builder process, transaction, packaging, and editor tests are dedicated
suites. They are not transitively covered by `tests/myworld/test-all.sh`.

## Toolchain And Runtime

`config/static-analysis/tools.json` pins the exact Checkstyle, PMD, SpotBugs,
ShellCheck, and Ruff release assets and SHA-256 digests. Downloads are stored
outside Git under `${XDG_CACHE_HOME:-~/.cache}/spoiled-milk/static-analysis`.
Override that path with `SPOILED_MILK_LINT_CACHE`.

The production compilation lane remains Java 8 with source/target 1.8. The
analysis tools run under Java 21 or newer; set `ANALYSIS_JAVA_HOME` when Java
21 is not the current runtime. Analyzer runtime selection does not change the
produced bytecode target.

Prepare and prove the cache:

```bash
./scripts/lint.sh bootstrap
./scripts/lint.sh bootstrap --offline
./scripts/lint.sh self-test --offline
```

The self-test feeds deliberate violations to every configured analyzer and
fails unless each tool detects its fixture.

## Normal Use

Build all products with full compiler warnings and enforce only new gated
warning fingerprints:

```bash
./scripts/lint.sh compiler --base spoiled-milk/main
```

Analyze the existing Ant-built jars and changed source lines:

```bash
./scripts/lint.sh analyze --base spoiled-milk/main
```

Run both phases locally:

```bash
./scripts/lint.sh all --base spoiled-milk/main
```

Use `--offline` after the verified cache is populated. Machine-readable XML,
JSON, logs, fingerprints, and run metadata are written beneath
`output/diagnostics/static-analysis/` by default. `--reports` may select another ignored
output directory.

For an occasional full, report-only source scan plus whole-program comparison:

```bash
./scripts/lint.sh report --base spoiled-milk/main
```

## Baseline Updates

Baseline replacement is always explicit:

```bash
./scripts/lint.sh compiler --update-baseline
./scripts/lint.sh analyze --update-baseline
```

The first command replaces only the selected javac warning fingerprints. The
second replaces only the SpotBugs fingerprints and requires current Ant-built
artifacts. Review every addition and removal. A finding should leave the
baseline in the same focused commit that fixes it; never regenerate either
file merely to make CI pass.

Fingerprint paths and members intentionally make moves visible. Renaming a
file or member with an unresolved finding creates a new fingerprint that
requires review rather than silently carrying or discarding the exception.
Javac fingerprints also retain normalized source context, and both baselines
retain occurrence counts so repeated findings cannot collapse into one entry.

## CI Lanes

`.github/workflows/static-analysis.yml` has two jobs:

1. Java 8 runs the three authoritative Ant builds with `-Xlint:all`, compares
   the compiler baseline, runs the repository contract test, and uploads the
   exact jars.
2. Java 21 downloads those jars, verifies the cached tools with intentional
   fixtures, enforces changed-line source checks, compares SpotBugs results,
   and uploads all reports even when a gate fails.

Existing findings remain visible in reports but do not block unrelated work.
Formatting, dependency upgrades, warning cleanup, and analyzer-rule expansion
remain separate follow-up branches.
