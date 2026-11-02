# Slayer release task activation review

Task branch: `fix/slayer-release-task-activation`
Baseline: `807afc243ca497896a60c97183283980af7a8cbd`
Status: implementation and offline gates passed; ready for exact manager handoff.

## Result

Both next-update production profiles (`server/myworld.conf` and
`server/myworld-host.conf`) enable the finished tower roster. All eight tower
creatures now participate in mandatory and ordinary repeatable assignments.
Death wings (NPC 768) receive `champions.death_wings` and
`champions.death_wings.repeatable`: 20 kills, 18 Elite points, repeatable weight 1.
The mandatory assignment comes before Greater Demons, after Terror Dogs and
Bloodvelds. This modest fourth-tier reward matches Terror Dogs and lies between
Lesser Demons (16) and Bloodvelds/Greater Demons (20).

Six selected-map Death wings live on tower level 3, so the old Legends Quest
restriction no longer prevents this assignment. All eight original underground
Death wings remain. No NPC definition, spawn, map, combat stat, shop, ingredient,
asset, toolkit, guide, live process or database was changed.

## Saved progress and historical inputs

The new task lives in a separate server-owned `MonsterSlayerRelease.json`.
`MonsterSlayerData` applies it only after constructing the frozen v4 roster,
giving the enabled release roster version **5**. The supported explicit-disabled
profile stays version **3**; unspecified/classic profiles still default to false.

| Roster | Meaning | Release handling |
| --- | --- | --- |
| v1 | Original guild | Validated against its original task identities, then upgraded |
| v2 | Historical tower expansion | Same original validation, then upgraded |
| v3 | Retired old families, tower disabled | Same original validation, then upgraded |
| v4 | Retired old families, tower enabled | Same original validation, then upgraded |
| v5 | Finished-map release plus Death wings | Current enabled profile |

The existing stable-identity migration algorithm is unchanged. It retains
surviving completed task IDs and partial active-task kills. Removed retired
tasks are cleared without a reward. Earned ranks grandfather their newly added
mandatory entries, preserving ranks, points, lifetime task count, inventory
entitlements, old legacy status/prestige and promotion acknowledgements. A
surviving in-flight assignment continues even if an added task belongs earlier
in its tier. After it finishes, the first unfinished task is selected
deterministically. An unfinished Champions tier now requires Death wings before
promotion; an existing Champion or higher keeps that standing and can receive
Death wings from the repeatable pool. No duplicate reward is paid at migration
or reconnect. Enabling is an upgrade, not a reversible configuration toggle for
already upgraded accounts: downgrading to v3 quarantines their state without
rewriting it.

Historical definition bytes are unchanged from the baseline:

- `MonsterSlayer.json`: `fedb45259186a4d6c741ae298e79c8ce1b4e66244e52dd39bb39a5bebb14b11a`
- `MonsterSlayerTower.json`: `91972baf6cd02041bf6ab3b4f70f88d0b02a63f107e3edbc8ee7b24e5e8c564a`

## Focused validation

Run from this checkout under the shared heavy-build lock, with the vendored Ant:

```bash
flock /home/justin/.local/share/core-heavy-build.lock env \
  JAVA_HOME=/home/justin/jdk8u482-b08 \
  ANT_HOME="$PWD/tools/vendor/apache-ant-1.10.5" \
  ANT_OPTS='-Xmx512m -XX:+UseSerialGC' \
  JAVA_TOOL_OPTIONS='-Xmx512m -XX:+UseSerialGC' \
  sh tools/vendor/apache-ant-1.10.5/bin/ant -f server/build.xml \
  test_slayer_release test_slayer_retirement \
  -Dslayer.package.path=/home/justin/spoiled-milk-next-update/server-candidate/server/world-builder/packages/e343c03b026920ab0f6dd6e9fdd8d9601d1992177f24510f1d21a266e22e58cf/package \
  -Dslayer.manifest.sha256=c12686677f3bc49956af7957e1a7ec2916cffa622c4e4af8290c11a6451bacbb
```

`test_slayer_release` copies configurations, definitions and the reviewed package
into ignored `output/tests/slayer-release/runtime`. It never uses an owner
`local.conf`, server listeners, account data or JDBC. A security guard rejects
network access, writes outside the copied fixture and explicit process exit.
The target requires a newly written final success receipt, so an early exit
cannot be accepted as success. Runtime logging uses an explicitly disabled JMX
registration in this fixture only; result output bypasses redirected logging.

- New migration characterization: **11,369 assertions across 364 synthetic
  v1-v4 upgrades**, all historical mandatory boundaries (idle and one kill short
  of completion), repeatables, unstamped and fully completed characters. Includes
  mixed promotion acknowledgement bits, read-only proposals, production
  initialization, relog idempotence, partial progress, deterministic pending
  selection, retired IDs, unchanged currency/entitlements and exact-once payout.
- Existing original player-state and v1/v2 tower characterization: **PASS**.
- Real contact plugin Talk-to and Task routes cover all nine added assignments,
  their preparation/reminder text and deterministic repeatable selection:
  **PASS**. The old assertion requiring a disabled production profile was updated
  to assert the now-enabled release profile.
- Exact copied e343 runtime: **2,748 assertions**, actual config loaders,
  supported classic/disabled profiles, real definition/placement preflight and
  `loadForWorld`, all eight tower creature spawns, six tower plus eight old Death
  wings, all six real contacts, full mandatory progression through their Task
  callbacks to Legend, new repeatable pools, eligible-kill credit, no extra
  reconnect rewards: **PASS**.
- Adjacent Slayer retirement target: **exit 0**. Because its existing server
  logging swallowed the visible final line, an independent ignored
  `RetirementEvidence.java` wrapper invoked the same fixture's `main`, rejected
  explicit `System.exit` (including the inherited `DropTable.exit(0)` path),
  rejected network/listeners, and printed through the original stream only
  after normal return: **PASS**. No adjacent fixture or production workaround
  was needed. Commands and raw output are preserved below.
- `git diff --check`: **PASS**. Server core, gameplay overlay and plugins build
  successfully. Existing unchecked/Unsafe/deprecated test-helper warnings remain;
  no broad lint or full combat gate was rerun for this task.

The retirement return check, after the combined Ant invocation above, was:

```bash
flock /home/justin/.local/share/core-heavy-build.lock \
  /home/justin/jdk8u482-b08/bin/javac -J-Xmx512m -J-XX:+UseSerialGC \
  -cp 'output/tests/combat/test-classes:output/tests/combat/core-classes:server/plugins.jar:server/lib/*' \
  -d output/reviews/slayer-release-evidence output/reviews/slayer-release-evidence/RetirementEvidence.java
cd server
flock /home/justin/.local/share/core-heavy-build.lock \
  /home/justin/jdk8u482-b08/bin/java -Xmx512m -XX:+UseSerialGC \
  -cp '../output/reviews/slayer-release-evidence:../output/tests/combat/test-classes:../output/tests/combat/core-classes:plugins.jar:lib/*' \
  RetirementEvidence
```

Evidence logs and copied synthetic runtime remain under
`output/reviews/slayer-release-evidence/` and `output/tests/slayer-release/` in this worker.
Initial fixture logging under the security guard obscured the success output;
the final target now requires the independent success receipt and prints through
the original stream. This was fixture hardening, not a production workaround.

## Review limits and release pairing

The target proves server-side assignment, config, selected map population and
synthetic-cache semantics. It does not claim live-account database upgrade,
client visuals, a player fighting all targets, network login/restart or public
deployment. The manager will repeat final copied-account upgrade and combined
private acceptance before release. No server was launched, stopped or restarted,
and no staged candidate or owner editor save was changed in this worker.

Deploy these enabled profiles only together with the reviewed finished map and
the matching source/artifacts. The strict loader still rejects a map missing
required family spawns; it does not fabricate access or silently drop tasks.
