# Legacy Player location bootstrap — Core review

Status: **READY**. Tested implementation checkpoint:
`6457deed9229e2a8ffb77737e7fabb5409a19f38`; the final handoff adds review/evidence
only. Based on published `94fcee395717716b318d7b6795776ec566df1b98`.

## Confirmed defect and focused change

Before production edits, the actual LoginRequest and complete
PlayerService.loadPlayer flow reproduced the owner's report using fresh
synthetic SQLite rows and newly generated native terrain. Stored `(109,3509)`
with no `layerloc_*` keys loaded at signed level **0**, `(109,677)`, instead of
level **-1**. The original account/map/private candidate was not read or changed.

Native initialization had correctly interpreted the original packed database
Point, then normalized the runtime Point for native rendering. Later layered
restore received that mutable runtime projection as though it was the original
database receipt, losing its level band during bootstrap and stale-record checks.

The production change is confined to PlayerService: loadPlayerData returns its
already-created immutable LegacyPlayerLocationPersistenceSnapshot, retained in a
local variable for that one load transaction and passed to layered restoration.
Restore compares its original packed Point. The runtime projection, saved cache
format, migrations, terrain fail-safe, death/logout and save paths are unchanged.
There is no mutable/global per-service receipt.

## Verification

All heavy commands used the shared `/home/justin/.local/share/core-heavy-build.lock`,
Java 8, 512 MiB and SerialGC. Final commands:

```bash
# JAVA_HOME=/home/justin/jdk8u482-b08
# JAVA_TOOL_OPTIONS and ANT_OPTS: -Xmx512m -XX:+UseSerialGC
sh tools/vendor/apache-ant-1.10.5/bin/ant -f server/build.xml \
  test_legacy_player_location_bootstrap test_combat_strict test_slow
python3 tests/myworld/test-market-atomicity.py
```

- New full account-load fixture: **57 assertions**. Covers missing cache for
  levels 0/1/2/-1; complete classic records; an authoritative native level -3
  record; rebasing from a stale complete record to original stored level 2;
  partial, malformed and divergent receipt refusal; and different accounts loaded
  through one service.
- Full source compilation; combat **145/145**, including death/relog/logout
  lifecycle characterizations.
- Slow **115 boundary assertions**, adapters, committed deadlines, legacy cadence
  and retained damage/RNG checks.
- Existing real-SQLite auction/login regression **252 + 4 + 4 assertions**, including
  pre/postcommit process crash/recovery and account settlement admission.

The new fixture reuses F5's synthetic non-economic profile/authentication scaffold
but runs actual LoginRequest.processInternal and complete PlayerService.loadPlayer.
Its stored locations, caches and economic containers are fresh SQLite rows; cache
and economic reads use production JDBC queries. The terrain package is newly
generated with explicit empty placements. Networking/listeners are denied, and
the runner requires a completion marker and rejects unexpected scheduler errors.
MarketAtomicityFixture.LoginDatabase merely becomes subclassable for this reuse;
no market implementation changes occur.

Pre-fix proof: `output/tests/player-location-bootstrap/2ca2533470/login.log`.
Corrected proof: `output/tests/player-location-bootstrap/9f608174e7/login.log`.
Regression log: `/tmp/location-bootstrap-regressions.log`.
Auction regression: `output/tests/market-atomicity/85327b5436/`.
Hashes and result metadata are in `legacy-player-location-bootstrap-evidence.json`.

Limits: no public/private server operations, real account databases, owner map
edits or socket/password testing. Synthetic profile/authentication scaffolding is
not a claim of authentication coverage. This is an account-load receipt fix;
existing migration and terrain-recovery policy remains intact.
