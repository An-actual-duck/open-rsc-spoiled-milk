# Server Build Source of Truth

## Current authority

The production server build is the repository-bundled Apache Ant 1.10.5
launcher executing [`server/build.xml`](../../../server/build.xml). This is the
authority for development builds, private startup, hosted startup, smoke tests,
and the server artifacts used by the live checkout.

[`server/build.gradle`](../../../server/build.gradle) is **secondary and
non-authoritative**. It is retained for compatibility and possible future
parity work, but its dependency graph, source layout, and default artifacts are
not production-equivalent. No server deployment or release decision should use
a successful Gradle task as proof that the Ant path works.

No dependency upgrade, build-system migration, or fat-jar redesign is implied
by this document. Those require separate branches.

## Operational build, run, deployment, and release chain

| Purpose | Maintained entry point | Actual server build path |
| --- | --- | --- |
| Build core and plugins | `scripts/build-server.sh` | generated-data check, then bundled Ant `compile_core` and `compile_plugins` |
| Private development | `scripts/run-server.sh` | safety/config checks, then bundled Ant `compile-and-run -DconfFile=myworld` |
| Private ZGC run | `scripts/run-server-zgc.sh` | bundled Ant `runserverzgc`; it expects artifacts already built |
| Fresh private world | `scripts/start-fresh.sh` | checks, SQLite initialization, `build-server.sh`, then `run-server.sh` |
| Hosted alpha | `scripts/run-hosted-server.sh` | detached/published-live and database checks, then bundled Ant `compile-and-run -DconfFile=myworld-host` |
| Desktop live launcher | `Launch Live Server.sh` | delegates to the fixed live checkout's `scripts/run-hosted-server.sh` |
| Smoke test | `tests/myworld/test-smoke.sh` | `build-server.sh`, then the private Ant run wrapper with a bounded timeout |
| Worker/dev aliases | `dev/myworld/compile-server.sh`, `dev/myworld/run-server.sh`, `dev/myworld/run-server-zgc.sh` | delegate to the maintained scripts above |
| Standalone private server | `scripts/private-server/server.sh` and `.bat` | bundled Ant `compile-and-run` with the SQLite dev database |

`server/compile_server.sh` is an inherited, superseded helper: it invokes a
system `sudo ant` and then references a nonexistent `run_server.sh`. It is not a
maintained or authoritative entry point. Use `scripts/build-server.sh` or
`scripts/run-server.sh`.

Live deployment and player packaging have different roles:

- `scripts/deploy-live-main.sh` deploys a clean detached source checkout. It
  does not copy a prebuilt server jar. Hosted startup then rebuilds the two
  server artifacts through Ant.
- `scripts/ai-manager.sh release-check` verifies repository/workspace state;
  it does not build server artifacts.
- `scripts/package-player-release.sh` packages the client and client assets
  only. `core.jar`, `plugins.jar`, and `server/lib` are not player-package
  inputs. Server changes still require the Ant build and hosted/smoke checks,
  but do not change the player archive layout.

## Active source roots and produced artifacts

Ant compiles two active Java source roots separately:

| Source root | Current count | Ant target | Artifact | Shape |
| --- | ---: | --- | --- | --- |
| `server/src` | 940 Java files | `compile_core` | `server/core.jar` | Executable fat jar with `com.openrsc.server.Server` as `Main-Class` and every `server/lib/*.jar` merged into it |
| `server/plugins` | 492 Java files | `compile_plugins` | `server/plugins.jar` | Plugin-class jar compiled against `core.jar`; no dependency jars are merged |

The one non-Java file under the plugin source root is a contributor README and
is not copied into `plugins.jar` by the current Ant target. Runtime definitions,
configuration, maps, SQLite databases, and other files under `server/conf`,
`server/database`, and `server/inc` are runtime inputs rather than compiled
source roots.

Plugin discovery intentionally remains external to the main classpath.
`PluginJarLoader` opens `./plugins.jar` through a `URLClassLoader`, enumerates
its top-level classes, and `PluginHandler` registers their implemented trigger
interfaces. A correct build therefore requires both artifacts in the server
working directory even though only `core.jar` appears in Ant's JVM classpath.

After a clean authoritative build at the time of this inventory:

- `core.jar` contained 18,230 entries and 17,138 class entries, including the
  server main class, plugin loader, and merged dependency classes.
- `plugins.jar` contained 841 entries and 725 class entries, including the
  separately compiled gameplay plugins and no `Server.class`.

The reproducible check described below recalculates these properties rather
than relying on the recorded counts.

## Shipped Ant dependency and classpath inventory

All 21 jars under `server/lib` are tracked repository inputs. `compile_core`
names 20 directly; `log4j-slf4j18-impl-2.17.0.jar` is not a direct compiler
entry. The `zipgroupfileset` still merges **all 21** into `core.jar`, and both
runtime targets include `server/lib/*`, so every shipped jar is also supplied
externally at runtime.

| Shipped jar | Direct `compile_core` entry | Included in fat jar | Included by runtime wildcard |
| --- | --- | --- | --- |
| `JDA-4.0.0_55-withDependencies.jar` | yes | yes | yes |
| `commons-codec-1.14.jar` | yes | yes | yes |
| `commons-compress-1.18.jar` | yes | yes | yes |
| `commons-lang-2.6.jar` | yes | yes | yes |
| `commons-lang3-3.12.0.jar` | yes | yes | yes |
| `disruptor-3.3.11.jar` | yes | yes | yes |
| `emoji-java-5.1.1.jar` | yes | yes | yes |
| `gitlab4j-api-4.12.17.jar` | yes | yes | yes |
| `guava-30.1.1-jre.jar` | yes | yes | yes |
| `guice-5.0.2-jar-with-dependencies.jar` | yes | yes | yes |
| `json-20190722.jar` | yes | yes | yes |
| `log4j-api-2.17.0.jar` | yes | yes | yes |
| `log4j-core-2.17.0.jar` | yes | yes | yes |
| `log4j-iostreams-2.17.0.jar` | yes | yes | yes |
| `log4j-slf4j18-impl-2.17.0.jar` | no | yes | yes |
| `mysql-connector-j-9.4.0.jar` | yes | yes | yes |
| `netty-all-4.1.33.Final.jar` | yes | yes | yes |
| `slf4j-nop-2.0.0-alpha5.jar` | yes | yes | yes |
| `sqlite-jdbc-3.34.0.jar` | yes | yes | yes |
| `xpp3-1.1.4c.jar` | yes | yes | yes |
| `xstream-1.4.18.jar` | yes | yes | yes |

`compile_plugins` uses `core.jar`, the ambient Java classpath, Log4j API/core,
and Commons Lang 2.6. Because `core.jar` is already the Ant-built fat jar, it
also exposes the dependency classes needed while compiling plugins.

Both `runserver` and `runserverzgc` currently put selected jars first, then
`server/lib/*`, and finally `core.jar`. The external jar copies therefore win
normal class lookup, while the fat jar contains another copy. The inventory
measured 14,070 unique external library classes that were also present in
`core.jar`, plus 2,065 duplicate class inputs between the library jars
themselves (primarily because the JDA and Guice jars bundle dependencies).
This is documented debt, not a request to alter packaging on this branch.

### Proven obsolete missing entries removed

The Ant file previously named jars that do not exist:

- `disruptor-3.3.0.jar` and `disruptor-3.3.5.jar`;
- `xpp3_min-1.1.4c.jar`;
- `xstream-1.4.9.jar`.

They were removed only from the explicit classpath lists. Their required
classes remain available through the shipped replacements:

- Disruptor 3.3.11 is a direct core compile dependency, is merged into
  `core.jar`, and is present through the runtime wildcard. Plugin compilation
  receives it from `core.jar`.
- `xpp3-1.1.4c.jar` and XStream 1.4.18 are direct core compile dependencies,
  merged into `core.jar`, and present through the runtime wildcard.

The authoritative core/plugin build succeeds after removal, and the inventory
check verifies representative Disruptor, XPP3, and XStream classes in the
resulting `core.jar`. No tracked jar was deleted or upgraded.

## Gradle drift and retained-task meaning

Gradle declares `server/src` and `server/plugins` together as one main source
set, unlike Ant's separate artifacts. Its ordinary Java/Application artifacts
therefore do not establish `core.jar`/`plugins.jar` parity. `ant.importBuild`
also exposes the Ant targets through the Gradle wrapper; those imported targets
are still Ant work, but that does not make Gradle's dependency resolution,
`build`, `run`, or test runtime authoritative.

Known declaration differences include:

| Dependency | Shipped Ant input | Gradle declaration | Status |
| --- | --- | --- | --- |
| Emoji Java | 5.1.1 | 4.0.0 | version drift |
| XStream | 1.4.18 | 1.4.9 | version drift |
| Guice | filename identifies 5.0.2 fat jar | 5.0.1 | version and packaging drift |
| Netty All | 4.1.33.Final | 4.1.107.Final | version drift |
| Log4j SLF4J binding | `log4j-slf4j18-impl` 2.17.0 | `log4j-slf4j-impl` 2.17.0 | artifact drift |
| Commons Collections 4 | no standalone shipped jar (bundled transitively in fat inputs) | 4.0 | graph/packaging drift |
| SLF4J NOP | explicit 2.0.0-alpha5 shipped jar | no direct declaration | graph drift |

JDA's local jar, GitLab4J, MySQL connector, SQLite JDBC, Disruptor,
Commons Lang/Compress/Codec, Log4j API/core/iostreams, JSON, XPP3, and Guava have
matching top-level declared versions. Matching declarations do not prove the
same transitive graph because Ant consumes checked-in fat and ordinary jars
while Gradle resolves Maven metadata.

The Gradle file is explicitly labeled secondary. Its `run` and `test` task
descriptions state that they do not imply production-Ant artifact or packaging
parity. Dependency upgrades or a Gradle parity/migration decision must be done
separately.

## Reproducible validation

Run:

```bash
./scripts/build-server.sh
python3 scripts/audit-server-build.py --check --require-artifacts
```

Add `--json` for machine-readable output. The audit:

- parses `server/build.xml`, not Gradle, for expected source roots,
  classpaths, wildcard coverage, and artifacts;
- fails on missing explicit Ant jar entries;
- inventories every shipped jar with size and SHA-256;
- verifies the runtime targets retain `server/lib/*` and `core.jar`;
- verifies `core.jar` main/plugin-loader/dependency classes and the separate
  plugin artifact shape;
- measures fat-jar/runtime class duplication;
- reports Gradle declarations as observed drift rather than expected values;
- guards the maintained wrappers' bundled-Ant calls and `./plugins.jar`
  discovery boundary.

The test suite runs the same check through
`tests/myworld/test-server-build-source-of-truth.py`.
`scripts/build-server.sh` also runs the checked artifact inventory after both
Ant targets, so the normal production build exposes classpath drift immediately.

## Runtime verification and limits

The supported local runtime proof is SQLite with `myworld.conf`. If the normal
private ports are occupied, copy that config to a temporary file, change both
loopback ports, and pass the temporary absolute config stem to Ant. Do not edit
the hosted config or bind a test server publicly. A successful proof reaches
`Definitions Completed`, `World Completed`, and `Plugins Completed` before a
bounded shutdown.

MySQL runtime testing is optional for this work. No isolated MySQL credentials
or disposable MySQL environment are committed to the repository, so this
branch records that limitation and does not infer MySQL runtime success from a
compile or from the presence of the connector jar.
